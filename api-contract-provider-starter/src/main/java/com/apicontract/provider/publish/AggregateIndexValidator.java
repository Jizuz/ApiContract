package com.apicontract.provider.publish;

import java.util.Map;
import java.util.TreeMap;

/**
 * 聚合索引跨服务判重（规格 contract-metadata：契约标识冲突 CONTRACT_CONFLICT，
 * 发布期聚合索引校验，冲突中断启动）。
 *
 * <p>校验规则：聚合索引中契约标识已被<b>其他服务</b>占用即冲突；本服务重发布
 * （重启/升级）不视为冲突。一次报出全部冲突，避免多轮重启逐个发现。
 */
public final class AggregateIndexValidator {

  private AggregateIndexValidator() {
  }

  /**
   * 发布前校验。
   *
   * @param bundle   本服务发布包
   * @param existing 配置中心现有聚合索引（mt → 归属服务名）
   * @throws ContractConflictException 存在跨服务标识冲突
   */
  public static void validate(ContractMetaBundle bundle, Map<String, String> existing) {
    bundle.validate();
    Map<String, String> conflicts = new TreeMap<>();
    bundle.aggregateIndex().forEach((mt, owner) -> {
      String currentOwner = existing.get(mt);
      if (currentOwner != null && !currentOwner.equals(owner)) {
        conflicts.put(mt, currentOwner);
      }
    });
    if (conflicts.isEmpty()) {
      return;
    }
    StringBuilder detail = new StringBuilder();
    conflicts.forEach((mt, holder) ->
        detail.append(detail.length() == 0 ? "" : "; ")
            .append(mt).append(" 已被 ").append(holder).append(" 占用"));
    throw new ContractConflictException("契约标识冲突(CONTRACT_CONFLICT): 服务 "
        + bundle.serviceName() + " 发布被拒绝, " + detail
        + "；请修正标识后重新发布");
  }
}
