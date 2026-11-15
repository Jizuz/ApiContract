package com.apicontract.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.GatewayResponse;
import com.apicontract.gateway.index.VersionRouter;
import com.apicontract.it.itapi.ItEcho;
import com.apicontract.provider.publish.ContractMetaBundle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 任务25 多版本共存 + 单机调试模式集成测试：
 * 多版本 = v1/v2 两份 dataId 推送 → 默认版本取最新稳定（v2）、
 * 显式版本精确寻址 v1、旧版本条目保留可回滚；
 * 单机调试 = 进程内直连（不走注册中心，实例地址手工推送）调用成功。
 */
class MultiVersionStandaloneIT {

  private static final String SERVICE = "it-multi-version-service";

  private static ProviderAppFixture provider;
  private static GatewayFixture gateway;
  private static String echoContract;

  @BeforeAll
  static void setUpOnce() throws Exception {
    provider = new ProviderAppFixture(SERVICE, "2.0.0")
        .contract(ItEcho.class, (ItEcho) (id, name) -> "v2:" + id + ":" + name)
        .start();
    NacosBridge bridge = new NacosBridge(NacosServerContainer.serverAddr());
    // 清理跨次运行残留：同版本不同内容会被防覆盖守卫拒绝，先删再发保证幂等
    bridge.removeContractConfig(SERVICE, "1.0.0");
    bridge.removeContractConfig(SERVICE, "2.0.0");
    provider.publish(bridge);
    gateway = new GatewayFixture(bridge);
    // 先订阅旧版本 v1，再发布/订阅 v2：模拟灰度期间两版本共存推送
    publishVersion(bridge, "1.0.0");
    publishVersion(bridge, "2.0.0");
    gateway.subscribeService(SERVICE, "1.0.0");
    gateway.subscribeService(SERVICE, "2.0.0");
    echoContract = provider.bindings.get(0).meta().contractName();
  }

  /**
   * 直接向配置中心发布一份指定版本的契约元数据（复用发布器，dataId 带版本后缀；
   * metas 按目标版本重新扫描——契约条目的 version 字段随版本 dataId 变化）。
   */
  private static void publishVersion(NacosBridge bridge, String version) {
    Map<String, ContractMeta> versionMetas = new LinkedHashMap<>();
    for (com.apicontract.provider.scan.ContractBinding binding :
        com.apicontract.provider.scan.ContractScanner.scanInterface(
            ItEcho.class, (ItEcho) (id, name) -> version + ":" + id + ":" + name,
            new com.apicontract.provider.scan.ScanOptions(
                SERVICE, version, Map.of()),
            line -> { })) {
      versionMetas.put(binding.meta().contractName(), binding.meta());
    }
    ContractMetaBundle bundle = new ContractMetaBundle(SERVICE, version,
        List.copyOf(versionMetas.values()),
        provider.publishedBundle() == null ? Map.of()
            : provider.publishedBundle().aggregateIndex());
    new com.apicontract.provider.publish.NacosConfigMetaPublisher(
        bridge.configCarrierWriter(), new com.fasterxml.jackson.databind.ObjectMapper(),
        true).publish(bundle);
  }

  @AfterAll
  static void tearDownOnce() {
    if (provider != null) {
      provider.stop();
    }
  }

  @Test
  void shouldRouteToLatestStableVersionByDefault() {
    ContractMeta meta = VersionRouter.route(gateway.indexes, echoContract, null);
    assertNotNull(meta, "默认版本应命中");
    assertEquals("2.0.0", meta.version(), "默认版本 = 最新稳定 v2");
  }

  @Test
  void shouldRouteToExplicitPinnedVersion() {
    ContractMeta v1 = VersionRouter.route(gateway.indexes, echoContract, "1.0.0");
    assertNotNull(v1, "显式 v1 应精确命中");
    assertEquals("1.0.0", v1.version(), "灰度回滚场景：显式指定旧版本");
    ContractMeta unknown =
        VersionRouter.route(gateway.indexes, echoContract, "9.9.9");
    assertNull(unknown, "不存在的版本未命中（CONTRACT_NOT_FOUND 语义）");
  }

  @Test
  void shouldServeThroughStandaloneDirectConnectionWithoutRegistry() {
    // 单机调试模式：实例地址手工推送（进程内直连，不依赖注册中心）
    gateway.instances.onPush(SERVICE, List.of(provider.baseUrl()));
    ContractGatewayHandler handler = gateway.handler();
    GatewayResponse response = handler.handle(
        echoContract, "POST", Map.of(), "{\"id\":7,\"name\":\"solo\"}");
    assertEquals("OK", response.code(), "进程内直连调用应成功: " + response);
    assertEquals("v2:7:solo", response.data().asText(), "应命中 v2 默认版本实现");
  }
}
