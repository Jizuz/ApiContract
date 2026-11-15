package com.apicontract.gateway.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 告警规则文件加载（spec contract-async-stability：熔断打开、连接池耗尽、
 * 超时率异常、失败率异常）。
 *
 * <p>规则文件为 classpath properties（默认 {@code alert-rules.properties}），
 * 缺失或部分缺项时回退内置默认值——规则文件只覆盖阈值，不改变规则集合。
 */
public final class AlertRules {

  /** 默认规则文件名（装配层可按环境覆盖）。 */
  public static final String DEFAULT_RESOURCE = "alert-rules.properties";

  private static final String BREAKER_OPEN = "breaker-open";
  private static final String POOL_EXHAUSTED = "pool-exhausted";
  private static final String ERROR_RATE = "error-rate";
  private static final String TIMEOUT_RATE = "timeout-rate";

  private final Map<String, Long> minCounts = new LinkedHashMap<>();
  private final Map<String, Double> thresholds = new LinkedHashMap<>();

  private AlertRules() {
  }

  /** 加载 classpath 默认规则文件（缺失回退内置默认）。 */
  public static AlertRules loadDefault() {
    return load(DEFAULT_RESOURCE);
  }

  /** 加载指定 classpath 规则文件（缺失回退内置默认，不抛异常）。 */
  public static AlertRules load(String resourceName) {
    AlertRules rules = builtin();
    try (InputStream in =
        AlertRules.class.getClassLoader().getResourceAsStream(resourceName)) {
      if (in == null) {
        return rules;
      }
      Properties props = new Properties();
      props.load(in);
      rules.minCounts.put(BREAKER_OPEN,
          parseLong(props, "alert.breaker-open.min-count", 1));
      rules.minCounts.put(POOL_EXHAUSTED,
          parseLong(props, "alert.pool-exhausted.min-count", 1));
      rules.thresholds.put(ERROR_RATE,
          parseDouble(props, "alert.error-rate.threshold", 0.5));
      rules.thresholds.put(TIMEOUT_RATE,
          parseDouble(props, "alert.timeout-rate.threshold", 0.1));
      return rules;
    } catch (IOException failure) {
      return rules;
    }
  }

  /** 内置默认规则（与仓库规则文件一致的兜底值）。 */
  public static AlertRules builtin() {
    AlertRules rules = new AlertRules();
    rules.minCounts.put(BREAKER_OPEN, 1L);
    rules.minCounts.put(POOL_EXHAUSTED, 1L);
    rules.thresholds.put(ERROR_RATE, 0.5);
    rules.thresholds.put(TIMEOUT_RATE, 0.1);
    return rules;
  }

  /** 返回按内置顺序复制并覆盖指定阈值的新规则（测试与运行期调参用）。 */
  public AlertRules withThreshold(String ruleName, double threshold) {
    AlertRules copy = builtin();
    copy.thresholds.put(ruleName, threshold);
    return copy;
  }

  /** 规则名集合（顺序固定：计数类在前、比率类在后）。 */
  public java.util.Set<String> ruleNames() {
    java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
    names.addAll(minCounts.keySet());
    names.addAll(thresholds.keySet());
    return names;
  }

  /** 比率类规则阈值（未知规则返回 0，评估恒不触发）。 */
  public double threshold(String ruleName) {
    return thresholds.getOrDefault(ruleName, 0.0);
  }

  /** 计数类规则触发下限（未知规则返回 Long.MAX_VALUE，评估恒不触发）。 */
  public long minCount(String ruleName) {
    return minCounts.getOrDefault(ruleName, Long.MAX_VALUE);
  }

  private static long parseLong(Properties props, String key, long fallback) {
    String value = props.getProperty(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException failure) {
      return fallback;
    }
  }

  private static double parseDouble(Properties props, String key,
      double fallback) {
    String value = props.getProperty(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException failure) {
      return fallback;
    }
  }
}
