package com.apicontract.gateway.cluster;

import com.apicontract.api.spi.Spi;
import java.util.concurrent.Callable;

/**
 * 集群容错扩展点（规格 contract-spi-extension）：失败重试与换实例策略。
 *
 * <p>内置实现：failover（默认重试 2，重试重新执行完整链路不绕过路由）、
 * failfast、failsafe（失败吞并返回 null 按降级处理）。
 */
@Spi(name = "集群策略", defaultImpl = "failover")
public interface ClusterFaultTolerance {

  /**
   * 执行带容错的调用链尝试。
   *
   * @param attempt 一次完整链路尝试（限流 → LB → 调用）；重试即重新执行
   * @return 尝试成功结果；failsafe 吞失败时返回 null（调用方按降级处理）
   * @throws Exception 最后一次尝试的失败（业务级失败立即上抛不重试）
   */
  <T> T invoke(Callable<T> attempt) throws Exception;
}
