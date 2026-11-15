package com.apicontract.it.bench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/**
 * 压测样本统计器（任务26）：线程安全收集成功/错误/超时样本与 RT，
 * 输出 QPS、分位数（最近邻秩）、超时率——验收判定的唯一数据源。
 *
 * <p>采样路径零锁争抢（ThreadLocal 缓冲 + LongAdder 计数），避免
 * 统计器本身扭曲被测链路的吞吐与延迟；分位数在测量结束后合并计算。
 */
public final class BenchStats {

  private enum Kind {
    SUCCESS, ERROR, TIMEOUT
  }

  private record Sample(double rtMillis, Kind kind) {
  }

  private final LongAdder success = new LongAdder();
  private final LongAdder error = new LongAdder();
  private final LongAdder timeout = new LongAdder();
  private final List<List<Sample>> buffers = new CopyOnWriteArrayList<>();
  private final ThreadLocal<List<Sample>> local =
      ThreadLocal.withInitial(() -> {
        List<Sample> buffer = new ArrayList<>(4_096);
        buffers.add(buffer);
        return buffer;
      });

  /** 记录一次成功调用（rtMillis 为端到端耗时）。 */
  public void recordSuccess(double rtMillis) {
    local.get().add(new Sample(rtMillis, Kind.SUCCESS));
    success.increment();
  }

  /** 记录一次失败调用（网络/业务错误，非超时口径）。 */
  public void recordError(double rtMillis) {
    local.get().add(new Sample(rtMillis, Kind.ERROR));
    error.increment();
  }

  /** 记录一次显式超时（整体超时上限触发）。 */
  public void recordTimeout(double rtMillis) {
    local.get().add(new Sample(rtMillis, Kind.TIMEOUT));
    timeout.increment();
  }

  /** 完成样本总数（成功 + 错误 + 超时，吞吐口径基数）。 */
  public long totalSamples() {
    return success.sum() + error.sum() + timeout.sum();
  }

  /** 显式超时样本数。 */
  public long timeoutCount() {
    return timeout.sum();
  }

  /** 错误样本数（非超时口径的失败）。 */
  public long errorCount() {
    return error.sum();
  }

  /** 吞吐（ops/s）：elapsedMillis 内完成的请求数。 */
  public double qps(long elapsedMillis) {
    if (elapsedMillis <= 0) {
      throw new IllegalArgumentException("统计时长必须为正: " + elapsedMillis);
    }
    return totalSamples() * 1_000.0 / elapsedMillis;
  }

  /**
   * 超时率：显式超时样本 + RT 超过阈值的成功/错误样本，除以总样本
   * （验收口径：契约级整体超时内未完成即超时）。
   */
  public double timeoutRate(double thresholdMillis) {
    long total = totalSamples();
    if (total == 0) {
      return 0.0;
    }
    long slow = 0;
    for (List<Sample> buffer : buffers) {
      List<Sample> snapshot;
      synchronized (buffer) {
        snapshot = new ArrayList<>(buffer);
      }
      for (Sample sample : snapshot) {
        if (sample.kind() != Kind.TIMEOUT
            && sample.rtMillis() > thresholdMillis) {
          slow++;
        }
      }
    }
    long timedOut = timeout.sum() + slow;
    return (double) timedOut / total;
  }

  /**
   * 分位数（最近邻秩：升序第 ceil(p*n) 个样本）；无样本返回 0。
   */
  public double percentile(double quantile) {
    if (quantile <= 0 || quantile > 1) {
      throw new IllegalArgumentException("分位数必须在 (0,1] 区间: " + quantile);
    }
    List<Double> rts = new ArrayList<>((int) totalSamples());
    for (List<Sample> buffer : buffers) {
      synchronized (buffer) {
        for (Sample sample : buffer) {
          rts.add(sample.rtMillis());
        }
      }
    }
    if (rts.isEmpty()) {
      return 0.0;
    }
    Collections.sort(rts);
    int rank = (int) Math.ceil(quantile * rts.size());
    return rts.get(Math.min(rank, rts.size()) - 1);
  }
}
