package com.apicontract.api.spi;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;

/**
 * SPI 统一装载器（规格 contract-spi-extension）：按扩展点维度装载全部实现并缓存单例，
 * 统一按名称获取。
 *
 * <p>装载来源：内置实现经 {@link #registerBuiltin} 编程注册（框架启动期一次性）；
 * 业务实现经 JDK ServiceLoader 从 {@code META-INF/services/{扩展点接口全名}} 装载，
 * 实现类须标注 {@link SpiImpl} 命名。
 *
 * <p>覆盖语义：业务实现与内置实现同名时业务优先，启动日志提示被覆盖项
 * （{@link #overriddenNames}）。
 *
 * <p>失败语义：实现未命名 / 类型不符 / 实例化失败抛 {@link SpiLoadFailedException}
 * （启动失败不静默）；名称不存在抛 {@link SpiNotFoundException}（指明扩展点与名称）。
 *
 * <p>线程模型：装载期为一次性动作（方法加锁），运行期只读；策略实现需自行保证线程安全。
 */
public final class SpiLoader {

  private static final Logger LOG = Logger.getLogger(SpiLoader.class.getName());

  private static final Map<Class<?>, LinkedHashMap<String, Object>> REGISTRY =
      new LinkedHashMap<>();
  private static final Map<Class<?>, LinkedHashSet<String>> OVERRIDDEN =
      new LinkedHashMap<>();
  private static final Set<Class<?>> OVERRIDES_LOADED = new LinkedHashSet<>();

  private SpiLoader() {
  }

  /**
   * 注册内置实现（框架启动期调用一次；实例按单例缓存随应用生命周期）。
   */
  public static synchronized <T> void registerBuiltin(Class<T> spi, String implName, T impl) {
    requireSpiInterface(spi);
    if (implName == null || implName.isEmpty()) {
      throw new SpiLoadFailedException("内置实现名不可为空: " + spi.getName());
    }
    if (impl == null || !spi.isInstance(impl)) {
      throw new SpiLoadFailedException(
          "内置实现类型不符: " + spi.getName() + " <- " + safeTypeName(impl));
    }
    REGISTRY.computeIfAbsent(spi, key -> new LinkedHashMap<>()).put(implName, impl);
  }

  /**
   * 从 META-INF/services 装载业务实现声明：同名覆盖内置实现并记录覆盖项。
   *
   * @throws SpiLoadFailedException 实现未命名、类型不符或实例化失败（不静默回退）
   */
  public static synchronized <T> void loadOverrides(Class<T> spi) {
    requireSpiInterface(spi);
    if (OVERRIDES_LOADED.contains(spi)) {
      return;
    }
    LinkedHashMap<String, Object> impls =
        REGISTRY.computeIfAbsent(spi, key -> new LinkedHashMap<>());
    try {
      for (T impl : ServiceLoader.load(spi)) {
        SpiImpl naming = impl.getClass().getAnnotation(SpiImpl.class);
        if (naming == null || naming.value().isEmpty()) {
          throw new SpiLoadFailedException("SPI 实现未声明 @SpiImpl 名称: 扩展点 "
              + spi.getName() + ", 实现类 " + impl.getClass().getName());
        }
        String name = naming.value();
        Object builtin = impls.get(name);
        if (builtin != null) {
          OVERRIDDEN.computeIfAbsent(spi, key -> new LinkedHashSet<>()).add(name);
          LOG.warning("SPI 扩展点[" + spi.getName() + "]内置实现[" + name
              + "]被业务实现覆盖: " + impl.getClass().getName());
        }
        impls.put(name, impl);
      }
      OVERRIDES_LOADED.add(spi);
    } catch (SpiLoadFailedException failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new SpiLoadFailedException(
          "SPI 实现装载失败: " + spi.getName() + "; 原因: " + rootCause(failure), failure);
    }
  }

  /**
   * 按默认实现名获取（@Spi 的 defaultImpl）。
   *
   * @throws SpiNotFoundException 扩展点无默认实现或名称不存在
   */
  public static synchronized <T> T get(Class<T> spi) {
    Spi annotation = requireSpiInterface(spi);
    if (annotation.defaultImpl().isEmpty()) {
      throw new SpiNotFoundException("扩展点[" + annotation.name() + "]"
          + spi.getName() + " 为链式扩展点，无默认实现，须按名称获取");
    }
    return get(spi, annotation.defaultImpl());
  }

  /**
   * 按名称获取单例实现。
   *
   * @throws SpiNotFoundException 名称不存在（指明扩展点与名称及可用清单）
   */
  public static synchronized <T> T get(Class<T> spi, String implName) {
    requireSpiInterface(spi);
    if (implName == null || implName.isEmpty()) {
      throw new SpiNotFoundException(
          "扩展点 " + spi.getName() + " 的实现名不可为空，可用: " + implNames(spi));
    }
    Object impl = REGISTRY.getOrDefault(spi, new LinkedHashMap<>()).get(implName);
    if (impl == null) {
      throw new SpiNotFoundException("扩展点 " + spi.getName()
          + " 不存在实现[" + implName + "]，可用: " + implNames(spi));
    }
    return spi.cast(impl);
  }

  /** 已注册实现名清单（保持注册顺序，含内置与业务）。 */
  public static synchronized List<String> implNames(Class<?> spi) {
    LinkedHashMap<String, Object> impls = REGISTRY.get(spi);
    return impls == null ? List.of() : List.copyOf(impls.keySet());
  }

  /** 被业务实现覆盖的内置实现名清单（启动提示用）。 */
  public static synchronized List<String> overriddenNames(Class<?> spi) {
    LinkedHashSet<String> names = OVERRIDDEN.get(spi);
    return names == null ? List.of() : List.copyOf(names);
  }

  /** 清空全部注册与缓存（仅测试隔离用）。 */
  public static synchronized void reset() {
    REGISTRY.clear();
    OVERRIDDEN.clear();
    OVERRIDES_LOADED.clear();
  }

  private static Spi requireSpiInterface(Class<?> spi) {
    Spi annotation = spi.getAnnotation(Spi.class);
    if (annotation == null) {
      throw new SpiLoadFailedException(
          "接口未声明为 SPI 扩展点（缺 @Spi）: " + spi.getName());
    }
    return annotation;
  }

  private static String safeTypeName(Object impl) {
    return impl == null ? "null" : impl.getClass().getName();
  }

  private static String rootCause(Throwable failure) {
    Throwable root = failure;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root.toString();
  }
}
