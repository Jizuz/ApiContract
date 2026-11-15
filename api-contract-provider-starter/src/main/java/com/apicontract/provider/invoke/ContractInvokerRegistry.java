package com.apicontract.provider.invoke;

import java.util.HashMap;
import java.util.Map;

/**
 * 调用器注册表（mt -&gt; 调用器）：启动期一次性构建，运行期只读。
 *
 * <p>规格（contract-provider-invoke）：调用器常驻内存，随应用生命周期；
 * 运行期不生成新调用器；未命中直接返回 null（由统一入口转 CONTRACT_NOT_FOUND，
 * 不进入任何兜底调用）。
 */
public final class ContractInvokerRegistry {

  private final Map<String, ContractInvoker> invokers;

  private ContractInvokerRegistry(Map<String, ContractInvoker> invokers) {
    this.invokers = invokers;
  }

  /**
   * 构建只读注册表（防御性拷贝源 Map，构建后源变更不影响注册表）。
   *
   * @param source 契约标识到调用器的映射
   * @return 只读注册表
   */
  public static ContractInvokerRegistry of(Map<String, ContractInvoker> source) {
    return new ContractInvokerRegistry(new HashMap<>(source));
  }

  /** 按契约标识查找调用器；未命中返回 null。 */
  public ContractInvoker get(String contractName) {
    return invokers.get(contractName);
  }

  /** 已注册契约数量（发布自检日志 contract ready 计数依据）。 */
  public int size() {
    return invokers.size();
  }
}
