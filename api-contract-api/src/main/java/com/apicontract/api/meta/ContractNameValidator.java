package com.apicontract.api.meta;

import java.util.regex.Pattern;

/**
 * 契约标识（mt）命名规范校验器。
 *
 * <p>规格（contract-definition）：格式为 {@code 业务域.动作}，全小写、点分层、
 * 无中文与空格；业务域支持多级（如 contract.order.create），至少两段；
 * 每段以小写字母开头，仅含小写字母与数字。
 */
public final class ContractNameValidator {

  /** 契约标识合法格式：至少两段、每段 [a-z][a-z0-9]*、以点分隔。 */
  public static final Pattern NAME_PATTERN =
      Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$");

  private ContractNameValidator() {
  }

  /** 校验契约标识是否合法（null / 空白 / 格式非法均返回 false）。 */
  public static boolean isValid(String name) {
    return name != null && NAME_PATTERN.matcher(name).matches();
  }

  /**
   * 强制校验：非法命名抛出 {@link IllegalArgumentException}，用于扫描与发布阶段
   * 阻止不合规契约进入元数据。
   *
   * @param name 契约标识
   * @throws IllegalArgumentException 命名为 null、空白或不符合规范
   */
  public static void validate(String name) {
    if (!isValid(name)) {
      throw new IllegalArgumentException(
          "非法契约标识（mt）: [" + name + "]，规范：业务域.动作，全小写、点分层、"
              + "无中文与空格、每段以小写字母开头（示例：apollo.getuser / contract.order.create）");
    }
  }
}
