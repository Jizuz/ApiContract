package com.apicontract.provider.publish;

import java.util.Map;

/**
 * 实例注册 SPI：发布成功后由装配层执行注册（注册中心可用性在此体现，
 * 失败同样中断启动）。
 */
@FunctionalInterface
public interface InstanceRegistrar {

  /** 注册实例并写入极简标记（contract-version、contract-count）。 */
  void register(Map<String, String> instanceMark);

  /**
   * 反注册实例（应用关闭时优雅下线，避免「实例在而契约缺」中间态窗口）。
   *
   * <p>默认空实现（无需反注册的注册中心/测试替身零成本兼容）；
   * 实现应容忍重复调用（幂等）。
   */
  default void deregister() {
  }
}
