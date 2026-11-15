package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 扫描测试：与 UserContract 重复的契约标识（CONTRACT_CONFLICT 全局判重）。 */
public interface DupContract {

  @Contract("apollo.getuser")
  String duplicate(Long userId);
}
