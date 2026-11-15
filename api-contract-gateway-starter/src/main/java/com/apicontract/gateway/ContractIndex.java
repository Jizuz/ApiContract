package com.apicontract.gateway;

import com.apicontract.api.meta.ContractMeta;

/**
 * 契约主索引端口（mt → 契约条目）：由订阅配置中心构建（三级索引任务），
 * MVP 阶段可注入内存实现（单机调试模式）。
 */
@FunctionalInterface
public interface ContractIndex {

  /** 按契约标识查找；未命中返回 null。 */
  ContractMeta find(String contractName);
}
