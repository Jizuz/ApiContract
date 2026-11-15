package com.apicontract.gateway.remote;

/**
 * 连接池参数（design：max-connections=500、每主机连接上限、max-idle=30s、
 * acquire-timeout=1000ms；等待队列有界，超限快速失败不排队放大）。
 *
 * <p>池按目标地址维度维护长连接 keep-alive 复用；全局 maxConnections 为
 * 容量规划基线（≈每主机上限 × 预期目标主机数），异步实现以每主机上限
 * 作为单地址池上限。
 *
 * @param maxConnections 全局最大连接数（容量基线，默认 500）
 * @param maxConnectionsPerHost 每主机连接上限（默认 100）
 * @param maxIdleMillis 最大空闲时间（默认 30s，超时空闲连接回收）
 * @param acquireTimeoutMillis 获取连接等待超时（默认 1000ms，超时快速失败）
 * @param pendingAcquireMaxCount 等待队列上限（有界，默认 1000）
 */
public record ConnectionPoolSettings(
    int maxConnections,
    int maxConnectionsPerHost,
    long maxIdleMillis,
    long acquireTimeoutMillis,
    int pendingAcquireMaxCount) {

  /** 设计默认值（design 异步流程设计参数）。 */
  public static final ConnectionPoolSettings DEFAULT = new ConnectionPoolSettings(
      500, 100, 30_000, 1_000, 1_000);

  public ConnectionPoolSettings {
    if (maxConnections <= 0 || maxConnectionsPerHost <= 0 || maxIdleMillis <= 0
        || acquireTimeoutMillis <= 0 || pendingAcquireMaxCount <= 0) {
      throw new IllegalArgumentException(
          "连接池参数必须有界为正（快速失败语义）: " + this);
    }
  }
}
