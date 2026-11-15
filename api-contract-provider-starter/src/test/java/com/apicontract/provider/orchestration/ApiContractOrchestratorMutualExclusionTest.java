package com.apicontract.provider.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.provider.autoconfigure.ApiContractAutoConfiguration;
import com.apicontract.provider.publish.ContractMetaBundle;
import com.apicontract.provider.publish.ContractMetaPublisher;
import com.apicontract.provider.publish.InstanceRegistrar;
import com.apicontract.provider.web.ContractRpcHandler;
import com.apicontract.testapi.EchoContract;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 任务5（红）测试：编排互斥（CR 阻断项：无双发布）——
 * 业务定义任意 {@link ApiContractOrchestrator} Bean 时内置编排 Bean 必须不存在；
 * {@code api-contract.orchestration.enabled=false} 时内置编排不执行（兜底件不受影响）；
 * 反注册幂等（重复 destroy 恰好一次 / 未注册不反注册）；
 * 互斥生效/开关关闭有明示日志（{@link OrchestrationCoverage}）。
 */
class ApiContractOrchestratorMutualExclusionTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ApiContractAutoConfiguration.class))
      .withPropertyValues("spring.application.name=mutual-exclusion-test");

  @Test
  void shouldBackOffWhenUserDefinesCustomOrchestrator() {
    runner.withUserConfiguration(EchoImplConfig.class, ManualOrchestrationConfig.class,
            RecordingPublisherConfig.class)
        .run(context -> {
          org.assertj.core.api.Assertions.assertThat(context)
              .doesNotHaveBean("apiContractOrchestrator");
          assertFalse(context.getBeanNamesForType(DefaultApiContractOrchestrator.class).length > 0,
              "容器内不得存在 DefaultApiContractOrchestrator 类型实例");
          assertTrue(context.getBeanNamesForType(ApiContractOrchestrator.class).length == 1,
              "同一应用任一时刻仅一套编排生效");
          assertTrue(context.getBean(ApiContractOrchestrator.class)
              instanceof ManualOrchestrationConfig.BusinessOrchestrator,
              "生效编排应为业务自定义实现");
        });
  }

  @Test
  void shouldNotAssembleBuiltInOrchestrationWhenSwitchedOff() {
    runner.withPropertyValues("api-contract.orchestration.enabled=false")
        .withUserConfiguration(EchoImplConfig.class)
        .run(context -> {
          assertFalse(context.getBeanNamesForType(DefaultApiContractOrchestrator.class).length > 0,
              "orchestration.enabled=false 时内置编排不装配");
          org.assertj.core.api.Assertions.assertThat(context)
              .hasSingleBean(ContractRpcHandler.class)
              .hasSingleBean(ContractMetaPublisher.class);
        });
  }

  @Test
  void shouldDeregisterExactlyOnceAfterRepeatedDestroy() {
    runner.withUserConfiguration(EchoImplConfig.class, RecordingPublisherConfig.class,
            CountingRegistrarConfig.class)
        .run(context -> {
          DefaultApiContractOrchestrator orchestrator =
              context.getBean(DefaultApiContractOrchestrator.class);
          CountingRegistrar registrar = context.getBean(CountingRegistrar.class);

          orchestrator.run(null);
          assertEquals(1, registrar.registerCount.get(), "Runner 阶段应完成一次注册");

          orchestrator.destroy();
          orchestrator.destroy();
          orchestrator.destroy();
          assertEquals(1, registrar.deregisterCount.get(),
              "反注册幂等：重复 destroy 只反注册一次");
        });
  }

  @Test
  void shouldNotDeregisterWhenNeverRegistered() {
    runner.withUserConfiguration(EchoImplConfig.class, RecordingPublisherConfig.class,
            CountingRegistrarConfig.class)
        .run(context -> {
          DefaultApiContractOrchestrator orchestrator =
              context.getBean(DefaultApiContractOrchestrator.class);
          CountingRegistrar registrar = context.getBean(CountingRegistrar.class);

          orchestrator.destroy();
          assertEquals(0, registrar.deregisterCount.get(),
              "从未注册成功时 destroy 不得触发反注册");
        });
  }

  @Test
  void shouldLogExplicitNoticeWhenCoverageDecided() {
    List<String> lines = new ArrayList<>();

    OrchestrationCoverage.report(
        OrchestrationCoverage.State.OVERRIDDEN_BY_USER, ManualOrchestrationConfig.class,
        lines::add);
    OrchestrationCoverage.report(
        OrchestrationCoverage.State.DISABLED_BY_PROPERTY, null, lines::add);
    OrchestrationCoverage.report(
        OrchestrationCoverage.State.ENABLED_DEFAULT, null, lines::add);

    assertEquals(3, lines.size(), "三种编排装配结果均输出明示日志");
    assertTrue(lines.get(0).contains("业务自定义编排") && lines.get(0).contains("互斥"),
        "业务覆盖时日志应明示互斥生效: " + lines.get(0));
    assertTrue(lines.get(0).contains(ManualOrchestrationConfig.class.getName()),
        "日志应指明业务编排组件类型");
    assertTrue(lines.get(1).contains("orchestration.enabled=false"),
        "开关关闭时日志应明示原因: " + lines.get(1));
    assertTrue(lines.get(2).contains("内置启动编排生效"),
        "默认形态日志应明示内置编排生效: " + lines.get(2));
  }

  /** 容器内契约实现 Bean。 */
  @Configuration
  static class EchoImplConfig {

    @Bean
    EchoContract echoContractImpl() {
      return (id, name) -> "echo:mutual:" + id;
    }
  }

  /** 业务自定义编排（旧手抄形态升级示例：实现标记接口即声明自带编排）。 */
  @Configuration
  static class ManualOrchestrationConfig {

    @Bean
    ApiContractOrchestrator businessOrchestrator() {
      return new BusinessOrchestrator();
    }

    static final class BusinessOrchestrator implements ApiContractOrchestrator {
    }
  }

  @Configuration
  static class RecordingPublisherConfig {

    @Bean
    ContractMetaPublisher contractMetaPublisher() {
      return bundle -> {
      };
    }
  }

  @Configuration
  static class CountingRegistrarConfig {

    @Bean
    InstanceRegistrar instanceRegistrar() {
      return new CountingRegistrar();
    }
  }

  static final class CountingRegistrar implements InstanceRegistrar {

    final AtomicInteger registerCount = new AtomicInteger();
    final AtomicInteger deregisterCount = new AtomicInteger();

    @Override
    public void register(Map<String, String> instanceMark) {
      registerCount.incrementAndGet();
    }

    @Override
    public void deregister() {
      deregisterCount.incrementAndGet();
    }
  }
}
