package com.apicontract.gateway.loadbalance;

import com.apicontract.api.spi.Spi;

/**
 * 熔断器扩展点（规格 contract-spi-extension）：熔断判定。
 *
 * <p>内置实现：失败率熔断（failure-rate：窗口失败率超阈值打开，半开探测恢复）。
 */
@Spi(name = "熔断器", defaultImpl = "failure-rate")
public interface CircuitBreaker {

  /** 是否放行本次请求（打开期拒绝；半开仅放行探测）。 */
  boolean allowRequest();

  /** 记录一次成功。 */
  void recordSuccess();

  /** 记录一次失败。 */
  void recordFailure();
}
