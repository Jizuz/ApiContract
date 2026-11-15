package com.apicontract.provider.publish;

/**
 * 契约元数据发布失败（CONFIG_CENTER_UNAVAILABLE 等启动期错误）：
 * 向上传播中断启动，禁止出现"实例已上线但契约缺失"的中间态。
 */
public class MetaPublishException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MetaPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
