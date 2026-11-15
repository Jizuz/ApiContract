package com.apicontract.provider.publish;

import com.apicontract.api.spi.SpiImpl;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * nacosMetadata 发布模式（旧链路/回退）：实例元数据仅携带契约版本、
 * 契约数量等极简标记，不携带契约明细（契约明细仅在配置中心条目中）。
 */
@SpiImpl("nacos-metadata")
public final class NacosMetadataMetaPublisher implements ContractMetaPublisher {

  public static final String KEY_CONTRACT_VERSION = "contract-version";
  public static final String KEY_CONTRACT_COUNT = "contract-count";

  private final InstanceMetadataWriter writer;

  public NacosMetadataMetaPublisher(InstanceMetadataWriter writer) {
    this.writer = writer;
  }

  @Override
  public void publish(ContractMetaBundle bundle) {
    bundle.validate();
    Map<String, String> mark = new LinkedHashMap<>();
    mark.put(KEY_CONTRACT_VERSION, bundle.version());
    mark.put(KEY_CONTRACT_COUNT, String.valueOf(bundle.contracts().size()));
    try {
      writer.write(mark);
    } catch (RuntimeException failure) {
      throw new MetaPublishException(
          "实例元数据写入失败: service=" + bundle.serviceName(), failure);
    }
  }
}
