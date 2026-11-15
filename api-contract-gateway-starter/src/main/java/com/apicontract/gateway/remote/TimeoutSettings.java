package com.apicontract.gateway.remote;

import com.apicontract.api.meta.ContractMeta;

/**
 * 三级超时配置（design：连接超时 / 读超时 / 整体调用超时，整体默认 3000ms，
 * 契约级 {@code timeout} 覆盖全局默认）。
 *
 * @param connectMillis 连接超时毫秒
 * @param readMillis 读超时毫秒
 * @param overallMillis 整体调用超时毫秒（默认 3000）
 */
public record TimeoutSettings(
    int connectMillis,
    int readMillis,
    long overallMillis) {

  /** 设计默认值：整体调用超时 3000ms（契约级 timeout 可覆盖）。 */
  public static final TimeoutSettings DEFAULT = new TimeoutSettings(1_000, 2_000, 3_000);

  public TimeoutSettings {
    if (connectMillis <= 0 || readMillis <= 0 || overallMillis <= 0) {
      throw new IllegalArgumentException("三级超时必须有界为正: " + this);
    }
  }

  /** 契约生效整体超时：契约级 timeout 声明优先，未声明沿用全局默认。 */
  public long overallFor(ContractMeta meta) {
    return meta.timeout() != null && meta.timeout() > 0
        ? meta.timeout() : overallMillis;
  }
}
