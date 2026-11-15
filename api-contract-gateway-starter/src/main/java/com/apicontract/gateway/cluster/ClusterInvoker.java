package com.apicontract.gateway.cluster;

import com.apicontract.gateway.metrics.ContractMetrics;
import java.util.concurrent.Callable;

/**
 * 集群容错执行器（design 调用链顺序不变量：路由 → 限流 → 负载均衡 → 调用 → 容错）。
 *
 * <p>每次尝试 = 完整执行一次 {@code attempt}（限流 → LB 选实例 → 调用），
 * 失败重试即<b>重新执行 attempt</b>——重试不绕过路由链：重试请求仍过限流、
 * 仍经负载均衡换实例，禁止对同一实例绕过链路裸调。
 */
public final class ClusterInvoker {

  /** 网关默认：failover，重试 2 次。 */
  public static final ClusterInvoker DEFAULT =
      new ClusterInvoker(ClusterPolicy.FAILOVER, 2);

  private final ClusterPolicy policy;
  private final int retries;

  public ClusterInvoker(ClusterPolicy policy, int retries) {
    if (policy == null) {
      throw new IllegalArgumentException("集群策略不可为空");
    }
    if (retries < 0) {
      throw new IllegalArgumentException("重试次数不可为负: " + retries);
    }
    this.policy = policy;
    this.retries = retries;
  }

  public ClusterPolicy policy() {
    return policy;
  }

  public int retries() {
    return retries;
  }

  /**
   * 执行带容错的调用链尝试。
   *
   * @param attempt 一次完整链路尝试（限流 → LB → 调用）；重试即重新执行
   * @return 尝试成功结果；failsafe 吞失败时返回 null（调用方按降级处理）
   * @throws Exception 最后一次尝试的失败（业务级失败立即上抛不重试）
   */
  public <T> T invoke(Callable<T> attempt) throws Exception {
    int maxAttempts = policy == ClusterPolicy.FAILOVER ? 1 + retries : 1;
    Exception lastFailure = null;
    for (int execution = 0; execution < maxAttempts; execution++) {
      try {
        return attempt.call();
      } catch (Exception failure) {
        if (policy == ClusterPolicy.FAILSAFE) {
          return null;
        }
        if (!FrameworkFailures.retryable(failure)) {
          throw failure;
        }
        ContractMetrics.recordRetry();
        lastFailure = failure;
      }
    }
    throw lastFailure;
  }
}
