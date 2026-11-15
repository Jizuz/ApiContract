package com.apicontract.gateway.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 任务19（红-绿）测试：同步降级实现——功能不缺失、超时同样有界
 * （降级同步不等于无限等待）。
 */
class SyncHttpRemoteCallerTest {

  @Test
  @Timeout(10)
  void shouldRoundTripSuccessfully() throws Exception {
    try (LocalHttpServer provider = LocalHttpServer.start(0)) {
      SyncHttpRemoteCaller caller = new SyncHttpRemoteCaller(TimeoutSettings.DEFAULT);

      String response = caller.call(provider.baseUrl, "echo.call", "[1,\"jiz\"]", 3_000);

      assertTrue(response.contains("\"OK\""), "同步降级往返成功: " + response);
    }
  }

  @Test
  @Timeout(10)
  void shouldFailFastOnOverallTimeout() throws Exception {
    try (LocalHttpServer provider = LocalHttpServer.start(3_000)) {
      SyncHttpRemoteCaller caller = new SyncHttpRemoteCaller(
          new TimeoutSettings(500, 2_000, 3_000));

      long begin = System.nanoTime();
      TimeoutException timeout = assertThrows(TimeoutException.class,
          () -> caller.call(provider.baseUrl, "echo.call", "[]", 300));
      long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;

      assertTrue(elapsedMillis < 1_500,
          "同步实现整体超时快速返回（实际 " + elapsedMillis + "ms）");
      assertTrue(timeout.getMessage() != null && !timeout.getMessage().isBlank());
    }
  }

  @Test
  @Timeout(10)
  void shouldPropagateTraceIdHeaderToProvider() throws Exception {
    try (LocalHttpServer provider = LocalHttpServer.start(0)) {
      SyncHttpRemoteCaller caller = new SyncHttpRemoteCaller(TimeoutSettings.DEFAULT);

      caller.call(provider.baseUrl, "echo.call", "[]", 3_000, "trace-sync");

      assertEquals("trace-sync", provider.lastTraceIdHeader,
          "同步降级实现同样透传 X-Contract-TraceId（降级不丢功能）");
    }
  }
}
