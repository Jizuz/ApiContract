package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 扫描测试：签名一致性校验（实现类缺少对应方法时 SIGNATURE_MISMATCH 跳过并告警）。 */
public interface MismatchContract {

  @Contract("mismatch.call")
  String call(String input);
}
