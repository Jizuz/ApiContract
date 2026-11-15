package com.apicontract.provider.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.provider.invoke.ContractInvoker;
import com.apicontract.provider.invoke.ContractInvokerFactory;
import com.apicontract.provider.invoke.ContractInvokerRegistry;
import com.apicontract.provider.invoke.InvokerMode;
import com.apicontract.provider.scan.ContractBinding;
import com.apicontract.provider.scan.ContractScanner;
import com.apicontract.provider.scan.ScanOptions;
import com.apicontract.testapi.EchoContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 任务1（红）测试：内置编排的后填充契约——编排器在容器单例就绪后对兜底
 * {@link ContractRpcHandler} 执行一次性 reset，填充真实注册表与契约元数据。
 *
 * <p>规格（contract-starter-access）：内置启动编排全自动；编排结果在启动完成后
 * 对并发请求只读（不撕裂）；未填充前兜底空注册表行为不变（CONTRACT_NOT_FOUND）。
 * handler 现有构造签名与既有行为保持兼容（可替换语义不变）。
 */
class ContractRpcHandlerResetTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<String> warnings = new ArrayList<>();

  /** 契约表快照：registry 与 metas 必须同源（同一实现集合构建）。 */
  private record ContractTable(ContractInvokerRegistry registry, Map<String, ContractMeta> metas) {
  }

  @Test
  void shouldKeepEmptyRegistryBehaviorBeforeReset() {
    ContractRpcHandler handler = new ContractRpcHandler(
        ContractInvokerRegistry.of(Map.of()), Map.of(), objectMapper, 1024 * 1024);

    RpcResponse response = handler.handle("echo.call", "[42, \"jiz\"]");

    assertEquals(RpcResponse.CODE_NOT_FOUND, response.code(),
        "未 reset 前保持兜底空注册表行为：未命中直接 CONTRACT_NOT_FOUND，不进入任何兜底");
  }

  @Test
  void shouldServeContractsAfterReset() {
    ContractRpcHandler handler = new ContractRpcHandler(
        ContractInvokerRegistry.of(Map.of()), Map.of(), objectMapper, 1024 * 1024);

    ContractTable table = buildTable(id -> "echo:reset:" + id);
    handler.reset(table.registry(), table.metas());

    RpcResponse response = handler.handle("echo.call", "[42, \"jiz\"]");
    assertEquals(RpcResponse.CODE_OK, response.code(), "reset 后新注册表与元数据生效");
    assertEquals("echo:reset:42", response.data(), "调用器应路由到 reset 后的实现");
  }

  @Test
  void shouldReplacePreviousTableAfterReset() {
    ContractTable initial = buildTable(id -> "echo:first:" + id);
    ContractRpcHandler handler =
        new ContractRpcHandler(initial.registry(), initial.metas(), objectMapper, 1024 * 1024);

    ContractTable replaced = buildTable(id -> "echo:second:" + id);
    handler.reset(replaced.registry(), replaced.metas());

    RpcResponse response = handler.handle("echo.call", "[42, \"jiz\"]");
    assertEquals("echo:second:42", response.data(), "重复 reset 以最后一次为准（整体替换语义）");
  }

  /**
   * 并发不撕裂：reset 是启动期单写，运行期并发读必须看到同一张完整契约表——
   * registry 与 metas 任一时刻同源，不允许出现「invoker 命中新表、meta 取自旧表」
   * 导致的 CONTRACT_NOT_FOUND 或参数错配。
   */
  @Test
  void shouldNeverObserveTornTableUnderConcurrentResetAndRead() throws Exception {
    ContractTable first = buildTable(id -> "echo:old:" + id);
    ContractTable second = buildTable(id -> "echo:new:" + id);
    ContractRpcHandler handler =
        new ContractRpcHandler(first.registry(), first.metas(), objectMapper, 1024 * 1024);

    int readerThreads = 4;
    AtomicBoolean running = new AtomicBoolean(true);
    AtomicReference<String> violation = new AtomicReference<>();
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch doneGate = new CountDownLatch(readerThreads);
    ExecutorService pool = Executors.newFixedThreadPool(readerThreads);
    Set<String> allowedResults = Set.of("echo:old:42", "echo:new:42");
    for (int index = 0; index < readerThreads; index++) {
      pool.submit(() -> {
        try {
          startGate.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          doneGate.countDown();
          return;
        }
        try {
          while (running.get() && violation.get() == null) {
            RpcResponse response = handler.handle("echo.call", "[42, \"jiz\"]");
            if (!RpcResponse.CODE_OK.equals(response.code())
                || !allowedResults.contains(String.valueOf(response.data()))) {
              violation.set("code=" + response.code() + " data=" + response.data());
            }
          }
        } finally {
          doneGate.countDown();
        }
      });
    }

    startGate.countDown();
    for (int round = 0; round < 2_000 && violation.get() == null; round++) {
      handler.reset(round % 2 == 0 ? second.registry() : first.registry(),
          round % 2 == 0 ? second.metas() : first.metas());
    }
    running.set(false);
    assertTrue(doneGate.await(15, TimeUnit.SECONDS), "并发读线程应正常结束");
    pool.shutdownNow();

    assertEquals(null, violation.get(),
        "并发读期间不允许撕裂（registry 与 metas 必须同源）: " + violation.get());
  }

  /** 用与既有测试一致的方式从契约接口构建调用器表。 */
  private ContractTable buildTable(java.util.function.LongFunction<String> echoImpl) {
    EchoContract implementation = (id, name) -> echoImpl.apply(id);
    Map<String, ContractInvoker> invokers = new HashMap<>();
    Map<String, ContractMeta> metas = new HashMap<>();
    for (ContractBinding binding : ContractScanner.scanInterface(
        EchoContract.class, implementation, ScanOptions.of("reset-test"), warnings::add)) {
      invokers.put(binding.meta().contractName(),
          ContractInvokerFactory.create(binding, InvokerMode.REFLECTION));
      metas.put(binding.meta().contractName(), binding.meta());
    }
    return new ContractTable(ContractInvokerRegistry.of(invokers), metas);
  }
}
