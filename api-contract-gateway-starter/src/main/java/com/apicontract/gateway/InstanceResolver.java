package com.apicontract.gateway;

import java.util.List;

/**
 * 实例地址解析端口（服务 → 可调用实例 baseUrl 列表）：由注册中心订阅
 * 任务提供；刷新失败沿用最近快照不清空。
 */
@FunctionalInterface
public interface InstanceResolver {

  /** 解析服务的实例地址列表（形如 http://host:port）；空列表表示无可用实例。 */
  List<String> resolve(String serviceName);
}
