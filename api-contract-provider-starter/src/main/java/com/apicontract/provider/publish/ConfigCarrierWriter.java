package com.apicontract.provider.publish;

/**
 * 配置中心写入端口：隔离 nacos-client（optional 依赖），由装配层在
 * nacos-client 存在时提供真实实现；测试与降级场景提供替代实现。
 */
@FunctionalInterface
public interface ConfigCarrierWriter {

  /**
   * 覆盖写入配置条目（契约元数据按服务维度一份）。
   *
   * @param group 固定分组 CONTRACT
   * @param dataId {@code {serviceName}-contract-meta}
   * @param content 契约集合与聚合索引 JSON
   */
  void write(String group, String dataId, String content);

  /**
   * 读取现有条目内容（防覆盖乐观比较用）。
   * 默认返回 null（端口不支持读取时跳过防覆盖防护，退化为直接写入）。
   *
   * @param dataId 条目标识
   * @return 现有内容；不存在返回 null
   */
  default String read(String dataId) {
    return null;
  }
}
