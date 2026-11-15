package com.apicontract.gateway.cluster;

import java.io.IOException;

/**
 * 实例熔断打开（503 CIRCUIT_OPEN）：契约+实例维度失败率超阈值被短路。
 *
 * <p>继承 {@link IOException} 即框架级可重试失败：failover 语义下熔断
 * 打开的实例被短路后<b>换实例重试</b>（design：单实例异常换实例，
 * 熔断只隔离该实例不拒绝整个契约）；所有实例均打开时重试耗尽，
 * 收敛为 CIRCUIT_OPEN 503。
 */
public final class CircuitOpenException extends IOException {

  public CircuitOpenException(String instance) {
    super("实例熔断打开: " + instance);
  }
}
