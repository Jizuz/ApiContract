package com.apicontract.api.serialization.protobuf;

import com.apicontract.api.serialization.SerializationException;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 契约 DTO → Protobuf schema 注册表（schemaRef = DTO 类型）：
 * schema 随契约定义模块同版本分发（不经配置中心），注册后合成单一
 * FileDescriptor 供 {@link ProtobufContractSerializer} 动态编解码。
 */
public final class ContractProtoSchemas {

  record Registration(Class<?> dtoType, ProtoContractSchema schema) {
  }

  private record Registry(Map<Class<?>, Registration> byClass,
      Map<String, Registration> byMessageName) {

    static final Registry EMPTY = new Registry(Map.of(), Map.of());
  }

  private volatile Registry registry = Registry.EMPTY;
  private volatile Descriptors.FileDescriptor descriptor;

  /**
   * 注册 DTO schema（幂等：同类型同定义重复注册无害；
   * 同类型不同定义抛出冲突，防止多模块声明漂移）。
   */
  public synchronized void register(Class<?> dtoType, ProtoContractSchema schema) {
    Registration existing = registry.byClass.get(dtoType);
    if (existing != null) {
      if (existing.schema().equals(schema)) {
        return;
      }
      throw new IllegalArgumentException("DTO " + dtoType.getName()
          + " 已注册不同 schema 定义，禁止漂移");
    }
    Map<Class<?>, Registration> byClass = new LinkedHashMap<>(registry.byClass());
    Map<String, Registration> byMessageName =
        new LinkedHashMap<>(registry.byMessageName());
    Registration registration = new Registration(dtoType, schema);
    byClass.put(dtoType, registration);
    byMessageName.put(schema.messageName(), registration);
    registry = new Registry(Map.copyOf(byClass), Map.copyOf(byMessageName));
    descriptor = null;
  }

  /** 该 DTO 类型是否已注册 schema。 */
  public boolean supports(Class<?> dtoType) {
    return registry.byClass.containsKey(dtoType);
  }

  /** 取 DTO 的 schema 声明（未注册返回 null）。 */
  public ProtoContractSchema of(Class<?> dtoType) {
    Registration registration = registry.byClass.get(dtoType);
    return registration == null ? null : registration.schema();
  }

  /** 按 message 名定位注册项（嵌套 message 递归解码用）。 */
  Registration byMessageName(String messageName) {
    return registry.byMessageName.get(messageName);
  }

  /** schemaRef 有效性检查（启动一致性校验用）：message 是否已注册。 */
  public boolean isRegistered(String messageName) {
    return registry.byMessageName.containsKey(messageName);
  }

  /** 合成全部已注册 schema 的 FileDescriptor（懒构建、注册失效后重建）。 */
  Descriptors.FileDescriptor descriptor() {
    Descriptors.FileDescriptor current = descriptor;
    if (current == null) {
      synchronized (this) {
        current = descriptor;
        if (current == null) {
          current = buildDescriptor();
          descriptor = current;
        }
      }
    }
    return current;
  }

  /** 取 DTO 的 message Descriptor（未注册 → SCHEMA_REF_INVALID 语义异常）。 */
  Descriptors.Descriptor messageDescriptorOf(Class<?> dtoType) {
    ProtoContractSchema schema = of(dtoType);
    if (schema == null) {
      throw new SerializationException(
          "DTO 未注册 Protobuf schema（SCHEMA_REF_INVALID）: " + dtoType.getName());
    }
    Descriptors.Descriptor message =
        descriptor().findMessageTypeByName(schema.messageName());
    if (message == null) {
      throw new SerializationException(
          "Protobuf schema 中找不到 message: " + schema.messageName());
    }
    return message;
  }

  private Descriptors.FileDescriptor buildDescriptor() {
    FileDescriptorProto.Builder file = FileDescriptorProto.newBuilder()
        .setName("contract_schemas.proto")
        .setSyntax("proto3");
    registry.byMessageName.values().stream()
        .map(Registration::schema)
        .forEach(schema -> file.addMessageType(messageProtoOf(schema)));
    try {
      return Descriptors.FileDescriptor.buildFrom(file.build(),
          new Descriptors.FileDescriptor[0]);
    } catch (Exception invalid) {
      throw new SerializationException(
          "Protobuf schema 合成失败（message 引用可能未注册）", invalid);
    }
  }

  private static DescriptorProto messageProtoOf(ProtoContractSchema schema) {
    DescriptorProto.Builder message = DescriptorProto.newBuilder()
        .setName(schema.messageName());
    for (ProtoField field : schema.fields()) {
      FieldDescriptorProto.Builder proto = FieldDescriptorProto.newBuilder()
          .setName(field.name())
          .setNumber(field.number())
          .setLabel(field.isRepeated() ? Label.LABEL_REPEATED : Label.LABEL_OPTIONAL);
      String messageTypeName = field.messageTypeName();
      if (messageTypeName != null) {
        proto.setType(Type.TYPE_MESSAGE).setTypeName("." + messageTypeName);
      } else {
        proto.setType(wireTypeOf(field.type()));
      }
      message.addField(proto);
    }
    return message.build();
  }

  private static Type wireTypeOf(String declaredType) {
    return switch (declaredType) {
      case "int" -> Type.TYPE_INT32;
      case "long" -> Type.TYPE_INT64;
      case "string" -> Type.TYPE_STRING;
      case "boolean" -> Type.TYPE_BOOL;
      case "double" -> Type.TYPE_DOUBLE;
      case "decimal" -> Type.TYPE_STRING;
      default -> throw new SerializationException(
          "不支持的 Protobuf 字段类型声明: " + declaredType);
    };
  }
}
