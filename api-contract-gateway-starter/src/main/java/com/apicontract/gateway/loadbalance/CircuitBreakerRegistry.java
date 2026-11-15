package com.apicontract.gateway.loadbalance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 熔断器注册表：按「契约标识|实例地址」维度维护熔断器（design：
 * 按契约+实例维度统计，单实例熔断不拖垮其他实例）。
 */
public final class CircuitBreakerRegistry {

  private final Map<String, SimpleCircuitBreaker> breakers = new ConcurrentHashMap<>();
  private final long openTimeoutMillis;
  private final LongSupplier clock;

  public CircuitBreakerRegistry(long openTimeoutMillis, LongSupplier clock) {
    this.openTimeoutMillis = openTimeoutMillis;
    this.clock = clock;
  }

  /** 获取（或创建）key 对应熔断器；同 key 恒返回同一实例。 */
  public SimpleCircuitBreaker forKey(String key) {
    return breakers.computeIfAbsent(key,
        ignored -> new SimpleCircuitBreaker(20, 0.5, openTimeoutMillis, clock));
  }
}
