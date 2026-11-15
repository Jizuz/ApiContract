package com.apicontract.gateway.loadbalance;

import com.apicontract.api.spi.SpiImpl;
import com.apicontract.gateway.metrics.ContractMetrics;
import java.util.function.LongSupplier;

/**
 * 简单熔断器（design：按契约+实例维度统计失败率，阈值 failure-rate=0.5、
 * min-request=20；超阈值短路，半开单探测恢复；可经 SPI 替换为 Sentinel）。
 *
 * <p>状态机：CLOSED（计数窗口满 {@code minRequests} 时评估失败率）→
 * OPEN（冷却 {@code openTimeoutMillis} 内拒绝全部请求）→
 * HALF_OPEN（仅放行一个探测请求；成功恢复闭合，失败重新打开）。
 * 时钟可注入，便于测试与回拨。
 */
@SpiImpl("failure-rate")
public final class SimpleCircuitBreaker implements CircuitBreaker {

  private enum State { CLOSED, OPEN, HALF_OPEN }

  private final int minRequests;
  private final double failureRateThreshold;
  private final long openTimeoutMillis;
  private final LongSupplier clock;

  private State state = State.CLOSED;
  private long openedAtMillis;
  private boolean probeIssued;
  private int windowRequests;
  private int windowFailures;

  public SimpleCircuitBreaker(int minRequests, double failureRateThreshold,
      long openTimeoutMillis, LongSupplier clock) {
    this.minRequests = minRequests;
    this.failureRateThreshold = failureRateThreshold;
    this.openTimeoutMillis = openTimeoutMillis;
    this.clock = clock;
  }

  /** 是否放行本次请求（熔断打开期拒绝；半开仅放行一个探测）。 */
  public synchronized boolean allowRequest() {
    switch (state) {
      case OPEN:
        if (clock.getAsLong() - openedAtMillis < openTimeoutMillis) {
          return false;
        }
        state = State.HALF_OPEN;
        probeIssued = true;
        return true;
      case HALF_OPEN:
        if (probeIssued) {
          return false;
        }
        probeIssued = true;
        return true;
      case CLOSED:
      default:
        return true;
    }
  }

  /** 记录一次成功（半开探测成功恢复闭合）。 */
  public synchronized void recordSuccess() {
    if (state == State.HALF_OPEN) {
      reset();
      return;
    }
    windowRequests++;
    evaluateWindow();
  }

  /** 记录一次失败（窗口失败率达阈值打开；半开探测失败重新打开）。 */
  public synchronized void recordFailure() {
    if (state == State.HALF_OPEN) {
      trip();
      return;
    }
    windowRequests++;
    windowFailures++;
    evaluateWindow();
  }

  private void evaluateWindow() {
    if (windowRequests < minRequests) {
      return;
    }
    if (windowFailures / (double) windowRequests >= failureRateThreshold) {
      trip();
    } else {
      windowRequests = 0;
      windowFailures = 0;
    }
  }

  private void trip() {
    state = State.OPEN;
    openedAtMillis = clock.getAsLong();
    windowRequests = 0;
    windowFailures = 0;
    ContractMetrics.recordBreakerOpen();
  }

  private void reset() {
    state = State.CLOSED;
    probeIssued = false;
    windowRequests = 0;
    windowFailures = 0;
  }
}
