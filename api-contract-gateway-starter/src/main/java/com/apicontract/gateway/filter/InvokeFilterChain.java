package com.apicontract.gateway.filter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 过滤器链执行器：构造时按 {@link ContractInvokeFilter#order()} 升序排序
 * （稳定排序，同 order 保持注册序）；前置升序、后置降序（洋葱模型）、
 * 异常按升序通知后重抛原异常（spec：异常不吞没，由所处链路收敛错误码）。
 */
public final class InvokeFilterChain {

  private final List<ContractInvokeFilter> ordered;

  public InvokeFilterChain(List<ContractInvokeFilter> filters) {
    this.ordered = new ArrayList<>(filters);
    this.ordered.sort(Comparator.comparingInt(ContractInvokeFilter::order));
  }

  /** 前置：按 order 升序逐过滤器执行；抛出异常即中断后续。 */
  public void before(ContractInvocation invocation) {
    for (ContractInvokeFilter filter : ordered) {
      filter.before(invocation);
    }
  }

  /** 后置：按 order 降序（洋葱模型逆序返回）。 */
  public void after(ContractInvocation invocation, Object result) {
    for (int i = ordered.size() - 1; i >= 0; i--) {
      ordered.get(i).after(invocation, result);
    }
  }

  /** 异常通知：按 order 升序逐过滤器通知后重抛原异常。 */
  public void onError(ContractInvocation invocation, Throwable error) throws Throwable {
    for (ContractInvokeFilter filter : ordered) {
      filter.onError(invocation, error);
    }
    throw error;
  }
}
