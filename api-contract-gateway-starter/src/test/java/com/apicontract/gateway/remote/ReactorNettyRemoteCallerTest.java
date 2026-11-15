package com.apicontract.gateway.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 任务19（红-绿）测试：Reactor Netty 异步调用器——池耗尽快速失败（有界等待
 * 队列不排队放大）、整体超时契约级生效、正常往返（spec 异常用例/分支场景）。
 */
class ReactorNettyRemoteCallerTest {

  private final ExecutorService executor = Executors.newCachedThreadPool();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  @Timeout(15)
  void shouldRoundTripSuccessfully() throws Exception {
    try (LocalHttpServer provider = LocalHttpServer.start(0)) {
      ReactorNettyRemoteCaller caller = new ReactorNettyRemoteCaller(
          ConnectionPoolSettings.DEFAULT, TimeoutSettings.DEFAULT);

      String response = caller.call(provider.baseUrl, "echo.call", "[1,\"jiz\"]", 3_000);

      assertTrue(response.contains("\"OK\""), "正常往返: " + response);
      assertTrue(response.contains("len=" + "[1,\"jiz\"]".length()),
          "请求体完整送达: " + response);
    }
  }

  @Test
  @Timeout(15)
  void shouldTimeoutOverallOnSlowUpstream() throws Exception {
    try (LocalHttpServer provider = LocalHttpServer.start(3_000)) {
      ReactorNettyRemoteCaller caller = new ReactorNettyRemoteCaller(
          ConnectionPoolSettings.DEFAULT,
          new TimeoutSettings(500, 2_000, 3_000));

      long begin = System.nanoTime();
      TimeoutException timeout = assertThrows(TimeoutException.class,
          () -> caller.call(provider.baseUrl, "echo.call", "[]", 300));

      long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;
      assertTrue(elapsedMillis < 1_500,
          "整体超时 300ms 快速返回而非陪等慢上游（实际 " + elapsedMillis + "ms）");
      assertTrue(timeout.getMessage() != null && !timeout.getMessage().isBlank(),
          "超时异常信息明确");
    }
  }

  @Test
  @Timeout(20)
  void shouldFailFastWhenPoolExhausted() throws Exception {
    ConnectionPoolSettings singleConnection = new ConnectionPoolSettings(
        /* maxConnections= */ 1, /* maxConnectionsPerHost= */ 1,
        /* maxIdleMillis= */ 30_000, /* acquireTimeoutMillis= */ 200,
        /* pendingAcquireMaxCount= */ 1);
    try (LocalHttpServer provider = LocalHttpServer.start(1_500)) {
      ReactorNettyRemoteCaller caller = new ReactorNettyRemoteCaller(
          singleConnection, new TimeoutSettings(500, 5_000, 5_000));

      Future<String> occupying = CompletableFuture.supplyAsync(
          () -> silentCall(caller, provider.baseUrl), executor);
      TimeUnit.MILLISECONDS.sleep(300);

      long begin = System.nanoTime();
      Exception exhausted = assertThrows(Exception.class,
          () -> caller.call(provider.baseUrl, "echo.call", "[]", 3_000));
      long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;

      assertTrue(elapsedMillis < 1_000,
          "池耗尽按 acquire-timeout=200ms 快速失败，不排队放大（实际 "
              + elapsedMillis + "ms）");
      assertTrue(elapsedMillis >= 150,
          "确实经过了有界等待后才失败（实际 " + elapsedMillis + "ms）");
      assertTrue(exhausted.getMessage() != null,
          "失败信息明确: " + exhausted);
      assertInstanceOf(Exception.class, exhausted);
      occupying.get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  @Timeout(15)
  void shouldPropagateTraceIdHeaderToProvider() throws Exception {
    try (LocalHttpServer provider = LocalHttpServer.start(0)) {
      ReactorNettyRemoteCaller caller = new ReactorNettyRemoteCaller(
          ConnectionPoolSettings.DEFAULT, TimeoutSettings.DEFAULT);

      String response =
          caller.call(provider.baseUrl, "echo.call", "[]", 3_000, "trace-from-gateway");

      assertTrue(response.contains("\"OK\""), "往返成功: " + response);
      assertEquals("trace-from-gateway", provider.lastTraceIdHeader,
          "traceId 经 X-Contract-TraceId 请求头透传至 Provider（两侧日志可关联）");
    }
  }

  private static String silentCall(ReactorNettyRemoteCaller caller, String baseUrl) {
    try {
      return caller.call(baseUrl, "echo.call", "[]", 5_000);
    } catch (Exception failure) {
      return failure.toString();
    }
  }
}
