package com.apicontract.it;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Nacos 服务器供给（任务25 全链路集成测试）：
 * 优先复用宿主机已在 8848 运行的 Nacos（健康探测通过即直连，避免与本地常驻实例端口冲突）；
 * 否则回退 Testcontainers v2.2.3 standalone——2.x 客户端使用 gRPC
 * （端口 = 主端口 +1000/+1001），故 8848/9848/9849 必须与宿主机同号绑定，
 * 保证客户端按 serverAddr 推导的 gRPC 端口可达。
 *
 * <p>全部 IT 共享单例地址（容器模式启动一次约 30s，避免逐类冷启）。
 */
public final class NacosServerContainer {

  private static final GenericContainer<?> NACOS = new GenericContainer<>(
      DockerImageName.parse("nacos/nacos-server:v2.2.3"))
      .withEnv("MODE", "standalone")
      .withEnv("NACOS_AUTH_ENABLE", "false")
      // gRPC 偏移端口约束：宿主机端口必须与容器端口一致；
      // arm64 宿主预拉 amd64 镜像（官方无 arm64 manifest），create 显式指定平台
      .withCreateContainerCmdModifier(cmd -> cmd
          .withPlatform("linux/amd64")
          .withPortBindings(
              com.github.dockerjava.api.model.PortBinding.parse("8848:8848"),
              com.github.dockerjava.api.model.PortBinding.parse("9848:9848"),
              com.github.dockerjava.api.model.PortBinding.parse("9849:9849")))
      .withExposedPorts(8848, 9848, 9849)
      .waitingFor(new HttpWaitStrategy()
          .forPort(8848)
          .forPath("/nacos/v1/console/health/readiness")
          .withStartupTimeout(Duration.ofMinutes(4)));

  private static volatile String resolvedAddr;

  private NacosServerContainer() {
  }

  /** 就绪地址（幂等）：本机 8848 健康 → 直连复用；否则启动容器。 */
  public static synchronized String serverAddr() {
    if (resolvedAddr == null) {
      resolvedAddr = probeLocal() ? "127.0.0.1:8848" : startContainer();
    }
    return resolvedAddr;
  }

  private static boolean probeLocal() {
    try {
      HttpResponse<String> response = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(1)).build()
          .send(HttpRequest.newBuilder()
              .uri(URI.create(
                  "http://127.0.0.1:8848/nacos/v1/console/health/readiness"))
              .timeout(Duration.ofSeconds(2)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (Exception unreachable) {
      return false;
    }
  }

  private static String startContainer() {
    NACOS.start();
    return "127.0.0.1:" + NACOS.getMappedPort(8848);
  }
}
