package com.apicontract.api.serialization.policy;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.protobuf.ContractProtoSchemas;

/**
 * 两侧序列化声明一致性启动校验（spec：网关与 Provider 对同一契约的
 * 序列化方式声明一致，启动校验；不一致 → SERIALIZER_MISMATCH；
 * protobuf 而 schema 无效 → SCHEMA_REF_INVALID）。
 *
 * <p>生效值规则：元数据声明（固化的 serializer 字段）优先；
 * 未声明时按本地三级解析（契约 > 服务 > 全局）。
 * 返回生效 serializer 名（调用方用于编解码路由/元数据固化）。
 */
public final class SerializerConsistencyValidator {

  private SerializerConsistencyValidator() {
  }

  public static String validateEffective(ContractMeta meta,
      SerializationPolicy localPolicy, ContractProtoSchemas schemas) {
    String declared = meta.serializer();
    String localOverride =
        localPolicy.contractLevelOf(meta.contractName());
    if (declared != null && !declared.isBlank()
        && localOverride != null && !localOverride.equals(declared)) {
      throw new SerializerMismatchException("契约 " + meta.contractName()
          + " 序列化声明不一致：元数据=" + declared + "，本地覆盖=" + localOverride
          + "（SERIALIZER_MISMATCH，禁止以不一致状态上线）");
    }

    String effective = declared != null && !declared.isBlank()
        ? declared
        : localPolicy.resolve(meta.contractName(), meta.serviceName());

    if (ContractMeta.SERIALIZER_PROTOBUF.equals(effective)) {
      requireValidSchemaRef(meta, schemas);
    }
    return effective;
  }

  private static void requireValidSchemaRef(ContractMeta meta,
      ContractProtoSchemas schemas) {
    String schemaRef = meta.schemaRef();
    if (schemaRef == null || schemaRef.isBlank()) {
      throw new SchemaRefInvalidException("契约 " + meta.contractName()
          + " 启用 protobuf 但未声明 schemaRef（SCHEMA_REF_INVALID）");
    }
    if (schemas == null || !schemas.isRegistered(schemaRef)) {
      throw new SchemaRefInvalidException("契约 " + meta.contractName()
          + " schemaRef 引用无效: " + schemaRef
          + "（SCHEMA_REF_INVALID，schema 未随契约定义模块注册）");
    }
  }
}
