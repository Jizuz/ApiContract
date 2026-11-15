package com.apicontract.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.GatewayResponse;
import com.apicontract.it.itapi.ItEcho;
import com.apicontract.provider.scan.ContractScanner;
import com.apicontract.provider.scan.ScanOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 任务9（红-绿）测试：零编排代码端到端（规格 contract-starter-access 正向用例 +
 * contract-nacos-adapter 主流程）——业务只写三步（接口/白名单/实现）+配置，
 * 内置编排经官方 adapter 完成：元数据→配置中心、实例→注册中心、
 * 网关订阅后 {@code ?mt=} 往返、优雅下线无残留。
 *
 * <p>本机 8848 与 Docker Nacos 容器均不可达时整类跳过并明示（策略沿用 it 模块）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZeroOrchestrationAccessIT {

  private static final String SERVICE = "it-zero-orchestration";
  private static final String DATA_ID = SERVICE + "-contract-meta";

  private static ConfigurableApplicationContext provider;
  private static NacosBridge bridge;
  private static ContractGatewayHandler gatewayHandler;
  private static String echoContract;

  @BeforeAll
  static void setUpOnce() throws Exception {
    String serverAddr;
    try {
      serverAddr = NacosServerContainer.serverAddr();
    } catch (Throwable unavailable) {
      Assumptions.assumeTrue(false,
          "本机 8848 与 Docker Nacos 容器均不可达，跳过零编排端到端: "
              + unavailable.getMessage());
      return;
    }
    bridge = new NacosBridge(serverAddr);
    bridge.removeContractConfig(SERVICE, null);

    // 零编排代码应用：仅契约接口实现 Bean + 白名单 + 配置（无任何编排/发布/桥接代码）
    provider = new SpringApplicationBuilder(ZeroCodeApp.class)
        .properties("server.port=0", "spring.main.banner-mode=off",
            "spring.application.name=" + SERVICE,
            "api-contract.meta.publish-mode=nacosConfig",
            "api-contract.nacos.server-addr=" + serverAddr,
            "api-contract.export.location=zero-orchestration-export.txt")
        .run();

    echoContract = ContractScanner.scanInterface(
            ItEcho.class, (ItEcho) (id, name) -> "echo:" + id + ":" + name,
            ScanOptions.of(SERVICE), message -> {
            })
        .get(0).meta().contractName();

    GatewayFixture gateway = new GatewayFixture(bridge);
    gateway.subscribeService(SERVICE);
    gateway.refreshInstances(SERVICE);
    gatewayHandler = gateway.handler();
    awaitInstanceDiscovered(gateway);
  }

  /** 零编排代码的应用配置：三步接入之外零代码（starter 自动装配 + adapter 自动装配）。 */
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ZeroCodeApp {

    @Bean
    ItEcho itEchoImpl() {
      return (id, name) -> "echo:" + id + ":" + name;
    }
  }

  @Test
  @Order(1)
  void shouldPublishContractMetadataToConfigCenter() throws Exception {
    String content = bridge.configService().getConfig(DATA_ID, "CONTRACT", 3_000);
    assertNotNull(content, "内置编排应已发布契约元数据到配置中心");
    assertTrue(content.contains("\"serviceName\":\"" + SERVICE + "\""),
        "发布内容含服务名: " + content);
    assertTrue(content.contains(echoContract), "发布内容含契约条目: " + content);
  }

  @Test
  @Order(2)
  void shouldRegisterInstanceWithMinimalMark() throws Exception {
    List<com.alibaba.nacos.api.naming.pojo.Instance> instances =
        bridge.namingService().selectInstances(SERVICE, true);
    assertEquals(1, instances.size(), "内置编排应已注册实例");
    Map<String, String> metadata = instances.get(0).getMetadata();
    assertEquals("1.0.0", metadata.get("contract-version"), "实例标记含契约版本（极简）");
    assertEquals("1", metadata.get("contract-count"), "实例标记含契约数量（极简）");
  }

  @Test
  @Order(3)
  void shouldRoundTripThroughGatewayAfterSubscription() {
    GatewayResponse response = gatewayHandler.handle(
        echoContract, "POST", Map.of(), "{\"id\":9,\"name\":\"zero\"}");
    assertEquals("OK", response.code(), "网关订阅后 ?mt= 调用应成功: " + response);
    assertEquals("echo:9:zero", response.data().asText(), "参数与结果原样往返");
  }

  @Test
  @Order(4)
  void shouldDeregisterGracefullyOnContextClose() throws Exception {
    provider.close();

    long deadline = System.currentTimeMillis() + 15_000;
    while (!bridge.namingService().selectInstances(SERVICE, true).isEmpty()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("15s 内实例未从 Nacos 摘除（优雅下线无残留）");
      }
      Thread.sleep(500);
    }
    assertTrue(bridge.namingService().selectInstances(SERVICE, true).isEmpty(),
        "应用关闭后 Nacos 无残留实例（编排器 destroy → adapter 反注册）");
  }

  private static void awaitInstanceDiscovered(GatewayFixture gateway) {
    long deadline = System.currentTimeMillis() + 15_000;
    while (gateway.instances.resolve(SERVICE).isEmpty()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("15s 内未从 Nacos 发现零编排 provider 实例");
      }
      gateway.refreshInstances(SERVICE);
      try {
        Thread.sleep(500);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(failure);
      }
    }
  }

  @AfterAll
  static void tearDownOnce() {
    if (provider != null) {
      provider.close();
    }
  }
}
