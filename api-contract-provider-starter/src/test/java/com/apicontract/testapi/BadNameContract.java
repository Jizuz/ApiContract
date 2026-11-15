package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 扫描测试：非法契约标识命名（大写，命名规范校验拒绝）。 */
public interface BadNameContract {

  @Contract("Apollo.GetUser")
  String bad();
}
