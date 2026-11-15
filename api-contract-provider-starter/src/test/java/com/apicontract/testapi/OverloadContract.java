package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 扫描测试：契约方法存在重载（CONTRACT_OVERLOAD_FORBIDDEN，扫描阶段拒绝）。 */
public interface OverloadContract {

  @Contract("order.update")
  void update(String id);

  void update(String id, int times);
}
