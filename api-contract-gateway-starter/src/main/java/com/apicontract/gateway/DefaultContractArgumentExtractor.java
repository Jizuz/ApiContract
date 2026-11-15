package com.apicontract.gateway;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.spi.SpiImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * 内置参数提取器：委托 {@link ParameterExtractor} 统一提取规则。
 */
@SpiImpl("default")
public class DefaultContractArgumentExtractor implements ContractArgumentExtractor {

  @Override
  public String extractGet(ContractMeta meta, Map<String, String> query) {
    return ParameterExtractor.extractGet(meta, query);
  }

  @Override
  public String extractPost(ContractMeta meta, String body, ObjectMapper mapper) {
    return ParameterExtractor.extractPost(meta, body, mapper);
  }
}
