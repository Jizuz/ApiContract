package com.apicontract.api.serialization.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.protobuf.ContractProtoSchemas;
import com.apicontract.api.serialization.protobuf.ProtoContractSchema;
import com.apicontract.api.serialization.protobuf.ProtoField;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务18（红-绿）测试：两侧序列化声明一致性启动校验
 * （spec：不一致 → SERIALIZER_MISMATCH 启动失败；
 * schema 缺失/引用无效 → SCHEMA_REF_INVALID 契约不可启用二进制）。
 */
class SerializerConsistencyValidatorTest {

  @Test
  void shouldPassAndReturnDeclaredWhenNoLocalContractOverride() {
    String effective = SerializerConsistencyValidator.validateEffective(
        meta("mt.query", ContractMeta.SERIALIZER_PROTOBUF, "UserDto"),
        SerializationPolicy.builder().build(),
        schemasWithUserDto());

    assertEquals(ContractMeta.SERIALIZER_PROTOBUF, effective,
        "元数据声明为事实源：无本地契约覆盖时按声明生效");
  }

  @Test
  void shouldFailWhenLocalContractOverrideConflicts() {
    SerializerMismatchException mismatch = assertThrows(
        SerializerMismatchException.class,
        () -> SerializerConsistencyValidator.validateEffective(
            meta("mt.query", ContractMeta.SERIALIZER_PROTOBUF, "UserDto"),
            SerializationPolicy.builder()
                .contract("mt.query", ContractMeta.SERIALIZER_JSON).build(),
            schemasWithUserDto()));

    assertTrue(mismatch.getMessage().contains("mt.query"),
        "报错指明冲突契约: " + mismatch.getMessage());
    assertTrue(mismatch.getMessage().contains("不一致"),
        "报错说明两侧声明不一致: " + mismatch.getMessage());
  }

  @Test
  void shouldPassWhenLocalOverrideMatchesDeclaration() {
    String effective = SerializerConsistencyValidator.validateEffective(
        meta("mt.query", ContractMeta.SERIALIZER_PROTOBUF, "UserDto"),
        SerializationPolicy.builder()
            .contract("mt.query", ContractMeta.SERIALIZER_PROTOBUF).build(),
        schemasWithUserDto());

    assertEquals(ContractMeta.SERIALIZER_PROTOBUF, effective, "覆盖与声明一致：通过");
  }

  @Test
  void shouldFailWhenProtobufWithoutSchemaRef() {
    SchemaRefInvalidException invalid = assertThrows(
        SchemaRefInvalidException.class,
        () -> SerializerConsistencyValidator.validateEffective(
            meta("mt.query", ContractMeta.SERIALIZER_PROTOBUF, null),
            SerializationPolicy.builder().build(),
            schemasWithUserDto()));

    assertTrue(invalid.getMessage().contains("mt.query"),
        "报错指明契约: " + invalid.getMessage());
  }

  @Test
  void shouldFailWhenSchemaRefNotRegistered() {
    assertThrows(SchemaRefInvalidException.class,
        () -> SerializerConsistencyValidator.validateEffective(
            meta("mt.query", ContractMeta.SERIALIZER_PROTOBUF, "MissingDto"),
            SerializationPolicy.builder().build(),
            schemasWithUserDto()),
        "schemaRef 指向未注册 message → 契约不可启用二进制");
  }

  @Test
  void shouldPassForJsonWithoutSchemaRef() {
    String effective = SerializerConsistencyValidator.validateEffective(
        meta("mt.query", ContractMeta.SERIALIZER_JSON, null),
        SerializationPolicy.builder().build(),
        schemasWithUserDto());

    assertEquals(ContractMeta.SERIALIZER_JSON, effective, "json 形态无需 schemaRef");
  }

  @Test
  void shouldUseLocalResolutionWhenDeclarationAbsent() {
    String effective = SerializerConsistencyValidator.validateEffective(
        meta("mt.query", null, "UserDto"),
        SerializationPolicy.builder()
            .service("order-service", ContractMeta.SERIALIZER_PROTOBUF).build(),
        schemasWithUserDto());

    assertEquals(ContractMeta.SERIALIZER_PROTOBUF, effective,
        "元数据未声明时按本地三级解析（服务级 protobuf）且 schema 校验通过");
  }

  private static ContractProtoSchemas schemasWithUserDto() {
    ContractProtoSchemas schemas = new ContractProtoSchemas();
    schemas.register(SerializerConsistencyValidatorTest.class,
        new ProtoContractSchema("UserDto", List.of(
            ProtoField.of("id", 1, "long"),
            ProtoField.of("name", 2, "string"))));
    return schemas;
  }

  private static ContractMeta meta(String contractName, String serializer,
      String schemaRef) {
    return new ContractMeta(contractName, "com.example.OrderApi", "query",
        List.of(), List.of(), "java.lang.String", "order-service", "1.0.0",
        List.of(), serializer, schemaRef, null);
  }
}
