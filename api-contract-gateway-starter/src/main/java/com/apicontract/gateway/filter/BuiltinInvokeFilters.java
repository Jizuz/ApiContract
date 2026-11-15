package com.apicontract.gateway.filter;

import java.util.List;

/**
 * 内置调用过滤器清单：恰 trace / log / metrics 三个（按 order 升序）；
 * 鉴权不提供内置实现，仅预留扩展点（决策④）。
 */
public final class BuiltinInvokeFilters {

  private BuiltinInvokeFilters() {
  }

  public static List<ContractInvokeFilter> all() {
    return List.of(new TraceInvokeFilter(), new LogInvokeFilter(),
        new MetricsInvokeFilter());
  }
}
