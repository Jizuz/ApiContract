package com.apicontract.provider.autoconfigure;

import com.apicontract.provider.invoke.ContractInvokerRegistry;
import com.apicontract.provider.orchestration.ApiContractOrchestrator;
import com.apicontract.provider.orchestration.DefaultApiContractOrchestrator;
import com.apicontract.provider.orchestration.OrchestrationCoverage;
import com.apicontract.provider.publish.ConfigCarrierWriter;
import com.apicontract.provider.publish.ContractMetaPublisher;
import com.apicontract.provider.publish.InstanceMetadataWriter;
import com.apicontract.provider.publish.InstanceRegistrar;
import com.apicontract.provider.publish.MetaPublishers;
import com.apicontract.provider.publish.PublishMode;
import com.apicontract.provider.web.ContractRpcController;
import com.apicontract.provider.web.ContractRpcHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider Starter 自动配置（Spring Boot 3）：
 * 经 META-INF/spring/...AutoConfiguration.imports 导入；
 * 总开关 {@code api-contract.rpc.enabled=false} 时整体不装配；
 * 全部业务 Bean {@code @ConditionalOnMissingBean}（业务可整体替换）；
 * 注册中心/配置中心客户端端口 optional，缺失时发布实现降级 logOnly 并告警。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiContractProperties.class)
@ConditionalOnProperty(name = "api-contract.rpc.enabled", havingValue = "true",
    matchIfMissing = true)
public class ApiContractAutoConfiguration {

  private static final System.Logger COVERAGE_LOG = System.getLogger("api-contract");

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper apiContractObjectMapper() {
    return new ObjectMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  public ContractMetaPublisher apiContractMetaPublisher(ApiContractProperties properties,
      ObjectProvider<ConfigCarrierWriter> configCarrier,
      ObjectProvider<InstanceMetadataWriter> instanceWriter, ObjectMapper mapper) {
    PublishMode mode = properties.getMeta().getPublishMode();
    return MetaPublishers.select(mode,
        configCarrier.getIfAvailable(), instanceWriter.getIfAvailable(),
        mapper, line -> { });
  }

  /**
   * 泛化入口处理器。
   *
   * <p>调用器缓存与契约元数据在装配期由启动编排（白名单扫描 → 调用器生成 →
   * 注册缓存 → 发布）填充；本 Bean 仅保证缺省可用，可被业务整体替换。
   */
  @Bean
  @ConditionalOnMissingBean
  public ContractRpcHandler apiContractRpcHandler(ObjectProvider<ContractInvokerRegistry> registry,
      ObjectMapper mapper) {
    return new ContractRpcHandler(registry.getIfAvailable(
        () -> ContractInvokerRegistry.of(Map.of())), Map.of(), mapper, 1024 * 1024);
  }

  /**
   * 内置启动编排器（design 方案B）：单例就绪后白名单→扫描→调用器生成→handler 后填充；
   * Runner 阶段先元数据后实例注册（任一失败中断启动）；容器关闭优雅反注册。
   *
   * <p>互斥防双发布：容器存在任意 {@link ApiContractOrchestrator} Bean（含旧手抄编排
   * 实现该接口）即不装配；{@code api-contract.orchestration.enabled=false} 亦可关闭。
   */
  @Bean
  @ConditionalOnMissingBean(ApiContractOrchestrator.class)
  @ConditionalOnProperty(name = "api-contract.orchestration.enabled", havingValue = "true",
      matchIfMissing = true)
  public DefaultApiContractOrchestrator apiContractOrchestrator(
      ListableBeanFactory beanFactory, ApiContractProperties properties,
      ContractRpcHandler handler, ContractMetaPublisher publisher,
      ObjectProvider<InstanceRegistrar> registrar,
      @Value("${spring.application.name:application}") String serviceName) {
    return new DefaultApiContractOrchestrator(beanFactory, properties, handler, publisher,
        registrar, serviceName);
  }

  /**
   * 编排装配结果明示日志（单例就绪后输出，业务可巡检确认无双发布）。
   */
  @Bean
  public SmartInitializingSingleton apiContractOrchestrationCoverageNotice(
      ObjectProvider<ApiContractOrchestrator> orchestrator, ApiContractProperties properties) {
    return () -> {
      ApiContractOrchestrator present = orchestrator.getIfAvailable();
      OrchestrationCoverage.State state = !properties.getOrchestration().isEnabled()
          ? OrchestrationCoverage.State.DISABLED_BY_PROPERTY
          : present instanceof DefaultApiContractOrchestrator || present == null
              ? OrchestrationCoverage.State.ENABLED_DEFAULT
              : OrchestrationCoverage.State.OVERRIDDEN_BY_USER;
      OrchestrationCoverage.report(state, present == null ? null : present.getClass(),
          line -> COVERAGE_LOG.log(System.Logger.Level.INFO, line));
    };
  }

  /** 统一泛化入口（Web 环境自动装配；业务可定义同名 Bean 替换）。
   * 路径经 {@code api-contract.entry-path} 配置避让业务路径。
   */
  @Bean
  @ConditionalOnWebApplication
  @ConditionalOnClass(ContractRpcController.class)
  @ConditionalOnMissingBean
  public ContractRpcController apiContractRpcController(ContractRpcHandler handler) {
    return new ContractRpcController(handler);
  }
}
