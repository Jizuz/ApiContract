package com.apicontract.gateway.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.ContractRemoteCaller;
import com.apicontract.gateway.GatewayErrorCodes;
import com.apicontract.gateway.GatewayResponse;
import com.apicontract.gateway.loadbalance.CircuitBreakerRegistry;
import com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 任务21（红-绿）测试：契约+实例维度熔断（design：阈值 0.5/最小 20 请求、
 * OPEN 冷却期拒绝、半开单探测恢复、单实例熔断不拖垮其他实例/契约）。
 *
 * <p>经 Handler 真实链路验证（限流 → LB → 熔断检查 → 调用 → 容错），
 * 时钟注入可控推进 OPEN→HALF_OPEN。
 */
class CircuitBreakerGatewayTest {

  private static final long OPEN_TIMEOUT_MILLIS = 5_000L;

  private final AtomicLong clock = new AtomicLong(1_000_000L);
  private final AtomicInteger callerInvocations = new AtomicInteger();

  private ContractGatewayHandler handler;
  private ContractRemoteCaller caller;

  /** 组装带熔断注册表的 Handler：instances/caller 由用例定制。 */
  private ContractGatewayHandler build(Map<String, ContractMeta> index,
      List<String> instances) {
    CircuitBreakerRegistry breakers = new CircuitBreakerRegistry(
        OPEN_TIMEOUT_MILLIS, clock::get);
    caller = (baseUrl, mt, argsJson) -> {
      callerInvocations.incrementAndGet();
      if (failNextCalls.getAndDecrement() > 0) {
        throw new java.io.IOException("instance down");
      }
      return "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":\"ok\"}";
    };
    return new ContractGatewayHandler(index::get, service -> instances,
        caller, ClusterInvoker.DEFAULT, new RoundRobinLoadBalancer(),
        null, breakers, new ObjectMapper());
  }

  /** 连续失败次数倒计数（&gt;0 时 caller 抛 IOException）。 */
  private final AtomicInteger failNextCalls = new AtomicInteger();

  private static Map<String, ContractMeta> index(String... contractNames) {
    Map<String, ContractMeta> map = new HashMap<>();
    for (String name : contractNames) {
      map.put(name, new ContractMeta(name, "i", "m", List.of("id"),
          List.of("long"), "void", "user-provider", "1.0.0", List.of(),
          ContractMeta.SERIALIZER_JSON, null, null));
    }
    return map;
  }

  private GatewayResponse call(ContractGatewayHandler target) {
    return target.handle("echo.call", "POST", Map.of(), "[1]");
  }

  @Test
  void shouldOpenCircuitWhenFailureRateReachesThreshold() {
    handler = build(index("echo.call"), List.of("http://a:8080"));
    failNextCalls.set(10);

    for (int i = 0; i < 10; i++) {
      call(handler);
    }
    failNextCalls.set(10);
    for (int i = 0; i < 10; i++) {
      call(handler);
    }
    int callerCallsAtOpen = callerInvocations.get();

    GatewayResponse rejected = call(handler);

    assertEquals(GatewayErrorCodes.CIRCUIT_OPEN, rejected.code(),
        "窗口 20 请求失败率 0.5 达阈值：熔断打开，503 CIRCUIT_OPEN");
    assertEquals(callerCallsAtOpen, callerInvocations.get(),
        "熔断打开期不再触达实例（短路在远程调用之前）");
  }

  @Test
  void shouldRejectDuringOpenThenRecoverAfterProbeSucceeds() {
    handler = build(index("echo.call"), List.of("http://a:8080"));
    failNextCalls.set(20);
    for (int i = 0; i < 20; i++) {
      call(handler);
    }

    GatewayResponse duringOpen = call(handler);
    assertEquals(GatewayErrorCodes.CIRCUIT_OPEN, duringOpen.code());

    clock.addAndGet(OPEN_TIMEOUT_MILLIS + 1);
    GatewayResponse probe = call(handler);

    assertEquals(GatewayErrorCodes.OK, probe.code(),
        "冷却期满半开放行单探测，探测成功恢复闭合");
    GatewayResponse afterRecover = call(handler);
    assertEquals(GatewayErrorCodes.OK, afterRecover.code(),
        "恢复闭合后正常放行");
    assertEquals(22, callerInvocations.get(),
        "OPEN 期 1 次被短路 + 半开探测 1 次 + 恢复后 1 次真实触达");
  }

  @Test
  void shouldIsolateBreakersByContract() {
    Map<String, ContractMeta> contracts = index("echo.call", "other.call");
    handler = build(contracts, List.of("http://a:8080"));
    failNextCalls.set(20);
    for (int i = 0; i < 20; i++) {
      call(handler);
    }

    GatewayResponse tripped = call(handler);
    GatewayResponse other = handler.handle("other.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.CIRCUIT_OPEN, tripped.code(),
        "echo.call 打开熔断");
    assertEquals(GatewayErrorCodes.OK, other.code(),
        "同实例不同契约不受影响（熔断按契约+实例维度隔离）");
  }

  @Test
  void shouldFailoverToHealthyInstanceWhenCircuitOpen() {
    handler = build(index("echo.call"),
        List.of("http://a:8080", "http://b:8080"));
    List<String> attempted = new ArrayList<>();
    CircuitBreakerRegistry breakers = new CircuitBreakerRegistry(
        OPEN_TIMEOUT_MILLIS, clock::get);
    ContractGatewayHandler failoverHandler = new ContractGatewayHandler(
        index("echo.call")::get,
        service -> List.of("http://a:8080", "http://b:8080"),
        (baseUrl, mt, argsJson) -> {
          attempted.add(baseUrl);
          if (baseUrl.startsWith("http://a:")) {
            throw new java.io.IOException("instance a down");
          }
          return "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\","
              + "\"data\":\"from-b\"}";
        },
        ClusterInvoker.DEFAULT, new RoundRobinLoadBalancer(),
        null, breakers, new ObjectMapper());

    for (int i = 0; i < 20; i++) {
      failoverHandler.handle("echo.call", "POST", Map.of(), "[1]");
    }
    int callsAtOpen = attempted.size();

    GatewayResponse response = failoverHandler.handle("echo.call", "POST",
        Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.OK, response.code(),
        "实例 a 熔断打开：短路后 failover 换实例 b 成功");
    assertEquals(callsAtOpen + 1, attempted.size(),
        "a 被熔断短路零触达，仅 b 被真实调用 1 次");
    assertEquals("http://b:8080", attempted.get(attempted.size() - 1));
  }
}
