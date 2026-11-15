package com.apicontract.gateway.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.GatewayErrorCodes;
import com.apicontract.gateway.GatewayResponse;
import com.apicontract.gateway.cluster.ClusterInvoker;
import com.apicontract.gateway.cluster.ClusterPolicy;
import com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer;
import com.apicontract.gateway.ContractRemoteCaller;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 任务21（红-绿）测试：契约级限流接入调用链（spec 顺序：
 * 路由 → 限流 → 负载均衡 → 调用 → 容错）：令牌耗尽 429 RATE_LIMITED
 * 且不触达远程；不同契约独立；failover 重试重新过限流。
 */
class RateLimitGatewayTest {

  private final AtomicLong clock = new AtomicLong(1_000_000L);

  private static Map<String, ContractMeta> index(String... names) {
    Map<String, ContractMeta> map = new HashMap<>();
    for (String name : names) {
      map.put(name, new ContractMeta(name, "i", "m", List.of("id"),
          List.of("long"), "void", "user-provider", "1.0.0", List.of(),
          ContractMeta.SERIALIZER_JSON, null, null));
    }
    return map;
  }

  private ContractGatewayHandler build(Map<String, ContractMeta> index,
      List<String> instances, ContractRemoteCaller caller, int capacity) {
    return new ContractGatewayHandler(index::get, service -> instances,
        caller, ClusterInvoker.DEFAULT, new RoundRobinLoadBalancer(),
        new TokenBucketRateLimiter(capacity, 0.0, clock::get),
        null, new ObjectMapper());
  }

  @Test
  void shouldRejectWithRateLimitedWhenBucketExhausted() {
    List<String> remoteCalls = new java.util.ArrayList<>();
    ContractGatewayHandler limited = build(index("echo.call"),
        List.of("http://a:8080"),
        (baseUrl, mt, argsJson) -> {
          remoteCalls.add(baseUrl);
          return "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":\"ok\"}";
        },
        2);

    GatewayResponse first = limited.handle("echo.call", "POST", Map.of(), "[1]");
    GatewayResponse second = limited.handle("echo.call", "POST", Map.of(), "[1]");
    GatewayResponse third = limited.handle("echo.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.OK, first.code());
    assertEquals(GatewayErrorCodes.OK, second.code());
    assertEquals(GatewayErrorCodes.RATE_LIMITED, third.code(),
        "容量 2 耗尽：第 3 次 429 RATE_LIMITED");
    assertEquals(2, remoteCalls.size(),
        "限流拒绝在负载均衡/远程调用之前：不触达实例");
  }

  @Test
  void shouldIsolateBucketsByContract() {
    ContractGatewayHandler limited = build(index("echo.call", "other.call"),
        List.of("http://a:8080"),
        (baseUrl, mt, argsJson) ->
            "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":\"ok\"}",
        2);

    limited.handle("echo.call", "POST", Map.of(), "[1]");
    limited.handle("echo.call", "POST", Map.of(), "[1]");
    GatewayResponse echoTripped = limited.handle("echo.call", "POST",
        Map.of(), "[1]");
    GatewayResponse other = limited.handle("other.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.RATE_LIMITED, echoTripped.code());
    assertEquals(GatewayErrorCodes.OK, other.code(),
        "echo.call 耗尽不影响 other.call（契约级独立桶）");
  }

  @Test
  void shouldReacquireTokenOnEachFailoverAttempt() {
    List<String> attempts = new java.util.ArrayList<>();
    ContractGatewayHandler handler = build(index("echo.call"),
        List.of("http://a:8080", "http://b:8080"),
        (baseUrl, mt, argsJson) -> {
          attempts.add(baseUrl);
          if (baseUrl.startsWith("http://a:")) {
            throw new java.io.IOException("instance a down");
          }
          return "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\","
              + "\"data\":\"from-b\"}";
        },
        3);

    GatewayResponse first = handler.handle("echo.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.OK, first.code(), "a 失败 failover 到 b 成功");
    assertEquals(List.of("http://a:8080", "http://b:8080"), attempts);

    GatewayResponse second = handler.handle("echo.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.RATE_LIMITED, second.code(),
        "容量 3：首次调用耗 2 令牌（重试重新过限流），第二次首次尝试耗 1 后"
            + "重试无令牌可取：限流终止，不再重试");
    assertEquals(3, attempts.size(),
        "第二次调用仅 a 触达 1 次，重试被限流拒绝（b 零新增）");
  }

  @Test
  void shouldTerminateFailoverRetryWhenTokensExhausted() {
    List<String> attempts = new java.util.ArrayList<>();
    ContractGatewayHandler handler = new ContractGatewayHandler(
        index("echo.call")::get,
        service -> List.of("http://a:8080"),
        (baseUrl, mt, argsJson) -> {
          attempts.add(baseUrl);
          throw new java.io.IOException("down");
        },
        new ClusterInvoker(ClusterPolicy.FAILOVER, 5),
        new RoundRobinLoadBalancer(),
        new TokenBucketRateLimiter(2, 0.0, clock::get),
        null, new ObjectMapper());

    GatewayResponse first = handler.handle("echo.call", "POST", Map.of(), "[1]");
    GatewayResponse second = handler.handle("echo.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.RATE_LIMITED, first.code(),
        "failover(5) 重试重新过限流：容量 2 耗光后重试被限流终止");
    assertEquals(2, attempts.size(),
        "第 1 次尝试 + 1 次重试各耗 1 令牌后截断，不再继续剩余重试");
    assertEquals(GatewayErrorCodes.RATE_LIMITED, second.code(),
        "桶空后新请求首次尝试即被限流拒绝（业务级终止，不重试）");
    assertEquals(2, attempts.size());
  }
}
