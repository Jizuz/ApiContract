package com.apicontract.provider.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.SerializationException;
import com.apicontract.api.serialization.protobuf.ContractProtoSchemas;
import com.apicontract.api.serialization.protobuf.ProtoContractSchema;
import com.apicontract.api.serialization.protobuf.ProtoField;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务18（红-绿）测试：Provider 侧契约序列化路由（与网关侧同语义独立验证）：
 * 按元数据声明选择内部链路编解码器；按契约粒度回退互不影响。
 */
class ContractCodecsTest {

  @Test
  void shouldRouteToProtobufWhenDeclared() {
    ContractCodecs codecs = ContractCodecs.withDefaults(schemasWithUserDto());

    assertEquals("protobuf", codecs
        .effectiveFor(meta("mt.query", "protobuf", "UserDto")).name());
  }

  @Test
  void shouldRouteToJsonWhenDeclaredOrNull() {
    ContractCodecs codecs = ContractCodecs.withDefaults(schemasWithUserDto());

    assertEquals("json", codecs.effectiveFor(meta("mt.a", "json", null)).name());
    assertEquals("json", codecs.effectiveFor(meta("mt.b", null, null)).name(),
        "未声明序列化方式默认 json");
  }

  @Test
  void shouldFallbackToJsonForSingleContractOnly() {
    ContractCodecs codecs = ContractCodecs.withDefaults(schemasWithUserDto());

    assertEquals("json", codecs
        .effectiveFor(meta("mt.rolledBack", "json", null)).name(),
        "回退 JSON 的契约立即按 json 生效");
    assertEquals("protobuf", codecs
        .effectiveFor(meta("mt.stillBinary", "protobuf", "UserDto")).name(),
        "其他契约不受回退影响");
  }

  @Test
  void shouldFailExplicitlyOnUnknownSerializerName() {
    ContractCodecs codecs = ContractCodecs.withDefaults(schemasWithUserDto());

    SerializationException failure = assertThrows(SerializationException.class,
        () -> codecs.effectiveFor(meta("mt.bad", "avro", null)));

    assertTrue(failure.getMessage().contains("mt.bad"),
        "未知序列化方式显式报错并指明契约: " + failure.getMessage());
  }

  @Test
  void shouldExposeJsonForDebugScenarios() {
    ContractCodecs codecs = ContractCodecs.withDefaults(schemasWithUserDto());

    assertSame(JsonContractSerializer.INSTANCE, codecs.json());
    assertEquals("json", codecs.json().name(),
        "联调、内部后台场景始终可读 JSON");
    assertNotSame(codecs.json(),
        codecs.effectiveFor(meta("mt.query", "protobuf", "UserDto")),
        "JSON 编解码器与内部 protobuf 编解码器分离");
  }

  static ContractProtoSchemas schemasWithUserDto() {
    ContractProtoSchemas schemas = new ContractProtoSchemas();
    schemas.register(ContractCodecsTest.class, new ProtoContractSchema("UserDto",
        List.of(ProtoField.of("id", 1, "long"),
            ProtoField.of("name", 2, "string"))));
    return schemas;
  }

  static ContractMeta meta(String contractName, String serializer,
      String schemaRef) {
    return new ContractMeta(contractName, "com.example.OrderApi", "query",
        List.of(), List.of(), "java.lang.String", "order-service", "1.0.0",
        List.of(), serializer, schemaRef, null);
  }
}
