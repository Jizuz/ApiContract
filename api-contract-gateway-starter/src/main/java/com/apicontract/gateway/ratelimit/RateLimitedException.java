package com.apicontract.gateway.ratelimit;

/**
 * 限流拒绝（429 RATE_LIMITED）：契约级令牌桶耗尽。
 *
 * <p>业务级终止异常：不触发 failover 重试（令牌是共享资源，
 * 重试只会进一步挤占容量）；立即收敛为 429 响应。
 */
public final class RateLimitedException extends RuntimeException {

  public RateLimitedException(String contractName) {
    super("契约限流拒绝: " + contractName);
  }
}
