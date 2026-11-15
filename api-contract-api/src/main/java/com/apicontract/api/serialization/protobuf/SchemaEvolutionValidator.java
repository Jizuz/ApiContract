package com.apicontract.api.serialization.protobuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * schema 演进规则校验（spec 数据约束，任务 17）：
 * <ul>
 *   <li>字段编号一次性分配：已分配编号不得复用为不同字段（改名/改类型/
 *       改 repeated 均视为编号复用违规）</li>
 *   <li>字段不得删除：删除只允许标记 deprecated 并保留编号</li>
 *   <li>新增字段编号只增：必须大于已有最大编号</li>
 * </ul>
 * 返回违规描述列表（空 = 合规）；启动校验方据此中断发布。
 */
public final class SchemaEvolutionValidator {

  private SchemaEvolutionValidator() {
  }

  public static List<String> validate(ProtoContractSchema previous,
      ProtoContractSchema updated) {
    List<String> violations = new ArrayList<>();
    Map<Integer, ProtoField> oldByNumber = byNumber(previous);
    Map<Integer, ProtoField> newByNumber = byNumber(updated);

    for (ProtoField oldField : previous.fields()) {
      ProtoField updatedField = newByNumber.get(oldField.number());
      if (updatedField == null) {
        violations.add("字段 " + oldField.name() + "（编号 " + oldField.number()
            + "）被删除：只允许标记 deprecated 保留编号");
        continue;
      }
      if (!updatedField.name().equals(oldField.name())) {
        violations.add("编号 " + oldField.number() + " 已分配给字段 "
            + oldField.name() + "，禁止复用（现字段名 "
            + updatedField.name() + "）");
        continue;
      }
      if (!updatedField.type().equals(oldField.type())
          || updatedField.isRepeated() != oldField.isRepeated()) {
        violations.add("编号 " + oldField.number() + " 字段 " + oldField.name()
            + " 类型不允许修改（" + oldField.type()
            + (oldField.isRepeated() ? " repeated" : "") + " → "
            + updatedField.type() + (updatedField.isRepeated() ? " repeated" : "")
            + "）");
      }
    }

    int maxOldNumber = previous.fields().stream()
        .mapToInt(ProtoField::number).max().orElse(0);
    for (ProtoField newField : updated.fields()) {
      if (!oldByNumber.containsKey(newField.number())
          && newField.number() <= maxOldNumber) {
        violations.add("新增字段 " + newField.name() + " 编号 "
            + newField.number() + " 违反编号只增规则：必须大于已有最大编号 "
            + maxOldNumber);
      }
    }
    return violations;
  }

  private static Map<Integer, ProtoField> byNumber(ProtoContractSchema schema) {
    Map<Integer, ProtoField> byNumber = new HashMap<>();
    for (ProtoField field : schema.fields()) {
      ProtoField conflict = byNumber.putIfAbsent(field.number(), field);
      if (conflict != null) {
        throw new IllegalArgumentException("schema " + schema.messageName()
            + " 内编号重复: " + field.number());
      }
    }
    return byNumber;
  }
}
