package com.apicontract.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.GatewayResponse;
import com.apicontract.gateway.index.DiscoverySnapshotFetcher;
import com.apicontract.it.itapi.ItEcho;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * 任务25 降级矩阵集成测试（spec contract-gateway-degrade）：
 * 注册中心不可用时网关<b>沿用最近实例快照不清空</b>，调用继续成功；
 * 恢复后自动校准回正常。
 *
 * <p>宕机模拟采用开关式 fetcher（抛异常 = 注册中心不可达），
 * 不暂停共享服务器，保证与其他 IT 隔离。
 */
class DegradationMatrixIT {

  private static final String SERVICE = "it-degrade-service";

  private static ProviderAppFixture provider;
  private static GatewayFixture gateway;

  @AfterAll
  static void tearDownOnce() {
    if (provider != null) {
      provider.stop();
    }
  }

  @Test
  void shouldKeepLastSnapshotAndServeWhenRegistryDown() throws Exception {
    provider = new ProviderAppFixture(SERVICE, "1.0.0")
        .contract(ItEcho.class, (ItEcho) (id, name) -> "echo:" + id + ":" + name)
        .start();
    NacosBridge bridge = new NacosBridge(NacosServerContainer.serverAddr());
    provider.publish(bridge);
    AtomicBoolean registryDown = new AtomicBoolean(false);
    DiscoverySnapshotFetcher toggling = serviceName -> {
      if (registryDown.get()) {
        throw new IllegalStateException("registry unreachable (simulated)");
      }
      return bridge.discoveryFetcher().fetch(serviceName);
    };
    gateway = new GatewayFixture(bridge, toggling);
    gateway.subscribeService(SERVICE);
    awaitInstanceDiscovered();

    ContractGatewayHandler handler = gateway.handler();
    String echoContract = provider.bindings.get(0).meta().contractName();

    // 基线：注册中心在线时调用成功
    GatewayResponse baseline = handler.handle(
        echoContract, "POST", Map.of(), "{\"id\":1,\"name\":\"a\"}");
    assertEquals("OK", baseline.code(), "基线调用应成功: " + baseline);

    // 降级：注册中心不可达 → 沿用最近快照（不清空），调用继续成功
    registryDown.set(true);
    int degradedBefore = gateway.degradedRefreshCount();
    gateway.refreshInstances(SERVICE);
    assertEquals(degradedBefore + 1, gateway.degradedRefreshCount(),
        "刷新失败应产生一次降级告警");
    assertEquals(1, gateway.instances.resolve(SERVICE).size(),
        "实例快照必须沿用不清空");
    GatewayResponse degraded = handler.handle(
        echoContract, "POST", Map.of(), "{\"id\":2,\"name\":\"b\"}");
    assertEquals("OK", degraded.code(), "注册中心宕机期间调用应继续成功: "
        + degraded);

    // 恢复：注册中心回归后校准正常
    registryDown.set(false);
    gateway.refreshInstances(SERVICE);
    assertEquals(1, gateway.instances.resolve(SERVICE).size(),
        "恢复后快照应正常校准");
    GatewayResponse recovered = handler.handle(
        echoContract, "POST", Map.of(), "{\"id\":3,\"name\":\"c\"}");
    assertEquals("OK", recovered.code(), "恢复后调用应成功: " + recovered);
  }

  private void awaitInstanceDiscovered() {
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
}
