package com.apicontract.provider.export;

import com.apicontract.api.spi.Spi;

/**
 * 导出过滤器扩展点（规格 contract-spi-extension）：白名单二次过滤（环境、租户）。
 *
 * <p>内置实现：默认放行（不配置即全部导出）。
 */
@Spi(name = "导出过滤器", defaultImpl = "allow-all")
public interface ExportFilter {

  /**
   * 是否导出该契约（白名单通过后的二次过滤）。
   *
   * @param environment 部署环境标识（可为 null）
   * @param tenant 租户标识（可为 null）
   * @param contractName 契约标识
   * @return true 放行导出；false 过滤
   */
  boolean allow(String environment, String tenant, String contractName);
}
