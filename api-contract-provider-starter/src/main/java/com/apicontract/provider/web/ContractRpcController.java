package com.apicontract.provider.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一泛化入口 MVC 层（规格 contract-provider-invoke）。
 *
 * <p>默认路径 {@code /_contract/rpc}，可经配置 {@code apicontract.entry-path}
 * 避让业务路径（与业务路径冲突时启动报错 PATH_CONFLICT，装配阶段校验）。
 * 仅限内网访问（本期不做鉴权）；请求：{@code POST {入口}?mt={契约标识}}，
 * 请求体为参数数组 JSON；响应为统一结构（错误响应不含内部堆栈）。
 */
@RestController
@RequestMapping("${api-contract.entry-path:/_contract/rpc}")
public class ContractRpcController {

  private final ContractRpcHandler handler;

  public ContractRpcController(ContractRpcHandler handler) {
    this.handler = handler;
  }

  @PostMapping
  public RpcResponse rpc(@RequestParam(name = "mt", required = false) String contractName,
      @RequestHeader(name = "X-Contract-TraceId", required = false) String traceId,
      @RequestBody(required = false) String body) {
    return handler.handle(contractName, body, traceId);
  }
}
