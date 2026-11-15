package com.apicontract.gateway.remote;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 测试用本地 HTTP 服务：模拟 Provider 泛化入口
 * {@code POST /_contract/rpc?mt=xxx}，可注入处理延迟制造慢上游/占住连接。
 */
final class LocalHttpServer implements AutoCloseable {

  private final HttpServer server;
  final String baseUrl;
  volatile String lastTraceIdHeader;

  private LocalHttpServer(HttpServer server) {
    this.server = server;
    this.baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  static LocalHttpServer start(long delayMillis) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      LocalHttpServer local = new LocalHttpServer(server);
      server.createContext("/",
          (HttpExchange exchange) -> handle(exchange, delayMillis, local));
      server.start();
      return local;
    } catch (IOException failure) {
      throw new IllegalStateException("测试 HTTP 服务启动失败", failure);
    }
  }

  private static void handle(HttpExchange exchange, long delayMillis, LocalHttpServer local)
      throws IOException {
    local.lastTraceIdHeader = exchange.getRequestHeaders().getFirst("X-Contract-TraceId");
    byte[] body = exchange.getRequestBody().readAllBytes();
    if (delayMillis > 0) {
      try {
        Thread.sleep(delayMillis);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    byte[] response = ("{\"code\":\"OK\",\"message\":null,\"traceId\":\"t1\",\"data\":"
        + "\"len=" + body.length + "\"}").getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(response);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
