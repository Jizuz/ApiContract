package com.apicontract.provider.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;

/**
 * 发布模式选择器：按配置模式与可用客户端端口选择实现；
 * nacos-client 为 optional 依赖，未引入（端口缺失）时降级 logOnly 并告警，
 * 功能不缺失（design 降级矩阵）。
 */
public final class MetaPublishers {

  private MetaPublishers() {
  }

  /**
   * 选择发布实现。
   *
   * @param mode 配置的发布模式
   * @param configCarrier 配置中心写入端口（nacos-client 存在时由装配层提供，可空）
   * @param instanceWriter 实例元数据写入端口（可空）
   * @param mapper Jackson 映射器
   * @param warnLine 降级告警输出
   * @return 发布实现（永不为 null：不可用模式降级 logOnly）
   */
  public static ContractMetaPublisher select(PublishMode mode, ConfigCarrierWriter configCarrier,
      InstanceMetadataWriter instanceWriter, ObjectMapper mapper, Consumer<String> warnLine) {
    PublishMode effective = mode == null ? PublishMode.LOG_ONLY : mode;
    switch (effective) {
      case NACOS_CONFIG:
        if (configCarrier != null) {
          return new NacosConfigMetaPublisher(configCarrier, mapper);
        }
        warnLine.accept("publish-mode=nacosConfig 但配置中心客户端未引入（optional 依赖缺失），"
            + "已降级 logOnly，功能不缺失");
        return new LogOnlyMetaPublisher(line -> { });
      case NACOS_METADATA:
        if (instanceWriter != null) {
          return new NacosMetadataMetaPublisher(instanceWriter);
        }
        warnLine.accept("publish-mode=nacosMetadata 但注册中心客户端未引入（optional 依赖缺失），"
            + "已降级 logOnly，功能不缺失");
        return new LogOnlyMetaPublisher(line -> { });
      case LOG_ONLY:
      default:
        return new LogOnlyMetaPublisher(line -> { });
    }
  }
}
