package com.apicontract.provider.publish;

/**
 * 契约标识跨服务冲突（错误码 CONTRACT_CONFLICT）：发布期聚合索引校验失败，
 * 冲突中断启动。
 */
public class ContractConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ContractConflictException(String message) {
    super(message);
  }
}
