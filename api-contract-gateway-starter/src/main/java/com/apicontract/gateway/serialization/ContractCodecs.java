package com.apicontract.gateway.serialization;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.serialization.SerializationException;
import com.apicontract.api.serialization.protobuf.ContractProtoSchemas;
import com.apicontract.api.serialization.protobuf.ProtobufContractSerializer;
import java.util.Map;

/**
 * 网关侧契约序列化路由：按契约元数据声明选择网关↔Provider 内部链路
 * 编解码器（json/protobuf），按契约粒度切换与回退互不影响；
 * 前端↔网关恒 JSON（已确认决策③：前端不解析二进制）。
 */
public final class ContractCodecs {

  private final Map<String, ContractSerializer> serializers;
  private final JsonContractSerializer frontendJson;

  private ContractCodecs(Map<String, ContractSerializer> serializers) {
    this.serializers = serializers;
    this.frontendJson = JsonContractSerializer.INSTANCE;
  }

  /** 默认编解码器集：json + protobuf（protobuf 按注册 schema 动态编解码）。 */
  public static ContractCodecs withDefaults(ContractProtoSchemas schemas) {
    return new ContractCodecs(Map.of(
        ContractMeta.SERIALIZER_JSON, JsonContractSerializer.INSTANCE,
        ContractMeta.SERIALIZER_PROTOBUF, new ProtobufContractSerializer(schemas)));
  }

  /** 契约生效编解码器：声明 null → json；未知声明显式失败不静默。 */
  public ContractSerializer effectiveFor(ContractMeta meta) {
    String declared = meta.serializer();
    String name = declared == null || declared.isBlank()
        ? ContractMeta.SERIALIZER_JSON : declared;
    ContractSerializer serializer = serializers.get(name);
    if (serializer == null) {
      throw new SerializationException("契约 " + meta.contractName()
          + " 声明了未知序列化方式: " + name + "（仅 json/protobuf）");
    }
    return serializer;
  }

  /** 前端侧编解码器：恒 JSON（决策③，网关完成形态转换前端零感知）。 */
  public JsonContractSerializer frontendJson() {
    return frontendJson;
  }
}
