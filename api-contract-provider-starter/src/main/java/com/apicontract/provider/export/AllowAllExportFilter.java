package com.apicontract.provider.export;

import com.apicontract.api.spi.SpiImpl;

/**
 * 内置导出过滤器：默认放行（规格边界用例：不提供业务实现时全部使用内置默认值）。
 */
@SpiImpl("allow-all")
public class AllowAllExportFilter implements ExportFilter {

  @Override
  public boolean allow(String environment, String tenant, String contractName) {
    return true;
  }
}
