package com.apicontract.provider.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.provider.scan.ContractBinding;
import com.apicontract.provider.scan.ContractScanner;
import com.apicontract.provider.scan.ScanOptions;
import com.apicontract.provider.invoke.ContractInvoker;
import com.apicontract.provider.invoke.ContractInvokerFactory;
import com.apicontract.provider.invoke.ContractInvokerRegistry;
import com.apicontract.provider.invoke.InvokerMode;
import com.apicontract.testapi.BoomContract;
import com.apicontract.testapi.EchoContract;
import com.apicontract.testapi.NoParamContract;
import com.apicontract.testapi.OrderStatus;
import com.apicontract.testapi.RichContract;
import com.apicontract.testapi.UserContract;
import com.apicontract.testapi.UserContractImpl;
import com.apicontract.testapi.UserDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 任务6（红-绿）测试：泛化入口核心处理器。
 *
 * <p>规格（contract-provider-invoke）：按契约标识分发；未命中直接返回 CONTRACT_NOT_FOUND
 * 不兜底；参数个数不符/类型转换失败返回 CONTRACT_ARGUMENT_INVALID；
 * 业务异常 CONTRACT_INVOKE_ERROR（仅错误码与消息，不含内部堆栈）；
 * 框架级异常 CONTRACT_INTERNAL_ERROR；无参契约忽略请求体；
 * null 参数保持 null；日期、枚举、金额统一转换规则；请求体大小设上限。
 */
class ContractRpcHandlerTest {

  private final List<String> warnings = new ArrayList<>();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, com.apicontract.api.meta.ContractMeta> metas = new HashMap<>();
  private ContractInvokerRegistry registry;
  private ContractRpcHandler handler;

  @BeforeEach
  void setUp() {
    Map<String, ContractInvoker> invokers = new HashMap<>();
    putBinding(invokers, EchoContract.class, (EchoContract) (id, name) -> "echo:" + id + ":" + name);
    putBinding(invokers, RichContract.class, (RichContract) (amount, time, status, tags, extra) ->
        "rich:" + amount + "|" + time + "|" + status + "|" + tags + "|" + extra.name());
    putBinding(invokers, NoParamContract.class, (NoParamContract) () -> "pong");
    putBinding(invokers, BoomContract.class, (BoomContract) seed -> {
      throw new IllegalStateException("boom-" + seed);
    });
    putBinding(invokers, UserContract.class, new UserContractImpl());
    registry = ContractInvokerRegistry.of(invokers);
    handler = new ContractRpcHandler(registry, metas, objectMapper, 1024 * 1024);
  }

  private void putBinding(Map<String, ContractInvoker> invokers, Class<?> iface, Object impl) {
    for (ContractBinding binding : ContractScanner.scanInterface(
        iface, impl, ScanOptions.of("user-provider"), warnings::add)) {
      invokers.put(binding.meta().contractName(),
          ContractInvokerFactory.create(binding, InvokerMode.BYTECODE));
      metas.put(binding.meta().contractName(), binding.meta());
    }
  }

  @Test
  void shouldDispatchByContractNameAndConvertBasicTypes() {
    RpcResponse response = handler.handle("echo.call", "[42, \"jiz\"]");
    assertEquals(RpcResponse.CODE_OK, response.code(), "正常调用返回 OK");
    assertEquals("echo:42:jiz", response.data(), "字符串数字应转换为 Long，基础类型按契约转换");
    assertNotNull(response.traceId(), "响应应携带 traceId");
  }

  @Test
  void shouldReturnContractNotFoundWithoutFallback() {
    RpcResponse response = handler.handle("not.exists.contract", "[]");
    assertEquals("CONTRACT_NOT_FOUND", response.code(), "未导出/未注册契约返回 CONTRACT_NOT_FOUND");
    assertNotNull(response.message(), "错误消息可对外");
    assertNotNull(response.traceId(), "错误响应携带 traceId 便于关联本地日志");
  }

  @Test
  void shouldRejectMissingContractName() {
    RpcResponse response = handler.handle(null, "[]");
    assertEquals("CONTRACT_ARGUMENT_INVALID", response.code(), "缺失契约标识属于参数非法");
    assertTrue(response.message().contains("mt"), "消息应指明缺失原因: " + response.message());
  }

  @Test
  void shouldRejectArityMismatch() {
    RpcResponse response = handler.handle("echo.call", "[42]");
    assertEquals("CONTRACT_ARGUMENT_INVALID", response.code(), "参数个数不符直接拒绝，不发起目标方法调用");
    assertTrue(response.message().contains("个数") || response.message().contains("参数"),
        "消息应指明原因: " + response.message());
  }

  @Test
  void shouldConvertRichTypesWithUnifiedRules() {
    RpcResponse response = handler.handle("rich.call",
        "[\"12.34\", \"2026-11-14T10:15:30\", \"PAID\", [\"a\",\"b\"], {\"name\":\"u1\"}]");
    assertEquals(RpcResponse.CODE_OK, response.code(),
        "金额(字符串->BigDecimal)、日期(ISO->LocalDateTime)、枚举(名称)、集合、DTO(Map) 全部按统一规则转换: "
            + response.message());
    assertEquals("rich:12.34|2026-11-14T10:15:30|PAID|[a, b]|u1", response.data());
  }

  @Test
  void shouldConvertNumericJsonToBigDecimalAndDateObject() {
    RpcResponse response = handler.handle("rich.call",
        "[99, \"2026-11-14T10:15:30\", \"DONE\", [], {\"name\":\"u2\"}]");
    assertEquals(RpcResponse.CODE_OK, response.code(), "数字金额 -> BigDecimal 转换: " + response.message());
  }

  @Test
  void shouldKeepNullArgumentAsNull() {
    RpcResponse response = handler.handle("echo.call", "[null, \"jiz\"]");
    assertEquals("echo:null:jiz", response.data(), "空参数值保持 null，不做默认值填充");
  }

  @Test
  void shouldIgnoreBodyForNoParamContract() {
    RpcResponse response = handler.handle("noparam.ping",
        "任何请求体内容都应被忽略，甚至不是 JSON 数组也没关系");
    assertEquals(RpcResponse.CODE_OK, response.code(), "无参契约忽略请求体");
    assertEquals("pong", response.data());
  }

  @Test
  void shouldReturnInvokeErrorForBusinessExceptionWithoutStackTrace() throws Exception {
    RpcResponse response = handler.handle("boom.explode", "[\"x\"]");
    assertEquals("CONTRACT_INVOKE_ERROR", response.code(), "业务异常返回 CONTRACT_INVOKE_ERROR");
    assertEquals("boom-x", response.message(), "仅返回可对外消息");
    String json = new ObjectMapper().writeValueAsString(response);
    assertFalse(json.toLowerCase().contains("stacktrace"),
        "错误响应不含内部堆栈: " + json);
    assertFalse(json.contains("IllegalStateException"), "错误响应不含异常类名: " + json);
  }

  @Test
  void shouldReturnArgumentInvalidForTypeConversionFailure() {
    RpcResponse response = handler.handle("echo.call", "[\"not-a-number\", \"jiz\"]");
    assertEquals("CONTRACT_ARGUMENT_INVALID", response.code(),
        "类型转换失败返回参数错误，指明原因");
    assertTrue(response.message().contains("id") || response.message().contains("参数"),
        "消息应指明参数位置/原因: " + response.message());
  }

  @Test
  void shouldReturnInternalErrorForMalformedJsonBody() {
    RpcResponse response = handler.handle("echo.call", "not-a-json");
    assertEquals("CONTRACT_INTERNAL_ERROR", response.code(),
        "框架级序列化失败返回 CONTRACT_INTERNAL_ERROR，与业务异常可区分");
    assertFalse(String.valueOf(response.message()).contains("JsonParseException"),
        "内部异常细节不外泄: " + response.message());
  }

  @Test
  void shouldRejectOversizedBody() {
    ContractRpcHandler smallLimitHandler =
        new ContractRpcHandler(registry, metas, objectMapper, 16);
    RpcResponse response = smallLimitHandler.handle("echo.call", "[42, \"超长请求体应被拒绝\"]");
    assertEquals("CONTRACT_ARGUMENT_INVALID", response.code(), "请求体超限返回参数错误");
    assertTrue(response.message().contains("大小") || response.message().contains("上限"),
        "消息应说明请求体上限: " + response.message());
  }

  @Test
  void shouldConvertMapToDtoArgument() {
    RpcResponse response = handler.handle("apollo.getuser", "[7]");
    assertEquals(RpcResponse.CODE_OK, response.code());
    assertEquals(new UserDTO("user-7").toString(), String.valueOf(response.data()),
        "DTO 返回值按结构化数据返回");
  }
}
