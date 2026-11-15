package com.apicontract.it.itapi;

import com.apicontract.api.Contract;

/**
 * 集成测试 demo 契约：业务异常（INVOKE_ERROR 端到端链路）。
 */
public interface ItBoom {

  /** 恒抛业务异常：provider 收敛为 CONTRACT_INVOKE_ERROR，网关收敛 502 语义。 */
  @Contract("it.boom.explode")
  String explode(String seed);
}
