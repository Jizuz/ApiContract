package com.apicontract.provider.web;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.spi.SpiImpl;

/**
 * 内置参数校验器：参数个数与契约声明一致（可关闭——业务可覆盖扩展点替换策略）。
 */
@SpiImpl("standard")
public class StandardArgumentValidator implements ArgumentValidator {

  @Override
  public void validate(ContractMeta meta, Object[] args) {
    int expected = meta.paramNames() == null ? 0 : meta.paramNames().size();
    int actual = args == null ? 0 : args.length;
    if (expected != actual) {
      throw new ArgumentInvalidException(String.format(
          "契约[%s]参数个数不符: 声明 %d 个, 实际 %d 个", meta.contractName(), expected, actual));
    }
  }
}
