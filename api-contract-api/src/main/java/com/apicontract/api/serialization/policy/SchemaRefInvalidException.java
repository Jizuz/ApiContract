package com.apicontract.api.serialization.policy;

/**
 * schema 缺失或引用无效（SCHEMA_REF_INVALID）：契约不可启用二进制形态，
 * 启动校验报错（spec contract-serialization 异常场景）。
 */
public class SchemaRefInvalidException extends RuntimeException {

  public SchemaRefInvalidException(String message) {
    super(message);
  }
}
