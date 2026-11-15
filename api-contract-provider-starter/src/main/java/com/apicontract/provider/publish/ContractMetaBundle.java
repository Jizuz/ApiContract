package com.apicontract.provider.publish;

import com.apicontract.api.meta.ContractMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务契约发布包（规格 contract-metadata 输入定义）：服务名、版本、
 * 契约条目列表及聚合索引；按服务维度一份，全实例共享。
 *
 * <p>发布内容结构与 design.md 配置中心条目一致：
 * {@code {serviceName, version, contracts[], aggregateIndex{mt→serviceName}}}，
 * 聚合索引随契约集合一并发布，支持网关侧跨服务契约标识快速判重。
 */
public record ContractMetaBundle(
    String serviceName,
    String version,
    List<ContractMeta> contracts,
    Map<String, String> aggregateIndex) {

  /** 规范构造器：先赋值后校验（空契约集合不允许发布，空清单不发布不注册入口）。 */
  public ContractMetaBundle(String serviceName, String version, List<ContractMeta> contracts,
      Map<String, String> aggregateIndex) {
    this.serviceName = serviceName;
    this.version = version;
    this.contracts = List.copyOf(contracts);
    this.aggregateIndex = Map.copyOf(aggregateIndex);
    validate();
  }

  /** 校验发布包合法性：服务名、版本非空且契约集合非空。 */
  public void validate() {
    if (serviceName == null || serviceName.isBlank()) {
      throw new IllegalArgumentException("发布包缺少 serviceName");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("发布包缺少 version");
    }
    if (contracts == null || contracts.isEmpty()) {
      throw new IllegalArgumentException("空契约集合不允许发布（空清单不发布不注册入口）");
    }
  }

  /** 组装配置中心条目 JSON 内容（含契约集合与聚合索引）。 */
  public String toJson(ObjectMapper mapper) {
    validate();
    ObjectNode root = mapper.createObjectNode();
    root.put("serviceName", serviceName);
    root.put("version", version);
    ArrayNode entries = root.putArray("contracts");
    for (ContractMeta meta : contracts) {
      entries.addPOJO(meta);
    }
    Map<String, String> orderedIndex = new LinkedHashMap<>();
    aggregateIndex.forEach(orderedIndex::put);
    root.putPOJO("aggregateIndex", orderedIndex);
    return root.toString();
  }
}
