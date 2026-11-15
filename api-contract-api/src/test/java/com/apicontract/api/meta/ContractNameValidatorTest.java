package com.apicontract.api.meta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 任务2（红-绿）测试：契约标识（mt）命名规范校验。
 *
 * <p>规格（contract-definition 输入定义）：格式为 `业务域.动作`，全小写、点分层、
 * 无中文与空格；全局唯一（唯一性校验在扫描/发布阶段，本测试聚焦格式校验）。
 */
class ContractNameValidatorTest {

  @Test
  void shouldAcceptValidNames() {
    assertTrue(ContractNameValidator.isValid("apollo.getuser"), "两段：业务域.动作");
    assertTrue(ContractNameValidator.isValid("contract.order.create"), "业务域支持多级点分层");
    assertTrue(ContractNameValidator.isValid("user.getbyid2"), "动作段允许以数字结尾");
    assertTrue(ContractNameValidator.isValid("pay.refund9.v2"), "各级段允许数字结尾");
  }

  @Test
  void shouldRejectNullAndBlank() {
    assertFalse(ContractNameValidator.isValid(null), "null 非法");
    assertFalse(ContractNameValidator.isValid(""), "空串非法");
    assertFalse(ContractNameValidator.isValid("   "), "空白非法");
  }

  @Test
  void shouldRejectSingleSegment() {
    assertFalse(ContractNameValidator.isValid("apollo"), "必须至少 业务域.动作 两段");
  }

  @Test
  void shouldRejectUppercase() {
    assertFalse(ContractNameValidator.isValid("Apollo.getuser"), "业务段大写非法：全小写");
    assertFalse(ContractNameValidator.isValid("apollo.getUser"), "动作段大写非法：全小写");
    assertFalse(ContractNameValidator.isValid("APOLLO.GETUSER"), "全大写非法");
  }

  @Test
  void shouldRejectChineseAndWhitespace() {
    assertFalse(ContractNameValidator.isValid("apollo.获取用户"), "中文非法");
    assertFalse(ContractNameValidator.isValid("apollo.get user"), "空格非法");
    assertFalse(ContractNameValidator.isValid(" apollo.getuser"), "前后空白非法");
  }

  @Test
  void shouldRejectMalformedSegments() {
    assertFalse(ContractNameValidator.isValid("apollo..getuser"), "空段非法");
    assertFalse(ContractNameValidator.isValid(".apollo.getuser"), "首段为空非法");
    assertFalse(ContractNameValidator.isValid("apollo.getuser."), "尾段为空非法");
    assertFalse(ContractNameValidator.isValid("apollo.get_user"), "下划线非法");
    assertFalse(ContractNameValidator.isValid("1pollo.getuser"), "段以数字开头非法");
    assertFalse(ContractNameValidator.isValid("apollo.-getuser"), "特殊符号非法");
  }

  @Test
  void validateShouldThrowWithOffendingName() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> ContractNameValidator.validate("Apollo.getUser"),
            "非法命名应抛出明确异常，阻止契约发布");
    assertTrue(exception.getMessage().contains("Apollo.getUser"),
        "异常消息应包含违规命名，便于定位：实际=" + exception.getMessage());
    assertTrue(exception.getMessage().contains("全小写"),
        "异常消息应提示命名规范（全小写）：实际=" + exception.getMessage());
  }
}
