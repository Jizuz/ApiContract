package com.apicontract.provider.scan;

/**
 * 多实现未指定唯一实现（错误码 IMPL_AMBIGUOUS）。
 *
 * <p>规格（contract-definition）：一个白名单接口只允许一个实现参与发布，
 * 多实现必须显式指定唯一实现，否则启动失败。
 */
public class ImplAmbiguousException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ImplAmbiguousException(String message) {
    super(message);
  }
}
