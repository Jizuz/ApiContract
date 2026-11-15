package com.apicontract.gateway.spi;

import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.spi.SpiLoader;
import com.apicontract.gateway.ContractArgumentExtractor;
import com.apicontract.gateway.DefaultContractArgumentExtractor;
import com.apicontract.gateway.cluster.ClusterFaultTolerance;
import com.apicontract.gateway.cluster.FailoverClusterFaultTolerance;
import com.apicontract.gateway.filter.ContractInvokeFilter;
import com.apicontract.gateway.filter.LogInvokeFilter;
import com.apicontract.gateway.filter.MetricsInvokeFilter;
import com.apicontract.gateway.filter.TraceInvokeFilter;
import com.apicontract.gateway.loadbalance.CircuitBreaker;
import com.apicontract.gateway.loadbalance.LoadBalancer;
import com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer;
import com.apicontract.gateway.loadbalance.SimpleCircuitBreaker;
import com.apicontract.gateway.ratelimit.ContractRateLimiter;
import com.apicontract.gateway.ratelimit.TokenBucketRateLimiter;
import com.apicontract.gateway.route.ContractRouter;
import com.apicontract.gateway.route.PassthroughContractRouter;
import com.apicontract.gateway.serialization.JsonContractSerializer;

/**
 * 网关端内置 SPI 注册表（规格 contract-spi-extension）：启动期一次性注册。
 *
 * <p>注册范围（网关端 7 扩展点 + 双向序列化器）：路由器、负载均衡器、集群策略、
 * 限流器、调用过滤器（链式，trace/log/metrics 三内置，无单选默认）、
 * 参数提取器、熔断器、序列化器（json）。
 *
 * <p>限流器/熔断器为有状态策略，内置实例按默认参数注册
 * （容量 100/s、窗口最小 20 失败率 0.5）；生产装配由配置项覆盖参数或整体替换实现。
 */
public final class GatewayBuiltinSpiRegistry {

  private GatewayBuiltinSpiRegistry() {
  }

  /** 注册内置实现（应用启动期调用一次）。 */
  public static void registerAll() {
    SpiLoader.registerBuiltin(ContractRouter.class, "passthrough",
        new PassthroughContractRouter());
    SpiLoader.registerBuiltin(LoadBalancer.class, "round-robin",
        new RoundRobinLoadBalancer());
    SpiLoader.registerBuiltin(ClusterFaultTolerance.class, "failover",
        new FailoverClusterFaultTolerance());
    SpiLoader.registerBuiltin(ContractRateLimiter.class, "token-bucket",
        new TokenBucketRateLimiter(100, 100.0, System::currentTimeMillis));
    SpiLoader.registerBuiltin(ContractArgumentExtractor.class, "default",
        new DefaultContractArgumentExtractor());
    SpiLoader.registerBuiltin(CircuitBreaker.class, "failure-rate",
        new SimpleCircuitBreaker(20, 0.5, 10_000L, System::currentTimeMillis));
    SpiLoader.registerBuiltin(ContractSerializer.class, "json",
        JsonContractSerializer.INSTANCE);
    SpiLoader.registerBuiltin(ContractInvokeFilter.class, "trace",
        new TraceInvokeFilter());
    SpiLoader.registerBuiltin(ContractInvokeFilter.class, "log",
        new LogInvokeFilter());
    SpiLoader.registerBuiltin(ContractInvokeFilter.class, "metrics",
        new MetricsInvokeFilter());
  }
}
