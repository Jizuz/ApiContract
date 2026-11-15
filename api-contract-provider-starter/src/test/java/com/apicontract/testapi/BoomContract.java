package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 调用器测试：目标方法抛业务异常（双模式异常剥离一致性）。 */
public interface BoomContract {

  @Contract("boom.explode")
  String explode(String seed);
}
