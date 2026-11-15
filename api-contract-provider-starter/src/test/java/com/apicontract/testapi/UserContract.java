package com.apicontract.testapi;

import com.apicontract.api.Contract;

/** 扫描测试：一个标注方法 + 一个未标注方法（仅导出标注方法）。 */
public interface UserContract {

  @Contract("apollo.getuser")
  UserDTO getUser(Long userId);

  UserDTO getUserQuiet(Long userId);
}
