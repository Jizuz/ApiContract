package com.apicontract.api.serialization.policy;

import com.apicontract.api.meta.ContractMeta;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 序列化方式三级声明（spec 分支场景：全局默认 json、服务级声明、
 * 契约级覆盖；优先级 契约 > 服务 > 全局）。
 *
 * <p>两侧（网关/Provider）各自构建本地策略；元数据声明（已固化的
 * serializer 字段）为生效事实源，本地策略用于启动一致性比对与
 * 未声明契约的默认解析。
 */
public final class SerializationPolicy {

  private final String global;
  private final Map<String, String> services;
  private final Map<String, String> contracts;

  private SerializationPolicy(String global, Map<String, String> services,
      Map<String, String> contracts) {
    this.global = global;
    this.services = services;
    this.contracts = contracts;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * 三级解析：契约级 > 服务级 > 全局 > 默认 json。
   *
   * @param contractName 契约标识（mt）
   * @param serviceName 归属服务名（可为 null）
   */
  public String resolve(String contractName, String serviceName) {
    String contractLevel = contracts.get(contractName);
    if (contractLevel != null) {
      return contractLevel;
    }
    if (serviceName != null) {
      String serviceLevel = services.get(serviceName);
      if (serviceLevel != null) {
        return serviceLevel;
      }
    }
    return global != null ? global : ContractMeta.SERIALIZER_JSON;
  }

  /** 本地契约级覆盖声明（无则 null；一致性比对用）。 */
  public String contractLevelOf(String contractName) {
    return contracts.get(contractName);
  }

  /** 校验并构建三级声明（非法值 fail-fast，防脏配置上线）。 */
  public static final class Builder {

    private String global;
    private final Map<String, String> services = new LinkedHashMap<>();
    private final Map<String, String> contracts = new LinkedHashMap<>();

    public Builder global(String serializer) {
      this.global = requireLegal(serializer, "全局");
      return this;
    }

    public Builder service(String serviceName, String serializer) {
      if (serviceName == null || serviceName.isBlank()) {
        throw new IllegalArgumentException("服务级声明缺少服务名");
      }
      services.put(serviceName, requireLegal(serializer, "服务级 " + serviceName));
      return this;
    }

    public Builder contract(String contractName, String serializer) {
      if (contractName == null || contractName.isBlank()) {
        throw new IllegalArgumentException("契约级声明缺少契约标识");
      }
      contracts.put(contractName, requireLegal(serializer, "契约级 " + contractName));
      return this;
    }

    public SerializationPolicy build() {
      return new SerializationPolicy(global, Map.copyOf(services),
          Map.copyOf(contracts));
    }

    private static String requireLegal(String serializer, String level) {
      if (!ContractMeta.SERIALIZER_JSON.equals(serializer)
          && !ContractMeta.SERIALIZER_PROTOBUF.equals(serializer)) {
        throw new IllegalArgumentException(
            level + "序列化声明非法: " + serializer + "（仅 json/protobuf）");
      }
      return serializer;
    }
  }
}
