package com.apicontract.provider.publish;

import com.apicontract.api.spi.SpiImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * nacosConfig 发布模式（正式链路）：契约元数据写入配置中心，
 * group=CONTRACT、dataId={serviceName}-contract-meta，作为唯一权威存储。
 *
 * <p>多版本后缀隔离（{serviceName}-contract-meta-{version}）见多版本共存任务；
 * 并发防覆盖比较由装配层在写入前完成（见防覆盖任务）。
 */
@SpiImpl("nacos-config")
public final class NacosConfigMetaPublisher implements ContractMetaPublisher {

  /** 契约元数据固定分组（design 存储设计）。 */
  public static final String GROUP_CONTRACT = "CONTRACT";

  private final ConfigCarrierWriter carrier;
  private final ObjectMapper mapper;
  private final boolean versionedDataId;

  public NacosConfigMetaPublisher(ConfigCarrierWriter carrier, ObjectMapper mapper) {
    this(carrier, mapper, false);
  }

  /** 多版本后缀隔离：{@code dataId={serviceName}-contract-meta-{version}}。 */
  public NacosConfigMetaPublisher(ConfigCarrierWriter carrier, ObjectMapper mapper,
      boolean versionedDataId) {
    this.carrier = carrier;
    this.mapper = mapper;
    this.versionedDataId = versionedDataId;
  }

  @Override
  public void publish(ContractMetaBundle bundle) {
    bundle.validate();
    String dataId = bundle.serviceName() + "-contract-meta"
        + (versionedDataId ? "-" + bundle.version() : "");
    String content = bundle.toJson(mapper);
    String existing = carrier.read(dataId);
    if (!OverwriteGuard.shouldOverwrite(existing, content, mapper)) {
      return;
    }
    try {
      carrier.write(GROUP_CONTRACT, dataId, content);
    } catch (RuntimeException failure) {
      throw new MetaPublishException(
          "契约元数据发布失败(CONFIG_CENTER_UNAVAILABLE): dataId=" + dataId, failure);
    }
  }
}
