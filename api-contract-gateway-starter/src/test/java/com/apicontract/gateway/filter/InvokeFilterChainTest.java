package com.apicontract.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务22（红-绿）测试：调用过滤器链（design：ContractInvokeFilter
 * before/after/onError 按 order 链式；内置 trace/log/metrics 三个；
 * 鉴权仅预留扩展点，本期无内置实现——决策④）。
 */
class InvokeFilterChainTest {

  /** 记录型测试过滤器（order 可定制）。 */
  private static final class RecordingFilter implements ContractInvokeFilter {

    private final int order;
    private final String name;
    final List<String> events;

    RecordingFilter(int order, String name, List<String> events) {
      this.order = order;
      this.name = name;
      this.events = events;
    }

    @Override
    public int order() {
      return order;
    }

    @Override
    public void before(ContractInvocation invocation) {
      events.add(name + ":before");
    }

    @Override
    public void after(ContractInvocation invocation, Object result) {
      events.add(name + ":after");
    }

    @Override
    public void onError(ContractInvocation invocation, Throwable error) {
      events.add(name + ":error");
    }
  }

  private static ContractInvocation invocation() {
    return new ContractInvocation("echo.call", "[]", "trace-1");
  }

  @Test
  void shouldRunBeforeInOrderAndAfterInReverse() {
    List<String> events = new ArrayList<>();
    InvokeFilterChain chain = new InvokeFilterChain(List.of(
        new RecordingFilter(200, "metrics", events),
        new RecordingFilter(0, "trace", events),
        new RecordingFilter(100, "log", events)));

    chain.before(invocation());
    chain.after(invocation(), "ok");

    assertEquals(List.of("trace:before", "log:before", "metrics:before",
            "metrics:after", "log:after", "trace:after"),
        events, "前置按 order 升序、后置逆序（洋葱模型）");
  }

  @Test
  void shouldInvokeOnErrorAndPropagateFailure() {
    List<String> events = new ArrayList<>();
    InvokeFilterChain chain = new InvokeFilterChain(List.of(
        new RecordingFilter(0, "a", events),
        new RecordingFilter(100, "b", events)));

    IllegalStateException failure =
        assertThrows(IllegalStateException.class,
            () -> chain.onError(invocation(), new IllegalStateException("boom")));

    assertEquals("boom", failure.getMessage(),
        "onError 后异常继续传播：不吞没（spec：按所处链路收敛错误码）");
    assertEquals(List.of("a:error", "b:error"), events,
        "onError 按 order 升序逐过滤器通知");
  }

  @Test
  void shouldPropagateBeforeFailure() {
    InvokeFilterChain chain = new InvokeFilterChain(List.of(
        new ContractInvokeFilter() {
          @Override
          public int order() {
            return 0;
          }

          @Override
          public void before(ContractInvocation invocation) {
            throw new IllegalStateException("filter broken");
          }
        }));

    IllegalStateException failure =
        assertThrows(IllegalStateException.class,
            () -> chain.before(invocation()));
    assertEquals("filter broken", failure.getMessage(),
        "before 异常传播：由所处链路收敛为错误响应，不静默吞掉");
  }

  @Test
  void shouldProvideExactlyThreeBuiltinFiltersWithoutAuth() {
    List<ContractInvokeFilter> builtins = BuiltinInvokeFilters.all();

    assertEquals(3, builtins.size(),
        "内置过滤器恰 trace/log/metrics 三个（决策④：鉴权无内置实现）");
    assertTrue(builtins.stream().anyMatch(f -> f instanceof TraceInvokeFilter));
    assertTrue(builtins.stream().anyMatch(f -> f instanceof LogInvokeFilter));
    assertTrue(builtins.stream().anyMatch(f -> f instanceof MetricsInvokeFilter));
    assertEquals(3, builtins.stream().mapToInt(ContractInvokeFilter::order)
            .distinct().count(),
        "order 互不相同，链序确定");
  }

  @Test
  void traceFilterShouldFillMissingTraceId() {
    TraceInvokeFilter trace = new TraceInvokeFilter();
    ContractInvocation missing = new ContractInvocation("echo.call", "[]", null);

    trace.before(missing);

    assertNotNull(missing.traceId(), "traceId 缺失时由 trace 过滤器生成兜底");

    ContractInvocation present = new ContractInvocation("echo.call", "[]", "keep-me");
    trace.before(present);
    assertEquals("keep-me", present.traceId(), "已有 traceId 不覆盖");
  }

  @Test
  void metricsFilterShouldCountSuccessAndFailure() {
    MetricsInvokeFilter metrics = new MetricsInvokeFilter();
    ContractInvocation invocation = invocation();

    metrics.after(invocation, "ok");
    metrics.after(invocation, "ok");
    metrics.onError(invocation, new IllegalStateException("boom"));

    assertEquals(2, metrics.successCount());
    assertEquals(1, metrics.failureCount(),
        "契约维度成功/失败计数（任务24 接 Micrometer 的埋点出口）");
  }
}
