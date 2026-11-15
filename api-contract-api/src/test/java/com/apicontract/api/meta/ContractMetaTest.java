package com.apicontract.api.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务2（红-绿）测试：契约条目（ContractMeta）结构化模型。
 *
 * <p>规格（contract-definition 输出定义）：契约条目包含标识、接口全名、方法名、
 * 参数名与类型、返回类型、版本、标签、序列化方式、超时（12 字段与 design 接口契约一致）。
 */
class ContractMetaTest {

  private ContractMeta newMeta() {
    return new ContractMeta(
        "apollo.getuser",
        "com.apicontract.api.UserContract",
        "getUser",
        List.of("userId"),
        List.of("java.lang.Long"),
        "com.apicontract.api.UserDTO",
        "user-provider",
        "1.0.0",
        List.of("gray"),
        "json",
        null,
        null);
  }

  @Test
  void shouldExposeTwelveFieldsAsDeclared() {
    ContractMeta meta = newMeta();
    assertEquals("apollo.getuser", meta.contractName(), "契约标识（mt）");
    assertEquals("com.apicontract.api.UserContract", meta.interfaceName(), "接口全限定名");
    assertEquals("getUser", meta.methodName(), "方法名");
    assertEquals(List.of("userId"), meta.paramNames(), "参数名列表（按名取值依赖）");
    assertEquals(List.of("java.lang.Long"), meta.paramTypes(), "参数类型列表");
    assertEquals("com.apicontract.api.UserDTO", meta.returnType(), "返回类型");
    assertEquals("user-provider", meta.serviceName(), "归属服务名");
    assertEquals("1.0.0", meta.version(), "契约版本");
    assertEquals(List.of("gray"), meta.tags(), "实例标签（标签路由用）");
    assertEquals("json", meta.serializer(), "序列化方式（json/protobuf）");
    assertEquals(null, meta.schemaRef(), "Protobuf schema 引用（未声明为 null）");
    assertEquals(null, meta.timeout(), "契约级超时覆盖（未声明为 null，用全局默认）");
  }

  @Test
  void sameFieldValuesShouldBeEqual() {
    assertEquals(newMeta(), newMeta(), "同字段值等值：用于发布判重与索引比对");
    assertEquals(newMeta().hashCode(), newMeta().hashCode(), "等值实例 hashCode 一致");
  }

  @Test
  void differentContractNameShouldNotBeEqual() {
    ContractMeta other = new ContractMeta(
        "apollo.getorder", "com.apicontract.api.UserContract", "getUser",
        List.of("userId"), List.of("java.lang.Long"), "com.apicontract.api.UserDTO",
        "user-provider", "1.0.0", List.of("gray"), "json", null, null);
    assertNotEquals(newMeta(), other, "契约标识不同即不同契约条目");
  }
}
