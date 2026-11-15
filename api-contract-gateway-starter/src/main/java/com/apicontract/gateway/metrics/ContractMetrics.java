package com.apicontract.gateway.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 契约维度指标门面（spec contract-async-stability：QPS/RT/错误率/熔断次数/
 * 重试次数/连接池使用率 + 索引刷新耗时；design 监控小节）。
 *
 * <p>全局静态累计（与 {@code SpiLoader} 同风格），埋点出口：
 * 指标过滤器（成功/失败/RT）、集群策略（重试）、熔断器（打开）、
 * 异步客户端（超时/池耗尽）、实例注册表（索引刷新耗时）。
 * P99 分位与外部指标后端（Micrometer/Prometheus）桥接由装配层后续接入。
 */
public final class ContractMetrics {

  private static final Map<String, MetricCell> CONTRACTS = new ConcurrentHashMap<>();
  private static final AtomicLong RETRY_TOTAL = new AtomicLong();
  private static final AtomicLong BREAKER_OPEN_TOTAL = new AtomicLong();
  private static final AtomicLong POOL_EXHAUSTED_TOTAL = new AtomicLong();
  private static final AtomicLong INDEX_REFRESH_COUNT = new AtomicLong();
  private static final AtomicLong INDEX_REFRESH_FAILURE = new AtomicLong();
  private static final AtomicLong INDEX_REFRESH_MAX_MILLIS = new AtomicLong();
  private static final AtomicLong POOL_IN_USE = new AtomicLong();
  private static final AtomicLong POOL_MAX = new AtomicLong();

  private ContractMetrics() {
  }

  /** 记录一次契约调用成功（含 RT 毫秒）。 */
  public static void recordSuccess(String contractName, long rtMillis) {
    cell(contractName).recordSuccess(rtMillis);
  }

  /** 记录一次契约调用失败（含 RT 毫秒）。 */
  public static void recordFailure(String contractName, long rtMillis) {
    cell(contractName).recordFailure(rtMillis);
  }

  /** 记录一次契约调用整体超时。 */
  public static void recordTimeout(String contractName) {
    cell(contractName).timeouts.incrementAndGet();
  }

  /** 记录一次集群策略重试（全局维度：重试发生在完整链路重放，无单一契约归属）。 */
  public static void recordRetry() {
    RETRY_TOTAL.incrementAndGet();
  }

  /** 记录一次熔断打开（契约+实例维度由熔断器实例隔离，此处全局聚合）。 */
  public static void recordBreakerOpen() {
    BREAKER_OPEN_TOTAL.incrementAndGet();
  }

  /** 记录一次连接池等待超时/耗尽快速失败。 */
  public static void recordPoolExhausted() {
    POOL_EXHAUSTED_TOTAL.incrementAndGet();
  }

  /** 更新连接池使用率快照（在用/上限，由装配层或池回调周期上报）。 */
  public static void recordPoolUsage(long inUse, long max) {
    POOL_IN_USE.set(inUse);
    POOL_MAX.set(max);
  }

  /** 记录一次索引刷新（实例列表校准）耗时与结果。 */
  public static void recordIndexRefresh(long millis, boolean success) {
    INDEX_REFRESH_COUNT.incrementAndGet();
    if (!success) {
      INDEX_REFRESH_FAILURE.incrementAndGet();
    }
    INDEX_REFRESH_MAX_MILLIS.accumulateAndGet(millis, Math::max);
  }

  /** 契约维度快照（未观测契约返回空快照）。 */
  public static ContractMetricSnapshot snapshot(String contractName) {
    MetricCell cell = CONTRACTS.get(contractName);
    if (cell == null) {
      return new ContractMetricSnapshot(0, 0, 0, 0, 0, 0);
    }
    return new ContractMetricSnapshot(cell.successes.get(), cell.failures.get(),
        cell.timeouts.get(), cell.rtCount.get(), cell.rtTotal.get(),
        cell.rtMax.get());
  }

  public static long retryTotal() {
    return RETRY_TOTAL.get();
  }

  public static long breakerOpenTotal() {
    return BREAKER_OPEN_TOTAL.get();
  }

  public static long poolExhaustedTotal() {
    return POOL_EXHAUSTED_TOTAL.get();
  }

  public static long poolInUse() {
    return POOL_IN_USE.get();
  }

  public static long poolMax() {
    return POOL_MAX.get();
  }

  /** 连接池使用率（0~1；未上报上限时为 0）。 */
  public static double poolUsageRatio() {
    long max = POOL_MAX.get();
    return max <= 0 ? 0.0 : POOL_IN_USE.get() / (double) max;
  }

  public static long indexRefreshCount() {
    return INDEX_REFRESH_COUNT.get();
  }

  public static long indexRefreshFailureCount() {
    return INDEX_REFRESH_FAILURE.get();
  }

  public static long indexRefreshMaxMillis() {
    return INDEX_REFRESH_MAX_MILLIS.get();
  }

  /** 全局聚合视图（告警评估输入；跨契约聚合成功率/失败率/超时率）。 */
  public static MetricsView currentView() {
    long requests = 0;
    long failures = 0;
    long timeouts = 0;
    for (MetricCell cell : CONTRACTS.values()) {
      requests += cell.successes.get() + cell.failures.get();
      failures += cell.failures.get();
      timeouts += cell.timeouts.get();
    }
    return new MetricsView(requests, failures, timeouts,
        BREAKER_OPEN_TOTAL.get(), POOL_EXHAUSTED_TOTAL.get(),
        RETRY_TOTAL.get());
  }

  /** 已观测契约名列表（索引刷新等巡检输出用）。 */
  public static List<String> observedContracts() {
    return List.copyOf(CONTRACTS.keySet());
  }

  /** 清空全部指标（测试与运维重置用）。 */
  public static void reset() {
    CONTRACTS.clear();
    RETRY_TOTAL.set(0);
    BREAKER_OPEN_TOTAL.set(0);
    POOL_EXHAUSTED_TOTAL.set(0);
    INDEX_REFRESH_COUNT.set(0);
    INDEX_REFRESH_FAILURE.set(0);
    INDEX_REFRESH_MAX_MILLIS.set(0);
    POOL_IN_USE.set(0);
    POOL_MAX.set(0);
  }

  private static MetricCell cell(String contractName) {
    return CONTRACTS.computeIfAbsent(contractName, k -> new MetricCell());
  }

  /** 单契约维度累加器（桶内字段无锁更新，读取走不可变快照）。 */
  private static final class MetricCell {

    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong timeouts = new AtomicLong();
    private final AtomicLong rtCount = new AtomicLong();
    private final AtomicLong rtTotal = new AtomicLong();
    private final AtomicLong rtMax = new AtomicLong();

    private void recordSuccess(long rtMillis) {
      successes.incrementAndGet();
      recordRt(rtMillis);
    }

    private void recordFailure(long rtMillis) {
      failures.incrementAndGet();
      recordRt(rtMillis);
    }

    private void recordRt(long rtMillis) {
      rtCount.incrementAndGet();
      rtTotal.addAndGet(rtMillis);
      rtMax.accumulateAndGet(rtMillis, Math::max);
    }
  }
}
