package com.apicontract.gateway;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 网关统一响应结构（design 接口契约）：
 * {@code { "code": ..., "message": ..., "traceId": ..., "data": ... }}。
 *
 * <p>错误响应 {@code data} 恒为 null；错误消息可对外展示，不含内部堆栈。
 */
public record GatewayResponse(String code, String message, String traceId, JsonNode data) {

  public static GatewayResponse ok(JsonNode data, String traceId) {
    return new GatewayResponse(GatewayErrorCodes.OK, null, traceId, data);
  }

  public static GatewayResponse error(String code, String message, String traceId) {
    return new GatewayResponse(code, message, traceId, null);
  }
}
