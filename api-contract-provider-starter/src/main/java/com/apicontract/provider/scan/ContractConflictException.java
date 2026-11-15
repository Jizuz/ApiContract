package com.apicontract.provider.scan;

/**
 * 契约标识重复（错误码 CONTRACT_CONFLICT）。
 *
 * <p>规格（contract-definition）：契约标识全局唯一，同应用或跨服务重复时
 * 发布校验失败，中断启动。
 */
public class ContractConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ContractConflictException(String message) {
    super(message);
  }
}
