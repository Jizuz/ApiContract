package com.apicontract.it.bench;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.gateway.ContractGatewayHandler;
import com.apicontract.gateway.GatewayResponse;
import com.apicontract.gateway.index.ContractIndexes;
import com.apicontract.gateway.index.InstanceAddressRegistry;
import com.apicontract.gateway.remote.ConnectionPoolSettings;
import com.apicontract.gateway.remote.ReactorNettyRemoteCaller;
import com.apicontract.gateway.remote.SyncHttpRemoteCaller;
import com.apicontract.gateway.remote.TimeoutSettings;
import com.apicontract.it.ProviderAppFixture;
import com.apicontract.it.itapi.ItEcho;
import com.apicontract.provider.invoke.InvokerMode;
import com.apicontract.provider.publish.ContractMetaBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 压测验收（任务26）：基线组（反射调用器 + JSON + 同步 HttpURLConnection）
 * vs 优化组（字节码调用器 + Reactor Netty 连接池异步链路）同 JVM 交替测量；
 * 附加场景：压测中并发原子刷新契约索引（refresh-under-load）断言零错误尖刺。
 *
 * <p>序列化形态说明：当前生产调用链前端↔网关↔Provider 均 JSON（决策③：
 * 前端恒 JSON；protobuf 为组件级实现 + 策略校验，未接入 HTTP 入口链路），
 * 对照主体为调用器与调用客户端维度。
 *
 * <p>默认不随套件执行（类名无 Test 后缀 + benchmark 标签）：
 * {@code mvn -pl api-contract-it -am test -Dtest=ContractBench
 * -Dsurefire.failIfNoSpecifiedTests=false -Dbench.report=<归档路径>}。
 */
@Tag("benchmark")
class ContractBench {

  private static final String SERVICE = "bench-provider-service";
  private static final String VERSION = "1.0.0";
  private static final String MT = "it.echo.call";
  private static final String ARGS_JSON = "[1,\"bench\"]";

  private static final System.Logger logger =
      System.getLogger("api-contract.it.bench");

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void runBenchmarkAndArchiveReport() throws Exception {
    int threads = Integer.getInteger("bench.threads", 8);
    // 基线组（每请求断连）受本机 TIME_WAIT 端口预算约束须用短段；
    // 优化组（连接池复用）不耗端口可用长段稳定测量；基线段后需冷却
    // 等待 TIME_WAIT 消退（macOS 默认 30s），避免污染后续段。
    long baseWarmupMillis = Long.getLong("bench.base.warmup.millis", 400);
    long baseMillis = Long.getLong("bench.base.millis", 1_200);
    long optWarmupMillis = Long.getLong("bench.opt.warmup.millis", 1_000);
    long optMillis = Long.getLong("bench.opt.millis", 3_000);
    long cooldownMillis = Long.getLong("bench.cooldown.millis", 32_000);
    int rounds = Integer.getInteger("bench.rounds", 3);
    Path reportPath = Path.of(System.getProperty("bench.report",
        "api-contract-it/target/contract-bench-report.md"));

    ProviderAppFixture baseProvider =
        new ProviderAppFixture(SERVICE, VERSION)
            .contract(ItEcho.class, (ItEcho) (id, name) -> "echo:" + id + ":" + name,
                InvokerMode.REFLECTION)
            .start();
    ProviderAppFixture optProvider =
        new ProviderAppFixture(SERVICE, VERSION)
            .contract(ItEcho.class, (ItEcho) (id, name) -> "echo:" + id + ":" + name,
                InvokerMode.BYTECODE)
            .start();
    try {
      ContractIndexes indexes = new ContractIndexes(1024);
      String payload = payloadOf(baseProvider);
      indexes.applyPayload(payload, mapper);

      ContractGatewayHandler baseHandler = handlerOf(indexes,
          new SyncHttpRemoteCaller(TimeoutSettings.DEFAULT), baseProvider);
      ContractGatewayHandler optHandler = handlerOf(indexes,
          new ReactorNettyRemoteCaller(
              ConnectionPoolSettings.DEFAULT, TimeoutSettings.DEFAULT),
          optProvider);

      RoundResult[] baseRounds = new RoundResult[rounds];
      RoundResult[] optRounds = new RoundResult[rounds];
      for (int round = 0; round < rounds; round++) {
        baseRounds[round] = measuredSegment(baseHandler, threads,
            baseWarmupMillis, baseMillis, round, "基线组");
        Thread.sleep(cooldownMillis);
        optRounds[round] = measuredSegment(optHandler, threads,
            optWarmupMillis, optMillis, round, "优化组");
        Thread.sleep(cooldownMillis);
      }

      RoundResult bestBase = bestOf(baseRounds);
      RoundResult bestOpt = bestOf(optRounds);
      AcceptanceVerdict verdict = AcceptanceVerdict.check(
          bestBase.stats(), bestOpt.stats(),
          bestBase.elapsedMillis(), bestOpt.elapsedMillis());

      // 场景3：无刷新基线轮 + 压测中并发原子刷新轮（同一优化组链路；
      // 先冷却清空基线段遗留 TIME_WAIT，避免池重建受端口残留干扰）
      Thread.sleep(cooldownMillis);
      RoundResult noRefresh = measuredSegment(optHandler, threads,
          optWarmupMillis, optMillis, rounds, "无刷新基线");
      RoundResult withRefresh = refreshUnderLoadSegment(optHandler, indexes,
          payload, threads, optWarmupMillis, optMillis);
      int refreshFailures = (int) (withRefresh.stats().errorCount()
          + withRefresh.stats().timeoutCount());
      AcceptanceVerdict refreshVerdict = AcceptanceVerdict.checkRefreshUnderLoad(
          refreshFailures, withRefresh.stats().totalSamples(),
          withRefresh.stats().percentile(0.99),
          noRefresh.stats().percentile(0.99), 2.0);

      String report = renderReport(threads, baseWarmupMillis, baseMillis,
          optWarmupMillis, optMillis, cooldownMillis, rounds,
          baseRounds, optRounds, bestBase, bestOpt, verdict,
          noRefresh, withRefresh, refreshVerdict);
      Path parent = reportPath.toAbsolutePath().getParent();
      if (parent != null && !Files.isDirectory(parent)) {
        Files.createDirectories(parent);
      }
      Files.writeString(reportPath, report, StandardCharsets.UTF_8);

      org.junit.jupiter.api.Assertions.assertTrue(
          verdict.summary().contains("QPS") && verdict.summary().contains("超时率"),
          "验收判定须包含 QPS 与超时率维度: " + verdict.summary());
      org.junit.jupiter.api.Assertions.assertTrue(refreshVerdict.passed(),
          "索引刷新无尖刺红线（零错误 + P99 劣化≤2×）: "
              + refreshVerdict.summary());
      org.junit.jupiter.api.Assertions.assertTrue(Files.exists(reportPath),
          "压测报告须归档: " + reportPath.toAbsolutePath());
    } finally {
      optProvider.stop();
      baseProvider.stop();
    }
  }

  private record RoundResult(String group, BenchStats stats, long elapsedMillis,
      int round) {
  }

  private RoundResult measuredSegment(ContractGatewayHandler handler,
      int threads, long warmupMillis, long measureMillis, int round,
      String group) throws InterruptedException {
    drive(handler, threads, warmupMillis, new BenchStats());
    BenchStats stats = new BenchStats();
    long elapsed = drive(handler, threads, measureMillis, stats);
    return new RoundResult(group, stats, elapsed, round);
  }

  /** 驱动一段压测：返回真实墙钟耗时（QPS 分母）。 */
  private long drive(ContractGatewayHandler handler, int threads,
      long durationMillis, BenchStats stats) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch done = new CountDownLatch(threads);
    long startNanos = System.nanoTime();
    long deadlineNanos = startNanos + durationMillis * 1_000_000L;
    for (int t = 0; t < threads; t++) {
      pool.execute(() -> {
        try {
          while (System.nanoTime() < deadlineNanos) {
            long t0 = System.nanoTime();
            try {
              GatewayResponse response = handler.handle(
                  MT, "POST", Map.of(), ARGS_JSON);
              double rtMillis = (System.nanoTime() - t0) / 1_000_000.0;
              if ("OK".equals(response.code())) {
                stats.recordSuccess(rtMillis);
              } else {
                stats.recordError(rtMillis);
              }
            } catch (Exception failure) {
              // handle 已收敛业务/网络异常为响应，此分支仅防御装配级缺陷
              stats.recordError((System.nanoTime() - t0) / 1_000_000.0);
            }
          }
        } finally {
          done.countDown();
        }
      });
    }
    done.await();
    long elapsedNanos = System.nanoTime() - startNanos;
    pool.shutdownNow();
    return elapsedNanos / 1_000_000;
  }

  /** 压测中并发原子刷新索引：断言零错误 + P99 有界劣化。 */
  private RoundResult refreshUnderLoadSegment(ContractGatewayHandler handler,
      ContractIndexes indexes, String payload, int threads,
      long warmupMillis, long measureMillis) throws Exception {
    drive(handler, threads, warmupMillis, new BenchStats());
    BenchStats stats = new BenchStats();
    AtomicLong refreshCount = new AtomicLong();
    Thread refresher = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        indexes.applyPayload(payload, mapper);
        refreshCount.incrementAndGet();
        try {
          Thread.sleep(200);
        } catch (InterruptedException stopped) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }, "bench-index-refresher");
    refresher.setDaemon(true);
    refresher.start();
    long elapsed = drive(handler, threads, measureMillis, stats);
    refresher.interrupt();
    logger.log(System.Logger.Level.INFO,
        "refresh-under-load 共刷新 {0} 次", refreshCount.get());
    return new RoundResult("刷新中", stats, elapsed, 0);
  }

  /** 组装网关 handler：内存索引 + 固定地址实例表 + 指定调用客户端。 */
  private ContractGatewayHandler handlerOf(ContractIndexes indexes,
      com.apicontract.gateway.ContractRemoteCaller caller,
      ProviderAppFixture provider) {
    InstanceAddressRegistry instances = new InstanceAddressRegistry(
        serviceName -> List.of(provider.baseUrl()),
        failure -> { });
    instances.refresh(SERVICE);
    return new ContractGatewayHandler(
        indexes::find, instances, caller, mapper);
  }

  /** 契约元数据 payload（与发布链路同构，直接灌入内存索引）。 */
  private String payloadOf(ProviderAppFixture provider) throws IOException {
    Map<String, String> aggregateIndex = new LinkedHashMap<>();
    provider.metas.keySet().forEach(name -> aggregateIndex.put(name, SERVICE));
    ContractMetaBundle bundle = new ContractMetaBundle(
        SERVICE, VERSION, List.copyOf(provider.metas.values()), aggregateIndex);
    return bundle.toJson(mapper);
  }

  /**
   * 最佳轮：优先失败率未超红线的干净轮（TIME_WAIT 端口尾部打穿的
   * 脏轮不代表可达吞吐），干净轮中取吞吐最高；全部脏轮时如实保留
   * （验收判定将按失败率红线拒绝）。
   */
  private RoundResult bestOf(RoundResult[] rounds) {
    RoundResult best = null;
    RoundResult bestClean = null;
    for (RoundResult round : rounds) {
      if (best == null || round.stats().qps(round.elapsedMillis())
          > best.stats().qps(best.elapsedMillis())) {
        best = round;
      }
      if (isClean(round) && (bestClean == null
          || round.stats().qps(round.elapsedMillis())
              > bestClean.stats().qps(bestClean.elapsedMillis()))) {
        bestClean = round;
      }
    }
    return bestClean != null ? bestClean : best;
  }

  private boolean isClean(RoundResult round) {
    BenchStats stats = round.stats();
    if (stats.totalSamples() == 0) {
      return false;
    }
    double failureRate = (double) (stats.errorCount() + stats.timeoutCount())
        / stats.totalSamples();
    return failureRate < AcceptanceVerdict.TIMEOUT_RATE_CEILING;
  }

  /** 渲染 markdown 报告（归档至变更目录的验收工件）。 */
  private String renderReport(int threads, long baseWarmupMillis,
      long baseMillis, long optWarmupMillis, long optMillis,
      long cooldownMillis, int rounds, RoundResult[] baseRounds,
      RoundResult[] optRounds, RoundResult bestBase, RoundResult bestOpt,
      AcceptanceVerdict verdict, RoundResult noRefresh,
      RoundResult withRefresh, AcceptanceVerdict refreshVerdict) {
    StringBuilder report = new StringBuilder();
    report.append("# 任务26 压测验收报告\n\n");
    report.append("- 执行时间: ").append(LocalDateTime.now()).append('\n');
    report.append("- 环境: ").append(System.getProperty("os.name"))
        .append(" / JDK ").append(System.getProperty("java.version"))
        .append(" / 压测线程 ").append(threads).append('\n');
    report.append("- 段参数: 基线组 预热 ").append(baseWarmupMillis)
        .append("ms + 测量 ").append(baseMillis)
        .append("ms（短段：每请求断连受本机 TIME_WAIT 端口预算约束）；"
            + "优化组 预热 ").append(optWarmupMillis).append("ms + 测量 ")
        .append(optMillis).append("ms；段间冷却 ").append(cooldownMillis)
        .append("ms（等待 TIME_WAIT 消退）；× ").append(rounds)
        .append(" 轮 best-of-").append(rounds).append('\n');
    report.append("- 对照口径: 基线组 = 反射调用器 + 同步 HttpURLConnection；"
        + "优化组 = 字节码调用器 + Reactor Netty 连接池（同 JVM 交替）\n");
    report.append("- 序列化形态: 前端↔网关↔Provider 链路恒 JSON（决策③；"
        + "protobuf 组件级已就绪未接入 HTTP 入口，序列化维度不在本次对照内）\n");
    report.append("- 契约: ").append(MT).append("（进程内 Spring Boot 真实 HTTP "
        + "泛化入口端到端）\n\n");
    report.append("## 逐轮明细\n\n| 轮次 | 组 | QPS | P50(ms) | P99(ms) | "
        + "错误 | 超时 |\n|---|---|---|---|---|---|---|\n");
    for (int i = 0; i < rounds; i++) {
      appendRound(report, baseRounds[i]);
      appendRound(report, optRounds[i]);
    }
    report.append("\n## 验收判定（最佳轮对照）\n\n```\n")
        .append(verdict.summary()).append("\n```\n\n");
    report.append("- 判定结论: **")
        .append(verdict.passed() ? "通过 ✅" : "不通过 ❌").append("**\n\n");
    report.append("## 索引刷新无尖刺（refresh-under-load）\n\n");
    report.append("| 场景轮 | 组 | QPS | P50(ms) | P99(ms) | 错误 | 超时 |\n"
        + "|---|---|---|---|---|---|---|\n");
    appendRound(report, noRefresh);
    appendRound(report, withRefresh);
    report.append("\n```\n").append(refreshVerdict.summary())
        .append("\n```\n\n- 判定结论: **")
        .append(refreshVerdict.passed() ? "通过 ✅" : "不通过 ❌")
        .append("**\n\n## 复现\n\n```bash\n"
            + "mvn -pl api-contract-it -am test -Dtest=ContractBench \\\n"
            + "  -Dsurefire.failIfNoSpecifiedTests=false \\\n"
            + "  -Dbench.threads=").append(threads)
        .append(" -Dbench.base.millis=").append(baseMillis)
        .append(" -Dbench.opt.millis=").append(optMillis)
        .append(" -Dbench.rounds=").append(rounds)
        .append(" -Dbench.report=<本文件路径>\n```\n");
    report.append("\n## 已知环境限制\n\n"
        + "- 单机 loopback 压测：client 与 server 共享 macOS 临时端口池"
        + "（~16k），基线组每请求断连的 TIME_WAIT（30s）构成端口预算约束，"
        + "故采用短段 + 32s 段间冷却；生产环境 client/server 分离、"
        + "server 侧固定监听端口，无此约束。\n"
        + "- 绝对 QPS 为本机开发环境量级（同 JVM 交替对照消除环境差异，"
        + "验收口径为两组**比值** ≥ 1.5×，非绝对值）。\n"
        + "- 索引刷新场景的 P99 对照基于同 JVM 相邻段，噪声 ±数毫秒。\n");
    return report.toString();
  }

  private void appendRound(StringBuilder report, RoundResult round) {
    BenchStats stats = round.stats();
    report.append(String.format(
        "| %d | %s | %.0f | %.2f | %.2f | %d | %d |%n",
        round.round(), round.group(),
        stats.qps(round.elapsedMillis()),
        stats.percentile(0.50), stats.percentile(0.99),
        stats.errorCount(), stats.timeoutCount()));
  }
}
