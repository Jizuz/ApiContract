package com.apicontract.gateway.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.gateway.ContractGatewayHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 任务10（红-绿）测试：网关统一根路径入口（design 网关入口）：
 * {@code http(s)://gateway:{port}/}，mt query 必填；错误码对应 HTTP 状态
 * （MT_MISSING/ARGUMENT_INVALID→400、CONTRACT_NOT_FOUND→404、
 * INVOKE_ERROR→502、NO_INSTANCE/UPSTREAM_UNAVAILABLE→503）。
 */
class ContractGatewayControllerTest {

  private MockMvc mockMvc;
  private Map<String, ContractMeta> index;

  @BeforeEach
  void setUp() {
    ContractMeta meta = new ContractMeta("echo.call", "com.apicontract.testapi.EchoContract",
        "call", List.of("id", "name"), List.of("long", "java.lang.String"),
        "java.lang.String", "user-provider", "1.0.0", List.of(),
        ContractMeta.SERIALIZER_JSON, null, null);
    index = new HashMap<>();
    index.put("echo.call", meta);
    ContractGatewayHandler handler = new ContractGatewayHandler(
        index::get,
        service -> List.of("http://user-provider:8080"),
        (baseUrl, mt, argsJson) ->
            "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":\"echo-ok\"}",
        new ObjectMapper());
    mockMvc = MockMvcBuilders.standaloneSetup(new ContractGatewayController(handler)).build();
  }

  @Test
  void shouldRouteGetCallAtRootPath() throws Exception {
    mockMvc.perform(get("/").param("mt", "echo.call")
            .param("id", "42").param("name", "jiz"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data").value("echo-ok"))
        .andExpect(jsonPath("$.traceId").exists());
  }

  @Test
  void shouldReturn400MtMissing() throws Exception {
    mockMvc.perform(get("/"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MT_MISSING"));
  }

  @Test
  void shouldRoutePostObjectBody() throws Exception {
    mockMvc.perform(post("/").param("mt", "echo.call")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\": 42, \"name\": \"jiz\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data").value("echo-ok"));
  }

  @Test
  void shouldReturn400OnArityMismatch() throws Exception {
    mockMvc.perform(post("/").param("mt", "echo.call")
            .contentType(MediaType.APPLICATION_JSON)
            .content("[42]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ARGUMENT_INVALID"));
  }

  @Test
  void shouldReturn404OnUnknownMt() throws Exception {
    mockMvc.perform(get("/").param("mt", "not.exists"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
  }

  @Test
  void shouldReturn429OnRateLimited() throws Exception {
    ContractGatewayHandler limited = new ContractGatewayHandler(
        index::get,
        service -> List.of("http://user-provider:8080"),
        (baseUrl, mt, argsJson) ->
            "{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":\"ok\"}",
        com.apicontract.gateway.cluster.ClusterInvoker.DEFAULT,
        new com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer(),
        new com.apicontract.gateway.ratelimit.TokenBucketRateLimiter(
            1, 0.0, () -> 1_000_000L),
        null, new ObjectMapper());
    MockMvc limitedMvc = MockMvcBuilders
        .standaloneSetup(new ContractGatewayController(limited)).build();

    limitedMvc.perform(post("/").param("mt", "echo.call").content("[42, \"jiz\"]")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    limitedMvc.perform(post("/").param("mt", "echo.call").content("[42, \"jiz\"]")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void shouldReturn503OnCircuitOpen() throws Exception {
    java.util.concurrent.atomic.AtomicLong clock =
        new java.util.concurrent.atomic.AtomicLong(1_000_000L);
    com.apicontract.gateway.loadbalance.CircuitBreakerRegistry breakers =
        new com.apicontract.gateway.loadbalance.CircuitBreakerRegistry(
            5_000L, clock::get);
    ContractGatewayHandler tripped = new ContractGatewayHandler(
        index::get,
        service -> List.of("http://user-provider:8080"),
        (baseUrl, mt, argsJson) -> {
          throw new java.io.IOException("down");
        },
        com.apicontract.gateway.cluster.ClusterInvoker.DEFAULT,
        new com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer(),
        null, breakers, new ObjectMapper());
    MockMvc trippedMvc = MockMvcBuilders
        .standaloneSetup(new ContractGatewayController(tripped)).build();

    for (int i = 0; i < 20; i++) {
      tripped.handle("echo.call", "POST", Map.of(), "[42, \"jiz\"]");
    }
    trippedMvc.perform(post("/").param("mt", "echo.call").content("[42, \"jiz\"]")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CIRCUIT_OPEN"));
  }
}
