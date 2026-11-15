package com.apicontract.gateway.filter;

import com.apicontract.api.spi.SpiImpl;
import java.util.UUID;

/**
 * 内置 trace 过滤器（order=100）：保证上下文携带 traceId——
 * 缺失时兜底生成并回填，已有值不覆盖（网关入口已生成的 traceId 直接沿用）。
 */
@SpiImpl("trace")
public final class TraceInvokeFilter implements ContractInvokeFilter {

  @Override
  public int order() {
    return 100;
  }

  @Override
  public void before(ContractInvocation invocation) {
    if (invocation.traceId() == null || invocation.traceId().isBlank()) {
      invocation.setTraceId(newTraceId());
    }
  }

  private static String newTraceId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }
}
