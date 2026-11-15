package com.apicontract.gateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 告警规则文件加载与评估测试（spec contract-async-stability：
 * 告警 = 熔断打开、连接池耗尽、超时率异常、失败率异常）。
 */
class AlertRulesTest {

  @BeforeEach
  void resetMetricsBefore() {
    ContractMetrics.reset();
  }

  @AfterEach
  void resetMetrics() {
    ContractMetrics.reset();
  }

  @Test
  void classpathRuleFileDefinesFourBuiltinRules() {
    AlertRules rules = AlertRules.loadDefault();

    assertThat(rules.ruleNames()).containsExactlyInAnyOrder(
        "breaker-open", "pool-exhausted", "error-rate", "timeout-rate");
    assertThat(rules.threshold("error-rate")).isEqualTo(0.5);
    assertThat(rules.threshold("timeout-rate")).isEqualTo(0.1);
    assertThat(rules.minCount("breaker-open")).isEqualTo(1);
    assertThat(rules.minCount("pool-exhausted")).isEqualTo(1);
  }

  @Test
  void missingRuleFileFallsBackToBuiltinDefaults() {
    AlertRules rules = AlertRules.load("no-such-alert-rules.properties");

    assertThat(rules.ruleNames()).hasSize(4);
    assertThat(rules.threshold("error-rate")).isEqualTo(0.5);
  }

  @Test
  void evaluatorTriggersBreakerAndPoolRulesOnPositiveCounts() {
    ContractMetrics.recordBreakerOpen();
    ContractMetrics.recordPoolExhausted();

    List<String> alerts = AlertEvaluator.evaluate(
        ContractMetrics.currentView(), AlertRules.loadDefault());

    assertThat(alerts).contains("breaker-open", "pool-exhausted");
  }

  @Test
  void evaluatorTriggersErrorRateRuleAboveThreshold() {
    for (int i = 0; i < 4; i++) {
      ContractMetrics.recordSuccess("mt", 5);
    }
    ContractMetrics.recordFailure("mt", 5); // 20% 失败率 < 0.5 → 不触发
    assertThat(AlertEvaluator.evaluate(ContractMetrics.currentView(),
        ruleWithErrorRate(0.5))).doesNotContain("error-rate");

    for (int i = 0; i < 5; i++) {
      ContractMetrics.recordFailure("mt", 5);
    } // 10 请求 6 失败 = 60% ≥ 0.5 → 触发
    assertThat(AlertEvaluator.evaluate(ContractMetrics.currentView(),
        ruleWithErrorRate(0.5))).contains("error-rate");
  }

  @Test
  void evaluatorTriggersTimeoutRateRuleAboveThreshold() {
    ContractMetrics.recordSuccess("mt", 5);
    ContractMetrics.recordTimeout("mt"); // 50% > 0.1 → 触发

    List<String> alerts = AlertEvaluator.evaluate(
        ContractMetrics.currentView(), AlertRules.loadDefault());

    assertThat(alerts).contains("timeout-rate");
  }

  @Test
  void evaluatorSilentWhenAllMetricsHealthy() {
    ContractMetrics.recordSuccess("mt", 5);
    ContractMetrics.recordSuccess("mt", 5);

    List<String> alerts = AlertEvaluator.evaluate(
        ContractMetrics.currentView(), AlertRules.loadDefault());

    assertThat(alerts).isEmpty();
  }

  private AlertRules ruleWithErrorRate(double threshold) {
    return AlertRules.builtin().withThreshold("error-rate", threshold);
  }
}
