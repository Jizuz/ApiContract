package com.apicontract.provider.scan;

/**
 * 契约方法存在重载（错误码 CONTRACT_OVERLOAD_FORBIDDEN）。
 *
 * <p>规格（contract-definition）：违反契约规范，扫描阶段拒绝并明确报错；
 * 禁止重载是全链路硬约束，直接影响调用器生成与参数分发。
 */
public class ContractOverloadForbiddenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ContractOverloadForbiddenException(String message) {
    super(message);
  }
}
