package com.apicontract.provider.serialization;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.serialization.SerializationException;
import com.apicontract.api.serialization.protobuf.ContractProtoSchemas;
import com.apicontract.api.serialization.protobuf.ProtobufContractSerializer;
import java.util.Map;

/**
 * Provider 侧契约序列化路由：按契约元数据声明选择网关↔Provider 内部
 * 链路编解码器（json/protobuf），按契约粒度切换与回退互不影响
 * （与网关侧同语义，一致性由启动校验保证）。
 */
public final class ContractCodecs {

  private final Map<String, ContractSerializer> serializers;
  private final JsonContractSerializer jsonSerializer;

  private ContractCodecs(Map<String, ContractSerializer> serializers) {
    this.serializers = serializers;
    this.jsonSerializer = JsonContractSerializer.INSTANCE;
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

  /** JSON 编解码器（联调、内部后台场景始终可读可抓包）。 */
  public JsonContractSerializer json() {
    return jsonSerializer;
  }
}
