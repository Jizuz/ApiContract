package com.apicontract.provider.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务11（红-绿）测试：发布模式选择（design 五级开关之发布模式回退）：
 * nacosConfig → nacosMetadata → logOnly；注册中心/配置中心客户端为 optional
 * 依赖，缺失时降级 logOnly 并告警（功能不缺失）。
 */
class MetaPublishersTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void shouldSelectLogOnlyByDefault() {
    List<String> warnings = new ArrayList<>();
    ContractMetaPublisher publisher =
        MetaPublishers.select(PublishMode.LOG_ONLY, null, null, mapper, warnings::add);

    assertInstanceOf(LogOnlyMetaPublisher.class, publisher);
    assertTrue(warnings.isEmpty());
  }

  @Test
  void shouldSelectNacosConfigWhenCarrierPresent() {
    ContractMetaPublisher publisher = MetaPublishers.select(PublishMode.NACOS_CONFIG,
        (group, dataId, content) -> { }, null, mapper, line -> { });

    assertInstanceOf(NacosConfigMetaPublisher.class, publisher);
  }

  @Test
  void shouldDegradeToLogOnlyWhenCarrierMissing() {
    List<String> warnings = new ArrayList<>();
    ContractMetaPublisher publisher =
        MetaPublishers.select(PublishMode.NACOS_CONFIG, null, null, mapper, warnings::add);

    assertInstanceOf(LogOnlyMetaPublisher.class, publisher,
        "nacos-client 未引入（optional）：降级 logOnly，功能不缺失");
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("降级"));
  }

  @Test
  void shouldDegradeToLogOnlyWhenInstanceWriterMissing() {
    List<String> warnings = new ArrayList<>();
    ContractMetaPublisher publisher =
        MetaPublishers.select(PublishMode.NACOS_METADATA, null, null, mapper, warnings::add);

    assertInstanceOf(LogOnlyMetaPublisher.class, publisher);
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("降级"));
  }

  @Test
  void shouldSelectNacosMetadataWhenWriterPresent() {
    ContractMetaPublisher publisher = MetaPublishers.select(PublishMode.NACOS_METADATA,
        null, mark -> { }, mapper, line -> { });

    assertInstanceOf(NacosMetadataMetaPublisher.class, publisher);
  }

  @Test
  void shouldParseModeFromConfigValue() {
    assertEquals(PublishMode.NACOS_CONFIG, PublishMode.fromValue("nacosConfig"));
    assertEquals(PublishMode.NACOS_METADATA, PublishMode.fromValue("nacosMetadata"));
    assertEquals(PublishMode.LOG_ONLY, PublishMode.fromValue("logOnly"));
    assertEquals(PublishMode.LOG_ONLY, PublishMode.fromValue("  LOG_ONLY "));
    assertEquals(PublishMode.LOG_ONLY, PublishMode.fromValue(null),
        "未配置默认 logOnly（灰度首步试点模式）");
  }
}
