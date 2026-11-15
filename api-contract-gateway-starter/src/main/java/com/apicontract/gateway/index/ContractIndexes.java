package com.apicontract.gateway.index;

import com.apicontract.api.meta.ContractMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 网关契约三级本地索引（design 三级索引结构 + 多版本共存）：
 * 契约主索引（mt→默认版本条目）、服务索引（服务→版本→标识集合）、
 * 版本索引（mt+version→条目）。
 *
 * <p>一致性策略：配置推送解析后按「服务+版本」维度<b>原子替换</b>
 * （volatile 快照整体切换，清理已被删除的契约与版本），刷新期间读路径
 * 始终可用、无请求错误尖刺；默认版本 = 最新稳定（预发布不参与默认，
 * 仅有预发布时取最大预发布）；契约索引与实例地址列表分离；
 * 条目按 mt+版本计数设上限并监控告警（超限推送拒绝、保留最近快照）。
 */
public final class ContractIndexes {

  /** 索引不可变快照（读无锁）。 */
  private record Snapshot(
      Map<String, Map<String, ContractMeta>> entries,
      Map<String, Map<String, List<String>>> serviceVersions,
      Map<String, ContractMeta> mainIndex,
      Map<String, ContractMeta> versionIndex) {

    static final Snapshot EMPTY = new Snapshot(
        Map.of(), Map.of(), Map.of(), Map.of());
  }

  private final int maxEntries;
  private final Consumer<String> warnLine;
  private volatile Snapshot snapshot = Snapshot.EMPTY;

  public ContractIndexes(int maxEntries) {
    this(maxEntries, line -> { });
  }

  public ContractIndexes(int maxEntries, Consumer<String> warnLine) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("索引条目上限必须为正数: " + maxEntries);
    }
    this.maxEntries = maxEntries;
    this.warnLine = warnLine;
  }

  /** 应用一次服务契约配置推送：解析 payload 并按服务+版本原子替换。 */
  public synchronized void applyPayload(String payloadJson, ObjectMapper payloadMapper) {
    ParsedPayload parsed = parse(payloadJson, payloadMapper);
    Snapshot current = snapshot;
    int retained = current.versionIndex.size()
        - oldVersionedEntriesOf(current, parsed.serviceName, parsed.version);
    if (retained + parsed.contracts.size() > maxEntries) {
      warnLine.accept("契约条目超过上限(" + maxEntries + ")：拒绝服务 "
          + parsed.serviceName + " 版本 " + parsed.version + " 本次推送（"
          + parsed.contracts.size() + " 条），保留最近快照");
      return;
    }
    snapshot = rebuild(current, parsed);
  }

  /** 契约主索引查找（默认版本条目）：未命中返回 null。 */
  public ContractMeta find(String contractName) {
    return snapshot.mainIndex.get(contractName);
  }

  /** 版本索引查找（mt+version 精确寻址）。 */
  public ContractMeta findVersioned(String contractName, String version) {
    return snapshot.versionIndex.get(contractName + "@" + version);
  }

  /** 服务索引：服务名 → 契约标识集合（跨版本去重、有序）。 */
  public List<String> contractsOfService(String serviceName) {
    Map<String, List<String>> versions =
        snapshot.serviceVersions.getOrDefault(serviceName, Map.of());
    List<String> names = new ArrayList<>();
    versions.values().forEach(names::addAll);
    return names.stream().distinct().toList();
  }

  /** 契约的已发布版本列表（升序）。 */
  public List<String> versionsOf(String contractName) {
    Map<String, ContractMeta> byVersion = snapshot.entries.get(contractName);
    if (byVersion == null) {
      return List.of();
    }
    return byVersion.keySet().stream()
        .sorted(VersionRouter::compareVersions)
        .toList();
  }

  /** 实例地址列表视图（与契约索引分离，由实例订阅任务维护）。 */
  public Map<String, List<String>> instanceAddresses() {
    return Map.of();
  }

  private static int oldVersionedEntriesOf(Snapshot current, String serviceName,
      String version) {
    return current.serviceVersions
        .getOrDefault(serviceName, Map.of())
        .getOrDefault(version, List.of())
        .size();
  }

  private static Snapshot rebuild(Snapshot current, ParsedPayload parsed) {
    Map<String, Map<String, ContractMeta>> entries = new LinkedHashMap<>();
    current.entries.forEach((mt, byVersion) ->
        entries.put(mt, new LinkedHashMap<>(byVersion)));
    Map<String, Map<String, List<String>>> serviceVersions = new LinkedHashMap<>();
    current.serviceVersions.forEach((service, byVersion) ->
        serviceVersions.put(service, new LinkedHashMap<>(byVersion)));

    List<String> outdated = serviceVersions
        .getOrDefault(parsed.serviceName, Map.of())
        .getOrDefault(parsed.version, List.of());
    for (String mt : outdated) {
      Map<String, ContractMeta> byVersion = entries.get(mt);
      if (byVersion != null) {
        byVersion.remove(parsed.version);
        if (byVersion.isEmpty()) {
          entries.remove(mt);
        }
      }
    }

    List<String> names = new ArrayList<>();
    for (ContractMeta meta : parsed.contracts) {
      entries.computeIfAbsent(meta.contractName(), key -> new LinkedHashMap<>())
          .put(meta.version(), meta);
      names.add(meta.contractName());
    }
    serviceVersions.computeIfAbsent(parsed.serviceName, key -> new LinkedHashMap<>())
        .put(parsed.version, List.copyOf(names));

    return deriveSnapshot(entries, serviceVersions);
  }

  /** 由原始存储派生三级索引快照。 */
  private static Snapshot deriveSnapshot(
      Map<String, Map<String, ContractMeta>> entries,
      Map<String, Map<String, List<String>>> serviceVersions) {
    Map<String, ContractMeta> main = new LinkedHashMap<>();
    Map<String, ContractMeta> versions = new LinkedHashMap<>();
    entries.forEach((mt, byVersion) -> {
      byVersion.forEach((version, meta) ->
          versions.put(mt + "@" + version, meta));
      main.put(mt, byVersion.get(defaultVersionOf(byVersion.keySet())));
    });
    return new Snapshot(freezeEntries(entries), freezeServiceVersions(serviceVersions),
        Map.copyOf(main), Map.copyOf(versions));
  }

  /** 默认版本 = 最新稳定；无稳定版本时取最大预发布。 */
  private static String defaultVersionOf(java.util.Set<String> versions) {
    List<String> stable = versions.stream()
        .filter(VersionRouter::isStable).toList();
    List<String> pool = stable.isEmpty()
        ? new ArrayList<>(versions) : stable;
    return pool.stream()
        .max(VersionRouter::compareVersions)
        .orElseThrow(() -> new IllegalStateException("版本集合为空"));
  }

  private static Map<String, Map<String, ContractMeta>> freezeEntries(
      Map<String, Map<String, ContractMeta>> entries) {
    Map<String, Map<String, ContractMeta>> frozen = new LinkedHashMap<>();
    entries.forEach((mt, byVersion) -> frozen.put(mt, Map.copyOf(byVersion)));
    return Map.copyOf(frozen);
  }

  private static Map<String, Map<String, List<String>>> freezeServiceVersions(
      Map<String, Map<String, List<String>>> serviceVersions) {
    Map<String, Map<String, List<String>>> frozen = new LinkedHashMap<>();
    serviceVersions.forEach((service, byVersion) ->
        frozen.put(service, Map.copyOf(byVersion)));
    return Map.copyOf(frozen);
  }

  private record ParsedPayload(String serviceName, String version,
      List<ContractMeta> contracts) {
  }

  private static ParsedPayload parse(String payloadJson, ObjectMapper payloadMapper) {
    try {
      JsonNode root = payloadMapper.readTree(payloadJson);
      String serviceName = root.path("serviceName").asText();
      String version = root.path("version").asText();
      List<ContractMeta> contracts = new ArrayList<>();
      for (JsonNode entry : root.path("contracts")) {
        contracts.add(payloadMapper.treeToValue(entry, ContractMeta.class));
      }
      if (serviceName == null || serviceName.isBlank()) {
        throw new IllegalArgumentException("payload 缺少 serviceName");
      }
      if (version == null || version.isBlank()) {
        throw new IllegalArgumentException("payload 缺少 version");
      }
      return new ParsedPayload(serviceName, version, List.copyOf(contracts));
    } catch (IllegalArgumentException failure) {
      throw failure;
    } catch (Exception malformed) {
      throw new IllegalArgumentException("契约配置 payload 解析失败", malformed);
    }
  }
}
