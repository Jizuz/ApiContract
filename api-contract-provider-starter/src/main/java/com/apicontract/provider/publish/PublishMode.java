package com.apicontract.provider.publish;

/**
 * 元数据发布模式（配置 {@code api-contract.meta.publish-mode}，
 * 回退方向 nacosConfig → nacosMetadata → logOnly）。
 */
public enum PublishMode {

  /** 配置中心权威存储（正式链路）。 */
  NACOS_CONFIG("nacosConfig"),

  /** 实例元数据旧链路（回退）。 */
  NACOS_METADATA("nacosMetadata"),

  /** 仅日志（排障/灰度试点首步）。 */
  LOG_ONLY("logOnly");

  private final String configValue;

  PublishMode(String configValue) {
    this.configValue = configValue;
  }

  /** 配置文件取值（小驼峰）。 */
  public String configValue() {
    return configValue;
  }

  /** 宽松解析配置值：大小写/空白不敏感，null 或未知值默认 logOnly。 */
  public static PublishMode fromValue(String value) {
    if (value == null) {
      return LOG_ONLY;
    }
    String normalized = value.trim().toLowerCase().replace('-', '_');
    for (PublishMode mode : values()) {
      if (mode.name().equalsIgnoreCase(normalized)
          || mode.configValue.equalsIgnoreCase(normalized)) {
        return mode;
      }
    }
    return LOG_ONLY;
  }
}
