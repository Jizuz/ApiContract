package com.apicontract.gateway.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 任务14（红-绿）测试：版本路由（spec：请求可指定版本，未指定时按默认版本选取；
 * 多版本共存时按 mt+版本正确寻址，默认版本回退正确）。
 */
class VersionRouterTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ContractIndexes indexes = new ContractIndexes(1000);

  private void publish(String version) {
    indexes.applyPayload("{\"serviceName\": \"user-provider\",\"version\": \"" + version
        + "\",\"contracts\": [{\"contractName\": \"echo.call\","
        + "\"interfaceName\": \"i.F\",\"methodName\": \"m\",\"paramNames\": [],"
        + "\"paramTypes\": [],\"returnType\": \"java.lang.String\","
        + "\"serviceName\": \"user-provider\",\"version\": \"" + version
        + "\",\"tags\": [],\"serializer\": \"json\",\"schemaRef\": null,"
        + "\"timeout\": null}],\"aggregateIndex\": {}}", mapper);
  }

  @Test
  void shouldRouteToExactVersionWhenRequested() {
    publish("1.0.0");
    publish("2.0.0");

    assertEquals("1.0.0",
        VersionRouter.route(indexes, "echo.call", "1.0.0").version(),
        "显式版本精确寻址");
    assertEquals("2.0.0",
        VersionRouter.route(indexes, "echo.call", "2.0.0").version());
  }

  @Test
  void shouldRouteToDefaultWhenVersionOmitted() {
    publish("1.0.0");
    publish("2.0.0");

    assertEquals("2.0.0",
        VersionRouter.route(indexes, "echo.call", null).version(),
        "未指定版本：默认最新稳定");
  }

  @Test
  void shouldReturnNullWhenRequestedVersionMissing() {
    publish("1.0.0");

    assertNull(VersionRouter.route(indexes, "echo.call", "9.9.9"),
        "请求不存在的版本：未命中（CONTRACT_NOT_FOUND 语义）");
  }

  @Test
  void shouldReturnNullWhenContractUnknown() {
    assertNull(VersionRouter.route(indexes, "not.exists", null));
    assertNull(VersionRouter.route(indexes, "not.exists", "1.0.0"));
  }
}
