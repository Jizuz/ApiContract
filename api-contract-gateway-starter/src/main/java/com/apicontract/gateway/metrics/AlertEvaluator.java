package com.apicontract.gateway.metrics;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警评估器：按 {@link AlertRules} 阈值评估 {@link MetricsView}，
 * 返回触发的告警规则名（由装配层接通知渠道：日志/值班 webhook）。
 */
public final class AlertEvaluator {

  private AlertEvaluator() {
  }

  /** 评估当前视图，返回全部触发的告警规则名（按规则定义顺序）。 */
  public static List<String> evaluate(MetricsView view, AlertRules rules) {
    List<String> alerts = new ArrayList<>();
    if (view.breakerOpenTotal() >= rules.minCount("breaker-open")) {
      alerts.add("breaker-open");
    }
    if (view.poolExhaustedTotal() >= rules.minCount("pool-exhausted")) {
      alerts.add("pool-exhausted");
    }
    if (view.errorRate() >= rules.threshold("error-rate")
        && view.errorRate() > 0) {
      alerts.add("error-rate");
    }
    if (view.timeoutRate() >= rules.threshold("timeout-rate")
        && view.timeoutRate() > 0) {
      alerts.add("timeout-rate");
    }
    return List.copyOf(alerts);
  }
}
