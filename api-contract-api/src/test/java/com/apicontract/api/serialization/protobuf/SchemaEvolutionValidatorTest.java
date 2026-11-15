package com.apicontract.api.serialization.protobuf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务17（红-绿）测试：schema 字段编号演进规则校验
 * （spec 数据约束：编号一次性分配禁止复用、只增不改不删、
 * 删除仅允许 deprecated 保留）。
 */
class SchemaEvolutionValidatorTest {

  @Test
  void shouldAllowAddingFieldWithHigherNumber() {
    ProtoContractSchema old = schema(
        ProtoField.of("id", 1, "long"),
        ProtoField.of("name", 2, "string"));
    ProtoContractSchema updated = schema(
        ProtoField.of("id", 1, "long"),
        ProtoField.of("name", 2, "string"),
        ProtoField.of("tags", 5, "string").repeated());

    assertTrue(SchemaEvolutionValidator.validate(old, updated).isEmpty(),
        "新增编号大于已有最大编号：合规演进");
  }

  @Test
  void shouldRejectReusingNumberWithDifferentName() {
    ProtoContractSchema old = schema(ProtoField.of("id", 1, "long"));
    ProtoContractSchema updated = schema(ProtoField.of("userId", 1, "long"));

    List<String> violations = SchemaEvolutionValidator.validate(old, updated);

    assertEquals(1, violations.size());
    assertTrue(violations.get(0).contains("编号 1"),
        "违规信息指明冲突编号: " + violations);
  }

  @Test
  void shouldRejectChangingFieldType() {
    ProtoContractSchema old = schema(ProtoField.of("id", 1, "long"));
    ProtoContractSchema updated = schema(ProtoField.of("id", 1, "string"));

    List<String> violations = SchemaEvolutionValidator.validate(old, updated);

    assertEquals(1, violations.size());
    assertTrue(violations.get(0).contains("类型"),
        "同编号改类型视为编号复用违规: " + violations);
  }

  @Test
  void shouldRejectRemovingFieldInsteadOfDeprecating() {
    ProtoContractSchema old = schema(
        ProtoField.of("id", 1, "long"),
        ProtoField.of("nickname", 2, "string"));
    ProtoContractSchema updated = schema(ProtoField.of("id", 1, "long"));

    List<String> violations = SchemaEvolutionValidator.validate(old, updated);

    assertEquals(1, violations.size());
    assertTrue(violations.get(0).contains("nickname"),
        "直接删除字段违规，应标记 deprecated 保留: " + violations);
  }

  @Test
  void shouldAllowDeprecatingButKeepingField() {
    ProtoContractSchema old = schema(
        ProtoField.of("id", 1, "long"),
        ProtoField.of("nickname", 2, "string"));
    ProtoContractSchema updated = schema(
        ProtoField.of("id", 1, "long"),
        ProtoField.of("nickname", 2, "string").deprecated());

    assertTrue(SchemaEvolutionValidator.validate(old, updated).isEmpty(),
        "标记废弃但保留编号：合规");
  }

  @Test
  void shouldRejectNewFieldNumberLowerThanCurrentMax() {
    ProtoContractSchema old = schema(
        ProtoField.of("id", 1, "long"),
        ProtoField.of("memo", 5, "string"));
    ProtoContractSchema updated = schema(
        ProtoField.of("id", 1, "long"),
        ProtoField.of("memo", 5, "string"),
        ProtoField.of("extra", 3, "string"));

    List<String> violations = SchemaEvolutionValidator.validate(old, updated);

    assertEquals(1, violations.size());
    assertTrue(violations.get(0).contains("编号只增"),
        "新增字段编号必须大于已有最大编号: " + violations);
  }

  @Test
  void shouldRejectChangingRepeatedFlag() {
    ProtoContractSchema old = schema(ProtoField.of("tags", 1, "string").repeated());
    ProtoContractSchema updated = schema(ProtoField.of("tags", 1, "string"));

    List<String> violations = SchemaEvolutionValidator.validate(old, updated);

    assertEquals(1, violations.size(), "repeated 语义变更等同于类型变更: "
        + violations);
  }

  private static ProtoContractSchema schema(ProtoField... fields) {
    return new ProtoContractSchema("TestMessage", List.of(fields));
  }
}
