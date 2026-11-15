package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 扫描测试：default 方法标注契约（方法约束：非 default，跳过并告警）。 */
public interface DefaultMethodContract {

  @Contract("default.call")
  default String call() {
    return "default";
  }
}
