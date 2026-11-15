package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 泛化入口测试：无参契约（参数数组为空，忽略请求体）。 */
public interface NoParamContract {

  @Contract("noparam.ping")
  String ping();
}
