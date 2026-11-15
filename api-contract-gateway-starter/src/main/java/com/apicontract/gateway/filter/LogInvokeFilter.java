package com.apicontract.gateway.filter;

import com.apicontract.api.spi.SpiImpl;
import java.lang.System.Logger.Level;

/**
 * 内置日志过滤器（order=200）：契约级调用日志——记录 mt、目标实例、
 * 耗时与结果/异常并关联 traceId（design 可观测：两侧日志可关联）。
 */
@SpiImpl("log")
public final class LogInvokeFilter implements ContractInvokeFilter {

  private static final System.Logger logger = System.getLogger("api-contract.gateway.filter");

  @Override
  public int order() {
    return 200;
  }

  @Override
  public void before(ContractInvocation invocation) {
    // 日志过滤器仅在后置/异常通知时记录（前置无需处理）
  }

  @Override
  public void after(ContractInvocation invocation, Object result) {
    logger.log(Level.INFO,
        "契约调用完成 mt={0} instance={1} traceId={2} 耗时={3}ms",
        invocation.contractName(), invocation.instance(), invocation.traceId(),
        invocation.elapsedMillis());
  }

  @Override
  public void onError(ContractInvocation invocation, Throwable error) {
    logger.log(Level.WARNING,
        "契约调用失败 mt={0} instance={1} traceId={2} 耗时={3}ms 原因={4}",
        invocation.contractName(), invocation.instance(), invocation.traceId(),
        invocation.elapsedMillis(), String.valueOf(error));
  }
}
