package com.apicontract.provider.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.testapi.NotAnInterface;
import com.apicontract.testapi.OrderContract;
import com.apicontract.testapi.UserContract;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务3（红-绿）测试：导出白名单加载器。
 *
 * <p>规格（contract-definition）：每行一个接口全限定名，# 为注释，空行忽略；
 * 白名单某行不是接口类型（是类）：整行忽略并告警；
 * 条目类不存在或加载失败：EXPORT_LOAD_FAILED，按快速失败配置决定中断启动（默认）或告警跳过；
 * 空白名单/资源不存在：返回空列表，应用正常启动。
 */
class ExportWhitelistLoaderTest {

  private final List<String> warnings = new ArrayList<>();

  @Test
  void shouldLoadInterfacesInFileOrderIgnoringCommentsAndBlanks() {
    List<Class<?>> loaded =
        ExportWhitelistLoader.load("whitelist-normal.txt", true, warnings::add);
    assertEquals(List.of(UserContract.class, OrderContract.class), loaded,
        "应按文件出现顺序加载接口，注释与空行忽略");
    assertTrue(warnings.isEmpty(), "正常加载不产生告警");
  }

  @Test
  void shouldReturnEmptyListWhenResourceMissing() {
    List<Class<?>> loaded =
        ExportWhitelistLoader.load("whitelist-not-exists.txt", true, warnings::add);
    assertTrue(loaded.isEmpty(), "白名单资源不存在等效空白名单：返回空列表不报错");
    assertTrue(warnings.isEmpty(), "资源不存在不产生告警（未提供白名单则不导出任何契约）");
  }

  @Test
  void shouldReturnEmptyListWhenOnlyCommentsAndBlanks() {
    List<Class<?>> loaded =
        ExportWhitelistLoader.load("whitelist-comment-only.txt", true, warnings::add);
    assertTrue(loaded.isEmpty(), "仅注释与空行等效空白名单");
    assertTrue(warnings.isEmpty(), "注释与空行不产生告警");
  }

  @Test
  void shouldIgnoreNonInterfaceEntryWithWarning() {
    List<Class<?>> loaded =
        ExportWhitelistLoader.load("whitelist-non-interface.txt", true, warnings::add);
    assertEquals(List.of(UserContract.class), loaded,
        "非接口行整行忽略，不影响其他条目");
    assertEquals(1, warnings.size(), "非接口行应产生一条告警");
    assertTrue(warnings.get(0).contains(NotAnInterface.class.getName()),
        "告警应包含被忽略的类名，便于定位：实际=" + warnings.get(0));
    assertTrue(warnings.get(0).contains("接口"),
        "告警应说明该条目不是接口：实际=" + warnings.get(0));
  }

  @Test
  void nonInterfaceEntryShouldBeIgnoredEvenWhenFailFastDisabled() {
    List<Class<?>> loaded =
        ExportWhitelistLoader.load("whitelist-non-interface.txt", false, warnings::add);
    assertEquals(List.of(UserContract.class), loaded,
        "非接口行始终忽略并告警（快速失败仅作用于类不存在/加载失败）");
    assertEquals(1, warnings.size());
  }

  @Test
  void missingClassShouldAbortStartupWhenFailFastEnabled() {
    ExportLoadFailedException exception = assertThrows(ExportLoadFailedException.class,
        () -> ExportWhitelistLoader.load("whitelist-missing-class.txt", true, warnings::add),
        "默认快速失败：白名单条目类不存在应中断启动（EXPORT_LOAD_FAILED）");
    assertTrue(exception.getMessage().contains("com.not.exists.MissingContract"),
        "异常消息应包含加载失败的类名：实际=" + exception.getMessage());
  }

  @Test
  void missingClassShouldBeSkippedWithWarningWhenFailFastDisabled() {
    List<Class<?>> loaded =
        ExportWhitelistLoader.load("whitelist-missing-class.txt", false, warnings::add);
    assertEquals(List.of(UserContract.class), loaded, "fail-fast 关闭时跳过缺失类，不影响其他条目");
    assertEquals(1, warnings.size(), "缺失类应产生一条告警");
    assertTrue(warnings.get(0).contains("com.not.exists.MissingContract"),
        "告警应包含缺失类名：实际=" + warnings.get(0));
  }
}
