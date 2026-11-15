package com.apicontract.it;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.provider.invoke.ContractInvoker;
import com.apicontract.provider.invoke.ContractInvokerFactory;
import com.apicontract.provider.invoke.ContractInvokerRegistry;
import com.apicontract.provider.invoke.InvokerMode;
import com.apicontract.provider.publish.ContractMetaBundle;
import com.apicontract.provider.publish.ContractPublishPipeline;
import com.apicontract.provider.scan.ContractBinding;
import com.apicontract.provider.scan.ContractScanner;
import com.apicontract.provider.scan.ScanOptions;
import com.apicontract.provider.web.ContractRpcController;
import com.apicontract.provider.web.ContractRpcHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 进程内 Provider 应用（任务25）：Spring Boot web 起真实 HTTP 入口
 * {@code /_contract/rpc}（复用 starter 的 ContractRpcController/Handler），
 * 契约调用器经 ContractScanner + 字节码生成器装配；
 * 发布经 ContractPublishPipeline（先元数据后实例，顺序不变量）。
 */
public final class ProviderAppFixture {

  /**
   * 进程内应用配置：声明泛化入口 Bean（starter 自动配置仅提供缺省 Handler，
   * Controller 由使用方显式装配——此处测试自有 wire）。
   *
   * <p>多实例共存（任务26 压测：基线/优化两组 Provider 同 JVM）：
   * 静态注册表按 app-key 隔离元数据与调用器集合。
   */
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class App {

    @Bean
    ContractRpcHandler contractRpcHandler(ObjectMapper mapper,
        org.springframework.core.env.Environment env) {
      String key = env.getRequiredProperty("api-contract.it.app-key");
      return new ContractRpcHandler(
          ContractInvokerRegistry.of(AppMetaHolder.invokersOf(key)),
          AppMetaHolder.metasOf(key), mapper, 1024 * 1024);
    }

    @Bean
    ContractRpcController contractRpcController(ContractRpcHandler handler) {
      return new ContractRpcController(handler);
    }
  }

  /** Handler Bean 与测试装配之间的静态共享（按 app-key 隔离，支持多实例）。 */
  static final class AppMetaHolder {

    static final java.util.Map<String, java.util.Map<String, ContractMeta>> METAS_BY_APP =
        java.util.Collections.synchronizedMap(new HashMap<>());
    static final java.util.Map<String, java.util.Map<String, ContractInvoker>> INVOKERS_BY_APP =
        java.util.Collections.synchronizedMap(new HashMap<>());

    static Map<String, ContractMeta> metasOf(String key) {
      return METAS_BY_APP.get(key);
    }

    static Map<String, ContractInvoker> invokersOf(String key) {
      return INVOKERS_BY_APP.get(key);
    }
  }

  public final String serviceName;
  public final String version;
  public final List<ContractBinding> bindings = new ArrayList<>();
  public final Map<String, ContractMeta> metas = new LinkedHashMap<>();
  public final Map<String, ContractInvoker> invokers = new HashMap<>();

  private static final java.util.concurrent.atomic.AtomicInteger INSTANCE_SEQ =
      new java.util.concurrent.atomic.AtomicInteger();

  private final int instanceSeq = INSTANCE_SEQ.incrementAndGet();
  private ConfigurableApplicationContext context;
  private ContractMetaBundle publishedBundle;
  private AutoCloseable instanceHandle;

  public ProviderAppFixture(String serviceName, String version) {
    this.serviceName = serviceName;
    this.version = version;
  }

  /** 声明一个契约实现（扫描接口 → 绑定 → 字节码调用器，默认模式）。 */
  public ProviderAppFixture contract(Class<?> iface, Object impl) {
    return contract(iface, impl, InvokerMode.BYTECODE);
  }

  /** 声明一个契约实现并指定调用器模式（压测基线组=反射 / 优化组=字节码）。 */
  public ProviderAppFixture contract(Class<?> iface, Object impl,
      InvokerMode mode) {
    for (ContractBinding binding : ContractScanner.scanInterface(
        iface, impl,
        new ScanOptions(serviceName, version, Map.of()), line -> { })) {
      bindings.add(binding);
      invokers.put(binding.meta().contractName(),
          ContractInvokerFactory.create(binding, mode));
      metas.put(binding.meta().contractName(), binding.meta());
    }
    return this;
  }

  /** 启动 HTTP 应用（random port）。 */
  public ProviderAppFixture start() {
    String key = appKey();
    AppMetaHolder.METAS_BY_APP.put(key, new HashMap<>(metas));
    AppMetaHolder.INVOKERS_BY_APP.put(key, new HashMap<>(invokers));
    context = new SpringApplicationBuilder(App.class)
        .properties("server.port=0", "spring.main.banner-mode=off",
            "api-contract.rpc.enabled=true",
            "api-contract.it.app-key=" + key)
        .run();
    return this;
  }

  /** 本实例在静态注册表中的隔离键。 */
  private String appKey() {
    return serviceName + "@" + version + "#" + instanceSeq;
  }

  public int port() {
    return ((WebServerApplicationContext) context).getWebServer().getPort();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + port();
  }

  /** 发布契约元数据 + 注册实例（发布顺序不变量：先元数据后实例）。 */
  public void publish(NacosBridge bridge) throws Exception {
    ContractMetaBundle bundle = new ContractMetaBundle(
        serviceName, version, List.copyOf(metas.values()),
        aggregateIndexOf(metas.keySet()));
    new ContractPublishPipeline(bundle,
        new com.apicontract.provider.publish.NacosConfigMetaPublisher(
            bridge.configCarrierWriter(), new ObjectMapper()),
        mark -> {
          try {
            instanceHandle = bridge.registerInstance(
                serviceName, "127.0.0.1", port(), mark);
          } catch (Exception failure) {
            throw new RuntimeException(failure);
          }
        }).run();
    publishedBundle = bundle;
  }

  /** 已发布 bundle（断言用）。 */
  public ContractMetaBundle publishedBundle() {
    return publishedBundle;
  }

  /** 聚合索引：mt → 本服务名（发布条目与 design 配置中心结构一致）。 */
  private Map<String, String> aggregateIndexOf(
      java.util.Set<String> contractNames) {
    Map<String, String> index = new java.util.LinkedHashMap<>();
    contractNames.forEach(name -> index.put(name, serviceName));
    return index;
  }

  /** 反注册实例（模拟下线）。 */
  public void deregister(NacosBridge bridge) throws Exception {
    if (instanceHandle != null) {
      instanceHandle.close();
    }
  }

  public void stop() {
    if (context != null) {
      context.close();
    }
    String key = appKey();
    AppMetaHolder.METAS_BY_APP.remove(key);
    AppMetaHolder.INVOKERS_BY_APP.remove(key);
  }
}
