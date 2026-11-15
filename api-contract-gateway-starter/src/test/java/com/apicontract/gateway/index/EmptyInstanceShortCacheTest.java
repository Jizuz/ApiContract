package com.apicontract.gateway.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 任务12（红-绿）测试：空实例结果 1s 短缓存（design 热点防护：
 * 空实例结果短时缓存默认 1 秒防击穿；非空结果不缓存）。
 */
class EmptyInstanceShortCacheTest {

  private final AtomicLong clock = new AtomicLong(0);
  private final AtomicInteger resolveCount = new AtomicInteger();

  private EmptyInstanceShortCache cacheReturning(List<String> result) {
    return new EmptyInstanceShortCache(
        service -> {
          resolveCount.incrementAndGet();
          return result;
        }, clock::get);
  }

  @Test
  void shouldCacheEmptyResultWithinTtl() {
    EmptyInstanceShortCache cache = cacheReturning(List.of());

    assertTrue(cache.resolve("user-provider").isEmpty());
    assertTrue(cache.resolve("user-provider").isEmpty(), "TTL 内命中短缓存");
    assertTrue(cache.resolve("user-provider").isEmpty());

    assertEquals(1, resolveCount.get(), "空结果 1s 内只触发一次真实解析，防击穿");
  }

  @Test
  void shouldRefreshAfterTtlExpires() {
    EmptyInstanceShortCache cache = cacheReturning(List.of());

    cache.resolve("user-provider");
    clock.addAndGet(1001);

    cache.resolve("user-provider");

    assertEquals(2, resolveCount.get(), "TTL 过期后重新解析");
  }

  @Test
  void shouldNotCacheNonEmptyResults() {
    EmptyInstanceShortCache cache = cacheReturning(List.of("a:8080"));

    cache.resolve("user-provider");
    cache.resolve("user-provider");

    assertEquals(2, resolveCount.get(), "非空结果不缓存：实例上下线即时生效");
  }

  @Test
  void shouldRecoverFromEmptyToNonEmpty() {
    AtomicInteger empty = new AtomicInteger(1);
    EmptyInstanceShortCache cache = new EmptyInstanceShortCache(
        service -> empty.get() == 1 ? List.of() : List.of("a:8080"), clock::get);

    assertTrue(cache.resolve("user").isEmpty());
    empty.set(2);
    clock.addAndGet(1001);
    assertEquals(List.of("a:8080"), cache.resolve("user"), "空→非空恢复正确");
  }
}
