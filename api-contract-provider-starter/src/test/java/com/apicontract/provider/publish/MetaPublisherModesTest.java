package com.apicontract.provider.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务8（红-绿）测试：三种 publish-mode 发布内容断言（design 决策：发布方式 SPI 可回退）。
 *
 * <p>Nacos 两模式经注入写入端口隔离 nacos-client（optional 依赖），测试以
 * 假端口断言发布内容：dataId={service}-contract-meta、group=CONTRACT、
 * payload 含 serviceName/version/contracts/aggregateIndex；实例元数据模式
 * 仅写极简标记。
 */
class MetaPublisherModesTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private ContractMetaBundle bundle() {
    ContractMeta meta = new ContractMeta("echo.call", "com.apicontract.testapi.EchoContract",
        "call", List.of("id"), List.of("long"), "java.lang.String",
        "user-provider", "1.0.0", List.of("gray"), ContractMeta.SERIALIZER_JSON, null, null);
    return new ContractMetaBundle("user-provider", "1.0.0", List.of(meta),
        Map.of("echo.call", "user-provider"));
  }

  @Test
  void shouldOnlyLogInLogOnlyMode() {
    List<String> logLines = new ArrayList<>();
    LogOnlyMetaPublisher publisher = new LogOnlyMetaPublisher(logLines::add);

    publisher.publish(bundle());

    assertEquals(1, logLines.size());
    assertTrue(logLines.get(0).contains("meta published"),
        "logOnly 模式输出结构化自检日志 meta published");
    assertTrue(logLines.get(0).contains("user-provider"));
    assertTrue(logLines.get(0).contains("echo.call"));
  }

  @Test
  void shouldWriteServicePayloadInNacosConfigMode() throws Exception {
    List<String[]> writes = new ArrayList<>();
    NacosConfigMetaPublisher publisher =
        new NacosConfigMetaPublisher((group, dataId, content) -> writes.add(
            new String[] {group, dataId, content}), mapper);

    publisher.publish(bundle());

    assertEquals(1, writes.size());
    assertEquals("CONTRACT", writes.get(0)[0], "契约元数据固定分组 group=CONTRACT");
    assertEquals("user-provider-contract-meta", writes.get(0)[1], "dataId={serviceName}-contract-meta");
    JsonNode payload = mapper.readTree(writes.get(0)[2]);
    assertEquals("user-provider", payload.get("serviceName").asText());
    assertEquals("1.0.0", payload.get("version").asText());
    assertEquals("echo.call", payload.get("contracts").get(0).get("contractName").asText());
    assertEquals("user-provider", payload.get("aggregateIndex").get("echo.call").asText(),
        "聚合索引随契约集合一并发布，支持跨服务快速判重");
  }

  @Test
  void shouldWrapCarrierFailureAsMetaPublishException() {
    NacosConfigMetaPublisher publisher = new NacosConfigMetaPublisher(
        (group, dataId, content) -> {
          throw new RuntimeException("config center down");
        }, mapper);

    MetaPublishException failure =
        assertThrows(MetaPublishException.class, () -> publisher.publish(bundle()));
    assertEquals("config center down", failure.getCause().getMessage(),
        "原始异常作为 cause 保留，便于启动期定位");
  }

  @Test
  void shouldWriteMinimalMarkInNacosMetadataMode() {
    List<Map<String, String>> writes = new ArrayList<>();
    NacosMetadataMetaPublisher publisher =
        new NacosMetadataMetaPublisher(mark -> writes.add(new HashMap<>(mark)));

    publisher.publish(bundle());

    assertEquals(1, writes.size());
    Map<String, String> mark = writes.get(0);
    assertEquals("1.0.0", mark.get("contract-version"));
    assertEquals("1", mark.get("contract-count"));
    assertEquals(2, mark.size(), "实例元数据模式仅极简标记，不携带契约明细");
  }

  @Test
  void shouldRejectEmptyContractsInBundle() {
    assertThrows(IllegalArgumentException.class,
        () -> new ContractMetaBundle("user-provider", "1.0.0", List.of(), Map.of()).validate(),
        "空契约集合不允许构造合法发布包（空清单不发布）");
  }
}
