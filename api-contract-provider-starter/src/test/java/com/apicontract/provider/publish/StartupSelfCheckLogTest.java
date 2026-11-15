package com.apicontract.provider.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务9（红-绿）测试：发布自检四段结构化日志（design 一致性策略 6）：
 * export loaded / contract ready / meta published / registry joined，
 * 作为上线验收与回归断言依据（对账等价物）。
 */
class StartupSelfCheckLogTest {

  private static ContractMeta meta(String mt) {
    return new ContractMeta(mt, "com.apicontract.testapi.EchoContract", "call",
        List.of("id"), List.of("long"), "java.lang.String",
        "user-provider", "1.0.0", List.of(), ContractMeta.SERIALIZER_JSON, null, null);
  }

  @Test
  void shouldEmitFourStageStructuredLogsInOrder() {
    List<String> lines = new ArrayList<>();
    StartupSelfCheckLog selfCheck = new StartupSelfCheckLog(lines::add);

    selfCheck.exportLoaded(3);
    selfCheck.contractReady(2);
    selfCheck.metaPublished("user-provider", "1.0.0", 2);
    selfCheck.registryJoined("user-provider");

    assertEquals(4, lines.size(), "四段自检日志齐全");
    assertTrue(lines.get(0).contains("export loaded") && lines.get(0).contains("3"),
        "第一段：白名单加载数量");
    assertTrue(lines.get(1).contains("contract ready") && lines.get(1).contains("2"),
        "第二段：扫描就绪契约数");
    assertTrue(lines.get(2).contains("meta published") && lines.get(2).contains("user-provider"),
        "第三段：元数据发布完成");
    assertTrue(lines.get(3).contains("registry joined") && lines.get(3).contains("user-provider"),
        "第四段：实例注册完成");
  }

  @Test
  void shouldIncludeContractIdListInReadyStage() {
    List<String> lines = new ArrayList<>();
    StartupSelfCheckLog selfCheck = new StartupSelfCheckLog(lines::add);

    selfCheck.contractReadyWithIds(List.of("echo.call", "echo.ping"));

    assertTrue(lines.get(0).contains("contract ready"));
    assertTrue(lines.get(0).contains("echo.call") && lines.get(0).contains("echo.ping"),
        "就绪段附契约标识清单，供上线验收比对");
  }
}
