package com.apicontract.gateway.serialization;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.serialization.SerializationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 默认序列化实现（网关侧）：联调、内部后台场景可读可抓包；
 * 编解码失败抛 {@link SerializationException}（映射 SERIALIZE_ERROR 400），
 * 不静默。
 */
public final class JsonContractSerializer implements ContractSerializer {

  public static final JsonContractSerializer INSTANCE = new JsonContractSerializer();

  /** 小数按 BigDecimal 精确解析（防止 readTree 降级 double 截断精度）。 */
  private final ObjectMapper mapper = new ObjectMapper()
      .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  @Override
  public String name() {
    return ContractMeta.SERIALIZER_JSON;
  }

  @Override
  public byte[] encodeArguments(Object[] arguments) {
    try {
      return mapper.writeValueAsBytes(arguments == null ? new Object[0] : arguments);
    } catch (Exception failure) {
      throw new SerializationException("JSON 参数编码失败", failure);
    }
  }

  @Override
  public Object[] decodeArguments(byte[] payload, Class<?>[] parameterTypes) {
    try {
      JsonNode array = mapper.readTree(payload);
      if (array == null || !array.isArray()) {
        throw new SerializationException("JSON 请求体不是参数数组");
      }
      if (array.size() != parameterTypes.length) {
        throw new SerializationException(
            "JSON 参数个数不匹配: 请求 " + array.size() + " / 契约 "
                + parameterTypes.length);
      }
      Object[] arguments = new Object[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        arguments[i] = mapper.convertValue(array.get(i),
            mapper.constructType(parameterTypes[i]));
      }
      return arguments;
    } catch (SerializationException explicit) {
      throw explicit;
    } catch (Exception failure) {
      throw new SerializationException("JSON 参数解码失败", failure);
    }
  }

  @Override
  public byte[] encodeResult(Object result) {
    try {
      return mapper.writeValueAsBytes(result);
    } catch (Exception failure) {
      throw new SerializationException("JSON 返回值编码失败", failure);
    }
  }

  @Override
  public Object decodeResult(byte[] payload, Class<?> returnType) {
    try {
      JsonNode node = mapper.readTree(payload);
      if (node == null || node.isNull()) {
        return null;
      }
      return mapper.convertValue(node, mapper.constructType(returnType));
    } catch (Exception failure) {
      throw new SerializationException("JSON 返回值解码失败", failure);
    }
  }
}
