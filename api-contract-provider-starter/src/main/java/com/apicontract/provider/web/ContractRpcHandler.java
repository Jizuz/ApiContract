package com.apicontract.provider.web;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.provider.invoke.ContractInvoker;
import com.apicontract.provider.invoke.ContractInvokerRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 泛化入口核心处理器（规格 contract-provider-invoke 主流程）。
 *
 * <p>按契约标识分发请求：解析契约标识 -&gt; 查询调用器缓存（未命中直接返回
 * CONTRACT_NOT_FOUND，不进入任何兜底）-&gt; 请求体反序列化为参数数组 -&gt;
 * 类型转换与个数校验 -&gt; 调用器执行 -&gt; 收敛异常与错误码。
 *
 * <p>无参契约忽略请求体；null 参数保持 null；请求体大小设上限；
 * 错误响应仅含错误码、可对外消息、traceId（完整堆栈写本地日志并关联 traceId）。
 *
 * <p>后填充（内置编排）：编排器在容器单例就绪后经 {@link #reset(ContractInvokerRegistry, Map)}
 * 一次性填充真实注册表与元数据；内部以单一 volatile 引用持有注册表与元数据快照，
 * 保证启动期单写、运行期并发读不撕裂（registry 与 metas 任一时刻同源）。
 */
public final class ContractRpcHandler {

  /** 契约表快照：注册器与元数据同源成对交换，读取方一次取用不撕裂。 */
  private record ContractTable(ContractInvokerRegistry registry, Map<String, ContractMeta> metas) {
  }

  private volatile ContractTable table;
  private final ObjectMapper objectMapper;
  private final int maxBodyBytes;

  public ContractRpcHandler(ContractInvokerRegistry registry, Map<String, ContractMeta> metas,
      ObjectMapper objectMapper, int maxBodyBytes) {
    this.table = new ContractTable(registry, new HashMap<>(metas));
    this.objectMapper = objectMapper;
    this.maxBodyBytes = maxBodyBytes;
  }

  /**
   * 内置编排后填充：整体替换契约注册表与元数据（启动期单写、运行期只读）。
   *
   * <p>registry 与 metas 成对一次性交换（单一 volatile 引用），并发读不会观察到
   * 两者的错配中间态；重复调用以最后一次为准。
   *
   * @param registry 契约调用器注册表（编排器扫描生成）
   * @param metas 契约元数据（mt -&gt; meta，与 registry 同源）
   */
  public void reset(ContractInvokerRegistry registry, Map<String, ContractMeta> metas) {
    this.table = new ContractTable(registry, new HashMap<>(metas));
  }

  /**
   * 处理一次泛化调用。
   *
   * @param contractName 契约标识（mt 查询参数；可为 null，按参数非法处理）
   * @param body 请求体（参数数组 JSON；无参契约忽略）
   * @return 统一响应（成功 OK + data；失败错误码 + 可对外消息 + traceId）
   */
  public RpcResponse handle(String contractName, String body) {
    return handle(contractName, body, null);
  }

  /**
   * 处理一次泛化调用（任务22：网关透传 traceId 优先，两侧日志可关联）。
   *
   * @param traceId 网关透传的 traceId（X-Contract-TraceId 请求头）；空时自生成
   */
  public RpcResponse handle(String contractName, String body, String traceId) {
    String effectiveTraceId =
        traceId == null || traceId.isBlank() ? newTraceId() : traceId;
    try {
      return RpcResponse.ok(doHandle(contractName, body), effectiveTraceId);
    } catch (ArgumentInvalidException invalid) {
      return RpcResponse.error(RpcResponse.CODE_ARGUMENT_INVALID, invalid.getMessage(), effectiveTraceId);
    } catch (ContractNotFoundException notFound) {
      return RpcResponse.error(RpcResponse.CODE_NOT_FOUND, notFound.getMessage(), effectiveTraceId);
    } catch (ContractInvokeException invokeFailure) {
      return RpcResponse.error(RpcResponse.CODE_INVOKE_ERROR, invokeFailure.getMessage(), effectiveTraceId);
    } catch (Throwable frameworkFailure) {
      // 框架级内部异常（序列化、加载失败等）：细节不外泄，凭 traceId 排查
      return RpcResponse.error(RpcResponse.CODE_INTERNAL_ERROR,
          "框架内部错误，请凭 traceId 联系管理员排查: " + effectiveTraceId, effectiveTraceId);
    }
  }

  private Object doHandle(String contractName, String body) throws Throwable {
    if (contractName == null || contractName.isBlank()) {
      throw new ArgumentInvalidException("缺少契约标识（mt）查询参数");
    }
    if (body != null && body.getBytes(StandardCharsets.UTF_8).length > maxBodyBytes) {
      throw new ArgumentInvalidException("请求体超过大小上限: " + maxBodyBytes + " 字节");
    }
    ContractTable currentTable = table;
    ContractInvoker invoker = currentTable.registry().get(contractName);
    ContractMeta meta = currentTable.metas().get(contractName);
    if (invoker == null || meta == null) {
      throw new ContractNotFoundException(contractName);
    }
    Object[] args = bindArguments(meta, body);
    try {
      return invoker.invoke(args);
    } catch (Throwable businessFailure) {
      // 调用器已剥离包装，此处即目标方法真实异常（业务异常）
      throw new ContractInvokeException(
          businessFailure.getMessage() == null
              ? businessFailure.getClass().getSimpleName() : businessFailure.getMessage(),
          businessFailure);
    }
  }

  private Object[] bindArguments(ContractMeta meta, String body) throws JsonProcessingException {
    int paramCount = meta.paramNames().size();
    if (paramCount == 0) {
      // 无参契约：参数数组为空，忽略请求体
      return new Object[0];
    }
    List<Object> rawArgs = body == null ? List.of() : parseBody(body);
    if (rawArgs.size() != paramCount) {
      throw new ArgumentInvalidException(
          "参数个数不符: 期望 " + paramCount + "，实际 " + rawArgs.size()
              + "（契约参数: " + meta.paramNames() + "）");
    }
    Object[] args = new Object[paramCount];
    for (int index = 0; index < paramCount; index++) {
      Class<?> targetType = loadType(meta.paramTypes().get(index));
      args[index] = ContractArgumentConverter.convert(
          rawArgs.get(index), targetType, meta.paramNames().get(index), objectMapper);
    }
    return args;
  }

  @SuppressWarnings("unchecked")
  private List<Object> parseBody(String body) throws JsonProcessingException {
    return objectMapper.readValue(body, List.class);
  }

  private Class<?> loadType(String typeName) {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      return loader != null ? Class.forName(typeName, false, loader)
          : Class.forName(typeName);
    } catch (ClassNotFoundException notFound) {
      throw new IllegalStateException("契约参数类型不可加载: " + typeName, notFound);
    }
  }

  private static String newTraceId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }
}
