package com.apicontract.gateway.remote;

import com.apicontract.gateway.ContractRemoteCaller;
import com.apicontract.gateway.metrics.ContractMetrics;
import io.netty.channel.ChannelOption;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Reactor Netty 异步调用实现（可选依赖）：全链路事件循环非阻塞，
 * 按目标地址维度复用长连接；等待队列有界（pendingAcquireMaxCount），
 * 池耗尽按 acquire-timeout 快速失败不排队放大；三级超时 =
 * 连接（CONNECT_TIMEOUT）→ 读（responseTimeout）→ 整体（Mono.timeout）。
 *
 * <p>回调禁阻塞（design CR 阻断项）：回调链仅做字节聚合与字符串转换，
 * 禁止在回调中执行阻塞操作；{@link #call} 为同步外壳（调用线程阻塞等待
 * 整体结果），IO 与等待均由事件循环承担。
 */
public final class ReactorNettyRemoteCaller implements ContractRemoteCaller {

  private static final String ENTRY_PATH = "/_contract/rpc";

  private final HttpClient client;

  public ReactorNettyRemoteCaller(ConnectionPoolSettings pool,
      TimeoutSettings timeouts) {
    ConnectionProvider provider = ConnectionProvider
        .builder("api-contract-gateway")
        .maxConnections(pool.maxConnectionsPerHost())
        .pendingAcquireMaxCount(pool.pendingAcquireMaxCount())
        .pendingAcquireTimeout(Duration.ofMillis(pool.acquireTimeoutMillis()))
        .maxIdleTime(Duration.ofMillis(pool.maxIdleMillis()))
        .evictInBackground(Duration.ofMillis(pool.maxIdleMillis()))
        .build();
    this.client = HttpClient.create(provider)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeouts.connectMillis())
        .responseTimeout(Duration.ofMillis(timeouts.readMillis()));
  }

  @Override
  public String call(String baseUrl, String contractName, String argsJson)
      throws Exception {
    return call(baseUrl, contractName, argsJson, 3_000);
  }

  @Override
  public String call(String baseUrl, String contractName, String argsJson,
      long overallTimeoutMillis) throws Exception {
    return call(baseUrl, contractName, argsJson, overallTimeoutMillis, null);
  }

  @Override
  public String call(String baseUrl, String contractName, String argsJson,
      long overallTimeoutMillis, String traceId) throws Exception {
    String uri = baseUrl + ENTRY_PATH + "?mt="
        + URLEncoder.encode(contractName, StandardCharsets.UTF_8);
    String payload = argsJson == null ? "[]" : argsJson;
    boolean propagateTrace = traceId != null && !traceId.isBlank();
    try {
      return client.post().uri(uri)
          .send((request, outbound) -> {
            request.requestHeaders().set("Content-Type", "application/json");
            if (propagateTrace) {
              request.requestHeaders().set("X-Contract-TraceId", traceId);
            }
            return outbound.sendByteArray(
                Mono.just(payload.getBytes(StandardCharsets.UTF_8)));
          })
          .responseContent()
          .aggregate()
          .asString(StandardCharsets.UTF_8)
          .timeout(Duration.ofMillis(overallTimeoutMillis))
          .block(Duration.ofMillis(overallTimeoutMillis
              + Math.max(overallTimeoutMillis, 5_000)));
    } catch (RuntimeException failure) {
      if (failure.getClass().getName().contains("PendingAcquire")) {
        ContractMetrics.recordPoolExhausted();
      }
      TimeoutException timeout = asTimeout(failure, overallTimeoutMillis,
          contractName);
      if (timeout != null) {
        ContractMetrics.recordTimeout(contractName);
        throw timeout;
      }
      throw failure;
    }
  }

  /**
   * 整体超时收敛：Mono.timeout 产生的 TimeoutException（checked）经
   * block() 会以 RuntimeException 包装传播，需解包归一为
   * {@link TimeoutException}（网关映射 TIMEOUT 错误码）。
   */
  private static TimeoutException asTimeout(Throwable failure,
      long overallTimeoutMillis, String contractName) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < 5; depth++) {
      if (current instanceof TimeoutException) {
        return new TimeoutException(
            "远程调用整体超时（上限 " + overallTimeoutMillis + "ms）: "
                + contractName);
      }
      current = current.getCause();
    }
    return null;
  }
}
