package com.apicontract.gateway.loadbalance;

import com.apicontract.api.spi.Spi;
import java.util.List;

/**
 * 负载均衡器扩展点（规格 contract-spi-extension）：从候选实例中选择一个。
 *
 * <p>内置实现：轮询（round-robin）；随机/一致性哈希/加权由业务实现扩展点提供。
 */
@Spi(name = "负载均衡器", defaultImpl = "round-robin")
public interface LoadBalancer {

  /**
   * 选择实例。
   *
   * @param contractName 契约标识（策略维度，如轮询计数隔离）
   * @param instances 候选实例地址列表
   * @return 选中实例；空列表返回 null
   */
  String choose(String contractName, List<String> instances);
}
