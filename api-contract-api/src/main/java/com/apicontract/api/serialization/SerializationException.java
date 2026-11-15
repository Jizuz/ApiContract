package com.apicontract.api.serialization;

/**
 * 契约序列化失败（spec contract-serialization 异常场景）：
 * 返回明确错误码（网关 SERIALIZE_ERROR / Provider CONTRACT_INTERNAL_ERROR），
 * 禁止降级为静默空结果；编解码失败不重试编解码。
 */
public class SerializationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SerializationException(String message) {
    super(message);
  }

  public SerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
