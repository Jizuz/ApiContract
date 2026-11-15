package com.apicontract.provider.publish;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * 发布自检四段结构化日志（design 一致性策略 6 对账等价物）：
 * export loaded / contract ready / meta published / registry joined。
 *
 * <p>四段日志是上线验收与回归断言依据；日志行结构固定，供脚本化校验。
 */
public final class StartupSelfCheckLog {

  private final Consumer<String> logLine;

  public StartupSelfCheckLog(Consumer<String> logLine) {
    this.logLine = logLine;
  }

  /** 第一段：白名单加载完成（含导出接口数）。 */
  public void exportLoaded(int interfaceCount) {
    logLine.accept("[self-check] stage=export loaded interfaces=" + interfaceCount);
  }

  /** 第二段：契约扫描就绪（含契约数）。 */
  public void contractReady(int contractCount) {
    logLine.accept("[self-check] stage=contract ready contracts=" + contractCount);
  }

  /** 第二段（含契约标识清单，供上线验收比对）。 */
  public void contractReadyWithIds(Collection<String> contractIds) {
    logLine.accept("[self-check] stage=contract ready contracts=" + contractIds.size()
        + " ids=" + contractIds);
  }

  /** 第三段：契约元数据发布完成。 */
  public void metaPublished(String serviceName, String version, int contractCount) {
    logLine.accept("[self-check] stage=meta published service=" + serviceName
        + " version=" + version + " contracts=" + contractCount);
  }

  /** 第四段：实例注册完成（含极简标记）。 */
  public void registryJoined(String serviceName) {
    logLine.accept("[self-check] stage=registry joined service=" + serviceName);
  }
}
