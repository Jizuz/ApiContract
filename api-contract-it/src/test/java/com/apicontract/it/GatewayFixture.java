package com.apicontract.it;

import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.index.ContractIndexes;
import com.apicontract.gateway.index.InstanceAddressRegistry;
import com.apicontract.gateway.remote.ReactorNettyRemoteCaller;
import com.apicontract.gateway.index.DiscoverySnapshotFetcher;
import com.apicontract.provider.publish.NacosConfigMetaPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内网关装配（任务25）：从 Nacos 配置中心订阅契约元数据推送
 * （读取 {@code {service}-contract-meta} → 三级索引原子替换）、
 * 从 Nacos 发现拉取实例地址快照、Reactor Netty 异步调用直连 Provider。
 */
public final class GatewayFixture {

  public final ContractIndexes indexes = new ContractIndexes(1024,
      line -> { });
  public final InstanceAddressRegistry instances;
  public final ReactorNettyRemoteCaller caller =
      new ReactorNettyRemoteCaller(
          com.apicontract.gateway.remote.ConnectionPoolSettings.DEFAULT,
          com.apicontract.gateway.remote.TimeoutSettings.DEFAULT);
  public final ObjectMapper mapper = new ObjectMapper();
  private final NacosBridge bridge;
  private final AtomicInteger refreshCount = new AtomicInteger();

  public GatewayFixture(NacosBridge bridge) {
    this(bridge, bridge.discoveryFetcher());
  }

  /** 可注入故障注入式 fetcher（降级矩阵测试：抛异常 = 注册中心不可达）。 */
  public GatewayFixture(NacosBridge bridge,
      DiscoverySnapshotFetcher fetcherOverride) {
    this.bridge = bridge;
    this.instances = new InstanceAddressRegistry(fetcherOverride,
        line -> refreshCount.incrementAndGet());
  }

  /** 订阅单服务契约推送（装配层桥：配置中心 → 三级索引原子替换）。 */
  public void subscribeService(String serviceName) throws Exception {
    String payload = readPayload(serviceName, null);
    if (payload != null) {
      indexes.applyPayload(payload, mapper);
    }
  }

  /** 订阅指定版本推送（多版本 dataId 后缀隔离）。 */
  public void subscribeService(String serviceName, String version)
      throws Exception {
    String payload = readPayload(serviceName, version);
    if (payload != null) {
      indexes.applyPayload(payload, mapper);
    }
  }

  private String readPayload(String serviceName, String version)
      throws Exception {
    String dataId = serviceName + "-contract-meta"
        + (version == null ? "" : "-" + version);
    return bridge.configService().getConfig(dataId,
        NacosConfigMetaPublisher.GROUP_CONTRACT, 5_000);
  }

  /** 校准单服务实例地址（拉取模式）。 */
  public void refreshInstances(String serviceName) {
    instances.refresh(serviceName);
  }

  /** 网关处理器（便利构造：failover 2 + 轮询，无限流熔断；主索引=三级索引 find）。 */
  public ContractGatewayHandler handler() {
    return new ContractGatewayHandler(
        indexes::find, instances, caller, mapper);
  }

  /** 降级告警次数（沿用最近快照场景断言用）。 */
  public int degradedRefreshCount() {
    return refreshCount.get();
  }
}
