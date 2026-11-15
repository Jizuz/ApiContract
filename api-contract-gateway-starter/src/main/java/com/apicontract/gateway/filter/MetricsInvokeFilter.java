package com.apicontract.gateway.filter;

import com.apicontract.api.spi.SpiImpl;
import com.apicontract.gateway.metrics.ContractMetrics;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内置指标过滤器（order=300）：契约维度成功/失败计数埋点出口；
 * 同时写透全局指标门面 {@link ContractMetrics}（QPS/RT/错误率，
 * spec contract-async-stability 可观测指标）。
 */
@SpiImpl("metrics")
public final class MetricsInvokeFilter implements ContractInvokeFilter {

  private final AtomicLong success = new AtomicLong();
  private final AtomicLong failure = new AtomicLong();

  @Override
  public int order() {
    return 300;
  }

  @Override
  public void before(ContractInvocation invocation) {
    // 指标过滤器仅在后置/异常通知时计数（前置无需处理）
  }

  @Override
  public void after(ContractInvocation invocation, Object result) {
    success.incrementAndGet();
    ContractMetrics.recordSuccess(invocation.contractName(),
        invocation.elapsedMillis());
  }

  @Override
  public void onError(ContractInvocation invocation, Throwable error) {
    failure.incrementAndGet();
    ContractMetrics.recordFailure(invocation.contractName(),
        invocation.elapsedMillis());
  }

  public long successCount() {
    return success.get();
  }

  public long failureCount() {
    return failure.get();
  }
}
