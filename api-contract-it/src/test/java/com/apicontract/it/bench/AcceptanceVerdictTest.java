package com.apicontract.it.bench;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 压测验收判定测试（任务26 先红）：QPS≥1.5×基线、超时率<0.1%、
 * 索引刷新无错误尖刺——验收红线语义（含等号边界）必须在判定器中固化。
 */
class AcceptanceVerdictTest {

  /** elapsed 固定 1_000_000ms（1000s），样本数即吞吐分子，便于构造精确比值。 */
  private static final long ELAPSED_MILLIS = 1_000_000;

  private static BenchStats stats(int successSamples, int timeoutSamples) {
    BenchStats stats = new BenchStats();
    for (int i = 0; i < successSamples; i++) {
      stats.recordSuccess(1.0);
    }
    for (int i = 0; i < timeoutSamples; i++) {
      stats.recordTimeout(1.0);
    }
    return stats;
  }

  @Test
  void passesWhenQpsRatioReachesFloorAndTimeoutRateBelowCeiling() {
    AcceptanceVerdict verdict = AcceptanceVerdict.check(
        stats(10_000, 0), stats(15_000, 0), ELAPSED_MILLIS, ELAPSED_MILLIS);
    assertTrue(verdict.passed(), "QPS 恰好 1.5×（含等号）+ 零超时 → 通过");
  }

  @Test
  void failsWhenQpsRatioBelowFloor() {
    AcceptanceVerdict verdict = AcceptanceVerdict.check(
        stats(10_000, 0), stats(14_999, 0), ELAPSED_MILLIS, ELAPSED_MILLIS);
    assertFalse(verdict.passed(), "1.4999× 低于 1.5× → 拒绝");
    assertTrue(verdict.summary().contains("QPS"),
        "拒绝原因须包含 QPS 维度说明");
  }

  @Test
  void failsWhenTimeoutRateReachesCeiling() {
    AcceptanceVerdict verdict = AcceptanceVerdict.check(
        stats(10_000, 0), stats(19_980, 20), ELAPSED_MILLIS, ELAPSED_MILLIS);
    assertFalse(verdict.passed(),
        "优化组超时率恰达 0.1% 上限（20/20000=0.001，不含等号）→ 拒绝");
    assertTrue(verdict.summary().contains("超时率"),
        "拒绝原因须包含超时率维度说明");
  }

  @Test
  void failsWhenBaselineFailureRateExceedsCeiling() {
    // 基线 10 错误 / 10000 样本 = 0.1% 恰达上限（不含等号）→ 判定无效拒绝
    BenchStats base = stats(9_990, 0);
    for (int i = 0; i < 10; i++) {
      base.recordError(1.0);
    }
    AcceptanceVerdict verdict = AcceptanceVerdict.check(
        base, stats(20_000, 0), ELAPSED_MILLIS, ELAPSED_MILLIS);
    assertFalse(verdict.passed(), "基线组失败率 0.1% 达上限（环境异常）→ 判定无效拒绝");
  }

  @Test
  void toleratesSporadicEnvironmentErrorsBelowCeiling() {
    // 优化组 2 错误 / 20000 样本 = 0.01%（本机 loopback 偶发端口耗尽量级）
    // → 未超红线不阻断，仍可对照判定
    BenchStats opt = stats(19_998, 0);
    opt.recordError(1.0);
    opt.recordError(1.0);
    AcceptanceVerdict verdict = AcceptanceVerdict.check(
        stats(10_000, 0), opt, ELAPSED_MILLIS, ELAPSED_MILLIS);
    assertTrue(verdict.passed(), "偶发环境错误在 0.1% 红线内 → 通过");
  }

  @Test
  void refreshUnderLoadPassesOnlyWithBoundedFailuresAndP99Regression() {
    assertTrue(AcceptanceVerdict.checkRefreshUnderLoad(
            0, 10_000, 1.8, 1.0, 2.0).passed(),
        "零失败 + P99 劣化 1.8×<2.0× 上限 → 通过");
    assertFalse(AcceptanceVerdict.checkRefreshUnderLoad(
            10, 10_000, 1.0, 1.0, 2.0).passed(),
        "刷新期间失败率恰达 0.1%（10/10000）→ 拒绝（错误尖刺）");
    assertFalse(AcceptanceVerdict.checkRefreshUnderLoad(
            0, 10_000, 2.5, 1.0, 2.0).passed(),
        "P99 劣化 2.5× 超上限 → 拒绝（RT 尖刺）");
    assertTrue(AcceptanceVerdict.checkRefreshUnderLoad(
            1, 100_000, 1.5, 1.0, 2.0).passed(),
        "偶发 1 失败 / 10 万样本（0.001% 远低红线）→ 不阻断");
  }
}
