package com.apicontract.gateway.ratelimit;

import com.apicontract.api.spi.SpiImpl;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 令牌桶限流（契约级、按需补充、时钟可注入）：
 * 每个契约独立桶（容量 + 恒定速率补充，补充封顶容量不得超发）；
 * 并发取令牌不超发（桶内同步）。
 */
@SpiImpl("token-bucket")
public final class TokenBucketRateLimiter implements ContractRateLimiter {

  private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
  private final int capacity;
  private final double refillTokensPerSecond;
  private final LongSupplier clockMillis;

  public TokenBucketRateLimiter(int capacity, double refillTokensPerSecond,
      LongSupplier clockMillis) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("令牌桶容量必须为正数: " + capacity);
    }
    this.capacity = capacity;
    this.refillTokensPerSecond = refillTokensPerSecond;
    this.clockMillis = clockMillis;
  }

  @Override
  public boolean tryAcquire(String contractName) {
    return buckets
        .computeIfAbsent(contractName, key -> new TokenBucket())
        .tryAcquire();
  }

  /** 单契约令牌桶（惰性按流逝时间补充）。 */
  private final class TokenBucket {

    private double tokens = capacity;
    private long lastRefillMillis = clockMillis.getAsLong();

    synchronized boolean tryAcquire() {
      refill();
      if (tokens >= 1.0) {
        tokens -= 1.0;
        return true;
      }
      return false;
    }

    private void refill() {
      long now = clockMillis.getAsLong();
      long elapsed = now - lastRefillMillis;
      if (elapsed <= 0) {
        return;
      }
      tokens = Math.min(capacity, tokens + elapsed * refillTokensPerSecond / 1000.0);
      lastRefillMillis = now;
    }
  }
}
