package com.apicontract.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.GatewayErrorCodes;
import com.apicontract.gateway.GatewayResponse;
import com.apicontract.it.itapi.ItBoom;
import com.apicontract.it.itapi.ItEcho;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * 任务25 主链路集成测试：Testcontainers Nacos 全链路——
 * Provider 发布（元数据→配置中心 + 实例→注册中心）→
 * 网关订阅（索引原子替换 + 实例快照拉取）→ 泛化调用 HTTP 往返，
 * 及代表性错误码端到端（MT_MISSING / CONTRACT_NOT_FOUND /
 * ARGUMENT_INVALID / NO_INSTANCE / INVOKE_ERROR）与 traceId 透传。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndContractIT {

  private static final String SERVICE = "it-demo-service";

  private static ProviderAppFixture provider;
  private static GatewayFixture gateway;
  private static String echoContract;
  private static String boomContract;
  private static ContractGatewayHandler handler;

  @BeforeAll
  static void setUpOnce() throws Exception {
    provider = new ProviderAppFixture(SERVICE, "1.0.0")
        .contract(ItEcho.class, (ItEcho) (id, name) -> "echo:" + id + ":" + name)
        .contract(ItBoom.class, (ItBoom) seed -> {
          throw new IllegalStateException("boom-" + seed);
        })
        .start();
    NacosBridge bridge = new NacosBridge(NacosServerContainer.serverAddr());
    provider.publish(bridge);
    gateway = new GatewayFixture(bridge);
    gateway.subscribeService(SERVICE);
    gateway.refreshInstances(SERVICE);
    echoContract = firstContractOf(ItEcho.class);
    boomContract = firstContractOf(ItBoom.class);
    handler = gateway.handler();
    awaitInstanceDiscovered();
  }

  private static String firstContractOf(Class<?> iface) {
    return provider.bindings.stream()
        .filter(b -> iface.equals(b.method().getDeclaringClass()))
        .map(b -> b.meta().contractName())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "未找到接口契约绑定: " + iface.getName()));
  }

  /** Nacos 客户端侧实例列表为最终一致：轮询至网关拉到实例。 */
  private static void awaitInstanceDiscovered() {
    long deadline = System.currentTimeMillis() + 15_000;
    while (gateway.instances.resolve(SERVICE).isEmpty()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("15s 内未从 Nacos 发现 provider 实例");
      }
      gateway.refreshInstances(SERVICE);
      try {
        Thread.sleep(500);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(failure);
      }
    }
  }

  @AfterAll
  static void tearDownOnce() {
    if (provider != null) {
      provider.stop();
    }
  }

  @Test
  @Order(1)
  void shouldRoundTripThroughRealNacosDiscoveryAndHttp() {
    GatewayResponse response = handler.handle(
        echoContract, "POST", Map.of(), "{\"id\":1,\"name\":\"jiz\"}");
    assertEquals("OK", response.code(), "全链路调用应成功: " + response);
    assertNotNull(response.data(), "响应应携带 data");
    assertEquals("echo:1:jiz", response.data().asText(), "应原样往返业务结果");
    assertNotNull(response.traceId(), "网关应生成并回传 traceId");
  }

  @Test
  @Order(2)
  void shouldPropagateBusinessExceptionAsInvokeError() {
    GatewayResponse response = handler.handle(
        boomContract, "POST", Map.of(), "{\"seed\":\"bad\"}");
    assertEquals(GatewayErrorCodes.INVOKE_ERROR, response.code(),
        "业务异常应端到端收敛 INVOKE_ERROR: " + response);
  }

  @Test
  @Order(3)
  void shouldReturnMtMissingWhenContractNameAbsent() {
    GatewayResponse response = handler.handle(null, "GET", Map.of(), null);
    assertEquals(GatewayErrorCodes.MT_MISSING, response.code());
  }

  @Test
  @Order(4)
  void shouldReturnContractNotFoundForUnknownContract() {
    GatewayResponse response = handler.handle(
        "it-unknown.contract", "POST", Map.of(), "{}");
    assertEquals(GatewayErrorCodes.CONTRACT_NOT_FOUND, response.code());
  }

  @Test
  @Order(5)
  void shouldReturnArgumentInvalidWithoutRemoteCallWhenArityMismatch() {
    // 数组形态长度 != 契约参数个数 → 网关侧拦截（对象形态缺键补 null 为规格语义）
    GatewayResponse response = handler.handle(
        echoContract, "POST", Map.of(), "[1]");
    assertEquals(GatewayErrorCodes.ARGUMENT_INVALID, response.code(),
        "参数个数不符应在网关侧拦截，不发起远程调用: " + response);
  }

  @Test
  @Order(6)
  void shouldReturnNoInstanceAfterDeregister() throws Exception {
    NacosBridge bridge = new NacosBridge(NacosServerContainer.serverAddr());
    provider.deregister(bridge);
    awaitInstanceEmptied(bridge);
    GatewayResponse response = handler.handle(
        echoContract, "POST", Map.of(), "{\"id\":1,\"name\":\"jiz\"}");
    assertEquals(GatewayErrorCodes.NO_INSTANCE, response.code(),
        "实例全部下线后应 NO_INSTANCE: " + response);
    assertTrue(gateway.instances.resolve(SERVICE).isEmpty(),
        "网关实例快照应已被校准为空");
  }

  private static void awaitInstanceEmptied(NacosBridge bridge) throws Exception {
    long deadline = System.currentTimeMillis() + 15_000;
    while (!bridge.discoveryFetcher().fetch(SERVICE).isEmpty()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("15s 内实例未从 Nacos 摘除");
      }
      Thread.sleep(500);
    }
    long refreshDeadline = System.currentTimeMillis() + 5_000;
    while (System.currentTimeMillis() < refreshDeadline) {
      gateway.refreshInstances(SERVICE);
      if (gateway.instances.resolve(SERVICE).isEmpty()) {
        return;
      }
      Thread.sleep(300);
    }
  }
}
