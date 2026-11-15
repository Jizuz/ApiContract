package com.apicontract.gateway.filter;

/**
 * 调用上下文（贯穿过滤器链的一次远程调用快照）：进入过滤器链时创建，
 * traceId 可由 {@link TraceInvokeFilter} 兜底补齐；instance 由负载均衡
 * 选出实例后填充（before 阶段可能尚未选定，为 null）。
 */
public final class ContractInvocation {

  private final String contractName;
  private final String argsJson;
  private final long startTimeMillis;
  private String traceId;
  private String instance;

  public ContractInvocation(String contractName, String argsJson, String traceId) {
    this.contractName = contractName;
    this.argsJson = argsJson;
    this.traceId = traceId;
    this.startTimeMillis = System.currentTimeMillis();
  }

  public String contractName() {
    return contractName;
  }

  public String argsJson() {
    return argsJson;
  }

  public String traceId() {
    return traceId;
  }

  /** traceId 兜底回填（已有值不覆盖，见 {@link TraceInvokeFilter}）。 */
  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  /** 本次调用选中的目标实例（负载均衡后填充，供日志/指标维度使用）。 */
  public String instance() {
    return instance;
  }

  public void setInstance(String instance) {
    this.instance = instance;
  }

  public long startTimeMillis() {
    return startTimeMillis;
  }

  /** 截至当前的处理耗时（毫秒）。 */
  public long elapsedMillis() {
    return System.currentTimeMillis() - startTimeMillis;
  }
}
