package com.apicontract.provider.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.provider.invoke.ContractInvoker;
import com.apicontract.provider.invoke.ContractInvokerFactory;
import com.apicontract.provider.invoke.ContractInvokerRegistry;
import com.apicontract.provider.invoke.InvokerMode;
import com.apicontract.provider.scan.ContractBinding;
import com.apicontract.provider.scan.ContractScanner;
import com.apicontract.provider.scan.ScanOptions;
import com.apicontract.testapi.EchoContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 任务6（红-绿）测试：泛化入口 MVC 层（默认路径 /_contract/rpc，薄转发层）。
 *
 * <p>规格（contract-provider-invoke）：统一泛化入口接收调用请求并解析契约标识；
 * 入口路径可配置避让业务路径；错误结构含错误码、消息、traceId。
 */
class ContractRpcControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Map<String, ContractInvoker> invokers = new HashMap<>();
    Map<String, ContractMeta> metas = new HashMap<>();
    List<String> warnings = new ArrayList<>();
    for (ContractBinding binding : ContractScanner.scanInterface(
        EchoContract.class, (EchoContract) (id, name) -> "echo:" + id + ":" + name,
        ScanOptions.of("user-provider"), warnings::add)) {
      invokers.put(binding.meta().contractName(),
          ContractInvokerFactory.create(binding, InvokerMode.BYTECODE));
      metas.put(binding.meta().contractName(), binding.meta());
    }
    ContractRpcHandler handler =
        new ContractRpcHandler(ContractInvokerRegistry.of(invokers), metas, new ObjectMapper(),
            1024 * 1024);
    mockMvc = MockMvcBuilders.standaloneSetup(new ContractRpcController(handler)).build();
  }

  @Test
  void shouldRouteGenericEntryCall() throws Exception {
    mockMvc.perform(post("/_contract/rpc")
            .param("mt", "echo.call")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("[42, \"jiz\"]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data").value("echo:42:jiz"))
        .andExpect(jsonPath("$.traceId").exists());
  }

  @Test
  void shouldReturnContractNotFoundViaMvc() throws Exception {
    mockMvc.perform(post("/_contract/rpc")
            .param("mt", "not.exists")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("[]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"))
        .andExpect(jsonPath("$.traceId").exists());
  }

  @Test
  void shouldReturnArgumentInvalidWhenMtMissing() throws Exception {
    mockMvc.perform(post("/_contract/rpc")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("[]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CONTRACT_ARGUMENT_INVALID"))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void shouldReturnResultForNoBodyWhenContractHasParams() throws Exception {
    mockMvc.perform(post("/_contract/rpc")
            .param("mt", "echo.call"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CONTRACT_ARGUMENT_INVALID"))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void shouldEchoGatewayTraceIdFromHeader() throws Exception {
    mockMvc.perform(post("/_contract/rpc")
            .param("mt", "echo.call")
            .header("X-Contract-TraceId", "trace-from-gateway")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("[42, \"jiz\"]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"))
        // 任务22：网关 traceId 经请求头透传，两侧日志可关联
        .andExpect(jsonPath("$.traceId").value("trace-from-gateway"));
  }

  @Test
  void shouldGenerateTraceIdWhenHeaderAbsent() throws Exception {
    mockMvc.perform(post("/_contract/rpc")
            .param("mt", "echo.call")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("[42, \"jiz\"]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"))
        // 无透传 traceId 时 Provider 自生成，本地日志仍可关联
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }
}
