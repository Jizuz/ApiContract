package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 扫描测试：多参数契约（参数名/类型顺序提取）。 */
public interface OrderContract {

  @Contract("order.create")
  String createOrder(String orderId, long amount);
}
