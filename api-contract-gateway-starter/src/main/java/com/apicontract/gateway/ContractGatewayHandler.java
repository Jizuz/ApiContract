package com.apicontract.gateway;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.gateway.cluster.CircuitOpenException;
import com.apicontract.gateway.cluster.ClusterInvoker;
import com.apicontract.gateway.loadbalance.CircuitBreakerRegistry;
import com.apicontract.gateway.loadbalance.RoundRobinLoadBalancer;
import com.apicontract.gateway.loadbalance.SimpleCircuitBreaker;
import com.apicontract.gateway.filter.ContractInvocation;
import com.apicontract.gateway.filter.ContractInvokeFilter;
import com.apicontract.gateway.filter.InvokeFilterChain;
import com.apicontract.gateway.ratelimit.ContractRateLimiter;
import com.apicontract.gateway.ratelimit.RateLimitedException;
import com.apicontract.gateway.remote.TimeoutSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 网关 MVP 核心链路（design 调用链路顺序不变量）：
 * 解析 mt（缺失→MT_MISSING）→ 契约主索引（未命中→CONTRACT_NOT_FOUND）→
 * 取实例列表（空→NO_INSTANCE）→ 参数提取组装（个数不符→ARGUMENT_INVALID，
 * 不发起远程调用）→ 远程调用 → 异常收敛 → 返回统一结构。
 *
 * <p>路由过滤/限流/负载均衡/集群容错按链路顺序在后续任务插入；
 * MVP 单实例直取，网络失败收敛 UPSTREAM_UNAVAILABLE。
 */
public final class ContractGatewayHandler {

  private final ContractIndex index;
  private final InstanceResolver instances;
  private final ContractRemoteCaller caller;
  private final ClusterInvoker cluster;
  private final RoundRobinLoadBalancer loadBalancer;
  private final ContractRateLimiter rateLimiter;
  private final CircuitBreakerRegistry breakers;
  private final InvokeFilterChain filters;
  private final ObjectMapper mapper;

  private static final System.Logger logger =
      System.getLogger("api-contract.gateway");

  /** 便利构造（既有测试/简单场景）：默认 failover 重试 2 + 轮询负载均衡，无限流/熔断/过滤器。 */
  public ContractGatewayHandler(ContractIndex index, InstanceResolver instances,
      ContractRemoteCaller caller, ObjectMapper mapper) {
    this(index, instances, caller, ClusterInvoker.DEFAULT,
        new RoundRobinLoadBalancer(), null, null, null, mapper);
  }

  /**
   * 完整构造（spec 调用链顺序：路由 → 限流 → 负载均衡 → 调用 → 容错）。
   *
   * @param rateLimiter 契约级限流器；null 表示未装配限流（透传）
   * @param breakers 契约+实例维度熔断注册表；null 表示未装配熔断（不短路）
   */
  public ContractGatewayHandler(ContractIndex index, InstanceResolver instances,
      ContractRemoteCaller caller, ClusterInvoker cluster,
      RoundRobinLoadBalancer loadBalancer, ContractRateLimiter rateLimiter,
      CircuitBreakerRegistry breakers, ObjectMapper mapper) {
    this(index, instances, caller, cluster, loadBalancer, rateLimiter, breakers,
        null, mapper);
  }

  /**
   * 完整构造（任务22：过滤器链）。
   *
   * @param filters 调用过滤器（按 order 链式）；null 表示未装配（透传）。
   *     链路位置（design 顺序不变量）：参数提取组装成功后前置，
   *     远程调用返回后置 / 异常收敛时通知；早期失败（mt 缺失、
   *     契约不存在、参数不符）不进入过滤器链。
   */
  public ContractGatewayHandler(ContractIndex index, InstanceResolver instances,
      ContractRemoteCaller caller, ClusterInvoker cluster,
      RoundRobinLoadBalancer loadBalancer, ContractRateLimiter rateLimiter,
      CircuitBreakerRegistry breakers, List<ContractInvokeFilter> filters,
      ObjectMapper mapper) {
    this.index = index;
    this.instances = instances;
    this.caller = caller;
    this.cluster = cluster;
    this.loadBalancer = loadBalancer;
    this.rateLimiter = rateLimiter;
    this.breakers = breakers;
    this.filters = new InvokeFilterChain(filters == null ? List.of() : filters);
    this.mapper = mapper;
  }

  /**
   * 处理一次网关请求。
   *
   * @param contractName mt 查询参数（可空，按 MT_MISSING 处理）
   * @param httpMethod GET / POST（决定参数提取形态）
   * @param query GET 查询参数（按参数名取首值）
   * @param body POST 请求体（对象或参数数组）
   * @return 统一响应 {@code {code, message, traceId, data}}
   */
  public GatewayResponse handle(String contractName, String httpMethod,
      Map<String, String> query, String body) {
    String traceId = newTraceId();
    if (contractName == null || contractName.isBlank()) {
      return GatewayResponse.error(GatewayErrorCodes.MT_MISSING,
          "缺少契约标识（mt）查询参数", traceId);
    }
    ContractMeta meta = index.find(contractName);
    if (meta == null) {
      return GatewayResponse.error(GatewayErrorCodes.CONTRACT_NOT_FOUND,
          "契约不存在: " + contractName, traceId);
    }
    final String argsJson;
    try {
      argsJson = "GET".equalsIgnoreCase(httpMethod)
          ? ParameterExtractor.extractGet(meta, query)
          : ParameterExtractor.extractPost(meta, body, mapper);
    } catch (IllegalArgumentException invalid) {
      return GatewayResponse.error(GatewayErrorCodes.ARGUMENT_INVALID,
          invalid.getMessage(), traceId);
    }
    // 过滤器链前置（design：参数提取组装 → 过滤器链前置 → 调用）；
    // 早期失败（MT_MISSING / CONTRACT_NOT_FOUND / ARGUMENT_INVALID）不进入链。
    ContractInvocation invocation =
        new ContractInvocation(contractName, argsJson, traceId);
    try {
      filters.before(invocation);
    } catch (Throwable filterFailure) {
      return GatewayResponse.error(GatewayErrorCodes.UPSTREAM_UNAVAILABLE,
          "调用过滤器执行失败: " + filterFailure.getMessage(), traceId);
    }
    List<String> addresses = instances.resolve(meta.serviceName());
    if (addresses.isEmpty()) {
      notifyError(invocation,
          new java.io.IOException("服务无可用实例: " + meta.serviceName()));
      return GatewayResponse.error(GatewayErrorCodes.NO_INSTANCE,
          "服务无可用实例: " + meta.serviceName(), traceId);
    }
    final long overallTimeout = TimeoutSettings.DEFAULT.overallFor(meta);
    String response;
    try {
      response = cluster.invoke(() -> {
        if (rateLimiter != null && !rateLimiter.tryAcquire(contractName)) {
          throw new RateLimitedException(contractName);
        }
        String address = loadBalancer.choose(contractName, addresses);
        if (address == null) {
          throw new java.io.IOException("负载均衡未选出实例");
        }
        invocation.setInstance(address);
        if (breakers != null) {
          SimpleCircuitBreaker breaker =
              breakers.forKey(contractName + "|" + address);
          if (!breaker.allowRequest()) {
            throw new CircuitOpenException(address);
          }
        }
        try {
          String result = caller.call(address, contractName, argsJson,
              overallTimeout, invocation.traceId());
          if (breakers != null) {
            breakers.forKey(contractName + "|" + address).recordSuccess();
          }
          return result;
        } catch (Exception callFailure) {
          if (breakers != null) {
            breakers.forKey(contractName + "|" + address).recordFailure();
          }
          throw callFailure;
        }
      });
    } catch (RateLimitedException limited) {
      notifyError(invocation, limited);
      return GatewayResponse.error(GatewayErrorCodes.RATE_LIMITED,
          limited.getMessage(), traceId);
    } catch (CircuitOpenException open) {
      notifyError(invocation, open);
      return GatewayResponse.error(GatewayErrorCodes.CIRCUIT_OPEN,
          open.getMessage(), traceId);
    } catch (Throwable upstreamFailure) {
      notifyError(invocation, upstreamFailure);
      return GatewayResponse.error(GatewayErrorCodes.UPSTREAM_UNAVAILABLE,
          "上游不可用: " + upstreamFailure.getMessage(), traceId);
    }
    if (response == null) {
      notifyError(invocation,
          new java.io.IOException("failsafe 降级: 上游失败已被集群策略吞掉"));
      return GatewayResponse.error(GatewayErrorCodes.UPSTREAM_UNAVAILABLE,
          "failsafe 降级: 上游失败已被集群策略吞掉", traceId);
    }
    notifyAfter(invocation, response);
    return parseProviderResponse(response, traceId);
  }

  /** 后置通知：拿到响应即视为调用完成；后置自身异常不覆盖既有响应（仅记录）。 */
  private void notifyAfter(ContractInvocation invocation, Object result) {
    try {
      filters.after(invocation, result);
    } catch (Throwable afterFailure) {
      logger.log(Level.WARNING, "过滤器后置处理失败（不覆盖调用结果）: {0}",
          String.valueOf(afterFailure));
    }
  }

  /** 异常通知：通知链自身异常不吞没原始错误（仅记录后忽略通知失败）。 */
  private void notifyError(ContractInvocation invocation, Throwable error) {
    try {
      filters.onError(invocation, error);
    } catch (Throwable notifyFailure) {
      logger.log(Level.WARNING, "过滤器异常通知失败: {0}",
          String.valueOf(notifyFailure));
    }
  }

  private GatewayResponse parseProviderResponse(String response, String traceId) {
    JsonNode root;
    try {
      root = mapper.readTree(response);
    } catch (Exception malformed) {
      return GatewayResponse.error(GatewayErrorCodes.UPSTREAM_UNAVAILABLE,
          "上游响应不可解析", traceId);
    }
    if (root.path("code").asText("").equals(GatewayErrorCodes.OK)) {
      return GatewayResponse.ok(root.path("data"), traceId);
    }
    return GatewayResponse.error(GatewayErrorCodes.INVOKE_ERROR,
        root.path("message").asText("调用失败"), traceId);
  }

  private static String newTraceId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }
}
