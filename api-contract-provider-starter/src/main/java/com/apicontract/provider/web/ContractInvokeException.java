package com.apicontract.provider.web;

/**
 * 调用异常（错误码 CONTRACT_INVOKE_ERROR）：目标方法抛出的业务异常
 * （已剥离代理/反射包装，取真实异常）；仅返回错误码与消息，不含内部堆栈。
 */
public class ContractInvokeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ContractInvokeException(String message, Throwable cause) {
    super(message, cause);
  }
}
