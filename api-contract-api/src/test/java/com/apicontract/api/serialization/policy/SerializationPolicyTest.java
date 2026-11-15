package com.apicontract.api.serialization.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 任务18（红-绿）测试：序列化方式三级优先级（契约 > 服务 > 全局，
 * spec 分支场景：全局默认 json、服务级声明、契约级覆盖）。
 */
class SerializationPolicyTest {

  @Test
  void shouldLetContractOverrideServiceAndGlobal() {
    SerializationPolicy policy = SerializationPolicy.builder()
        .global("json")
        .service("order-service", "protobuf")
        .contract("mt.createOrder", "json")
        .build();

    assertEquals("json", policy.resolve("mt.createOrder", "order-service"),
        "契约级覆盖服务级与全局");
  }

  @Test
  void shouldLetServiceOverrideGlobal() {
    SerializationPolicy policy = SerializationPolicy.builder()
        .global("json")
        .service("order-service", "protobuf")
        .build();

    assertEquals("protobuf", policy.resolve("mt.any", "order-service"),
        "服务级覆盖全局");
    assertEquals("json", policy.resolve("mt.any", "user-service"),
        "未声明服务回落全局");
  }

  @Test
  void shouldFallBackToGlobalThenDefaultJson() {
    assertEquals("protobuf", SerializationPolicy.builder()
        .global("protobuf").build().resolve("mt.any", null),
        "无服务级声明时回落全局");

    assertEquals("json", SerializationPolicy.builder().build()
        .resolve("mt.any", null), "全空声明回落默认 json");
  }

  @Test
  void shouldExposeContractLevelOverride() {
    SerializationPolicy policy = SerializationPolicy.builder()
        .contract("mt.pay", "protobuf").build();

    assertEquals("protobuf", policy.contractLevelOf("mt.pay"));
    assertNull(policy.contractLevelOf("mt.other"));
  }

  @Test
  void shouldRejectIllegalGlobalValueAtBuild() {
    assertThrows(IllegalArgumentException.class,
        () -> SerializationPolicy.builder().global("xml").build(),
        "非法序列化方式构建期 fail-fast");
  }

  @Test
  void shouldRejectIllegalServiceValueAtBuild() {
    assertThrows(IllegalArgumentException.class,
        () -> SerializationPolicy.builder().service("order-service", "avro").build(),
        "非法服务级声明构建期 fail-fast");
  }
}
