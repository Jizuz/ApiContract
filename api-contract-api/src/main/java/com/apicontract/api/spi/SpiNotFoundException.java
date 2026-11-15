package com.apicontract.api.spi;

/**
 * 配置指定的实现名不存在（错误码 SPI_IMPL_NOT_FOUND）：启动失败并明确指明扩展点与名称。
 */
public class SpiNotFoundException extends IllegalStateException {

  public static final String CODE = "SPI_IMPL_NOT_FOUND";

  public SpiNotFoundException(String message) {
    super(message);
  }

  public String getCode() {
    return CODE;
  }
}
