package com.apicontract.gateway;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.spi.Spi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * 参数提取器扩展点（规格 contract-spi-extension）：从请求提取契约参数数组。
 *
 * <p>内置实现：default（query / jsonBody / protobufBody 统一提取规则）。
 */
@Spi(name = "参数提取器", defaultImpl = "default")
public interface ContractArgumentExtractor {

  /**
   * 从 query 参数提取（GET 形态）。
   *
   * @param meta 契约元数据
   * @param query query 参数集
   * @return 契约参数数组 JSON 字符串
   */
  String extractGet(ContractMeta meta, Map<String, String> query);

  /**
   * 从请求体提取（POST 形态）。
   *
   * @param meta 契约元数据
   * @param body 请求体（JSON）
   * @param mapper 容器 Jackson 映射器
   * @return 契约参数数组 JSON 字符串
   */
  String extractPost(ContractMeta meta, String body, ObjectMapper mapper);
}
