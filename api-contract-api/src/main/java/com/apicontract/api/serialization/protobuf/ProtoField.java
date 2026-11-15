package com.apicontract.api.serialization.protobuf;

/**
 * Protobuf schema 字段声明（spec 数据约束：字段编号一次性分配、
 * 禁止复用；删除字段只允许标记废弃并保留编号）。
 *
 * <p>类型声明：{@code int / long / string / boolean / double / decimal /
 * message:MessageName}；{@code decimal} 以字符串承载（精度无损）。
 */
public final class ProtoField {

  private final String name;
  private final int number;
  private final String type;
  private final boolean repeated;
  private final boolean deprecated;

  private ProtoField(String name, int number, String type, boolean repeated,
      boolean deprecated) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("字段名不能为空");
    }
    if (number <= 0) {
      throw new IllegalArgumentException("字段编号必须为正数: " + name);
    }
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("字段类型不能为空: " + name);
    }
    this.name = name;
    this.number = number;
    this.type = type;
    this.repeated = repeated;
    this.deprecated = deprecated;
  }

  public static ProtoField of(String name, int number, String type) {
    return new ProtoField(name, number, type, false, false);
  }

  public ProtoField repeated() {
    return new ProtoField(name, number, type, true, deprecated);
  }

  public ProtoField deprecated() {
    return new ProtoField(name, number, type, repeated, true);
  }

  public String name() {
    return name;
  }

  public int number() {
    return number;
  }

  public String type() {
    return type;
  }

  /** {@code message:X} 的消息名（非 message 类型返回 null）。 */
  public String messageTypeName() {
    return type.startsWith("message:") ? type.substring("message:".length()) : null;
  }

  public boolean isRepeated() {
    return repeated;
  }

  public boolean isDeprecated() {
    return deprecated;
  }
}
