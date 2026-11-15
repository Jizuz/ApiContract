package com.apicontract.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 任务10（红-绿）测试：网关 MVP 核心链路与错误码矩阵
 * （design 调用链路顺序不变量：解析 mt → 契约主索引 → 实例列表 →
 * 参数提取组装（个数不符不发起远程调用）→ 远程调用 → 返回）。
 */
class ContractGatewayHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<String> remoteCalls = new ArrayList<>();
  private ContractGatewayHandler handler;

  @BeforeEach
  void setUp() {
    remoteCalls.clear();
    ContractMeta meta = new ContractMeta("echo.call", "com.apicontract.testapi.EchoContract",
        "call", List.of("id", "name"), List.of("long", "java.lang.String"),
        "java.lang.String", "user-provider", "1.0.0", List.of(),
        ContractMeta.SERIALIZER_JSON, null, null);
    Map<String, ContractMeta> index = new HashMap<>();
    index.put("echo.call", meta);
    handler = new ContractGatewayHandler(
        mt -> index.get(mt),
        service -> List.of("http://user-provider:8080"),
        (baseUrl, mt, argsJson) -> {
          remoteCalls.add(baseUrl + "|" + mt + "|" + argsJson);
          return "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":\"echo:42:jiz\"}";
        },
        mapper);
  }

  private GatewayResponse call(String mt, String body) {
    return handler.handle(mt, "POST", Map.of(), body);
  }

  @Test
  void shouldReturnMtMissingWhenMtAbsent() {
    GatewayResponse response = call(null, "[]");

    assertEquals(GatewayErrorCodes.MT_MISSING, response.code());
    assertTrue(response.traceId() != null && !response.traceId().isBlank());
    assertTrue(remoteCalls.isEmpty());
  }

  @Test
  void shouldReturnContractNotFoundWhenIndexMiss() {
    GatewayResponse response = call("not.exists", "[]");

    assertEquals(GatewayErrorCodes.CONTRACT_NOT_FOUND, response.code());
    assertTrue(remoteCalls.isEmpty());
  }

  @Test
  void shouldReturnArgumentInvalidAndSkipRemoteWhenArityMismatch() {
    GatewayResponse response = call("echo.call", "[42]");

    assertEquals(GatewayErrorCodes.ARGUMENT_INVALID, response.code());
    assertTrue(remoteCalls.isEmpty(), "参数个数不符：不发起远程调用（design 链路顺序）");
  }

  @Test
  void shouldReturnNoInstanceWhenInstanceListEmpty() {
    ContractGatewayHandler noInstance = new ContractGatewayHandler(
        mt -> new ContractMeta("echo.call", "i", "m", List.of("id"),
            List.of("long"), "void", "user-provider", "1.0.0", List.of(),
            ContractMeta.SERIALIZER_JSON, null, null),
        service -> List.of(),
        (baseUrl, mt, argsJson) -> {
          throw new IllegalStateException("不应被调用");
        },
        mapper);

    GatewayResponse response = noInstance.handle("echo.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.NO_INSTANCE, response.code());
  }

  @Test
  void shouldRouteCallAndPassThroughData() {
    GatewayResponse response = call("echo.call", "[42, \"jiz\"]");

    assertEquals(GatewayErrorCodes.OK, response.code());
    assertEquals("echo:42:jiz", response.data().asText(), "Provider data 原样透传前端");
    assertEquals(1, remoteCalls.size());
    assertEquals("http://user-provider:8080|echo.call|[42,\"jiz\"]", remoteCalls.get(0),
        "远程调用：实例地址 + mt + 参数数组 JSON");
  }

  @Test
  void shouldReturnUpstreamUnavailableWhenRemoteCallFails() {
    ContractGatewayHandler failing = new ContractGatewayHandler(
        mt -> new ContractMeta("echo.call", "i", "m", List.of("id"),
            List.of("long"), "void", "user-provider", "1.0.0", List.of(),
            ContractMeta.SERIALIZER_JSON, null, null),
        service -> List.of("http://down:8080"),
        (baseUrl, mt, argsJson) -> {
          throw new java.io.IOException("connection refused");
        },
        mapper);

    GatewayResponse response = failing.handle("echo.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.UPSTREAM_UNAVAILABLE, response.code(),
        "网络级失败：上游不可用（集群容错在后续任务实现，MVP 直接收敛）");
  }

  @Test
  void shouldMapProviderBusinessErrorToInvokeError() {
    ContractGatewayHandler providerError = new ContractGatewayHandler(
        mt -> new ContractMeta("echo.call", "i", "m", List.of("id"),
            List.of("long"), "void", "user-provider", "1.0.0", List.of(),
            ContractMeta.SERIALIZER_JSON, null, null),
        service -> List.of("http://user-provider:8080"),
        (baseUrl, mt, argsJson) ->
            "{\"code\":\"CONTRACT_INVOKE_ERROR\",\"message\":\"boom-42\","
                + "\"traceId\":\"t1\",\"data\":null}",
        mapper);

    GatewayResponse response = providerError.handle("echo.call", "POST", Map.of(), "[42]");

    assertEquals(GatewayErrorCodes.INVOKE_ERROR, response.code(),
        "Provider 业务异常映射为网关 INVOKE_ERROR");
    assertTrue(response.message().contains("boom-42"), "Provider 错误消息保留");
    assertFalse(response.code().equals("CONTRACT_INVOKE_ERROR"),
        "对前端呈现统一网关错误码，不泄露内部契约入口语义");
  }

  @Test
  void shouldFailoverToNextInstanceWhenFirstDown() {
    List<String> attempts = new ArrayList<>();
    ContractGatewayHandler failover = new ContractGatewayHandler(
        mt -> new ContractMeta("echo.call", "i", "m", List.of("id"),
            List.of("long"), "void", "user-provider", "1.0.0", List.of(),
            ContractMeta.SERIALIZER_JSON, null, null),
        service -> List.of("http://a:8080", "http://b:8080"),
        (baseUrl, mt, argsJson) -> {
          attempts.add(baseUrl);
          if (baseUrl.startsWith("http://a:")) {
            throw new java.io.IOException("connection refused");
          }
          return "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\","
              + "\"data\":\"from-b\"}";
        },
        mapper);

    GatewayResponse response = failover.handle("echo.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.OK, response.code(),
        "任务20：failover 首实例失败换下一实例成功");
    assertEquals("from-b", response.data().asText());
    assertEquals(List.of("http://a:8080", "http://b:8080"), attempts,
        "默认轮询换实例，而非对同实例裸重试");
  }

  @Test
  void shouldExhaustFailoverAttemptsBeforeFailing() {
    List<String> attempts = new ArrayList<>();
    ContractGatewayHandler failing = new ContractGatewayHandler(
        mt -> new ContractMeta("echo.call", "i", "m", List.of("id"),
            List.of("long"), "void", "user-provider", "1.0.0", List.of(),
            ContractMeta.SERIALIZER_JSON, null, null),
        service -> List.of("http://a:8080", "http://b:8080"),
        (baseUrl, mt, argsJson) -> {
          attempts.add(baseUrl);
          throw new java.io.IOException("connection refused");
        },
        mapper);

    GatewayResponse response = failing.handle("echo.call", "POST", Map.of(), "[1]");

    assertEquals(GatewayErrorCodes.UPSTREAM_UNAVAILABLE, response.code());
    assertEquals(3, attempts.size(),
        "failover 默认重试 2 次：共 3 次尝试（1 + 2）");
  }

  @Test
  void shouldRunInvokeFiltersAroundSuccessfulCall() {
    List<String> events = new ArrayList<>();
    com.apicontract.gateway.filter.ContractInvokeFilter first =
        recordingFilter(0, "first", events);
    com.apicontract.gateway.filter.ContractInvokeFilter second =
        recordingFilter(100, "second", events);
    ContractGatewayHandler filtered = new ContractGatewayHandler(
        mt -> "echo.call".equals(mt) ? echoMeta() : null,
        service -> List.of("http://user-provider:8080"),
        (baseUrl, mt, argsJson) -> {
          remoteCalls.add(baseUrl);
          return "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":\"ok\"}";
        },
        com.apicontract.gateway.cluster.ClusterInvoker.DEFAULT,
        new com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer(),
        null, null,
        List.of(second, first), mapper);

    GatewayResponse response =
        filtered.handle("echo.call", "POST", Map.of(), "[42, \"jiz\"]");

    assertEquals(GatewayErrorCodes.OK, response.code());
    assertEquals(List.of("first:before", "second:before", "second:after", "first:after"),
        events, "过滤器链：前置按 order 升序、后置逆序（注册乱序不影响链序）");
    assertEquals(1, remoteCalls.size());
  }

  @Test
  void shouldConvergeWhenBeforeFilterFails() {
    ContractGatewayHandler broken = new ContractGatewayHandler(
        mt -> "echo.call".equals(mt) ? echoMeta() : null,
        service -> List.of("http://user-provider:8080"),
        (baseUrl, mt, argsJson) -> {
          remoteCalls.add(baseUrl);
          return "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":\"ok\"}";
        },
        com.apicontract.gateway.cluster.ClusterInvoker.DEFAULT,
        new com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer(),
        null, null,
        List.of(new com.apicontract.gateway.filter.ContractInvokeFilter() {
          @Override
          public int order() {
            return 0;
          }

          @Override
          public void before(com.apicontract.gateway.filter.ContractInvocation invocation) {
            throw new IllegalStateException("filter broken");
          }
        }),
        mapper);

    GatewayResponse response = broken.handle("echo.call", "POST", Map.of(), "[42, \"jiz\"]");

    assertEquals(GatewayErrorCodes.UPSTREAM_UNAVAILABLE, response.code(),
        "过滤器前置异常按所处链路收敛错误码（spec：不吞没）");
    assertTrue(remoteCalls.isEmpty(), "过滤器失败不触达远程调用");
  }

  private static ContractMeta echoMeta() {
    return new ContractMeta("echo.call", "com.apicontract.testapi.EchoContract",
        "call", List.of("id", "name"), List.of("long", "java.lang.String"),
        "java.lang.String", "user-provider", "1.0.0", List.of(),
        ContractMeta.SERIALIZER_JSON, null, null);
  }

  private static com.apicontract.gateway.filter.ContractInvokeFilter recordingFilter(
      int order, String name, List<String> events) {
    return new com.apicontract.gateway.filter.ContractInvokeFilter() {
      @Override
      public int order() {
        return order;
      }

      @Override
      public void before(com.apicontract.gateway.filter.ContractInvocation invocation) {
        events.add(name + ":before");
      }

      @Override
      public void after(com.apicontract.gateway.filter.ContractInvocation invocation,
          Object result) {
        events.add(name + ":after");
      }
    };
  }
}
