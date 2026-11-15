package com.apicontract.provider.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.apicontract.api.meta.ContractMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务14（红-绿）测试：多版本配置条目后缀隔离
 * （design：dataId={serviceName}-contract-meta-{version}）。
 */
class NacosConfigVersionSuffixTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private ContractMetaBundle bundle(String version) {
    ContractMeta meta = new ContractMeta("echo.call", "i.F", "m",
        List.of(), List.of(), "java.lang.String",
        "user-provider", version, List.of(), ContractMeta.SERIALIZER_JSON, null, null);
    return new ContractMetaBundle("user-provider", version, List.of(meta),
        Map.of("echo.call", "user-provider"));
  }

  @Test
  void shouldWriteVersionedDataIdWhenSuffixEnabled() {
    List<String[]> writes = new ArrayList<>();
    NacosConfigMetaPublisher publisher = new NacosConfigMetaPublisher(
        (group, dataId, content) -> writes.add(new String[] {group, dataId, content}),
        mapper, true);

    publisher.publish(bundle("2.0.0"));

    assertEquals("user-provider-contract-meta-2.0.0", writes.get(0)[1],
        "多版本后缀隔离：dataId={serviceName}-contract-meta-{version}");
    assertEquals("CONTRACT", writes.get(0)[0]);
  }

  @Test
  void shouldKeepUnversionedDataIdByDefault() {
    List<String[]> writes = new ArrayList<>();
    NacosConfigMetaPublisher publisher = new NacosConfigMetaPublisher(
        (group, dataId, content) -> writes.add(new String[] {group, dataId, content}),
        mapper);

    publisher.publish(bundle("2.0.0"));

    assertEquals("user-provider-contract-meta", writes.get(0)[1],
        "默认（单版本）保持无后缀 dataId");
  }
}
