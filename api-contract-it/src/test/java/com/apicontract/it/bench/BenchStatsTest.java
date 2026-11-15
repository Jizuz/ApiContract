package com.apicontract.it.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 压测统计器测试（任务26 先红）：QPS、分位数（P50/P99）、超时率、
 * 并发收集正确性——统计口径错误会直接污染验收判定，必须先行验证。
 */
class BenchStatsTest {

  @Test
  void qpsIsTotalOpsDividedByElapsedSeconds() {
    BenchStats stats = new BenchStats();
    for (int i = 0; i < 2_000; i++) {
      stats.recordSuccess(1.0);
    }
    assertEquals(2_000.0, stats.qps(1_000), 0.001,
        "1000ms 内 2000 次成功 → QPS=2000");
    assertEquals(4_000.0, stats.qps(500), 0.001,
        "同样本量 500ms → QPS 翻倍");
  }

  @Test
  void percentileFollowsNearestRankOnSortedSamples() {
    BenchStats stats = new BenchStats();
    for (int rt = 1; rt <= 100; rt++) {
      stats.recordSuccess(rt);
    }
    assertEquals(50.0, stats.percentile(0.50), 0.001,
        "1..100 均匀样本 P50=50（最近邻秩）");
    assertEquals(99.0, stats.percentile(0.99), 0.001,
        "P99=99（ceil(0.99*100)=99 秩）");
    assertEquals(100.0, stats.percentile(1.00), 0.001,
        "P100=最大值");
  }

  @Test
  void timeoutRateCountsSamplesBeyondThresholdPlusExplicitTimeouts() {
    BenchStats stats = new BenchStats();
    for (int i = 0; i < 7; i++) {
      stats.recordSuccess(5.0);
    }
    stats.recordSuccess(50.0);
    stats.recordSuccess(80.0);
    stats.recordTimeout(30.0);
    assertEquals(0.3, stats.timeoutRate(40.0), 0.001,
        "10 样本：1 个 RT>40ms + 1 个显式超时 → 超时率 0.3");
    assertEquals(1, stats.timeoutCount(), "显式超时样本 1 个");
    assertEquals(10, stats.totalSamples(), "总样本 10 个");
  }

  @Test
  void errorCountTrackedSeparatelyFromTimeouts() {
    BenchStats stats = new BenchStats();
    stats.recordSuccess(1.0);
    stats.recordError(2.0);
    stats.recordTimeout(3.0);
    assertEquals(1, stats.errorCount());
    assertEquals(1, stats.timeoutCount());
    assertEquals(3, stats.totalSamples());
  }

  @Test
  void concurrentRecordingIsThreadSafe() throws Exception {
    BenchStats stats = new BenchStats();
    int threads = 8;
    int perThread = 1_000;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    for (int t = 0; t < threads; t++) {
      pool.execute(() -> {
        try {
          start.await();
          for (int i = 0; i < perThread; i++) {
            stats.recordSuccess(1.0);
          }
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    assertTrue(done.await(10, TimeUnit.SECONDS), "并发记录须在时限内完成");
    pool.shutdownNow();
    assertEquals(threads * perThread, stats.totalSamples(),
        "8 线程各 1000 样本无丢失");
  }
}
