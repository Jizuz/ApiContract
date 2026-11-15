package com.apicontract.testapi;

/** 扫描测试用订单实现。 */
public class OrderContractImpl implements OrderContract {

  @Override
  public String createOrder(String orderId, long amount) {
    return orderId + ":" + amount;
  }
}
