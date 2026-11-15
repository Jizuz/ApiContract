package com.apicontract.provider.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务6（红）测试：编排装配结果明示日志（规格 contract-starter-access
 * 「组件覆盖清单启动日志」）——内置生效 / 被业务覆盖（互斥） / 开关关闭三种
 * 装配结果均有确定性文案，供上线验收与巡检脚本比对。
 */
class OrchestrationCoverageTest {

  @Test
  void shouldReportExplicitLineForEachState() {
    List<String> lines = new ArrayList<>();

    OrchestrationCoverage.report(OrchestrationCoverage.State.ENABLED_DEFAULT, null, lines::add);
    OrchestrationCoverage.report(OrchestrationCoverage.State.OVERRIDDEN_BY_USER,
        DefaultApiContractOrchestratorTest.class, lines::add);
    OrchestrationCoverage.report(OrchestrationCoverage.State.DISABLED_BY_PROPERTY, null,
        lines::add);

    assertEquals(3, lines.size());
    assertTrue(lines.get(0).contains("内置启动编排生效"),
        "默认形态明示内置生效: " + lines.get(0));
    assertTrue(lines.get(1).contains("业务自定义编排") && lines.get(1).contains("禁用"),
        "业务覆盖明示互斥禁用: " + lines.get(1));
    assertTrue(lines.get(1).contains(DefaultApiContractOrchestratorTest.class.getName()),
        "日志携带业务编排组件类型: " + lines.get(1));
    assertTrue(lines.get(2).contains("api-contract.orchestration.enabled=false"),
        "开关关闭明示原因: " + lines.get(2));
  }
}
