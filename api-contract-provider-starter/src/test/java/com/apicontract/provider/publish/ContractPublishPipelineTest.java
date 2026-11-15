package com.apicontract.provider.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务8（红-绿）测试：发布顺序不变量（CR 阻断项）。
 *
 * <p>规格（contract-metadata 数据约束）：先发布契约元数据、后注册实例，
 * 任一步失败中断启动，禁止出现"实例已上线但契约缺失"的中间态。
 * 实例标记仅保留 contract-version、contract-count 极简键值，不携带契约明细。
 */
class ContractPublishPipelineTest {

  private static ContractMeta meta(String mt) {
    return new ContractMeta(mt, "com.apicontract.testapi.EchoContract", "call",
        List.of("id", "name"), List.of("long", "java.lang.String"), "java.lang.String",
        "user-provider", "1.0.0", List.of(), ContractMeta.SERIALIZER_JSON, null, null);
  }

  private static ContractMetaBundle bundle() {
    return new ContractMetaBundle("user-provider", "1.0.0",
        List.of(meta("echo.call"), meta("echo.ping")),
        Map.of("echo.call", "user-provider", "echo.ping", "user-provider"));
  }

  @Test
  void shouldRegisterInstanceOnlyAfterPublishSucceeds() {
    List<String> events = new ArrayList<>();
    List<Map<String, String>> registered = new ArrayList<>();
    ContractPublishPipeline pipeline = new ContractPublishPipeline(
        bundle(), bundle -> events.add("publish"),
        mark -> {
          events.add("register");
          registered.add(mark);
        });

    pipeline.run();

    assertEquals(List.of("publish", "register"), events,
        "顺序不变量：必须先发布契约元数据、后注册实例");
  }

  @Test
  void shouldNotRegisterInstanceWhenPublishFails() {
    List<String> events = new ArrayList<>();
    ContractPublishPipeline pipeline = new ContractPublishPipeline(
        bundle(),
        bundle -> {
          throw new MetaPublishException("配置中心不可用", new RuntimeException("down"));
        },
        mark -> events.add("register"));

    assertThrows(MetaPublishException.class, pipeline::run,
        "发布失败必须中断启动（异常向上传播）");
    assertTrue(events.isEmpty(),
        "发布失败时禁止注册实例，杜绝\"实例已上线但契约缺失\"中间态");
  }

  @Test
  void shouldInterruptStartupWhenRegisterFails() {
    ContractPublishPipeline pipeline = new ContractPublishPipeline(
        bundle(), bundle -> { },
        mark -> {
          throw new IllegalStateException("registry down");
        });

    assertThrows(IllegalStateException.class, pipeline::run,
        "任一步失败均中断启动：注册实例失败同样向上传播");
  }

  @Test
  void shouldWriteMinimalInstanceMarkWithoutContractDetails() {
    List<Map<String, String>> registered = new ArrayList<>();
    ContractPublishPipeline pipeline = new ContractPublishPipeline(
        bundle(), bundle -> { }, registered::add);

    pipeline.run();

    assertEquals(1, registered.size());
    Map<String, String> mark = registered.get(0);
    assertEquals("1.0.0", mark.get("contract-version"));
    assertEquals("2", mark.get("contract-count"));
    assertFalse(mark.containsKey("contracts"),
        "实例标记不携带契约明细（契约明细仅在配置中心条目中）");
  }

  @Test
  void shouldRejectBundleWithoutContracts() {
    assertThrows(IllegalArgumentException.class,
        () -> new ContractMetaBundle("user-provider", "1.0.0", List.of(), Map.of()),
        "空契约集合在构造发布包时即被拒绝（空清单不发布不注册入口）");
  }
}
