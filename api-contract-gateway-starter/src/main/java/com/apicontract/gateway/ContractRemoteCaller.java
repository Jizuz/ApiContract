package com.apicontract.gateway;

/**
 * 远程调用端口（网关 → Provider 泛化入口）：MVP 为同步实现；
 * 异步任务以 Reactor Netty 实现替换（未引入依赖时降级同步并告警）。
 *
 * <p>契约：返回 Provider 统一响应 JSON 原文（由网关解析 code/data）；
 * 网络级失败抛出异常（映射 UPSTREAM_UNAVAILABLE）。
 */
@FunctionalInterface
public interface ContractRemoteCaller {

  /**
   * 调用 Provider 泛化入口。
   *
   * @param baseUrl  实例地址（http://host:port）
   * @param contractName 契约标识（mt，仅网关寻址，不透传业务语义）
   * @param argsJson 参数数组 JSON
   * @return Provider 统一响应 JSON 原文
   * @throws Exception 网络级失败
   */
  String call(String baseUrl, String contractName, String argsJson) throws Exception;

  /**
   * 带整体超时的调用（任务19：三级超时之整体调用超时，契约级覆盖全局默认）。
   *
   * <p>默认实现忽略超时参数（兼容既有简单实现）；真实客户端实现必须
   * 以该值为整体调用上限（连接/读超时由 {@link TimeoutSettings} 三级控制）。
   *
   * @param overallTimeoutMillis 整体调用超时毫秒（契约级 timeout 或全局默认）
   */
  default String call(String baseUrl, String contractName, String argsJson,
      long overallTimeoutMillis) throws Exception {
    return call(baseUrl, contractName, argsJson);
  }

  /**
   * 带整体超时与 traceId 的调用（任务22：traceId 经
   * {@code X-Contract-TraceId} 请求头透传至 Provider，两侧日志可关联）。
   *
   * <p>默认实现忽略 traceId（兼容既有简单实现）；真实客户端实现应在
   * traceId 非空时携带请求头。
   */
  default String call(String baseUrl, String contractName, String argsJson,
      long overallTimeoutMillis, String traceId) throws Exception {
    return call(baseUrl, contractName, argsJson, overallTimeoutMillis);
  }
}
