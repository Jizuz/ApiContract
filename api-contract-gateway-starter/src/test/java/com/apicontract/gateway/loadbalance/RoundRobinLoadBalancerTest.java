package com.apicontract.gateway.loadbalance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务12（红-绿）测试：按 mt 轮询负载均衡（design 调用链路：
 * 负载均衡选实例；不同契约独立轮询，防止单契约流量偏斜共享计数）。
 */
class RoundRobinLoadBalancerTest {

  @Test
  void shouldRotateInstancesInOrder() {
    RoundRobinLoadBalancer balancer = new RoundRobinLoadBalancer();
    List<String> instances = List.of("a:8080", "b:8080", "c:8080");

    assertEquals("a:8080", balancer.choose("echo.call", instances));
    assertEquals("b:8080", balancer.choose("echo.call", instances));
    assertEquals("c:8080", balancer.choose("echo.call", instances));
    assertEquals("a:8080", balancer.choose("echo.call", instances), "循环轮转");
  }

  @Test
  void shouldReturnSingleInstanceAlways() {
    RoundRobinLoadBalancer balancer = new RoundRobinLoadBalancer();

    assertEquals("a:8080", balancer.choose("echo.call", List.of("a:8080")));
    assertEquals("a:8080", balancer.choose("echo.call", List.of("a:8080")));
  }

  @Test
  void shouldCountIndependentlyPerContract() {
    RoundRobinLoadBalancer balancer = new RoundRobinLoadBalancer();
    List<String> instances = List.of("a:8080", "b:8080");

    assertEquals("a:8080", balancer.choose("echo.call", instances));
    assertEquals("a:8080", balancer.choose("other.call", instances),
        "不同契约计数独立，互不推进对方轮转");
    assertEquals("b:8080", balancer.choose("echo.call", instances));
  }

  @Test
  void shouldReturnNullForEmptyInstanceList() {
    RoundRobinLoadBalancer balancer = new RoundRobinLoadBalancer();

    assertNull(balancer.choose("echo.call", List.of()));
    assertThrows(NullPointerException.class,
        () -> balancer.choose("echo.call", null));
  }
}
