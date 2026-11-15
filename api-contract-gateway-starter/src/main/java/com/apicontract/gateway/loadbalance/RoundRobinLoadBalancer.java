package com.apicontract.gateway.loadbalance;

import com.apicontract.api.spi.SpiImpl;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按契约标识（mt）维度轮询负载均衡：不同契约独立计数，
 * 防止单契约流量偏斜共享轮转计数（design 调用链路：负载均衡选实例）。
 */
@SpiImpl("round-robin")
public final class RoundRobinLoadBalancer implements LoadBalancer {

  private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

  /**
   * 选择实例。
   *
   * @param contractName 契约标识（计数维度）
   * @param instances 候选实例地址列表
   * @return 选中实例；空列表返回 null；列表为 null 抛 NPE
   */
  public String choose(String contractName, List<String> instances) {
    if (instances.isEmpty()) {
      return null;
    }
    long seq = counters
        .computeIfAbsent(contractName, key -> new AtomicLong())
        .getAndIncrement();
    int index = (int) Math.floorMod(seq, instances.size());
    return instances.get(index);
  }
}
