package com.apicontract.provider.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.apicontract.provider.publish.ContractMetaPublisher;
import com.apicontract.provider.publish.LogOnlyMetaPublisher;
import com.apicontract.provider.web.ContractRpcHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 任务11（红-绿）测试：Spring Boot 3 自动装配（要点：自动配置导入清单、
 * 全部 Bean @ConditionalOnMissingBean、总开关关闭不装配、注册中心客户端 optional）。
 */
class ApiContractAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ApiContractAutoConfiguration.class));

  @Test
  void shouldAssembleCoreBeansWhenEnabledByDefault() {
    runner.run(context -> {
      assertThat(context).hasSingleBean(ContractRpcHandler.class);
      assertThat(context).hasSingleBean(ContractMetaPublisher.class);
      assertThat(context).hasSingleBean(ApiContractProperties.class);
      assertThat(context.getBean(ContractMetaPublisher.class))
          .isInstanceOf(LogOnlyMetaPublisher.class);
    });
  }

  @Test
  void shouldNotAssembleAnythingWhenMasterSwitchOff() {
    runner.withPropertyValues("api-contract.rpc.enabled=false").run(context -> {
      assertThat(context).doesNotHaveBean(ContractRpcHandler.class);
      assertThat(context).doesNotHaveBean(ContractMetaPublisher.class);
      assertThat(context).doesNotHaveBean(ApiContractProperties.class);
    });
  }

  @Test
  void shouldBackOffWhenUserDefinesCustomPublisher() {
    runner.withUserConfiguration(CustomPublisherConfig.class).run(context -> {
      assertThat(context).hasSingleBean(ContractMetaPublisher.class);
      assertThat(context.getBean(ContractMetaPublisher.class))
          .isSameAs(context.getBean("customPublisher", ContractMetaPublisher.class));
    });
  }

  @Test
  void shouldBackOffWhenUserDefinesCustomHandler() {
    runner.withUserConfiguration(CustomHandlerConfig.class).run(context -> {
      assertThat(context).hasSingleBean(ContractRpcHandler.class);
      assertThat(context.getBean(ContractRpcHandler.class))
          .isSameAs(context.getBean("customHandler", ContractRpcHandler.class));
    });
  }

  @Test
  void shouldDegradeToLogOnlyForNacosConfigModeWithoutClient() {
    runner.withPropertyValues("api-contract.meta.publish-mode=nacosConfig").run(context -> {
      assertThat(context).hasSingleBean(ContractMetaPublisher.class);
      assertThat(context.getBean(ContractMetaPublisher.class))
          .isInstanceOf(LogOnlyMetaPublisher.class);
    });
  }

  @Configuration
  static class CustomPublisherConfig {

    @Bean
    ContractMetaPublisher customPublisher() {
      return bundle -> { };
    }
  }

  @Configuration
  static class CustomHandlerConfig {

    @Bean
    ContractRpcHandler customHandler() {
      return new ContractRpcHandler(null, java.util.Map.of(), null, 1024);
    }
  }
}
