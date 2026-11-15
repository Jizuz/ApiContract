package com.apicontract.provider.export;

/**
 * 白名单条目类不存在或加载失败（错误码 EXPORT_LOAD_FAILED）。
 *
 * <p>规格（contract-definition）：按快速失败配置决定中断启动（默认）或告警跳过。
 */
public class ExportLoadFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ExportLoadFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
