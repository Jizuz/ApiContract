package com.apicontract.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apicontract.api.meta.ContractMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务10（红-绿）测试：三种参数形态提取（design 网关入口参数提取规则）。
 *
 * <p>GET 按参数名从 query 取首值（缺省 null）；POST 按参数名从 JSON 对象
 * 取值或直接为参数数组（长度须等于参数个数）；无参契约忽略请求体；
 * 输出统一为参数数组 JSON（类型转换由 Provider 泛化入口完成）。
 */
class ParameterExtractorTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private ContractMeta meta(int paramCount) {
    return new ContractMeta("echo.call", "com.apicontract.testapi.EchoContract", "call",
        List.of("id", "name").subList(0, paramCount),
        List.of("long", "java.lang.String").subList(0, paramCount),
        "java.lang.String", "user-provider", "1.0.0", List.of(),
        ContractMeta.SERIALIZER_JSON, null, null);
  }

  @Test
  void shouldExtractByParamNameFromQuery() {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("id", "42");
    query.put("name", "jiz");

    String args = ParameterExtractor.extractGet(meta(2), query);

    assertEquals("[\"42\",\"jiz\"]", args,
        "GET：按参数名取值，保持字符串原样（类型转换由 Provider 完成）");
  }

  @Test
  void shouldFillNullWhenQueryParamMissing() {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("id", "42");

    String args = ParameterExtractor.extractGet(meta(2), query);

    assertEquals("[\"42\",null]", args, "GET：缺失参数补 null（null 保持 null）");
  }

  @Test
  void shouldExtractFromJsonObjectByParamName() {
    String body = "{\"id\": 42, \"name\": \"jiz\", \"extra\": true}";

    String args = ParameterExtractor.extractPost(meta(2), body, mapper);

    assertEquals("[42,\"jiz\"]", args,
        "POST 对象：按参数名取值，多余字段忽略");
  }

  @Test
  void shouldFillNullWhenJsonFieldMissing() {
    String args = ParameterExtractor.extractPost(meta(2), "{\"id\": 42}", mapper);

    assertEquals("[42,null]", args, "POST 对象：缺失字段补 null");
  }

  @Test
  void shouldPassThroughJsonArrayAsArgs() {
    String args = ParameterExtractor.extractPost(meta(2), "[42, \"jiz\"]", mapper);

    assertEquals("[42,\"jiz\"]", args, "POST 数组：直接作为参数数组（元素原样）");
  }

  @Test
  void shouldRejectArrayLengthMismatchWithoutRemoteCall() {
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> ParameterExtractor.extractPost(meta(2), "[42]", mapper),
        "POST 数组长度须等于参数个数，不符即拒绝（不发起远程调用）");
    assertEquals(true, failure.getMessage().contains("2"));
  }

  @Test
  void shouldIgnoreBodyWhenContractHasNoParam() {
    assertEquals("[]", ParameterExtractor.extractGet(meta(0), Map.of("id", "1")),
        "GET 无参契约忽略查询参数");
    assertEquals("[]", ParameterExtractor.extractPost(meta(0), "{\"any\": 1}", mapper),
        "POST 无参契约忽略请求体");
  }

  @Test
  void shouldRejectMalformedPostBody() {
    assertThrows(IllegalArgumentException.class,
        () -> ParameterExtractor.extractPost(meta(2), "not-json", mapper),
        "POST 请求体非法 JSON：参数非法拒绝");
    assertThrows(IllegalArgumentException.class,
        () -> ParameterExtractor.extractPost(meta(2), "42", mapper),
        "POST 请求体既非对象也非数组：参数非法拒绝");
  }
}
