package com.apicontract.provider.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.provider.autoconfigure.ApiContractAutoConfiguration;
import com.apicontract.provider.publish.ContractMetaBundle;
import com.apicontract.provider.publish.ContractMetaPublisher;
import com.apicontract.provider.publish.InstanceRegistrar;
import com.apicontract.provider.publish.MetaPublishException;
import com.apicontract.testapi.EchoContract;
import com.apicontract.provider.web.ContractRpcHandler;
import com.apicontract.provider.web.RpcResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 任务3（红）测试：内置启动编排器（design 方案B 时序）——
 * SmartInitializingSingleton 阶段完成白名单加载→契约扫描→调用器生成→handler 后填充；
 * ApplicationRunner 阶段（Web 端口已定）先发布元数据、成功后注册实例；
 * 任一步失败异常传播中断启动（ApplicationRunner 异常即启动失败，Spring Boot 语义）；
 * 空白名单正常启动且不发布不注册。
 *
 * <p>ApplicationContextRunner 仅刷新容器（SmartInitializingSingleton 在刷新期触发），
 * Runner 阶段由测试手动调用 run() 模拟，等价于 Spring Boot 启动对 ApplicationRunner 的回调。
 */
class DefaultApiContractOrchestratorTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ApiContractAutoConfiguration.class))
      .withPropertyValues("spring.application.name=orchestration-test");

  @Test
  void shouldScanGenerateInvokersAndResetHandlerWhenSingletonsInstantiated() {
    runner.withUserConfiguration(EchoImplConfig.class).run(context -> {
      org.assertj.core.api.Assertions.assertThat(context)
          .hasSingleBean(DefaultApiContractOrchestrator.class);

      ContractRpcHandler handler = context.getBean(ContractRpcHandler.class);
      RpcResponse response = handler.handle("echo.call", "[42, \"jiz\"]");

      assertEquals(RpcResponse.CODE_OK, response.code(),
          "容器单例就绪后（Runner 之前）编排器应已完成扫描、调用器生成与 handler 后填充");
      assertEquals("echo:orchestrated:42", response.data(),
          "调用器应绑定容器内实现 Bean（复用既有扫描与生成链路）");
    });
  }

  @Test
  void shouldPublishMetadataThenRegisterInstanceInOrder() {
    runner.withUserConfiguration(EchoImplConfig.class, RecordingPublisherConfig.class,
            RecordingRegistrarConfig.class)
        .run(context -> {
          DefaultApiContractOrchestrator orchestrator =
              context.getBean(DefaultApiContractOrchestrator.class);
          RecordingPublisher publisher = context.getBean(RecordingPublisher.class);
          RecordingRegistrar registrar = context.getBean(RecordingRegistrar.class);

          orchestrator.run(null);

          assertEquals(1, publisher.bundles.size(), "Runner 阶段应发布一次契约元数据");
          ContractMetaBundle bundle = publisher.bundles.get(0);
          assertEquals("orchestration-test", bundle.serviceName(),
              "服务名取 spring.application.name");
          assertEquals(1, bundle.contracts().size(), "契约条目来自白名单扫描");
          assertEquals("1.0.0", bundle.version(), "契约版本默认 1.0.0");

          assertEquals(1, registrar.marks.size(), "元数据发布成功后应注册实例");
          Map<String, String> mark = registrar.marks.get(0);
          assertEquals("1.0.0", mark.get("contract-version"), "实例标记含契约版本");
          assertEquals("1", mark.get("contract-count"), "实例标记含契约数量（极简标记）");
        });
  }

  @Test
  void shouldNotRegisterInstanceWhenMetadataPublishFails() {
    runner.withUserConfiguration(EchoImplConfig.class, FailingPublisherConfig.class,
            RecordingRegistrarConfig.class)
        .run(context -> {
          DefaultApiContractOrchestrator orchestrator =
              context.getBean(DefaultApiContractOrchestrator.class);
          RecordingRegistrar registrar = context.getBean(RecordingRegistrar.class);

          assertThrows(MetaPublishException.class, () -> orchestrator.run(null),
              "元数据发布失败必须异常传播中断启动（ApplicationRunner 异常即启动失败）");

          assertTrue(registrar.marks.isEmpty(),
              "发布顺序不变量：元数据失败不得注册实例（无『实例在而契约缺』中间态）");
        });
  }

  @Test
  void shouldAbortWhenInstanceRegistrationFails() {
    runner.withUserConfiguration(EchoImplConfig.class, RecordingPublisherConfig.class,
            FailingRegistrarConfig.class)
        .run(context -> {
          DefaultApiContractOrchestrator orchestrator =
              context.getBean(DefaultApiContractOrchestrator.class);

          assertThrows(IllegalStateException.class, () -> orchestrator.run(null),
              "实例注册失败必须异常传播中断启动");
        });
  }

  @Test
  void shouldStartNormallyWithoutPublishingWhenWhitelistEmpty() {
    runner.withPropertyValues("api-contract.export.location=absent-whitelist-for-test.txt")
        .withUserConfiguration(RecordingPublisherConfig.class, RecordingRegistrarConfig.class)
        .run(context -> {
          org.assertj.core.api.Assertions.assertThat(context)
              .hasSingleBean(ContractRpcHandler.class)
              .hasSingleBean(DefaultApiContractOrchestrator.class);

          DefaultApiContractOrchestrator orchestrator =
              context.getBean(DefaultApiContractOrchestrator.class);
          orchestrator.run(null);

          RecordingPublisher publisher = context.getBean(RecordingPublisher.class);
          RecordingRegistrar registrar = context.getBean(RecordingRegistrar.class);
          assertTrue(publisher.bundles.isEmpty(), "空白名单：不发布契约");
          assertTrue(registrar.marks.isEmpty(), "空白名单：不注册对外入口");
        });
  }

  /**
   * 向后兼容保护：空白名单时不得用空表覆盖业务自定义 handler 的既有内容
   * （it fixture 等手工构建注册表的旧形态在引入 starter 后行为不变）。
   */
  @Test
  void shouldKeepUserHandlerUntouchedWhenWhitelistEmpty() {
    runner.withPropertyValues("api-contract.export.location=absent-whitelist-for-test.txt")
        .withUserConfiguration(PresetHandlerConfig.class)
        .run(context -> {
          RpcResponse response =
              context.getBean(ContractRpcHandler.class).handle("echo.call", "[42, \"jiz\"]");
          assertEquals(RpcResponse.CODE_OK, response.code(),
              "空白名单时内置编排不得清空业务自定义 handler 既有内容");
          assertEquals("echo:preset:42", response.data());
        });
  }

  /** 业务自定义 handler（预填充注册表，旧手抄形态等价物）。 */
  @Configuration
  static class PresetHandlerConfig {

    @Bean
    ContractRpcHandler customHandler() {
      Map<String, com.apicontract.api.meta.ContractMeta> metas = new java.util.HashMap<>();
      Map<String, com.apicontract.provider.invoke.ContractInvoker> invokers =
          new java.util.HashMap<>();
      for (com.apicontract.provider.scan.ContractBinding binding :
          com.apicontract.provider.scan.ContractScanner.scanInterface(
              com.apicontract.testapi.EchoContract.class,
              (com.apicontract.testapi.EchoContract) (id, name) -> "echo:preset:" + id,
              com.apicontract.provider.scan.ScanOptions.of("preset"),
              message -> {
              })) {
        metas.put(binding.meta().contractName(), binding.meta());
        invokers.put(binding.meta().contractName(),
            com.apicontract.provider.invoke.ContractInvokerFactory.create(
                binding, com.apicontract.provider.invoke.InvokerMode.REFLECTION));
      }
      return new ContractRpcHandler(
          com.apicontract.provider.invoke.ContractInvokerRegistry.of(invokers), metas,
          new com.fasterxml.jackson.databind.ObjectMapper(), 1024 * 1024);
    }
  }

  /** 容器内契约实现 Bean（第③步：实现接口并声明为普通 Bean）。 */
  @Configuration
  static class EchoImplConfig {

    @Bean
    EchoContract echoContractImpl() {
      return (id, name) -> "echo:orchestrated:" + id;
    }
  }

  @Configuration
  static class RecordingPublisherConfig {

    @Bean
    ContractMetaPublisher contractMetaPublisher() {
      return new RecordingPublisher();
    }
  }

  @Configuration
  static class FailingPublisherConfig {

    @Bean
    ContractMetaPublisher contractMetaPublisher() {
      return bundle -> {
        throw new MetaPublishException("模拟配置中心不可达: " + bundle.serviceName(),
            new RuntimeException("simulated-config-center-unavailable"));
      };
    }
  }

  @Configuration
  static class RecordingRegistrarConfig {

    @Bean
    InstanceRegistrar instanceRegistrar() {
      return new RecordingRegistrar();
    }
  }

  @Configuration
  static class FailingRegistrarConfig {

    @Bean
    InstanceRegistrar instanceRegistrar() {
      return mark -> {
        throw new IllegalStateException("模拟实例注册失败");
      };
    }
  }

  static final class RecordingPublisher implements ContractMetaPublisher {

    final List<ContractMetaBundle> bundles = new ArrayList<>();

    @Override
    public void publish(ContractMetaBundle bundle) {
      bundles.add(bundle);
    }
  }

  static final class RecordingRegistrar implements InstanceRegistrar {

    final List<Map<String, String>> marks = new ArrayList<>();

    @Override
    public void register(Map<String, String> instanceMark) {
      marks.add(instanceMark);
    }
  }
}
