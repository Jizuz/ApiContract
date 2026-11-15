package com.apicontract.gateway.web;

import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.GatewayErrorCodes;
import com.apicontract.gateway.GatewayResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关统一入口（design 网关入口）：{@code http(s)://gateway:{port}/}，
 * 统一根路径（可配置）；{@code mt} query 必填；读类 GET / 写类 POST
 * 以契约为准；前端侧恒 JSON（决策③）。
 *
 * <p>错误码对应 HTTP 状态码：MT_MISSING/ARGUMENT_INVALID→400、
 * CONTRACT_NOT_FOUND→404、INVOKE_ERROR→502、NO_INSTANCE/UPSTREAM_UNAVAILABLE→503。
 */
@RestController
@RequestMapping("${api-contract.gateway.root-path:/}")
public class ContractGatewayController {

  private final ContractGatewayHandler handler;

  public ContractGatewayController(ContractGatewayHandler handler) {
    this.handler = handler;
  }

  /** GET：按参数名从 query 取首值（缺省 null）。 */
  @GetMapping
  public ResponseEntity<GatewayResponse> get(
      @RequestParam(name = "mt", required = false) String contractName,
      @RequestParam Map<String, String> queryParams) {
    Map<String, String> query = new LinkedHashMap<>(queryParams);
    query.remove("mt");
    return respond(handler.handle(contractName, "GET", query, null));
  }

  /** POST：按参数名从 JSON 对象取值或直接为参数数组。 */
  @PostMapping
  public ResponseEntity<GatewayResponse> post(
      @RequestParam(name = "mt", required = false) String contractName,
      @RequestBody(required = false) String body) {
    return respond(handler.handle(contractName, "POST", Map.of(), body));
  }

  private static ResponseEntity<GatewayResponse> respond(GatewayResponse response) {
    return ResponseEntity.status(httpStatus(response.code())).body(response);
  }

  private static int httpStatus(String code) {
    return switch (code) {
      case GatewayErrorCodes.MT_MISSING, GatewayErrorCodes.ARGUMENT_INVALID -> 400;
      case GatewayErrorCodes.CONTRACT_NOT_FOUND -> 404;
      case GatewayErrorCodes.INVOKE_ERROR -> 502;
      case GatewayErrorCodes.RATE_LIMITED -> 429;
      case GatewayErrorCodes.NO_INSTANCE, GatewayErrorCodes.UPSTREAM_UNAVAILABLE,
          GatewayErrorCodes.CIRCUIT_OPEN -> 503;
      default -> 200;
    };
  }
}
