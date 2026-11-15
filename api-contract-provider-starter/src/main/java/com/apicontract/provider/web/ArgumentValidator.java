package com.apicontract.provider.web;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.spi.Spi;

/**
 * 参数校验器扩展点（规格 contract-spi-extension）：标准校验实现（可关闭）。
 */
@Spi(name = "参数校验器", defaultImpl = "standard")
public interface ArgumentValidator {

  /**
   * 校验调用参数与契约声明一致。
   *
   * @param meta 契约元数据
   * @param args 实际参数数组
   * @throws ArgumentInvalidException 校验失败（指明位置与原因）
   */
  void validate(ContractMeta meta, Object[] args);
}
