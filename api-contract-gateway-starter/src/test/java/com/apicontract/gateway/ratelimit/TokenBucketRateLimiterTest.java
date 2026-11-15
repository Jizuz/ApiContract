package com.apicontract.gateway.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 任务13（红-绿）测试：契约级令牌桶限流（design：SPI ContractRateLimiter，
 * 内置令牌桶；不同契约限流独立，防单契约拖垮整体；拒绝返回 429 RATE_LIMITED）。
 */
class TokenBucketRateLimiterTest {

  private final AtomicLong clock = new AtomicLong(0);

  @Test
  void shouldAllowRequestsUpToCapacity() {
    ContractRateLimiter limiter = new TokenBucketRateLimiter(3, 1.0, clock::get);

    assertTrue(limiter.tryAcquire("echo.call"));
    assertTrue(limiter.tryAcquire("echo.call"));
    assertTrue(limiter.tryAcquire("echo.call"), "容量内全部放行");
  }

  @Test
  void shouldRejectWhenBucketExhausted() {
    ContractRateLimiter limiter = new TokenBucketRateLimiter(2, 1.0, clock::get);

    assertTrue(limiter.tryAcquire("echo.call"));
    assertTrue(limiter.tryAcquire("echo.call"));
    assertFalse(limiter.tryAcquire("echo.call"), "令牌耗尽（时钟未推进）：拒绝");
  }

  @Test
  void shouldRefillTokensOverTime() {
    ContractRateLimiter limiter = new TokenBucketRateLimiter(1, 1.0, clock::get);

    assertTrue(limiter.tryAcquire("echo.call"));
    assertFalse(limiter.tryAcquire("echo.call"));

    clock.addAndGet(1001);
    assertTrue(limiter.tryAcquire("echo.call"), "按速率补充令牌后放行");
  }

  @Test
  void shouldCapRefillAtCapacity() {
    ContractRateLimiter limiter = new TokenBucketRateLimiter(2, 100.0, clock::get);

    assertTrue(limiter.tryAcquire("echo.call"));
    assertTrue(limiter.tryAcquire("echo.call"));
    assertFalse(limiter.tryAcquire("echo.call"));

    clock.addAndGet(10_000);
    assertTrue(limiter.tryAcquire("echo.call"));
    assertTrue(limiter.tryAcquire("echo.call"));
    assertFalse(limiter.tryAcquire("echo.call"), "补充封顶容量，不得超发");
  }

  @Test
  void shouldLimitIndependentlyPerContract() {
    ContractRateLimiter limiter = new TokenBucketRateLimiter(1, 1.0, clock::get);

    assertTrue(limiter.tryAcquire("echo.call"));
    assertFalse(limiter.tryAcquire("echo.call"));

    assertTrue(limiter.tryAcquire("other.call"), "契约间限流独立");
  }

  @Test
  void shouldNeverOverdraftUnderConcurrency() throws Exception {
    ContractRateLimiter limiter = new TokenBucketRateLimiter(100, 0.0, clock::get);
    int threads = 20;
    int attemptsPerThread = 10;
    AtomicInteger granted = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        try {
          start.await();
          for (int j = 0; j < attemptsPerThread; j++) {
            if (limiter.tryAcquire("echo.call")) {
              granted.incrementAndGet();
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    assertTrue(done.await(10, TimeUnit.SECONDS));
    pool.shutdownNow();

    assertTrue(granted.get() <= 100,
        "并发下不得超发（容量 100，实际放行 " + granted.get() + "）");
    assertTrue(granted.get() >= 99,
        "容量未被占用时应基本放满（竞争损耗允许 1）");
  }
}
