package com.apicontract.gateway.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.spi.SpiLoader;
import com.apicontract.api.spi.SpiNotFoundException;
import com.apicontract.gateway.ContractArgumentExtractor;
import com.apicontract.gateway.DefaultContractArgumentExtractor;
import com.apicontract.gateway.ParameterExtractor;
import com.apicontract.gateway.cluster.ClusterFaultTolerance;
import com.apicontract.gateway.cluster.FailoverClusterFaultTolerance;
import com.apicontract.gateway.filter.ContractInvokeFilter;
import com.apicontract.gateway.loadbalance.CircuitBreaker;
import com.apicontract.gateway.loadbalance.LoadBalancer;
import com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer;
import com.apicontract.gateway.loadbalance.SimpleCircuitBreaker;
import com.apicontract.gateway.ratelimit.ContractRateLimiter;
import com.apicontract.gateway.ratelimit.TokenBucketRateLimiter;
import com.apicontract.gateway.route.ContractRouter;
import com.apicontract.gateway.route.PassthroughContractRouter;
import com.apicontract.gateway.serialization.JsonContractSerializer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 网关端内置 SPI 注册测试（规格 contract-spi-extension：13 扩展点清单之网关 7 个）。
 */
class GatewayBuiltinSpiRegistryTest {

  private static final ContractMeta ONE_PARAM_META = new ContractMeta("mt", "IFace", "m",
      List.of("a"), List.of("java.lang.String"), "void", "svc", "1.0.0", null, "json",
      null, null);

  @BeforeEach
  void resetSpiState() {
    SpiLoader.reset();
    GatewayBuiltinSpiRegistry.registerAll();
  }

  @Test
  void router_defaultIsPassthrough_andKeepsCandidateInstances() {
    ContractRouter router = SpiLoader.get(ContractRouter.class);

    assertThat(router).isInstanceOf(PassthroughContractRouter.class);
    assertThat(router.filter("mt", List.of("h1:8080", "h2:8080")))
        .containsExactly("h1:8080", "h2:8080");
  }

  @Test
  void loadBalancer_defaultIsRoundRobin() {
    LoadBalancer balancer = SpiLoader.get(LoadBalancer.class);

    assertThat(balancer).isInstanceOf(RoundRobinLoadBalancer.class);
    List<String> instances = List.of("h1:8080", "h2:8080");
    assertThat(balancer.choose("mt", instances))
        .isNotEqualTo(balancer.choose("mt", instances));
  }

  @Test
  void clusterFaultTolerance_defaultIsFailover_andPassesThroughSuccess() throws Exception {
    ClusterFaultTolerance cluster = SpiLoader.get(ClusterFaultTolerance.class);

    assertThat(cluster).isInstanceOf(FailoverClusterFaultTolerance.class);
    assertThat(cluster.invoke(() -> "ok")).isEqualTo("ok");
  }

  @Test
  void rateLimiter_defaultIsTokenBucket() {
    ContractRateLimiter limiter = SpiLoader.get(ContractRateLimiter.class);

    assertThat(limiter).isInstanceOf(TokenBucketRateLimiter.class);
    assertThat(SpiLoader.implNames(ContractRateLimiter.class))
        .containsExactly("token-bucket");
  }

  @Test
  void circuitBreaker_defaultIsFailureRateImpl_andInitiallyClosed() {
    CircuitBreaker breaker = SpiLoader.get(CircuitBreaker.class);

    assertThat(breaker).isInstanceOf(SimpleCircuitBreaker.class);
    assertThat(breaker.allowRequest()).isTrue();
  }

  @Test
  void argumentExtractor_defaultDelegatesToBuiltinRules() {
    ContractArgumentExtractor extractor = SpiLoader.get(ContractArgumentExtractor.class);

    assertThat(extractor).isInstanceOf(DefaultContractArgumentExtractor.class);
    assertThat(extractor.extractGet(ONE_PARAM_META, Map.of("a", "v")))
        .isEqualTo(ParameterExtractor.extractGet(ONE_PARAM_META, Map.of("a", "v")));
  }

  @Test
  void invokeFilter_chainPointHasNoDefault_builtinThreeRegisteredWithOrder() {
    SpiLoader.loadOverrides(ContractInvokeFilter.class);

    assertThatThrownBy(() -> SpiLoader.get(ContractInvokeFilter.class))
        .isInstanceOfSatisfying(SpiNotFoundException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("SPI_IMPL_NOT_FOUND"));

    assertThat(SpiLoader.get(ContractInvokeFilter.class, "trace").order()).isEqualTo(100);
    assertThat(SpiLoader.get(ContractInvokeFilter.class, "log").order()).isEqualTo(200);
    assertThat(SpiLoader.get(ContractInvokeFilter.class, "metrics").order()).isEqualTo(300);
  }

  @Test
  void serializer_jsonIsBuiltinDefaultOnGatewaySide() {
    ContractSerializer serializer = SpiLoader.get(ContractSerializer.class, "json");

    assertThat(serializer).isSameAs(JsonContractSerializer.INSTANCE);
  }
}
