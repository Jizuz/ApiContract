package com.apicontract.provider.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 并发发布防覆盖守卫（design 一致性策略 2）：发布前按版本号/内容
 * 乐观比较，旧内容不得覆盖新内容。
 *
 * <p>规则：无旧内容 → 允许；新版本 &gt; 旧版本 → 允许；旧版本迟到 → 拒绝；
 * 同版本同内容 → 幂等允许；同版本不同内容 → 拒绝（滚动升级中间态防抖动）；
 * 旧内容损坏 → 允许重写修复。
 */
public final class OverwriteGuard {

  private OverwriteGuard() {
  }

  /**
   * 是否允许以 incoming 覆盖 existing。
   *
   * @param existingPayload 配置中心现有内容（null/空 = 首次发布）
   * @param incomingPayload 本次待发布内容
   * @return true 允许写入；false 拒绝（旧不覆盖新）
   */
  public static boolean shouldOverwrite(String existingPayload, String incomingPayload,
      ObjectMapper mapper) {
    if (existingPayload == null || existingPayload.isBlank()) {
      return true;
    }
    String existingVersion = extractVersion(existingPayload, mapper);
    if (existingVersion == null) {
      return true;
    }
    String incomingVersion = extractVersion(incomingPayload, mapper);
    if (incomingVersion == null) {
      throw new IllegalArgumentException("待发布内容缺少 version，拒绝写入");
    }
    int cmp = compareVersions(incomingVersion, existingVersion);
    if (cmp > 0) {
      return true;
    }
    if (cmp < 0) {
      return false;
    }
    return normalize(incomingPayload, mapper).equals(normalize(existingPayload, mapper));
  }

  private static String extractVersion(String payload, ObjectMapper mapper) {
    try {
      JsonNode root = mapper.readTree(payload);
      JsonNode version = root.path("version");
      return version.isMissingNode() || version.asText().isBlank() ? null
          : version.asText();
    } catch (Exception malformed) {
      return null;
    }
  }

  /** 规范化内容（字段序无关）用于等价比较。 */
  private static String normalize(String payload, ObjectMapper mapper) {
    try {
      return mapper.readTree(payload).toString();
    } catch (Exception malformed) {
      return payload;
    }
  }

  /** 语义化版本比较（semver）：主段数字比较；预发布低于同名正式版本。 */
  static int compareVersions(String left, String right) {
    String leftCore = coreOf(left);
    String rightCore = coreOf(right);
    int cmp = compareCore(leftCore, rightCore);
    if (cmp != 0) {
      return cmp;
    }
    String leftPre = preOf(left);
    String rightPre = preOf(right);
    if (leftPre == null && rightPre == null) {
      return 0;
    }
    if (leftPre == null) {
      return 1;
    }
    if (rightPre == null) {
      return -1;
    }
    return compareSegments(leftPre.split("\\."), rightPre.split("\\."));
  }

  private static String coreOf(String version) {
    int dash = version.indexOf('-');
    return dash < 0 ? version : version.substring(0, dash);
  }

  private static String preOf(String version) {
    int dash = version.indexOf('-');
    return dash < 0 ? null : version.substring(dash + 1);
  }

  private static int compareCore(String left, String right) {
    return compareSegments(left.split("\\."), right.split("\\."));
  }

  private static int compareSegments(String[] a, String[] b) {
    int length = Math.max(a.length, b.length);
    for (int i = 0; i < length; i++) {
      String partA = i < a.length ? a[i] : "0";
      String partB = i < b.length ? b[i] : "0";
      int cmp;
      if (partA.matches("\\d+") && partB.matches("\\d+")) {
        cmp = Integer.compare(Integer.parseInt(partA), Integer.parseInt(partB));
      } else {
        cmp = partA.compareTo(partB);
      }
      if (cmp != 0) {
        return cmp;
      }
    }
    return 0;
  }
}
