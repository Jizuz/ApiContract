package com.apicontract.api.serialization;

import com.apicontract.api.spi.Spi;

/**
 * 契约序列化 SPI（spec contract-serialization）：网关↔Provider 链路按
 * 契约元数据声明的序列化方式编解码；前端↔网关恒 JSON（网关完成形态
 * 转换，决策③）。
 *
 * <p>实现要求：编解码失败必须抛出 {@link SerializationException}
 * （网关侧映射 SERIALIZE_ERROR / Provider 侧映射 CONTRACT_INTERNAL_ERROR），
 * 禁止降级为静默空结果；已注册实现的 {@link #name()} 不可重复。
 */
@Spi(name = "契约序列化器", defaultImpl = "json")
public interface ContractSerializer {

  /** 序列化方式名（与契约元数据 serializer 字段一致，如 {@code json}）。 */
  String name();

  /**
   * 编码参数数组（网关→Provider 请求体）。
   *
   * @param arguments 按契约参数顺序组织的实参（null 视为空数组）
   */
  byte[] encodeArguments(Object[] arguments);

  /**
   * 解码参数数组（Provider 侧按声明类型重建实参）。
   *
   * @param payload        请求体字节
   * @param parameterTypes 契约参数类型（个数须与 payload 元素数一致）
   */
  Object[] decodeArguments(byte[] payload, Class<?>[] parameterTypes);

  /** 编码返回结果（Provider→网关响应体；null 结果必须可往返）。 */
  byte[] encodeResult(Object result);

  /**
   * 解码返回结果（网关侧按契约返回类型重建）。
   *
   * @param returnType 契约返回类型
   */
  Object decodeResult(byte[] payload, Class<?> returnType);
}
