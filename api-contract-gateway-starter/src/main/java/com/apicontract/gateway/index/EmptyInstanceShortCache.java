package com.apicontract.gateway.index;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 空实例结果 1s 短缓存（design 热点防护：空实例结果短时缓存默认 1 秒防击穿；
 * 非空结果不缓存，实例上下线即时生效）。实现 {@link
 * com.apicontract.gateway.InstanceResolver}。
 */
public final class EmptyInstanceShortCache
    implements com.apicontract.gateway.InstanceResolver {

  private static final long TTL_MILLIS = 1000;

  private final com.apicontract.gateway.InstanceResolver delegate;
  private final LongSupplier clockMillis;
  private final Map<String, CachedEmpty> emptyCache = new ConcurrentHashMap<>();

  private record CachedEmpty(long cachedAtMillis) {
  }

  public EmptyInstanceShortCache(com.apicontract.gateway.InstanceResolver delegate,
      LongSupplier clockMillis) {
    this.delegate = delegate;
    this.clockMillis = clockMillis;
  }

  @Override
  public List<String> resolve(String serviceName) {
    CachedEmpty cached = emptyCache.get(serviceName);
    long now = clockMillis.getAsLong();
    if (cached != null && now - cached.cachedAtMillis() < TTL_MILLIS) {
      return List.of();
    }
    emptyCache.remove(serviceName);
    List<String> result = delegate.resolve(serviceName);
    if (result == null || result.isEmpty()) {
      emptyCache.put(serviceName, new CachedEmpty(now));
      return List.of();
    }
    return result;
  }
}
