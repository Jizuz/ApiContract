package com.apicontract.testapi;

/** 扫描测试用实现（普通 Bean，不承担契约声明职责）。 */
public class UserContractImpl implements UserContract {

  @Override
  public UserDTO getUser(Long userId) {
    return new UserDTO("user-" + userId);
  }

  @Override
  public UserDTO getUserQuiet(Long userId) {
    return new UserDTO("quiet-" + userId);
  }
}
