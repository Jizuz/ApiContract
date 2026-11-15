package com.apicontract.provider.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 导出白名单加载器（规格 contract-definition）。
 *
 * <p>格式：每行一个接口全限定名，# 为注释行，空行忽略；
 * 非接口行（是类）整行忽略并告警；条目类不存在或加载失败按快速失败配置
 * 决定中断启动（默认）或告警跳过；白名单资源不存在等效空白名单，返回空列表。
 *
 * <p>白名单是架构级一票否决：未列入的接口不扫描、不发布、对外不可达。
 * 白名单属于应用自身，只读取本应用 classpath 单份资源，不做跨 jar 合并。
 */
public final class ExportWhitelistLoader {

  /** 默认白名单路径（规格 contract-starter-access 输入定义）。 */
  public static final String DEFAULT_LOCATION = "META-INF/export.txt";

  private ExportWhitelistLoader() {
  }

  /**
   * 加载导出白名单。
   *
   * @param location 白名单资源路径（如 META-INF/export.txt）
   * @param failFast 快速失败开关：true 时条目类不存在/加载失败中断启动（EXPORT_LOAD_FAILED）
   * @param warnSink 告警输出（非接口行、fail-fast 关闭时的缺失类行）
   * @return 白名单接口列表（按文件出现顺序）；空白名单返回空列表
   * @throws ExportLoadFailedException failFast 为 true 且存在无法加载的条目
   */
  public static List<Class<?>> load(String location, boolean failFast, Consumer<String> warnSink) {
    List<Class<?>> interfaces = new ArrayList<>();
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = ExportWhitelistLoader.class.getClassLoader();
    }
    // 只读取本应用单份资源，不做跨 jar 合并（规格：白名单不跨 jar 合并）
    URL resource = classLoader.getResource(location);
    if (resource == null) {
      return interfaces;
    }
    List<String> lines = readLines(resource);
    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      Class<?> type = resolve(line, failFast, warnSink);
      if (type == null) {
        continue;
      }
      if (!type.isInterface()) {
        warnSink.accept("白名单条目不是接口类型，整行忽略: " + type.getName());
        continue;
      }
      interfaces.add(type);
    }
    return interfaces;
  }

  private static List<String> readLines(URL resource) {
    try (InputStream input = resource.openStream();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      List<String> lines = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
      return lines;
    } catch (IOException exception) {
      throw new ExportLoadFailedException("白名单资源读取失败: " + resource, exception);
    }
  }

  private static Class<?> resolve(String className, boolean failFast, Consumer<String> warnSink) {
    try {
      return Class.forName(className, false,
          Thread.currentThread().getContextClassLoader() != null
              ? Thread.currentThread().getContextClassLoader()
              : ExportWhitelistLoader.class.getClassLoader());
    } catch (ClassNotFoundException | LinkageError error) {
      String warning = "白名单条目类不存在或加载失败(EXPORT_LOAD_FAILED): " + className;
      if (failFast) {
        throw new ExportLoadFailedException(warning, error);
      }
      warnSink.accept(warning);
      return null;
    }
  }
}
