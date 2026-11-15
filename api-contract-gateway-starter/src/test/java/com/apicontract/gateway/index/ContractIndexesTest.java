package com.apicontract.gateway.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 任务12（红-绿）测试：网关三级本地索引（design 三级索引结构 + 原子替换）：
 * 配置推送解析后按服务维度原子替换（清理已删契约），刷新期间无请求错误尖刺；
 * 契约索引与实例地址列表分离；条目设上限监控。
 */
class ContractIndexesTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static String payload(String service, String... mts) {
    StringBuilder contracts = new StringBuilder();
    for (int i = 0; i < mts.length; i++) {
      if (i > 0) {
        contracts.append(',');
      }
      contracts.append("{").append("\"contractName\": \"").append(mts[i]).append("\",")
          .append("\"interfaceName\": \"i.F\",").append("\"methodName\": \"m\",")
          .append("\"paramNames\": [],").append("\"paramTypes\": [],")
          .append("\"returnType\": \"java.lang.String\",")
          .append("\"serviceName\": \"").append(service).append("\",")
          .append("\"version\": \"1.0.0\",").append("\"tags\": [],")
          .append("\"serializer\": \"json\",")
          .append("\"schemaRef\": null,").append("\"timeout\": null}");
    }
    return "{\"serviceName\": \"" + service + "\",\"version\": \"1.0.0\","
        + "\"contracts\": [" + contracts + "],"
        + "\"aggregateIndex\": {}}";
  }

  @Test
  void shouldBuildThreeIndexesFromPayload() {
    ContractIndexes indexes = new ContractIndexes(1000);

    indexes.applyPayload(payload("user-provider", "echo.call", "echo.ping"), mapper);

    assertNotNull(indexes.find("echo.call"), "契约主索引（mt→契约条目）命中");
    assertNotNull(indexes.findVersioned("echo.ping", "1.0.0"),
        "版本索引（mt+version→契约条目）命中");
    assertEquals(List.of("echo.call", "echo.ping"),
        indexes.contractsOfService("user-provider"),
        "服务索引（服务→契约标识集合）命中");
  }

  @Test
  void shouldAtomicallyReplaceServiceAndEvictDeletedContracts() {
    ContractIndexes indexes = new ContractIndexes(1000);
    indexes.applyPayload(payload("user-provider", "echo.call", "echo.old"), mapper);

    indexes.applyPayload(payload("user-provider", "echo.call", "echo.new"), mapper);

    assertNotNull(indexes.find("echo.new"), "新增契约立即可达");
    assertNull(indexes.find("echo.old"),
        "契约删除以配置为准：被删契约立即不可达");
    assertFalse(indexes.contractsOfService("user-provider").contains("echo.old"),
        "服务索引同步清理已删契约");
  }

  @Test
  void shouldNotAffectOtherServicesWhenOneServiceRefreshes() {
    ContractIndexes indexes = new ContractIndexes(1000);
    indexes.applyPayload(payload("user-provider", "echo.call"), mapper);

    indexes.applyPayload(payload("order-provider", "order.create"), mapper);

    assertNotNull(indexes.find("echo.call"), "服务 A 条目不受服务 B 推送影响");
    assertNotNull(indexes.find("order.create"));
    assertEquals(List.of("echo.call"), indexes.contractsOfService("user-provider"));
    assertEquals(List.of("order.create"), indexes.contractsOfService("order-provider"));
  }

  @Test
  void shouldStayConsistentUnderConcurrentRefreshAndLookup() throws Exception {
    ContractIndexes indexes = new ContractIndexes(10_000);
    indexes.applyPayload(payload("user-provider", "echo.call", "echo.ping"), mapper);
    indexes.applyPayload(payload("order-provider", "order.create"), mapper);

    int threads = 8;
    AtomicInteger errors = new AtomicInteger();
    CountDownLatch done = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<String> variants = List.of(
        payload("user-provider", "echo.call"),
        payload("user-provider", "echo.call", "echo.ping", "echo.extra"),
        payload("order-provider", "order.create", "order.cancel"));
    for (int t = 0; t < threads; t++) {
      final int threadId = t;
      pool.submit(() -> {
        try {
          for (int i = 0; i < 200; i++) {
            indexes.applyPayload(variants.get((threadId + i) % variants.size()), mapper);
            if (indexes.find("echo.call") == null) {
              errors.incrementAndGet();
            }
          }
        } catch (Exception refreshFailure) {
          errors.incrementAndGet();
        } finally {
          done.countDown();
        }
      });
    }
    assertTrue(done.await(15, TimeUnit.SECONDS));
    pool.shutdownNow();

    assertEquals(0, errors.get(), "并发刷新与查找期间：无异常、基线契约始终可达（无错误尖刺）");
    assertNotNull(indexes.find("echo.call"), "刷新收敛后状态一致");
  }

  @Test
  void shouldRejectOversizedServiceWithWarningAndKeepSnapshot() {
    List<String> warnings = new ArrayList<>();
    ContractIndexes indexes = new ContractIndexes(3, warnings::add);
    indexes.applyPayload(payload("user-provider", "a", "b", "c"), mapper);

    indexes.applyPayload(payload("order-provider", "d", "e"), mapper);

    assertNull(indexes.find("d"), "超上限推送被拒绝：不纳入索引");
    assertEquals(1, warnings.size(), "超限产生监控告警");
    assertTrue(warnings.get(0).contains("上限"));
    assertNotNull(indexes.find("a"), "原快照条目不受影响");
  }

  @Test
  void shouldIsolateContractIndexFromInstanceAddresses() {
    ContractIndexes indexes = new ContractIndexes(1000);
    indexes.applyPayload(payload("user-provider", "echo.call"), mapper);

    Map<String, List<String>> addresses = indexes.instanceAddresses();
    assertTrue(addresses.isEmpty(),
        "契约索引与实例地址列表分离：契约变更不触碰地址列表（实例订阅任务独立维护）");
  }
}
