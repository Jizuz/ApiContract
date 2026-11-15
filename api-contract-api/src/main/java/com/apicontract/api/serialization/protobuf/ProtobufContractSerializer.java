package com.apicontract.api.serialization.protobuf;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.serialization.SerializationException;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Protobuf 序列化实现（网关↔Provider 内部链路，前端恒 JSON）：
 * 参数数组/返回结果经统一信封（Envelope{repeated Value argument,
 * optional Value result}）承载；DTO 参数按 schemaRef（= DTO 类型注册的
 * {@link ContractProtoSchemas} 声明）以 DynamicMessage 编解码。
 *
 * <p>失败不静默：任何编解码异常抛 {@link SerializationException}
 * （网关 SERIALIZE_ERROR / Provider CONTRACT_INTERNAL_ERROR）。
 */
public final class ProtobufContractSerializer implements ContractSerializer {

  private static final Descriptors.FileDescriptor ENVELOPE_FILE =
      buildEnvelopeFile();
  private static final Descriptors.Descriptor VALUE_DESCRIPTOR =
      ENVELOPE_FILE.findMessageTypeByName("Value");
  private static final Descriptors.Descriptor ENVELOPE_DESCRIPTOR =
      ENVELOPE_FILE.findMessageTypeByName("Envelope");
  private static final Descriptors.FieldDescriptor F_LONG =
      field("long_value");
  private static final Descriptors.FieldDescriptor F_STRING =
      field("string_value");
  private static final Descriptors.FieldDescriptor F_BOOL =
      field("bool_value");
  private static final Descriptors.FieldDescriptor F_DOUBLE =
      field("double_value");
  private static final Descriptors.FieldDescriptor F_MESSAGE_BYTES =
      field("message_value");
  private static final Descriptors.FieldDescriptor F_DECIMAL =
      field("decimal_value");
  private static final Descriptors.FieldDescriptor F_NULL =
      field("null_value");
  private static final Descriptors.FieldDescriptor F_ARGUMENT =
      envelopeField("argument");
  private static final Descriptors.FieldDescriptor F_RESULT =
      envelopeField("result");

  private final ContractProtoSchemas schemas;

  public ProtobufContractSerializer(ContractProtoSchemas schemas) {
    this.schemas = schemas;
  }

  @Override
  public String name() {
    return ContractMeta.SERIALIZER_PROTOBUF;
  }

  @Override
  public byte[] encodeArguments(Object[] arguments) {
    try {
      DynamicMessage.Builder envelope = DynamicMessage.newBuilder(ENVELOPE_DESCRIPTOR);
      for (Object argument : arguments == null ? new Object[0] : arguments) {
        envelope.addRepeatedField(F_ARGUMENT, valueOf(argument));
      }
      return envelope.build().toByteArray();
    } catch (SerializationException explicit) {
      throw explicit;
    } catch (Exception failure) {
      throw new SerializationException("Protobuf 参数编码失败", failure);
    }
  }

  @Override
  public Object[] decodeArguments(byte[] payload, Class<?>[] parameterTypes) {
    try {
      DynamicMessage envelope = parseEnvelope(payload);
      int count = envelope.getRepeatedFieldCount(F_ARGUMENT);
      if (count != parameterTypes.length) {
        throw new SerializationException("Protobuf 参数个数不匹配: 请求 "
            + count + " / 契约 " + parameterTypes.length);
      }
      Object[] arguments = new Object[parameterTypes.length];
      for (int i = 0; i < count; i++) {
        arguments[i] = topValue(
            (DynamicMessage) envelope.getRepeatedField(F_ARGUMENT, i),
            parameterTypes[i]);
      }
      return arguments;
    } catch (SerializationException explicit) {
      throw explicit;
    } catch (Exception failure) {
      throw new SerializationException("Protobuf 参数解码失败", failure);
    }
  }

  @Override
  public byte[] encodeResult(Object result) {
    try {
      DynamicMessage.Builder envelope = DynamicMessage.newBuilder(ENVELOPE_DESCRIPTOR);
      if (result != null) {
        envelope.setField(F_RESULT, valueOf(result));
      }
      return envelope.build().toByteArray();
    } catch (SerializationException explicit) {
      throw explicit;
    } catch (Exception failure) {
      throw new SerializationException("Protobuf 返回值编码失败", failure);
    }
  }

  @Override
  public Object decodeResult(byte[] payload, Class<?> returnType) {
    try {
      DynamicMessage envelope = parseEnvelope(payload);
      if (!envelope.hasField(F_RESULT)) {
        return null;
      }
      return topValue((DynamicMessage) envelope.getField(F_RESULT), returnType);
    } catch (SerializationException explicit) {
      throw explicit;
    } catch (Exception failure) {
      throw new SerializationException("Protobuf 返回值解码失败", failure);
    }
  }

  private DynamicMessage parseEnvelope(byte[] payload) throws Exception {
    return DynamicMessage.parseFrom(ENVELOPE_DESCRIPTOR, payload);
  }

  /** 顶层单值 → 信封 Value（oneof kind）。 */
  private DynamicMessage valueOf(Object argument) throws Exception {
    DynamicMessage.Builder value = DynamicMessage.newBuilder(VALUE_DESCRIPTOR);
    if (argument == null) {
      return value.setField(F_NULL, Boolean.TRUE).build();
    }
    if (argument instanceof Integer || argument instanceof Long) {
      return value.setField(F_LONG, ((Number) argument).longValue()).build();
    }
    if (argument instanceof String text) {
      return value.setField(F_STRING, text).build();
    }
    if (argument instanceof Boolean flag) {
      return value.setField(F_BOOL, flag).build();
    }
    if (argument instanceof Double number) {
      return value.setField(F_DOUBLE, number).build();
    }
    if (argument instanceof BigDecimal amount) {
      return value.setField(F_DECIMAL, amount.toPlainString()).build();
    }
    if (schemas.supports(argument.getClass())) {
      return value.setField(F_MESSAGE_BYTES,
          messageOf(argument).toByteArray()).build();
    }
    throw new SerializationException("Protobuf 形态不支持顶层集合/Map 参数"
        + "（请以 DTO 字段承载）: " + argument.getClass().getName());
  }

  /** 信封 Value → 目标类型 Java 值。 */
  private Object topValue(DynamicMessage value, Class<?> targetType)
      throws Exception {
    if (value.hasField(F_NULL)) {
      return null;
    }
    if (value.hasField(F_LONG)) {
      long raw = (Long) value.getField(F_LONG);
      if (targetType == int.class || targetType == Integer.class) {
        return Math.toIntExact(raw);
      }
      return raw;
    }
    if (value.hasField(F_STRING)) {
      return value.getField(F_STRING);
    }
    if (value.hasField(F_BOOL)) {
      return value.getField(F_BOOL);
    }
    if (value.hasField(F_DOUBLE)) {
      return value.getField(F_DOUBLE);
    }
    if (value.hasField(F_DECIMAL)) {
      return new BigDecimal((String) value.getField(F_DECIMAL));
    }
    if (value.hasField(F_MESSAGE_BYTES)) {
      Descriptors.Descriptor messageDescriptor =
          schemas.messageDescriptorOf(targetType);
      byte[] messageBytes =
          ((com.google.protobuf.ByteString) value.getField(F_MESSAGE_BYTES))
              .toByteArray();
      DynamicMessage message =
          DynamicMessage.parseFrom(messageDescriptor, messageBytes);
      return dtoFrom(message, targetType);
    }
    throw new SerializationException(
        "Protobuf 形态不支持的目标参数类型: " + targetType.getName());
  }

  /** DTO → DynamicMessage（按注册 schema 反射 public 字段）。 */
  private DynamicMessage messageOf(Object dto) throws Exception {
    ProtoContractSchema schema = schemas.of(dto.getClass());
    if (schema == null) {
      throw new SerializationException(
          "DTO 未注册 Protobuf schema（SCHEMA_REF_INVALID）: "
              + dto.getClass().getName());
    }
    DynamicMessage.Builder message =
        DynamicMessage.newBuilder(schemas.messageDescriptorOf(dto.getClass()));
    for (ProtoField field : schema.fields()) {
      Descriptors.FieldDescriptor descriptor =
          message.getDescriptorForType().findFieldByName(field.name());
      Object raw = javaField(dto.getClass(), field.name()).get(dto);
      if (raw == null) {
        continue;
      }
      if (field.isRepeated()) {
        for (Object element : (List<?>) raw) {
          message.addRepeatedField(descriptor, wireValue(element, field));
        }
      } else {
        message.setField(descriptor, wireValue(raw, field));
      }
    }
    return message.build();
  }

  /** DynamicMessage → DTO（反射构造并填充 public 字段）。 */
  private Object dtoFrom(DynamicMessage message, Class<?> dtoType)
      throws Exception {
    ProtoContractSchema schema = schemas.of(dtoType);
    if (schema == null) {
      throw new SerializationException(
          "DTO 未注册 Protobuf schema（SCHEMA_REF_INVALID）: " + dtoType.getName());
    }
    Object instance = dtoType.getDeclaredConstructor().newInstance();
    for (ProtoField field : schema.fields()) {
      Descriptors.FieldDescriptor descriptor =
          message.getDescriptorForType().findFieldByName(field.name());
      Field target = javaField(dtoType, field.name());
      if (field.isRepeated()) {
        List<Object> elements = new ArrayList<>();
        int count = message.getRepeatedFieldCount(descriptor);
        for (int i = 0; i < count; i++) {
          elements.add(javaValue(message.getRepeatedField(descriptor, i), field));
        }
        target.set(instance, elements);
      } else {
        target.set(instance, javaValue(message.getField(descriptor), field));
      }
    }
    return instance;
  }

  /** DTO 字段 Java 值 → wire 值（int/long/…/decimal/message）。 */
  private Object wireValue(Object raw, ProtoField field) throws Exception {
    return switch (field.type()) {
      case "int" -> ((Number) raw).intValue();
      case "long" -> ((Number) raw).longValue();
      case "string" -> (String) raw;
      case "boolean" -> (Boolean) raw;
      case "double" -> ((Number) raw).doubleValue();
      case "decimal" -> ((BigDecimal) raw).toPlainString();
      default -> messageOf(raw);
    };
  }

  /** wire 值 → DTO 字段 Java 值。 */
  private Object javaValue(Object wire, ProtoField field) throws Exception {
    return switch (field.type()) {
      case "int", "long", "string", "boolean", "double" -> wire;
      case "decimal" -> new BigDecimal((String) wire);
      default -> {
        String messageName = field.messageTypeName();
        ContractProtoSchemas.Registration registration =
            schemas.byMessageName(messageName);
        if (registration == null) {
          throw new SerializationException(
              "嵌套 message 未注册 schema: " + messageName);
        }
        yield dtoFrom((DynamicMessage) wire, registration.dtoType());
      }
    };
  }

  private static Field javaField(Class<?> type, String name) throws Exception {
    return type.getField(name);
  }

  private static Descriptors.FieldDescriptor field(String name) {
    return VALUE_DESCRIPTOR.findFieldByName(name);
  }

  private static Descriptors.FieldDescriptor envelopeField(String name) {
    return ENVELOPE_DESCRIPTOR.findFieldByName(name);
  }

  /** 信封描述符：Value{oneof kind} + Envelope{repeated argument, optional result}。 */
  private static Descriptors.FileDescriptor buildEnvelopeFile() {
    FileDescriptorProto.Builder file = FileDescriptorProto.newBuilder()
        .setName("contract_rpc_envelope.proto")
        .setSyntax("proto3");
    DescriptorProto.Builder value = file.addMessageTypeBuilder().setName("Value");
    value.addOneofDeclBuilder().setName("kind");
    value.addField(optional("long_value", 1, Type.TYPE_INT64, 0));
    value.addField(optional("string_value", 2, Type.TYPE_STRING, 0));
    value.addField(optional("bool_value", 3, Type.TYPE_BOOL, 0));
    value.addField(optional("double_value", 4, Type.TYPE_DOUBLE, 0));
    value.addField(optional("message_value", 5, Type.TYPE_BYTES, 0));
    value.addField(optional("decimal_value", 6, Type.TYPE_STRING, 0));
    value.addField(optional("null_value", 7, Type.TYPE_BOOL, 0));

    DescriptorProto.Builder envelope =
        file.addMessageTypeBuilder().setName("Envelope");
    envelope.addField(optional("argument", 1, Type.TYPE_MESSAGE, -1)
        .setLabel(Label.LABEL_REPEATED).setTypeName(".Value"));
    envelope.addField(optional("result", 2, Type.TYPE_MESSAGE, -1)
        .setTypeName(".Value"));
    try {
      return Descriptors.FileDescriptor.buildFrom(file.build(),
          new Descriptors.FileDescriptor[0]);
    } catch (Exception invalid) {
      throw new IllegalStateException("信封描述符构建失败", invalid);
    }
  }

  private static FieldDescriptorProto.Builder optional(String name, int number,
      Type type, int oneofIndex) {
    FieldDescriptorProto.Builder field = FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setLabel(Label.LABEL_OPTIONAL)
        .setType(type);
    if (oneofIndex >= 0) {
      field.setOneofIndex(oneofIndex);
    }
    return field;
  }
}
