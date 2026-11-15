package com.apicontract.gateway.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 任务13（红-绿）测试：实例订阅（design：推送刷新 + 10s 定时兜底校准；
 * 刷新失败沿用最近快照、不清空；实例变更只更新地址列表，不触发契约解析）。
 */
class InstanceAddressRegistryTest {

  private final AtomicInteger fetchCount = new AtomicInteger();
  private final List<String> warnings = new ArrayList<>();

  private InstanceAddressRegistry registryFetching(List<String> result) {
    return new InstanceAddressRegistry(
        service -> {
          fetchCount.incrementAndGet();
          return result;
        }, warnings::add);
  }

  @Test
  void shouldUpdateSnapshotOnPush() {
    InstanceAddressRegistry registry = registryFetching(List.of());

    registry.onPush("user-provider", List.of("a:8080", "b:8080"));

    assertEquals(List.of("a:8080", "b:8080"), registry.resolve("user-provider"),
        "实例推送：只更新地址列表（原子替换）");
  }

  @Test
  void shouldTreatEmptyPushAsAllInstancesDown() {
    InstanceAddressRegistry registry = registryFetching(List.of());
    registry.onPush("user-provider", List.of("a:8080"));

    registry.onPush("user-provider", List.of());

    assertTrue(registry.resolve("user-provider").isEmpty(),
        "空推送 = 全部实例下线（区别于刷新失败）");
  }

  @Test
  void shouldKeepLastSnapshotWhenRefreshFails() {
    InstanceAddressRegistry registry = new InstanceAddressRegistry(
        service -> {
          throw new IllegalStateException("discovery down");
        }, warnings::add);
    registry.onPush("user-provider", List.of("a:8080"));

    registry.refresh("user-provider");

    assertEquals(List.of("a:8080"), registry.resolve("user-provider"),
        "Discovery 异常：沿用最近快照，绝不清空");
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("user-provider"), "告警指明服务");
  }

  @Test
  void shouldReplaceSnapshotWhenRefreshSucceeds() {
    InstanceAddressRegistry registry = registryFetching(List.of("c:8080"));
    registry.onPush("user-provider", List.of("a:8080"));

    registry.refresh("user-provider");

    assertEquals(List.of("c:8080"), registry.resolve("user-provider"),
        "刷新成功：快照校准为最新列表");
  }

  @Test
  void shouldCalibratePeriodicallyViaScheduler() throws Exception {
    InstanceAddressRegistry registry = registryFetching(List.of("a:8080"));
    registry.onPush("user-provider", List.of("a:8080"));
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      registry.scheduleFallback(scheduler, 20, TimeUnit.MILLISECONDS);
      Thread.sleep(150);
    } finally {
      scheduler.shutdownNow();
    }

    assertTrue(fetchCount.get() >= 3,
        "定时兜底按周期校准全部已知服务（实测 " + fetchCount.get() + " 次）");
  }

  @Test
  void shouldNotTouchOtherServicesOnSingleServicePush() {
    InstanceAddressRegistry registry = registryFetching(List.of());
    registry.onPush("user-provider", List.of("a:8080"));
    registry.onPush("order-provider", List.of("b:8080"));

    assertEquals(List.of("a:8080"), registry.resolve("user-provider"));
    assertEquals(List.of("b:8080"), registry.resolve("order-provider"));
  }
}
