package com.apicontract.gateway.cluster;

/**
 * 集群容错策略（design：failover 默认重试 2 换实例、failfast、failsafe）。
 *
 * <ul>
 *   <li>{@link #FAILOVER}：默认；失败后换实例重试（默认 2 次），
 *       仅框架级异常重试，业务级异常直接失败</li>
 *   <li>{@link #FAILFAST}：首败即抛，不重试</li>
 *   <li>{@link #FAILSAFE}：吞掉上游失败返回降级空值，保障调用方不感知异常</li>
 * </ul>
 */
public enum ClusterPolicy {
  FAILOVER,
  FAILFAST,
  FAILSAFE
}
