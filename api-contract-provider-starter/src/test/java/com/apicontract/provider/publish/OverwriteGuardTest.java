package com.apicontract.provider.publish;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 任务15（红-绿）测试：并发发布防覆盖（design 一致性策略 2：
 * 发布前按版本号/内容哈希乐观比较，旧内容不得覆盖新内容）。
 */
class OverwriteGuardTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static String payload(String version, String marker) {
    return "{\"serviceName\": \"user-provider\", \"version\": \"" + version
        + "\", \"contracts\": [{\"contractName\": \"echo.call\","
        + "\"marker\": \"" + marker + "\"}], \"aggregateIndex\": {}}";
  }

  @Test
  void shouldAllowFirstPublishWhenNoExistingContent() {
    assertTrue(OverwriteGuard.shouldOverwrite(null, payload("1.0.0", "a"), mapper),
        "无旧内容：首次发布允许写入");
    assertTrue(OverwriteGuard.shouldOverwrite("", payload("1.0.0", "a"), mapper));
  }

  @Test
  void shouldAllowNewerVersionToOverwrite() {
    assertTrue(
        OverwriteGuard.shouldOverwrite(payload("1.0.0", "a"), payload("2.0.0", "b"), mapper),
        "新版本允许覆盖旧版本");
  }

  @Test
  void shouldRejectStaleVersionOverwrite() {
    assertFalse(
        OverwriteGuard.shouldOverwrite(payload("2.0.0", "a"), payload("1.0.0", "b"), mapper),
        "迟到旧版本发布：不得覆盖新内容（多实例并发发布/滚动发布乱序）");
  }

  @Test
  void shouldAllowIdempotentSameContentRewrite() {
    String content = payload("1.0.0", "a");
    assertTrue(OverwriteGuard.shouldOverwrite(content, content, mapper),
        "同版本同内容：幂等重写无害");
  }

  @Test
  void shouldRejectSameVersionDifferentContent() {
    assertFalse(OverwriteGuard.shouldOverwrite(
        payload("1.0.0", "a"), payload("1.0.0", "b"), mapper),
        "同版本不同内容：拒绝覆盖（滚动升级中间态防抖动，需人工介入排查冲突）");
  }

  @Test
  void shouldTreatPreReleaseLowerThanRelease() {
    assertFalse(OverwriteGuard.shouldOverwrite(
        payload("2.0.0", "a"), payload("2.0.0-RC1", "b"), mapper),
        "预发布版本低于同名正式版本：不得覆盖");
    assertTrue(OverwriteGuard.shouldOverwrite(
        payload("2.0.0-RC1", "a"), payload("2.0.0", "b"), mapper),
        "正式版本高于预发布：允许覆盖");
  }

  @Test
  void shouldOverwriteMalformedExistingContent() {
    assertTrue(
        OverwriteGuard.shouldOverwrite("not-json", payload("1.0.0", "a"), mapper),
        "旧内容损坏：视为无效，允许重写修复");
  }
}
