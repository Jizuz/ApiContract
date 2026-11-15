package com.apicontract.provider.web;

/**
 * 参数非法（错误码 CONTRACT_ARGUMENT_INVALID）：个数不符、类型转换失败、校验失败、请求体超限。
 */
public class ArgumentInvalidException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  public ArgumentInvalidException(String message) {
    super(message);
  }

  public ArgumentInvalidException(String message, Throwable cause) {
    super(message, cause);
  }
}
