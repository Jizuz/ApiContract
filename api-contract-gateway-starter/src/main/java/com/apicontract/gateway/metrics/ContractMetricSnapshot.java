package com.apicontract.gateway.metrics;

/**
 * 契约维度指标快照（不可变）：成功/失败/超时计数与 RT 聚合。
 *
 * <p>错误率 = failure / (success + failure)；无请求时为 0。
 */
public final class ContractMetricSnapshot {

  private final long successCount;
  private final long failureCount;
  private final long timeoutCount;
  private final long rtCount;
  private final long totalRtMillis;
  private final long maxRtMillis;

  public ContractMetricSnapshot(long successCount, long failureCount,
      long timeoutCount, long rtCount, long totalRtMillis, long maxRtMillis) {
    this.successCount = successCount;
    this.failureCount = failureCount;
    this.timeoutCount = timeoutCount;
    this.rtCount = rtCount;
    this.totalRtMillis = totalRtMillis;
    this.maxRtMillis = maxRtMillis;
  }

  public long successCount() {
    return successCount;
  }

  public long failureCount() {
    return failureCount;
  }

  public long timeoutCount() {
    return timeoutCount;
  }

  public long rtCount() {
    return rtCount;
  }

  public long totalRtMillis() {
    return totalRtMillis;
  }

  public long maxRtMillis() {
    return maxRtMillis;
  }

  /** 平均 RT（毫秒）；无采样返回 0。 */
  public long avgRtMillis() {
    return rtCount == 0 ? 0 : totalRtMillis / rtCount;
  }

  /** 错误率（failure / 总请求）；无请求返回 0。 */
  public double errorRate() {
    long total = successCount + failureCount;
    return total == 0 ? 0.0 : failureCount / (double) total;
  }
}
