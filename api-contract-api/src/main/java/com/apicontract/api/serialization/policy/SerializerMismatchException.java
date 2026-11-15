package com.apicontract.api.serialization.policy;

/**
 * 两侧序列化声明不一致（SERIALIZER_MISMATCH）：启动校验失败，
 * 不允许以不一致状态上线（spec contract-serialization 异常场景）。
 */
public class SerializerMismatchException extends RuntimeException {

  public SerializerMismatchException(String message) {
    super(message);
  }
}
