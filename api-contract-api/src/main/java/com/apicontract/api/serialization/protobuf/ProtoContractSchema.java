package com.apicontract.api.serialization.protobuf;

import java.util.List;

/**
 * 契约 DTO 的 Protobuf schema 声明（schemaRef = DTO 类型）：
 * 随契约定义模块同版本分发（不经配置中心传输），message 名即 DTO 简单名。
 */
public record ProtoContractSchema(String messageName, List<ProtoField> fields) {

  public ProtoContractSchema {
    if (messageName == null || messageName.isBlank()) {
      throw new IllegalArgumentException("message 名不能为空");
    }
    fields = List.copyOf(fields);
  }
}
