package com.apicontract.it.bench;

/**
 * 压测验收判定器（任务26）：固化 design 验收红线——
 * QPS ≥ 基线 1.5×（含等号）、P99 超时率 < 0.1%（不含等号）、
 * 索引刷新期间零请求错误且 P99 劣化有界。
 *
 * <p>判定与压测驱动解耦：输入 {@link BenchStats} 快照，输出结构化
 * 通过/拒绝结论与逐维度明细（报告归档直接引用）。
 */
public final class AcceptanceVerdict {

  /** 超时率验收上限（design：P99 超时率 < 0.1%）。 */
  public static final double TIMEOUT_RATE_CEILING = 0.001;

  /** RT 超时判定阈值（契约级整体超时默认 3000ms，与全局默认一致）。 */
  public static final double TIMEOUT_THRESHOLD_MILLIS = 3_000;

  private final boolean passed;
  private final String summary;

  private AcceptanceVerdict(boolean passed, String summary) {
    this.passed = passed;
    this.summary = summary;
  }

  public boolean passed() {
    return passed;
  }

  /** 逐维度明细（多行，报告直接归档）。 */
  public String summary() {
    return summary;
  }

  /**
   * 基线组 vs 优化组对照判定（design 红线：QPS ≥ 1.5×、失败率 < 0.1%；
   * 失败率 = 错误 + 超时占完成样本比，非 OK 响应与异常均计入）。
   *
   * @param base 基线组样本（反射 + 同步链路）
   * @param optimized 优化组样本（字节码 + 异步连接池链路）
   * @param baseElapsedMillis 基线组该轮实测时长（QPS 分母，各组各自）
   * @param optElapsedMillis 优化组该轮实测时长（QPS 分母，各组各自）
   */
  public static AcceptanceVerdict check(BenchStats base, BenchStats optimized,
      long baseElapsedMillis, long optElapsedMillis) {
    StringBuilder report = new StringBuilder();
    boolean pass = true;
    double baseFailureRate = failureRate(base);
    if (baseFailureRate >= TIMEOUT_RATE_CEILING) {
      report.append(String.format(
          "基线组失败率 %.5f ≥ %.3f%%（环境异常或基线缺陷，对照判定无效）%n",
          baseFailureRate, TIMEOUT_RATE_CEILING * 100));
      pass = false;
    }
    double optFailureRate = failureRate(optimized);
    if (optFailureRate >= TIMEOUT_RATE_CEILING) {
      report.append(String.format(
          "优化组失败率 %.5f ≥ %.3f%%（错误样本超红线，需排查）%n",
          optFailureRate, TIMEOUT_RATE_CEILING * 100));
      pass = false;
    }
    double baseQps = base.qps(baseElapsedMillis);
    double optQps = optimized.qps(optElapsedMillis);
    double ratio = baseQps <= 0 ? 0 : optQps / baseQps;
    report.append(String.format(
        "QPS 对照: 基线 %.0f vs 优化 %.0f（比值 %.3f，红线 ≥ 1.500）%n",
        baseQps, optQps, ratio));
    if (ratio < 1.5) {
      pass = false;
    }
    double baseTimeout = base.timeoutRate(TIMEOUT_THRESHOLD_MILLIS);
    double optTimeout = optimized.timeoutRate(TIMEOUT_THRESHOLD_MILLIS);
    report.append(String.format(
        "P99 超时率: 基线 %.5f vs 优化 %.5f（红线 < %.3f%%）%n",
        baseTimeout, optTimeout, TIMEOUT_RATE_CEILING * 100));
    if (optTimeout >= TIMEOUT_RATE_CEILING) {
      pass = false;
    }
    report.append(String.format(
        "失败样本: 基线 错误 %d/超时 %d，优化 错误 %d/超时 %d%n",
        base.errorCount(), base.timeoutCount(),
        optimized.errorCount(), optimized.timeoutCount()));
    report.append(String.format(
        "P99 延迟: 基线 %.2fms vs 优化 %.2fms",
        base.percentile(0.99), optimized.percentile(0.99)));
    return new AcceptanceVerdict(pass, report.toString().trim());
  }

  /** 失败率：非 OK 样本（错误 + 显式超时）占完成样本比。 */
  private static double failureRate(BenchStats stats) {
    long total = stats.totalSamples();
    if (total == 0) {
      return 1.0;
    }
    return (double) (stats.errorCount() + stats.timeoutCount()) / total;
  }

  /**
   * 索引刷新无尖刺判定（压测中并发原子刷新索引）：
   * 失败率未超红线（与主验收同阈值，容忍本机环境偶发）且
   * P99 劣化不超过给定量级（RT 尖刺拦截）。
   */
  public static AcceptanceVerdict checkRefreshUnderLoad(int failureCount,
      long totalSamples, double p99WithRefreshMillis, double p99BaselineMillis,
      double regressionCeiling) {
    double failureRate = totalSamples <= 0 ? 1.0
        : (double) failureCount / totalSamples;
    boolean pass = failureRate < TIMEOUT_RATE_CEILING
        && p99BaselineMillis > 0
        && p99WithRefreshMillis <= p99BaselineMillis * regressionCeiling;
    String summary = String.format(
        "刷新期间失败 %d/%d（失败率 %.5f，红线 < %.3f%%），"
            + "P99 %.2fms vs 无刷新基线 %.2fms（劣化 %.2f×，红线 ≤ %.1f×）",
        failureCount, totalSamples, failureRate, TIMEOUT_RATE_CEILING * 100,
        p99WithRefreshMillis, p99BaselineMillis,
        p99BaselineMillis > 0
            ? p99WithRefreshMillis / p99BaselineMillis : Double.POSITIVE_INFINITY,
        regressionCeiling);
    return new AcceptanceVerdict(pass, summary);
  }
}
