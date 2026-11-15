package com.apicontract.it.itapi;

import com.apicontract.api.Contract;

/**
 * 集成测试 demo 契约：回声（成功链路 + 基础类型转换）。
 */
public interface ItEcho {

  /** 两参数回声：网关 POST {"id":1,"name":"jiz"} 应原样往返。 */
  @Contract("it.echo.call")
  String call(Long id, String name);
}
