package com.apicontract.gateway.ratelimit;

import com.apicontract.api.spi.Spi;

/**
 * 契约级限流 SPI（design：内置令牌桶/漏桶，可替换为自研/Sentinel 实现）；
 * 拒绝返回 429 RATE_LIMITED；不同契约限流独立，防单契约拖垮整体。
 */
@Spi(name = "契约限流器", defaultImpl = "token-bucket")
public interface ContractRateLimiter {

  /**
   * 尝试获取一次调用配额。
   *
   * @param contractName 契约标识（限流维度）
   * @return true 放行；false 拒绝（429）
   */
  boolean tryAcquire(String contractName);
}
