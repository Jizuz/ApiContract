package com.apicontract.provider.publish;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务9（红-绿）测试：聚合索引跨服务判重（规格 contract-metadata：
 * 契约标识冲突 CONTRACT_CONFLICT，发布期聚合索引校验，冲突中断启动）。
 */
class AggregateIndexValidatorTest {

  private ContractMetaBundle bundleOf(String service, String mt) {
    ContractMeta meta = new ContractMeta(mt, "com.apicontract.testapi.EchoContract", "call",
        List.of("id"), List.of("long"), "java.lang.String",
        service, "1.0.0", List.of(), ContractMeta.SERIALIZER_JSON, null, null);
    return new ContractMetaBundle(service, "1.0.0", List.of(meta), Map.of(mt, service));
  }

  @Test
  void shouldInterruptStartupWhenContractIdOwnedByOtherService() {
    ContractMetaBundle bundle = bundleOf("user-provider", "echo.call");
    Map<String, String> existing = Map.of("echo.call", "order-service");

    ContractConflictException conflict = assertThrows(ContractConflictException.class,
        () -> AggregateIndexValidator.validate(bundle, existing),
        "契约标识已被其他服务占用：发布期判重，冲突中断启动");
    assertTrue(conflict.getMessage().contains("echo.call"));
    assertTrue(conflict.getMessage().contains("order-service"),
        "错误信息指明冲突标识与占用方，便于跨团队定位");
    assertTrue(conflict.getMessage().contains("user-provider"));
  }

  @Test
  void shouldAllowRepublishBySameService() {
    ContractMetaBundle bundle = bundleOf("user-provider", "echo.call");
    Map<String, String> existing = Map.of("echo.call", "user-provider");

    assertDoesNotThrow(() -> AggregateIndexValidator.validate(bundle, existing),
        "本服务重发布（重启/升级）不视为冲突");
  }

  @Test
  void shouldAllowWhenNoOverlap() {
    ContractMetaBundle bundle = bundleOf("user-provider", "echo.call");
    Map<String, String> existing = Map.of("order.create", "order-service");

    assertDoesNotThrow(() -> AggregateIndexValidator.validate(bundle, existing));
  }

  @Test
  void shouldReportAllConflictsAtOnce() {
    ContractMeta metaA = new ContractMeta("echo.call", "i.A", "m",
        List.of(), List.of(), "void", "user-provider", "1.0.0", List.of(),
        ContractMeta.SERIALIZER_JSON, null, null);
    ContractMeta metaB = new ContractMeta("order.create", "i.B", "m",
        List.of(), List.of(), "void", "user-provider", "1.0.0", List.of(),
        ContractMeta.SERIALIZER_JSON, null, null);
    ContractMetaBundle bundle = new ContractMetaBundle("user-provider", "1.0.0",
        List.of(metaA, metaB),
        Map.of("echo.call", "user-provider", "order.create", "user-provider"));
    Map<String, String> existing = Map.of(
        "echo.call", "order-service",
        "order.create", "pay-service");

    ContractConflictException conflict = assertThrows(ContractConflictException.class,
        () -> AggregateIndexValidator.validate(bundle, existing));
    assertTrue(conflict.getMessage().contains("echo.call"));
    assertTrue(conflict.getMessage().contains("order.create"),
        "一次报出全部冲突，避免多轮重启逐个发现");
  }
}
