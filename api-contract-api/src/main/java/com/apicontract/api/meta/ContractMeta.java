package com.apicontract.api.meta;

import java.util.List;

/**
 * 契约条目：对外可调用的能力单位（规格 contract-definition 输出定义）。
 *
 * <p>字段与网关侧配置中心元数据结构一致（见 design.md 接口契约）：
 * 标识、接口全名、方法名、参数名与类型、返回类型、归属服务、版本、标签、
 * 序列化方式、Protobuf schema 引用、契约级超时覆盖。
 *
 * @param contractName 契约标识（mt），全局唯一
 * @param interfaceName 契约接口全限定名
 * @param methodName 方法名（契约方法禁止重载）
 * @param paramNames 参数名列表（按名取值依赖编译期 -parameters）
 * @param paramTypes 参数类型全限定名列表
 * @param returnType 返回类型全限定名
 * @param serviceName 归属服务名（网关寻址目标服务）
 * @param version 契约版本（默认 1.0.0）
 * @param tags 实例标签（如 gray、canary，用于标签路由）
 * @param serializer 序列化方式：json（默认）/ protobuf
 * @param schemaRef Protobuf schema 引用（未声明为 null）
 * @param timeout 契约级超时毫秒（未声明为 null，使用全局默认）
 */
public record ContractMeta(
    String contractName,
    String interfaceName,
    String methodName,
    List<String> paramNames,
    List<String> paramTypes,
    String returnType,
    String serviceName,
    String version,
    List<String> tags,
    String serializer,
    String schemaRef,
    Long timeout) {

  /** 默认序列化方式：json（新契约先以 JSON 上线验证，再按契约粒度灰度切换）。 */
  public static final String SERIALIZER_JSON = "json";

  /** 二进制序列化方式：protobuf（仅网关与 Provider 之间）。 */
  public static final String SERIALIZER_PROTOBUF = "protobuf";
}
