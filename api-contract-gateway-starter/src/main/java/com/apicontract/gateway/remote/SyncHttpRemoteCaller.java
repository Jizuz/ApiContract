package com.apicontract.gateway.remote;

import com.apicontract.gateway.ContractRemoteCaller;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 同步降级实现（JDK HttpURLConnection，零额外依赖）：未引入 Reactor Netty
 * 时由工厂自动选用并告警，功能不缺失；超时同样有界（连接/读超时受整体
 * 超时约束，慢上游快速失败而非无限等待）。
 */
public final class SyncHttpRemoteCaller implements ContractRemoteCaller {

  private static final String ENTRY_PATH = "/_contract/rpc";

  private final TimeoutSettings timeouts;

  public SyncHttpRemoteCaller(TimeoutSettings timeouts) {
    this.timeouts = timeouts;
  }

  @Override
  public String call(String baseUrl, String contractName, String argsJson)
      throws Exception {
    return call(baseUrl, contractName, argsJson, timeouts.overallMillis());
  }

  @Override
  public String call(String baseUrl, String contractName, String argsJson,
      long overallTimeoutMillis) throws Exception {
    return call(baseUrl, contractName, argsJson, overallTimeoutMillis, null);
  }

  @Override
  public String call(String baseUrl, String contractName, String argsJson,
      long overallTimeoutMillis, String traceId) throws Exception {
    HttpURLConnection connection = null;
    try {
      URL url = new URL(baseUrl + ENTRY_PATH + "?mt="
          + URLEncoder.encode(contractName, StandardCharsets.UTF_8));
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setConnectTimeout(
          (int) Math.min(timeouts.connectMillis(), overallTimeoutMillis));
      connection.setReadTimeout(
          (int) Math.min(timeouts.readMillis(), overallTimeoutMillis));
      connection.setRequestProperty("Content-Type", "application/json");
      if (traceId != null && !traceId.isBlank()) {
        connection.setRequestProperty("X-Contract-TraceId", traceId);
      }
      String payload = argsJson == null ? "[]" : argsJson;
      try (OutputStream out = connection.getOutputStream()) {
        out.write(payload.getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = connection.getInputStream().readAllBytes();
      return new String(body, StandardCharsets.UTF_8);
    } catch (SocketTimeoutException timedOut) {
      throw new TimeoutException(
          "同步远程调用超时（整体上限 " + overallTimeoutMillis + "ms）: "
              + contractName);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}
