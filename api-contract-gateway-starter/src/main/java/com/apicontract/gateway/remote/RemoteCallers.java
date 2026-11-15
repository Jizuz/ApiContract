package com.apicontract.gateway.remote;

import com.apicontract.gateway.ContractRemoteCaller;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * 远程调用器工厂（design 五级开关之异步客户端：依赖引入与否）：
 * 类路径存在 Reactor Netty → 异步实现；未引入 → 自动降级同步实现并告警，
 * 功能不缺失（移除依赖即回退，属灰度回退开关）。
 */
public final class RemoteCallers {

  /** Reactor Netty 客户端类名（依赖探测目标）。 */
  static final String REACTOR_HTTP_CLIENT_CLASS =
      "reactor.netty.http.client.HttpClient";

  private static final Logger logger = System.getLogger("api-contract.gateway");

  /** 类路径依赖探测（可注入，测试模拟"未引入异步依赖"场景）。 */
  public interface DependencyProbe {

    boolean isPresent(String className);
  }

  /** 默认探测：当前类加载器可见性。 */
  public static final DependencyProbe CLASSPATH_PROBE = className -> {
    try {
      Class.forName(className, false, RemoteCallers.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException absent) {
      return false;
    }
  };

  /** 工厂选择结果：调用器 + 是否为降级同步（告警观测位）。 */
  public record Selection(ContractRemoteCaller caller, boolean degraded) {
  }

  public static Selection select(ConnectionPoolSettings pool,
      TimeoutSettings timeouts) {
    return select(pool, timeouts, CLASSPATH_PROBE);
  }

  public static Selection select(ConnectionPoolSettings pool,
      TimeoutSettings timeouts, DependencyProbe probe) {
    if (probe.isPresent(REACTOR_HTTP_CLIENT_CLASS)) {
      return new Selection(new ReactorNettyRemoteCaller(pool, timeouts), false);
    }
    logger.log(Level.WARNING,
        "告警：未引入 reactor-netty 依赖，网关远程调用降级同步实现"
            + "（吞吐受限）；引入依赖后自动恢复异步");
    return new Selection(new SyncHttpRemoteCaller(timeouts), true);
  }

  private RemoteCallers() {
  }
}
