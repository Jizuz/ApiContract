package com.apicontract.provider.publish;

import java.util.Map;

/**
 * 实例元数据写入端口：由装配层接注册中心客户端（optional），写极简标记。
 */
@FunctionalInterface
public interface InstanceMetadataWriter {

  /** 覆盖写入实例元数据（仅 contract-version、contract-count 等极简键值）。 */
  void write(Map<String, String> metadata);
}
