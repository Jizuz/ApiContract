package com.apicontract.gateway.index;

import com.apicontract.gateway.InstanceResolver;
import com.apicontract.gateway.metrics.ContractMetrics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 实例地址注册表（design：推送刷新 + 10s 定时兜底校准；刷新失败沿用最近
 * 快照、不清空；实例变更只更新地址列表，不触发契约解析——与契约索引分离）。
 *
 * <p>空推送 = 全部实例下线（合法状态）；拉取异常 = 沿用快照并告警。
 */
public final class InstanceAddressRegistry implements InstanceResolver {

  private final DiscoverySnapshotFetcher fetcher;
  private final Consumer<String> warnLine;
  private final Map<String, List<String>> addresses = new ConcurrentHashMap<>();

  public InstanceAddressRegistry(DiscoverySnapshotFetcher fetcher, Consumer<String> warnLine) {
    this.fetcher = fetcher;
    this.warnLine = warnLine;
  }

  /** 实例变更推送：按服务原子替换地址列表。 */
  public void onPush(String serviceName, List<String> instances) {
    addresses.put(serviceName, List.copyOf(instances));
  }

  /** 拉取校准单服务：失败沿用最近快照（不清空）并告警。 */
  public void refresh(String serviceName) {
    long startMillis = System.currentTimeMillis();
    List<String> snapshot;
    try {
      snapshot = fetcher.fetch(serviceName);
    } catch (Exception discoveryFailure) {
      warnLine.accept("实例列表刷新失败，沿用最近快照: service=" + serviceName
          + " reason=" + discoveryFailure.getMessage());
      ContractMetrics.recordIndexRefresh(
          System.currentTimeMillis() - startMillis, false);
      return;
    }
    addresses.put(serviceName, List.copyOf(snapshot));
    ContractMetrics.recordIndexRefresh(
        System.currentTimeMillis() - startMillis, true);
  }

  /** 启动定时兜底校准（默认周期由装配层按 10s 配置）。 */
  public void scheduleFallback(ScheduledExecutorService scheduler, long period,
      TimeUnit unit) {
    scheduler.scheduleWithFixedDelay(this::refreshAll, period, period, unit);
  }

  /** 校准全部已知服务。 */
  public void refreshAll() {
    for (String serviceName : addresses.keySet()) {
      refresh(serviceName);
    }
  }

  @Override
  public List<String> resolve(String serviceName) {
    return addresses.getOrDefault(serviceName, List.of());
  }
}
