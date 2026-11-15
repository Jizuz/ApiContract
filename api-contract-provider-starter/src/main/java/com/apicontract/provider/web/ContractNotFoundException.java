package com.apicontract.provider.web;

/**
 * 契约不存在（错误码 CONTRACT_NOT_FOUND）：未导出、未标注或未注册；
 * 直接明确返回，不进入任何兜底调用。
 */
public class ContractNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ContractNotFoundException(String contractName) {
    super("契约不存在(CONTRACT_NOT_FOUND): " + contractName + "（未导出、未标注或未注册）");
  }
}
