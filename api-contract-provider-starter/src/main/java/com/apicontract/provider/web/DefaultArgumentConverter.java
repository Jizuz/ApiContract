package com.apicontract.provider.web;

import com.apicontract.api.spi.SpiImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 内置参数转换器：委托 {@link ContractArgumentConverter} 统一转换规则。
 */
@SpiImpl("default")
public class DefaultArgumentConverter implements ArgumentConverter {

  @Override
  public Object convert(Object raw, Class<?> target, String paramName, ObjectMapper mapper) {
    return ContractArgumentConverter.convert(raw, target, paramName, mapper);
  }
}
