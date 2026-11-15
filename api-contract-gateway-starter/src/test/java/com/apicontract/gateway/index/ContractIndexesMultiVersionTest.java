package com.apicontract.gateway.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 任务14（红-绿）测试：多版本共存（design：多版本推荐按版本后缀隔离配置条目；
 * 契约数量少时可单配置多版本，按 mt+版本区分寻址；无版本默认最新稳定）。
 */
class ContractIndexesMultiVersionTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static String payload(String service, String version, String... mts) {
    StringBuilder contracts = new StringBuilder();
    for (int i = 0; i < mts.length; i++) {
      if (i > 0) {
        contracts.append(',');
      }
      contracts.append("{").append("\"contractName\": \"").append(mts[i]).append("\",")
          .append("\"interfaceName\": \"i.F\",").append("\"methodName\": \"m\",")
          .append("\"paramNames\": [],").append("\"paramTypes\": [],")
          .append("\"returnType\": \"java.lang.String\",")
          .append("\"serviceName\": \"").append(service).append("\",")
          .append("\"version\": \"").append(version).append("\",")
          .append("\"tags\": [],").append("\"serializer\": \"json\",")
          .append("\"schemaRef\": null,").append("\"timeout\": null}");
    }
    return "{\"serviceName\": \"" + service + "\",\"version\": \"" + version
        + "\",\"contracts\": [" + contracts + "],\"aggregateIndex\": {}}";
  }

  @Test
  void shouldKeepBothVersionsWhenSameMtPublishedTwice() {
    ContractIndexes indexes = new ContractIndexes(1000);
    indexes.applyPayload(payload("user-provider", "1.0.0", "echo.call"), mapper);
    indexes.applyPayload(payload("user-provider", "2.0.0", "echo.call"), mapper);

    assertEquals(2, indexes.versionsOf("echo.call").size(),
        "同 mt 多版本共存，互不覆盖");
    assertNotNull(indexes.findVersioned("echo.call", "1.0.0"));
    assertNotNull(indexes.findVersioned("echo.call", "2.0.0"));
  }

  @Test
  void shouldDefaultToLatestStableVersion() {
    ContractIndexes indexes = new ContractIndexes(1000);
    indexes.applyPayload(payload("user-provider", "1.0.0", "echo.call"), mapper);
    indexes.applyPayload(payload("user-provider", "2.0.0", "echo.call"), mapper);

    assertEquals("2.0.0", indexes.find("echo.call").version(),
        "无版本寻址默认最新稳定版本");
  }

  @Test
  void shouldExcludePreReleaseFromDefault() {
    ContractIndexes indexes = new ContractIndexes(1000);
    indexes.applyPayload(payload("user-provider", "1.0.0", "echo.call"), mapper);
    indexes.applyPayload(payload("user-provider", "2.0.0-RC1", "echo.call"), mapper);

    assertEquals("1.0.0", indexes.find("echo.call").version(),
        "预发布版本不作为默认（最新稳定才可默认路由）");
    assertNotNull(indexes.findVersioned("echo.call", "2.0.0-RC1"),
        "但显式指定版本仍可精确寻址");
  }

  @Test
  void shouldFallBackToPreReleaseWhenOnlyVersion() {
    ContractIndexes indexes = new ContractIndexes(1000);
    indexes.applyPayload(payload("user-provider", "2.0.0-RC1", "echo.call"), mapper);

    assertEquals("2.0.0-RC1", indexes.find("echo.call").version(),
        "仅有预发布版本时可用其作默认");
  }

  @Test
  void shouldReplaceOnlyTargetVersionOnRepublish() {
    ContractIndexes indexes = new ContractIndexes(1000);
    indexes.applyPayload(payload("user-provider", "1.0.0", "echo.call"), mapper);
    indexes.applyPayload(payload("user-provider", "2.0.0", "echo.call"), mapper);

    // 同版本重推（dataId 隔离粒度 = 服务+版本）：该版本条目被替换
    indexes.applyPayload(payload("user-provider", "1.0.0", "echo.call", "echo.extra"),
        mapper);

    assertNotNull(indexes.findVersioned("echo.call", "1.0.0"));
    assertNotNull(indexes.findVersioned("echo.extra", "1.0.0"),
        "同版本重推纳入新增契约");
    assertNotNull(indexes.findVersioned("echo.call", "2.0.0"),
        "版本后缀隔离：其他版本条目不受影响（旧版本保留至下线）");
    assertEquals(2, indexes.versionsOf("echo.call").size());
  }

  @Test
  void shouldCountDistinctContractsAcrossVersionsForLimit() {
    ContractIndexes indexes = new ContractIndexes(3, line -> { });
    indexes.applyPayload(payload("user-provider", "1.0.0", "a", "b"), mapper);
    indexes.applyPayload(payload("user-provider", "2.0.0", "a", "b"), mapper);
    indexes.applyPayload(payload("order-provider", "1.0.0", "c", "d"), mapper);

    assertNull(indexes.find("c"),
        "条目上限按 mt+版本 计数：跨版本条目占额度，超限拒绝");
  }
}
