package com.apicontract.gateway;

import com.apicontract.api.meta.ContractMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Map;

/**
 * 网关参数提取（design 网关入口参数提取规则）：
 * GET 按参数名从 query 取首值（缺省 null）；POST 按参数名从 JSON 对象取值
 * 或直接为参数数组（长度须等于参数个数）；无参契约忽略请求体。
 *
 * <p>输出统一为参数数组 JSON 字符串（值原样传递，类型转换由 Provider
 * 泛化入口完成）；形态或个数不符抛 {@link IllegalArgumentException}，
 * 网关据此返回 ARGUMENT_INVALID 且<b>不发起远程调用</b>。
 */
public final class ParameterExtractor {

  private ParameterExtractor() {
  }

  /** GET 形态：按参数名从 query 取首值，缺失补 null。 */
  public static String extractGet(ContractMeta meta, Map<String, String> query) {
    ArrayNode args = new ObjectMapper().createArrayNode();
    for (String paramName : meta.paramNames()) {
      String value = query.get(paramName);
      args.add(value == null ? null : args.textNode(value));
    }
    return args.toString();
  }

  /** POST 形态：JSON 对象按参数名取值（缺失补 null），或直接为参数数组。 */
  public static String extractPost(ContractMeta meta, String body, ObjectMapper mapper) {
    if (meta.paramNames().isEmpty()) {
      // 无参契约忽略请求体
      return "[]";
    }
    JsonNode root = parse(body, mapper, meta);
    if (root.isArray()) {
      requireArity(root, meta);
      return root.toString();
    }
    ArrayNode args = mapper.createArrayNode();
    for (String paramName : meta.paramNames()) {
      JsonNode value = root.get(paramName);
      args.add(value == null ? args.nullNode() : value);
    }
    return args.toString();
  }

  private static JsonNode parse(String body, ObjectMapper mapper, ContractMeta meta) {
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException(
          "POST 请求体为空，契约 " + meta.contractName() + " 需要 "
              + meta.paramNames().size() + " 个参数（或提供参数对象）");
    }
    JsonNode root;
    try {
      root = mapper.readTree(body);
    } catch (Exception malformed) {
      throw new IllegalArgumentException("POST 请求体非法 JSON: " + body, malformed);
    }
    if (!root.isObject() && !root.isArray()) {
      throw new IllegalArgumentException(
          "POST 请求体须为 JSON 对象（按参数名取值）或参数数组，实际: " + body);
    }
    return root;
  }

  private static void requireArity(JsonNode array, ContractMeta meta) {
    int expected = meta.paramNames().size();
    if (array.size() != expected) {
      throw new IllegalArgumentException(
          "参数个数不符: 期望 " + expected + "，实际 " + array.size()
              + "（不发起远程调用）");
    }
  }
}
