package com.apicontract.gateway.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * 任务20（红-绿）测试：集群策略容错——failover 默认重试 2 换实例、
 * 仅框架级异常重试、failfast/failsafe 行为、
 * 重试不绕过路由链（每次重试完整重走 限流→LB→调用）。
 */
class ClusterInvokerTest {

  private static final List<String> NO_CALLS = List.of();

  /** 模拟一次完整调用链尝试：限流 → LB → 调用，记录环节序列。 */
  private static final class ChainAttempt {
    final List<String> steps = new ArrayList<>();
    final List<String> calledInstances = new ArrayList<>();
    int remainingFailures;

    ChainAttempt(int failuresOnFirstInstances) {
      this.remainingFailures = failuresOnFirstInstances;
    }

    String run(List<String> instances) throws Exception {
      steps.add("rate-limit");
      steps.add("load-balance");
      String instance = instances.get(calledInstances.size() % instances.size());
      steps.add("call:" + instance);
      calledInstances.add(instance);
      if (remainingFailures-- > 0) {
        throw new IOException("instance " + instance + " down");
      }
      return "ok@" + instance;
    }
  }

  @Test
  void shouldReplayFullChainOnRetry() throws Exception {
    ClusterInvoker failover = new ClusterInvoker(ClusterPolicy.FAILOVER, 2);
    ChainAttempt attempt = new ChainAttempt(1);

    String result = failover.invoke(() -> attempt.run(List.of("a", "b")));

    assertEquals("ok@b", result);
    assertEquals(List.of("rate-limit", "load-balance", "call:a",
        "rate-limit", "load-balance", "call:b"),
        attempt.steps,
        "重试必须完整重走 限流→LB→调用（不绕过路由链，重试请求仍过限流）");
  }

  @Test
  void shouldRetryFrameworkTimeoutThenExhaust() {
    ClusterInvoker failover = new ClusterInvoker(ClusterPolicy.FAILOVER, 2);
    int[] executions = {0};

    TimeoutException exhausted = assertThrows(TimeoutException.class,
        () -> failover.invoke(() -> {
          executions[0]++;
          throw new TimeoutException("overall timeout");
        }));

    assertEquals("overall timeout", exhausted.getMessage(),
        "重试耗尽后抛出最后一次失败");
    assertEquals(3, executions[0], "默认重试 2 → 共 3 次尝试（1 + 2）");
  }

  @Test
  void shouldNotRetryBusinessLevelFailure() {
    ClusterInvoker failover = new ClusterInvoker(ClusterPolicy.FAILOVER, 2);
    ChainAttempt attempt = new ChainAttempt(0);

    IllegalArgumentException business = assertThrows(
        IllegalArgumentException.class,
        () -> failover.invoke(() -> {
          attempt.steps.add("call:a");
          throw new IllegalArgumentException("业务级失败不重试");
        }));

    assertEquals("业务级失败不重试", business.getMessage());
    assertEquals(1, attempt.steps.size(), "仅框架级异常重试，业务异常直接失败");
  }

  @Test
  void shouldFailFastWithoutRetry() {
    ClusterInvoker failfast = new ClusterInvoker(ClusterPolicy.FAILFAST, 2);
    ChainAttempt attempt = new ChainAttempt(0);

    IOException failure = assertThrows(IOException.class,
        () -> failfast.invoke(() -> {
          attempt.steps.add("call:a");
          throw new IOException("connect refused");
        }));

    assertEquals("connect refused", failure.getMessage());
    assertEquals(1, attempt.steps.size(), "failfast 首败即抛，不重试");
  }

  @Test
  void shouldSwallowFailureInFailsafeMode() throws Exception {
    ClusterInvoker failsafe = new ClusterInvoker(ClusterPolicy.FAILSAFE, 2);

    assertNull(failsafe.invoke(() -> {
      throw new IOException("instance down");
    }), "failsafe 吞掉上游失败，返回降级空值");
  }

  @Test
  void shouldExposeDefaultPolicyForGateway() {
    assertEquals(ClusterPolicy.FAILOVER, ClusterInvoker.DEFAULT.policy(),
        "网关默认集群策略 failover");
    assertEquals(2, ClusterInvoker.DEFAULT.retries(),
        "failover 默认重试 2 次");
  }
}
