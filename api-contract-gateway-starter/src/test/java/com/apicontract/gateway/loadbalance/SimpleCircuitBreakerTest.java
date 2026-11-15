package com.apicontract.gateway.loadbalance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 任务12（红-绿）测试：简单熔断器（design：按契约+实例维度统计失败率，
 * 阈值 failure-rate=0.5、min-request=20；超阈值短路，半开探测恢复）。
 */
class SimpleCircuitBreakerTest {

  private static final int MIN_REQUESTS = 20;
  private final AtomicLong clock = new AtomicLong(0);

  private SimpleCircuitBreaker newBreaker() {
    return new SimpleCircuitBreaker(MIN_REQUESTS, 0.5, 10_000, clock::get);
  }

  @Test
  void shouldAllowRequestsWhenClosed() {
    SimpleCircuitBreaker breaker = newBreaker();

    assertTrue(breaker.allowRequest(), "闭合态（初始）放行全部请求");
  }

  @Test
  void shouldOpenAfterConsecutiveFailuresReachThreshold() {
    SimpleCircuitBreaker breaker = newBreaker();

    for (int i = 0; i < MIN_REQUESTS; i++) {
      assertTrue(breaker.allowRequest());
      breaker.recordFailure();
    }

    assertFalse(breaker.allowRequest(),
        "连续失败满阈值（失败率 100% >= 0.5 且请求达 min-request=20）：熔断打开");
  }

  @Test
  void shouldStayClosedBelowMinRequestsWithFailures() {
    SimpleCircuitBreaker breaker = newBreaker();

    for (int i = 0; i < MIN_REQUESTS - 1; i++) {
      breaker.recordFailure();
    }

    assertTrue(breaker.allowRequest(),
        "失败率虽高但请求未达 min-request=20：统计不足不熔断");
  }

  @Test
  void shouldStayClosedWhenFailureRateBelowThreshold() {
    SimpleCircuitBreaker breaker = newBreaker();

    for (int i = 0; i < MIN_REQUESTS; i++) {
      breaker.recordSuccess();
    }
    for (int i = 0; i < 9; i++) {
      breaker.recordFailure();
    }

    assertTrue(breaker.allowRequest(), "失败率 9/29 < 0.5：不熔断");
  }

  @Test
  void shouldRejectAllRequestsWhileOpen() {
    SimpleCircuitBreaker breaker = newBreaker();
    for (int i = 0; i < MIN_REQUESTS; i++) {
      breaker.recordFailure();
    }

    clock.addAndGet(5_000);

    assertFalse(breaker.allowRequest(), "熔断打开期内（未到冷却超时）拒绝请求");
  }

  @Test
  void shouldHalfOpenAndRecoverOnProbeSuccess() {
    SimpleCircuitBreaker breaker = newBreaker();
    for (int i = 0; i < MIN_REQUESTS; i++) {
      breaker.recordFailure();
    }

    clock.addAndGet(10_001);
    assertTrue(breaker.allowRequest(), "冷却超时后进入半开：放行探测请求");

    breaker.recordSuccess();
    assertTrue(breaker.allowRequest(), "探测成功：恢复闭合");
  }

  @Test
  void shouldReopenOnProbeFailure() {
    SimpleCircuitBreaker breaker = newBreaker();
    for (int i = 0; i < MIN_REQUESTS; i++) {
      breaker.recordFailure();
    }
    clock.addAndGet(10_001);
    assertTrue(breaker.allowRequest());

    breaker.recordFailure();

    clock.addAndGet(10_001);
    assertTrue(breaker.allowRequest(), "探测失败重新打开，需再等冷却超时半开");
    clock.addAndGet(1);
    assertFalse(breaker.allowRequest(), "半开仅放行一个探测请求，其余拒绝");
  }

  @Test
  void shouldShareBreakerPerKeyAndIsolateBetweenKeys() {
    CircuitBreakerRegistry registry = new CircuitBreakerRegistry(10_000, clock::get);

    SimpleCircuitBreaker a = registry.forKey("echo.call|a:8080");
    SimpleCircuitBreaker aAgain = registry.forKey("echo.call|a:8080");
    SimpleCircuitBreaker b = registry.forKey("echo.call|b:8080");

    assertSame(a, aAgain, "同契约同实例共享同一熔断器");
    assertNotSame(a, b, "不同实例熔断隔离，单实例熔断不拖垮其他实例");

    for (int i = 0; i < MIN_REQUESTS; i++) {
      a.recordFailure();
    }
    assertTrue(b.allowRequest(), "a 实例熔断不影响 b 实例");
  }
}
