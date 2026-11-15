package com.apicontract.gateway.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务19（红-绿）测试：远程调用器工厂——设计默认参数（池/超时）、
 * 未引入异步依赖降级同步并保持功能不缺失（spec 边界用例）。
 */
class RemoteCallersTest {

  @Test
  void shouldExposeDesignDefaultsOnPoolAndTimeouts() {
    ConnectionPoolSettings pool = ConnectionPoolSettings.DEFAULT;
    assertEquals(500, pool.maxConnections(), "max-connections=500");
    assertEquals(100, pool.maxConnectionsPerHost(), "每主机连接上限默认 100");
    assertEquals(30_000, pool.maxIdleMillis(), "max-idle=30s");
    assertEquals(1_000, pool.acquireTimeoutMillis(), "acquire-timeout=1000ms");
    assertTrue(pool.pendingAcquireMaxCount() > 0, "等待队列必须有界（>0）");

    TimeoutSettings timeouts = TimeoutSettings.DEFAULT;
    assertEquals(3_000, timeouts.overallMillis(), "整体调用超时默认 3000ms");
    assertTrue(timeouts.connectMillis() > 0, "连接超时有界");
    assertTrue(timeouts.readMillis() > 0, "读超时有界");
  }

  @Test
  void shouldLetContractTimeoutOverrideGlobalDefault() {
    TimeoutSettings timeouts = TimeoutSettings.DEFAULT;

    assertEquals(3_000,
        timeouts.overallFor(metaWithTimeout(null)),
        "契约未声明沿用全局默认");
    assertEquals(500L, timeouts.overallFor(metaWithTimeout(500L)),
        "契约级 timeout 覆盖全局默认");
  }

  @Test
  void shouldDegradeToSyncAndStayFunctionalWhenAsyncAbsent() throws Exception {
    try (LocalHttpServer provider = LocalHttpServer.start(0)) {
      RemoteCallers.Selection selection = RemoteCallers.select(
          ConnectionPoolSettings.DEFAULT, TimeoutSettings.DEFAULT,
          className -> false);

      assertTrue(selection.degraded(), "未引入异步依赖 → 降级同步并告警");
      assertInstanceOf(SyncHttpRemoteCaller.class, selection.caller());

      String response = selection.caller()
          .call(provider.baseUrl, "echo.call", "[1,\"jiz\"]", 3_000);
      assertTrue(response.contains("\"OK\""), "降级同步功能不缺失: " + response);
    }
  }

  @Test
  void shouldCreateAsyncCallerWhenDependencyPresent() {
    RemoteCallers.Selection selection = RemoteCallers.select(
        ConnectionPoolSettings.DEFAULT, TimeoutSettings.DEFAULT);

    assertFalse(selection.degraded(), "类路径存在 Reactor Netty → 异步实现");
    assertInstanceOf(ReactorNettyRemoteCaller.class, selection.caller());
  }

  private static ContractMeta metaWithTimeout(Long timeout) {
    return new ContractMeta("echo.call", "com.example.EchoContract", "call",
        List.of(), List.of(), "java.lang.String", "user-provider", "1.0.0",
        List.of(), ContractMeta.SERIALIZER_JSON, null, timeout);
  }
}
