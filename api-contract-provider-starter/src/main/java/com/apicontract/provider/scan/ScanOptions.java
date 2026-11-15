package com.apicontract.provider.scan;

import java.util.Map;

/**
 * 契约扫描选项。
 *
 * @param serviceName 归属服务名（写入契约条目，供网关寻址）
 * @param version 契约版本，空时默认 1.0.0
 * @param explicitImplBeans 多实现时显式指定的唯一实现 Bean 名（接口 -&gt; Bean 名）；
 *     规格要求：一个白名单接口只允许一个实现参与发布，多实现必须显式指定唯一实现
 */
public record ScanOptions(String serviceName, String version, Map<Class<?>, String> explicitImplBeans) {

  /** 默认契约版本（规格 contract-definition：契约版本默认 1.0.0）。 */
  public static final String DEFAULT_VERSION = "1.0.0";

  /** 无显式指定实现的扫描选项。 */
  public static ScanOptions of(String serviceName) {
    return new ScanOptions(serviceName, DEFAULT_VERSION, Map.of());
  }
}
