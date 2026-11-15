package com.apicontract.api.spi;

/**
 * SPI 实现装载或初始化失败（错误码 SPI_LOAD_FAILED）：启动失败，不静默回退到错误状态。
 */
public class SpiLoadFailedException extends IllegalStateException {

  public static final String CODE = "SPI_LOAD_FAILED";

  public SpiLoadFailedException(String message) {
    super(message);
  }

  public SpiLoadFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public String getCode() {
    return CODE;
  }
}
