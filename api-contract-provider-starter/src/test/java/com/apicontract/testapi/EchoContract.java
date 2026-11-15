package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 泛化入口测试：基础类型参数契约。 */
public interface EchoContract {

  @Contract("echo.call")
  String call(Long id, String name);
}
