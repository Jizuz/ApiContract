package com.apicontract.gateway.cluster;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * 失败分级（design：failover 仅框架级异常重试）：网络/连接/超时等
 * 远程框架级失败可换实例重试；业务级失败（参数错误、Provider 业务异常、
 * 限流拒绝等）重试无意义，直接失败。
 */
public final class FrameworkFailures {

  private static final int MAX_CAUSE_DEPTH = 8;

  /** 是否框架级失败（可重试换实例）。 */
  public static boolean retryable(Throwable failure) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof IOException
          || current instanceof TimeoutException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private FrameworkFailures() {
  }
}
