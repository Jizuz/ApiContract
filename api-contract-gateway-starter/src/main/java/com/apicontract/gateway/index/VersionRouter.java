package com.apicontract.gateway.index;

import com.apicontract.api.meta.ContractMeta;

/**
 * 版本路由（spec：请求可指定版本，未指定时按默认版本选取）：
 * 显式版本 → mt+version 精确寻址；未指定 → 契约主索引默认（最新稳定）。
 */
public final class VersionRouter {

  private VersionRouter() {
  }

  /**
   * 路由到目标契约条目。
   *
   * @param indexes 三级索引
   * @param contractName 契约标识
   * @param requestedVersion 请求版本（null/空 = 默认版本）
   * @return 命中条目；未命中返回 null（CONTRACT_NOT_FOUND 语义）
   */
  public static ContractMeta route(ContractIndexes indexes, String contractName,
      String requestedVersion) {
    if (requestedVersion == null || requestedVersion.isBlank()) {
      return indexes.find(contractName);
    }
    return indexes.findVersioned(contractName, requestedVersion);
  }

  /** 稳定版本判定：无预发布/标签后缀（不含 '-'）。 */
  static boolean isStable(String version) {
    return version != null && !version.contains("-");
  }

  /** 语义化版本比较（semver）：主段数字比较；预发布低于同名正式版本。 */
  static int compareVersions(String left, String right) {
    String leftCore = coreOf(left);
    String rightCore = coreOf(right);
    int cmp = compareSegments(leftCore.split("\\."), rightCore.split("\\."));
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
