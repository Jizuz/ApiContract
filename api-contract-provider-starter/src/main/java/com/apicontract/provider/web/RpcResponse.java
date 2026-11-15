package com.apicontract.provider.web;

/**
 * 泛化入口统一响应结构（规格 contract-provider-invoke 输出定义）。
 *
 * <p>错误结构仅含错误码、可对外消息、traceId；不包含内部堆栈
 * （完整堆栈只写本地日志并关联 traceId）。
 *
 * @param code 错误码或 OK
 * @param message 可对外消息（框架内部异常不外泄细节）
 * @param data 成功时的调用结果（按契约返回类型编码；可为 null）
 * @param traceId 链路标识，用于关联本地完整堆栈日志
 */
public record RpcResponse(String code, String message, Object data, String traceId) {

  /** 成功码。 */
  public static final String CODE_OK = "OK";

  /** 契约不存在（未导出、未标注或未注册）。 */
  public static final String CODE_NOT_FOUND = "CONTRACT_NOT_FOUND";

  /** 参数非法（个数、类型、校验失败）。 */
  public static final String CODE_ARGUMENT_INVALID = "CONTRACT_ARGUMENT_INVALID";

  /** 调用异常（目标方法抛出业务异常）。 */
  public static final String CODE_INVOKE_ERROR = "CONTRACT_INVOKE_ERROR";

  /** 框架级内部异常（序列化、转换失败）。 */
  public static final String CODE_INTERNAL_ERROR = "CONTRACT_INTERNAL_ERROR";

  static RpcResponse ok(Object data, String traceId) {
    return new RpcResponse(CODE_OK, null, data, traceId);
  }

  static RpcResponse error(String code, String message, String traceId) {
    return new RpcResponse(code, message, null, traceId);
  }
}
