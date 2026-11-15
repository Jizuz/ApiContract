package com.apicontract.provider.orchestration;

import java.util.function.Consumer;

/**
 * 编排装配结果明示日志（规格 contract-starter-access「组件覆盖清单启动日志」）：
 * 内置生效 / 被业务覆盖（互斥，无双发布） / 开关关闭三种结果均有确定性文案，
 * 供上线验收与巡检脚本比对。
 */
public final class OrchestrationCoverage {

  private OrchestrationCoverage() {
  }

  /** 编排装配结果。 */
  public enum State {

    /** 内置编排生效（默认形态）。 */
    ENABLED_DEFAULT,

    /** 业务自定义编排存在，内置编排互斥禁用（防双发布）。 */
    OVERRIDDEN_BY_USER,

    /** api-contract.orchestration.enabled=false，内置编排被开关关闭。 */
    DISABLED_BY_PROPERTY
  }

  /**
   * 输出装配结果明示日志。
   *
   * @param state 装配结果
   * @param userOrchestratorType 业务编排组件类型（仅 OVERRIDDEN_BY_USER 时非空）
   * @param logLine 日志输出
   */
  public static void report(State state, Class<?> userOrchestratorType,
      Consumer<String> logLine) {
    switch (state) {
      case OVERRIDDEN_BY_USER -> logLine.accept(
          "[api-contract] 检测到业务自定义编排组件（"
              + (userOrchestratorType == null ? "unknown" : userOrchestratorType.getName())
              + "）：内置启动编排已禁用（互斥生效，无双发布）");
      case DISABLED_BY_PROPERTY -> logLine.accept(
          "[api-contract] api-contract.orchestration.enabled=false：内置启动编排未装配"
              + "（应用自行编排，兜底组件保持可用）");
      case ENABLED_DEFAULT -> logLine.accept(
          "[api-contract] 内置启动编排生效（default）：白名单→扫描→调用器→发布→注册全自动");
      default -> {
      }
    }
  }
}
