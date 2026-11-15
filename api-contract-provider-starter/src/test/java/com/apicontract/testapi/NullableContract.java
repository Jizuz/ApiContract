package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 调用器测试：null 返回值透传。 */
public interface NullableContract {

  @Contract("nullable.call")
  String call(String input);
}
