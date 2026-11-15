package com.apicontract.provider.publish;

import com.apicontract.api.spi.Spi;

/**
 * 契约元数据发布 SPI（design 决策：发布方式 SPI 可回退）。
 *
 * <p>内置实现：{@link LogOnlyMetaPublisher}（仅日志，排障用）、
 * {@link NacosConfigMetaPublisher}（配置中心权威存储，正式链路）、
 * {@link NacosMetadataMetaPublisher}（实例元数据旧链路）。
 *
 * <p>契约：发布失败必须抛出 {@link MetaPublishException}（携带原始 cause），
 * 由 {@link ContractPublishPipeline} 保证发布成功后才注册实例。
 */
@Spi(name = "契约元数据发布器", defaultImpl = "nacos-config")
@FunctionalInterface
public interface ContractMetaPublisher {

  /**
   * 发布服务契约集合。
   *
   * @param bundle 服务契约发布包（非空契约集合）
   * @throws MetaPublishException 发布失败（配置中心不可用等），中断启动
   */
  void publish(ContractMetaBundle bundle);
}
