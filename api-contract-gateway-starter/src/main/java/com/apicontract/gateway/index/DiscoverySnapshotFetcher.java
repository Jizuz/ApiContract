package com.apicontract.gateway.index;

import java.util.List;

/**
 * 服务发现快照拉取端口：隔离注册中心客户端（optional），
 * 由装配层在 nacos-discovery 存在时提供真实实现。
 */
@FunctionalInterface
public interface DiscoverySnapshotFetcher {

  /**
   * 拉取服务当前实例地址快照。
   *
   * @param serviceName 服务名
   * @return 实例地址列表（空列表 = 全部下线）
   * @throws Exception 注册中心异常（调用方沿用最近快照，不清空）
   */
  List<String> fetch(String serviceName) throws Exception;
}
