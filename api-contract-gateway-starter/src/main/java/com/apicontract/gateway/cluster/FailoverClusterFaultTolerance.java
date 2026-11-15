package com.apicontract.gateway.cluster;

import com.apicontract.api.spi.SpiImpl;
import java.util.concurrent.Callable;

/**
 * 内置集群容错：委托 {@link ClusterInvoker#DEFAULT}（failover，重试 2 次，
 * 重试重新执行完整链路不绕过路由/限流）。
 */
@SpiImpl("failover")
public class FailoverClusterFaultTolerance implements ClusterFaultTolerance {

  @Override
  public <T> T invoke(Callable<T> attempt) throws Exception {
    return ClusterInvoker.DEFAULT.invoke(attempt);
  }
}
