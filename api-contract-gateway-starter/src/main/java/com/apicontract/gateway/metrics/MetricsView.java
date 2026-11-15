package com.apicontract.gateway.metrics;

/**
 * 全局指标视图（告警评估输入）：跨契约聚合请求/失败/超时与全局事件计数。
 */
public final class MetricsView {

  private final long totalRequests;
  private final long totalFailures;
  private final long totalTimeouts;
  private final long breakerOpenTotal;
  private final long poolExhaustedTotal;
  private final long retryTotal;

  public MetricsView(long totalRequests, long totalFailures, long totalTimeouts,
      long breakerOpenTotal, long poolExhaustedTotal, long retryTotal) {
    this.totalRequests = totalRequests;
    this.totalFailures = totalFailures;
    this.totalTimeouts = totalTimeouts;
    this.breakerOpenTotal = breakerOpenTotal;
    this.poolExhaustedTotal = poolExhaustedTotal;
    this.retryTotal = retryTotal;
  }

  public long totalRequests() {
    return totalRequests;
  }

  public long totalFailures() {
    return totalFailures;
  }

  public long totalTimeouts() {
    return totalTimeouts;
  }

  public long breakerOpenTotal() {
    return breakerOpenTotal;
  }

  public long poolExhaustedTotal() {
    return poolExhaustedTotal;
  }

  public long retryTotal() {
    return retryTotal;
  }

  /** 全局错误率（无请求返回 0）。 */
  public double errorRate() {
    return totalRequests == 0 ? 0.0 : totalFailures / (double) totalRequests;
  }

  /** 全局超时率（无请求返回 0）。 */
  public double timeoutRate() {
    return totalRequests == 0 ? 0.0 : totalTimeouts / (double) totalRequests;
  }
}
