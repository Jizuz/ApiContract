package com.apicontract.gateway.filter;

import com.apicontract.api.spi.Spi;

/**
 * 调用过滤器 SPI（spec contract-spi-extension 扩展点「调用过滤器」）：
 * 网关远程调用前后拦截；实现按 {@link #order()} 升序链式执行。
 *
 * <p>内置实现：trace / log / metrics（{@link BuiltinInvokeFilters}）；
 * 鉴权本期仅预留扩展点、不提供内置实现（决策④）——业务可自行实现本接口注册。
 */
@Spi(name = "调用过滤器", defaultImpl = "")
public interface ContractInvokeFilter {

  /** 链内顺序（升序；内置约定 trace=100 / log=200 / metrics=300）。 */
  int order();

  /**
   * 前置处理（远程调用发起前，按 order 升序）。
   *
   * <p>抛出异常将中断本次调用并按所处链路收敛错误码（不吞没）。
   */
  void before(ContractInvocation invocation);

  /** 后置处理（远程调用返回响应后，按 order 降序——洋葱模型）。 */
  default void after(ContractInvocation invocation, Object result) {
  }

  /** 异常通知（本次调用以异常收敛时，按 order 升序通知后重抛原异常）。 */
  default void onError(ContractInvocation invocation, Throwable error) {
  }
}
