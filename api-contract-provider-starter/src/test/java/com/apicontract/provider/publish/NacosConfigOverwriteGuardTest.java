package com.apicontract.provider.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apicontract.api.meta.ContractMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务15（红-绿）测试：防覆盖集成进配置中心发布
 * （发布前读旧内容比较，旧版本迟到发布不写入且不掩盖异常）。
 */
class NacosConfigOverwriteGuardTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static final String EXISTING = "{\"serviceName\": \"user-provider\","
      + "\"version\": \"2.0.0\", \"contracts\": [], \"aggregateIndex\": {}}";

  private ContractMetaBundle bundle(String version) {
    ContractMeta meta = new ContractMeta("echo.call", "i.F", "m",
        List.of(), List.of(), "java.lang.String",
        "user-provider", version, List.of(), ContractMeta.SERIALIZER_JSON, null, null);
    return new ContractMetaBundle("user-provider", version, List.of(meta),
        Map.of("echo.call", "user-provider"));
  }

  @Test
  void shouldNotWriteWhenStaleVersionArrivesLate() {
    Map<String, String> store = new HashMap<>();
    store.put("user-provider-contract-meta", EXISTING);
    List<String[]> writes = new ArrayListWrap();

    NacosConfigMetaPublisher publisher = new NacosConfigMetaPublisher(
        new RecordingCarrier(store, writes), mapper);

    publisher.publish(bundle("1.0.0"));

    assertEquals(0, writes.size(), "旧版本迟到发布：不产生写入");
    assertEquals(EXISTING, store.get("user-provider-contract-meta"),
        "配置中心新内容原样保留");
  }

  @Test
  void shouldWriteWhenNoExistingContent() {
    Map<String, String> store = new HashMap<>();
    List<String[]> writes = new ArrayListWrap();

    NacosConfigMetaPublisher publisher = new NacosConfigMetaPublisher(
        new RecordingCarrier(store, writes), mapper);

    publisher.publish(bundle("1.0.0"));

    assertEquals(1, writes.size(), "首次发布正常写入");
  }

  @Test
  void shouldWriteWhenNewerVersion() {
    Map<String, String> store = new HashMap<>();
    store.put("user-provider-contract-meta", EXISTING);
    List<String[]> writes = new ArrayListWrap();

    NacosConfigMetaPublisher publisher = new NacosConfigMetaPublisher(
        new RecordingCarrier(store, writes), mapper);

    publisher.publish(bundle("3.0.0"));

    assertEquals(1, writes.size(), "新版本发布正常写入");
  }

  /** 记录写入并同步到存储的假配置中心端口（支持读旧内容）。 */
  private static final class RecordingCarrier implements ConfigCarrierWriter {

    private final Map<String, String> store;
    private final List<String[]> writes;

    RecordingCarrier(Map<String, String> store, List<String[]> writes) {
      this.store = store;
      this.writes = writes;
    }

    @Override
    public void write(String group, String dataId, String content) {
      writes.add(new String[] {group, dataId, content});
      store.put(dataId, content);
    }

    public String read(String dataId) {
      return store.get(dataId);
    }
  }

  /** 简单 ArrayList 别名（测试可读性）。 */
  private static final class ArrayListWrap extends java.util.ArrayList<String[]> {
  }
}
