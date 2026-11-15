package com.apicontract.gateway;

/**
 * 网关侧统一错误码（design 接口契约：网关入口错误码集）。
 *
 * <p>错误结构统一 {@code {code, message, traceId, data:null}}；
 * 与 Provider 侧错误码（CONTRACT_* 前缀）区分，对前端不泄露内部契约入口语义。
 */
public final class GatewayErrorCodes {

  /** 成功。 */
  public static final String OK = "OK";

  /** 缺少 mt 查询参数（400）。 */
  public static final String MT_MISSING = "MT_MISSING";

  /** 契约不存在（404）：主索引未命中。 */
  public static final String CONTRACT_NOT_FOUND = "CONTRACT_NOT_FOUND";

  /** 参数非法（400）：形态不符、个数不符等，不发起远程调用。 */
  public static final String ARGUMENT_INVALID = "ARGUMENT_INVALID";

  /** 无可用实例（503）。 */
  public static final String NO_INSTANCE = "NO_INSTANCE";

  /** 调用失败（502）：Provider 业务异常映射。 */
  public static final String INVOKE_ERROR = "INVOKE_ERROR";

  /** 上游不可用（503）：网络级失败、连接拒绝等。 */
  public static final String UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";

  /** 限流拒绝（429）：契约级令牌桶耗尽。 */
  public static final String RATE_LIMITED = "RATE_LIMITED";

  /** 熔断打开（503）：契约+实例维度失败率超阈值短路。 */
  public static final String CIRCUIT_OPEN = "CIRCUIT_OPEN";

  private GatewayErrorCodes() {
  }
}
