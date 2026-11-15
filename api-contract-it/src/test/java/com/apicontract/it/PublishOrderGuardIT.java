package com.apicontract.it;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.it.itapi.ItEcho;
import com.apicontract.provider.publish.MetaPublishException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务25 发布顺序破坏集成测试（spec contract-provider-register 顺序不变量）：
 * 配置中心不可用时，元数据发布失败 → 启动中断，实例<b>不注册</b>——
 * 杜绝“实例已上线但契约缺失”的中间态。
 *
 * <p>宕机模拟采用死地址桥（127.0.0.1:1 连接即拒），不暂停共享服务器，
 * 保证与其他 IT 隔离。
 */
class PublishOrderGuardIT {

  private static final String SERVICE = "it-order-guard-service";

  @Test
  void shouldAbortStartupAndSkipInstanceRegistrationWhenConfigCenterDown()
      throws Exception {
    NacosServerContainer.serverAddr();
    ProviderAppFixture provider = new ProviderAppFixture(SERVICE, "1.0.0")
        .contract(ItEcho.class, (ItEcho) (id, name) -> "echo:" + id + ":" + name)
        .start();
    try {
      NacosBridge dead = new NacosBridge("127.0.0.1:1");
      MetaPublishException failure = assertThrows(MetaPublishException.class,
          () -> provider.publish(dead),
          "配置中心不可用时应中断启动（元数据发布失败向上传播）");
      assertTrue(failure.getMessage().contains("CONFIG_CENTER_UNAVAILABLE"),
          "应收敛 CONFIG_CENTER_UNAVAILABLE: " + failure.getMessage());
      NacosBridge live = new NacosBridge(NacosServerContainer.serverAddr());
      List<String> instances = live.discoveryFetcher().fetch(SERVICE);
      assertTrue(instances.isEmpty(),
          "元数据发布失败的实例绝不能注册进注册中心: " + instances);
    } finally {
      provider.stop();
    }
  }
}
