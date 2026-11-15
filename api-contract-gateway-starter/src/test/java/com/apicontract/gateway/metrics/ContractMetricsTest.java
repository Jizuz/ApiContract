package com.apicontract.gateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.apicontract.gateway.cluster.ClusterInvoker;
import com.apicontract.gateway.cluster.ClusterPolicy;
import com.apicontract.gateway.filter.ContractInvocation;
import com.apicontract.gateway.filter.MetricsInvokeFilter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 契约维度指标门面测试（spec contract-async-stability：
 * QPS/RT/错误率/熔断/重试/连接池使用率 + 索引刷新耗时）。
 */
class ContractMetricsTest {

  @BeforeEach
  void resetMetricsBefore() {
    ContractMetrics.reset();
  }

  @AfterEach
  void resetMetrics() {
    ContractMetrics.reset();
  }

  @Test
  void successAndFailureCountedPerContract() {
    ContractMetrics.recordSuccess("mt", 5);
    ContractMetrics.recordSuccess("mt", 6);
    ContractMetrics.recordFailure("mt", 1);

    ContractMetricSnapshot snapshot = ContractMetrics.snapshot("mt");
    assertThat(snapshot.successCount()).isEqualTo(2);
    assertThat(snapshot.failureCount()).isEqualTo(1);
    assertThat(snapshot.errorRate()).isCloseTo(1.0 / 3, within(1e-9));
  }

  @Test
  void rtAggregatesCountTotalAndMaxPerContract() {
    ContractMetrics.recordSuccess("mt", 5);
    ContractMetrics.recordSuccess("mt", 50);
    ContractMetrics.recordSuccess("mt", 20);

    ContractMetricSnapshot snapshot = ContractMetrics.snapshot("mt");
    assertThat(snapshot.rtCount()).isEqualTo(3);
    assertThat(snapshot.totalRtMillis()).isEqualTo(75);
    assertThat(snapshot.maxRtMillis()).isEqualTo(50);
    assertThat(snapshot.avgRtMillis()).isEqualTo(25);
  }

  @Test
  void contractsAreIsolatedAndUnknownIsEmpty() {
    ContractMetrics.recordSuccess("mt-a", 5);

    assertThat(ContractMetrics.snapshot("mt-a").successCount()).isEqualTo(1);
    assertThat(ContractMetrics.snapshot("mt-b").successCount()).isZero();
    assertThat(ContractMetrics.snapshot("mt-b").failureCount()).isZero();
  }

  @Test
  void timeoutCountedPerContract() {
    ContractMetrics.recordTimeout("mt");

    assertThat(ContractMetrics.snapshot("mt").timeoutCount()).isEqualTo(1);
  }

  @Test
  void retryCountedGlobally() {
    ContractMetrics.recordRetry();
    ContractMetrics.recordRetry();

    assertThat(ContractMetrics.retryTotal()).isEqualTo(2);
  }

  @Test
  void breakerOpenCountedGlobally() {
    ContractMetrics.recordBreakerOpen();
    ContractMetrics.recordBreakerOpen();

    assertThat(ContractMetrics.breakerOpenTotal()).isEqualTo(2);
  }

  @Test
  void poolExhaustedCountedAndUsageRatioTracked() {
    ContractMetrics.recordPoolExhausted();
    ContractMetrics.recordPoolExhausted();
    ContractMetrics.recordPoolExhausted();
    ContractMetrics.recordPoolUsage(75, 100);

    assertThat(ContractMetrics.poolExhaustedTotal()).isEqualTo(3);
    assertThat(ContractMetrics.poolInUse()).isEqualTo(75);
    assertThat(ContractMetrics.poolMax()).isEqualTo(100);
    assertThat(ContractMetrics.poolUsageRatio()).isCloseTo(0.75, within(1e-9));
  }

  @Test
  void indexRefreshTracksCountMaxAndFailure() {
    ContractMetrics.recordIndexRefresh(120, true);
    ContractMetrics.recordIndexRefresh(200, true);
    ContractMetrics.recordIndexRefresh(80, false);

    assertThat(ContractMetrics.indexRefreshCount()).isEqualTo(3);
    assertThat(ContractMetrics.indexRefreshMaxMillis()).isEqualTo(200);
    assertThat(ContractMetrics.indexRefreshFailureCount()).isEqualTo(1);
  }

  @Test
  void resetClearsAllState() {
    ContractMetrics.recordSuccess("mt", 5);
    ContractMetrics.recordRetry();
    ContractMetrics.recordBreakerOpen();
    ContractMetrics.recordIndexRefresh(100, true);

    ContractMetrics.reset();

    assertThat(ContractMetrics.snapshot("mt").successCount()).isZero();
    assertThat(ContractMetrics.retryTotal()).isZero();
    assertThat(ContractMetrics.breakerOpenTotal()).isZero();
    assertThat(ContractMetrics.indexRefreshCount()).isZero();
  }

  @Test
  void metricsFilterWritesThroughToContractMetrics() {
    MetricsInvokeFilter filter = new MetricsInvokeFilter();
    ContractInvocation invocation = new ContractInvocation("mt", "[]", null);

    filter.after(invocation, "ok");
    filter.onError(invocation, new RuntimeException("boom"));

    ContractMetricSnapshot snapshot = ContractMetrics.snapshot("mt");
    assertThat(snapshot.successCount()).isEqualTo(1);
    assertThat(snapshot.failureCount()).isEqualTo(1);
    assertThat(snapshot.rtCount()).isEqualTo(2);
  }

  @Test
  void clusterInvokerRetryRecordsGlobalRetryMetric() throws Exception {
    ClusterInvoker invoker = new ClusterInvoker(ClusterPolicy.FAILOVER, 2);
    AtomicInteger attempts = new AtomicInteger();

    String result = invoker.invoke(() -> {
      if (attempts.incrementAndGet() == 1) {
        throw new IOException("instance down");
      }
      return "recovered";
    });

    assertThat(result).isEqualTo("recovered");
    assertThat(ContractMetrics.retryTotal()).isEqualTo(1);
  }
}
