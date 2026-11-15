package com.apicontract.it;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingService;
import com.apicontract.gateway.index.DiscoverySnapshotFetcher;
import com.apicontract.provider.publish.ConfigCarrierWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos 客户端桥（任务25 集成测试装配层，等价于正式装配的 nacos 桥）：
 * 配置中心写入端口、实例注册（发布顺序：先元数据后实例）、
 * 网关侧实例快照拉取端口——全部走真实 nacos-client。
 */
public final class NacosBridge {

  private final String serverAddr;
  private volatile ConfigService configService;
  private volatile NamingService namingService;

  public NacosBridge(String serverAddr) {
    this.serverAddr = serverAddr;
  }

  public ConfigService configService() throws Exception {
    if (configService == null) {
      configService = NacosFactory.createConfigService(props());
    }
    return configService;
  }

  public NamingService namingService() throws Exception {
    if (namingService == null) {
      namingService = NacosFactory.createNamingService(props());
    }
    return namingService;
  }

  /** 配置中心写入端口实现（group=CONTRACT 固定分组，支持读回做防覆盖比较）。 */
  public ConfigCarrierWriter configCarrierWriter() {
    return new ConfigCarrierWriter() {
      @Override
      public void write(String group, String dataId, String content) {
        try {
          if (!configService().publishConfig(dataId, group, content)) {
            throw new IllegalStateException("nacos publishConfig 返回 false: "
                + dataId);
          }
        } catch (Exception failure) {
          throw new RuntimeException(failure);
        }
      }

      @Override
      public String read(String dataId) {
        try {
          return configService().getConfig(dataId,
              NacosBridgeConfig.GROUP_CONTRACT, 3_000);
        } catch (Exception failure) {
          return null;
        }
      }
    };
  }

  /** 删除契约元 dataId（IT 跨次运行清残留：防覆盖守卫会拒绝同版本不同内容的迟到写入）。 */
  public void removeContractConfig(String serviceName, String version)
      throws Exception {
    String dataId = serviceName + "-contract-meta"
        + (version == null ? "" : "-" + version);
    configService().removeConfig(dataId, NacosBridgeConfig.GROUP_CONTRACT);
  }

  /** 实例注册：发布管线第二步（写入极简元数据标记）。 */
  public AutoCloseable registerInstance(String serviceName, String ip, int port,
      Map<String, String> metadata) throws Exception {
    com.alibaba.nacos.api.naming.pojo.Instance instance =
        new com.alibaba.nacos.api.naming.pojo.Instance();
    instance.setIp(ip);
    instance.setPort(port);
    instance.setMetadata(metadata);
    namingService().registerInstance(serviceName,
        com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP, instance);
    return () -> namingService().deregisterInstance(serviceName,
        com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP, instance);
  }

  /** 网关侧实例快照拉取（仅健康实例；异常向上传播由注册表沿用最近快照）。 */
  public DiscoverySnapshotFetcher discoveryFetcher() {
    return serviceName -> {
      List<com.alibaba.nacos.api.naming.pojo.Instance> instances =
          namingService().selectInstances(serviceName, true);
      return instances.stream()
          .map(i -> "http://" + i.getIp() + ":" + i.getPort())
          .toList();
    };
  }

  private Properties props() {
    Properties props = new Properties();
    props.setProperty("serverAddr", serverAddr);
    return props;
  }

  /** 桥内固定常量（与 NacosConfigMetaPublisher.GROUP_CONTRACT 对齐）。 */
  public static final class NacosBridgeConfig {
    public static final String GROUP_CONTRACT = "CONTRACT";
  }
}
