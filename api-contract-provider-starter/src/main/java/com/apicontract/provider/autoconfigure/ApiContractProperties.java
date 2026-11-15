package com.apicontract.provider.autoconfigure;

import com.apicontract.provider.export.ExportWhitelistLoader;
import com.apicontract.provider.invoke.InvokerMode;
import com.apicontract.provider.publish.PublishMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider Starter 配置项（design 五级开关）：
 * 总开关 rpc.enabled、发布模式 meta.publish-mode、调用器模式 invoker.mode、
 * 白名单 fail-fast 开关 export.fail-fast 与路径 export.location、
 * 内置编排开关 orchestration.enabled 与契约版本 orchestration.version、
 * 泛化入口路径 entry-path。
 */
@ConfigurationProperties(prefix = "api-contract")
public class ApiContractProperties {

  private final Rpc rpc = new Rpc();
  private final Meta meta = new Meta();
  private final Invoker invoker = new Invoker();
  private final Export export = new Export();
  private final Orchestration orchestration = new Orchestration();
  private String entryPath = "/_contract/rpc";

  public Rpc getRpc() {
    return rpc;
  }

  public Meta getMeta() {
    return meta;
  }

  public Invoker getInvoker() {
    return invoker;
  }

  public Export getExport() {
    return export;
  }

  public Orchestration getOrchestration() {
    return orchestration;
  }

  /** 泛化入口路径（避让业务路径）。 */
  public String getEntryPath() {
    return entryPath;
  }

  public void setEntryPath(String entryPath) {
    this.entryPath = entryPath;
  }

  /** 能力总开关：关闭即整体下线。 */
  public static class Rpc {

    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /** 元数据发布模式（回退 nacosConfig → nacosMetadata → logOnly）。 */
  public static class Meta {

    private PublishMode publishMode = PublishMode.LOG_ONLY;

    public PublishMode getPublishMode() {
      return publishMode;
    }

    public void setPublishMode(PublishMode publishMode) {
      this.publishMode = publishMode;
    }
  }

  /** 调用器模式（bytecode ↔ reflection 回退）。 */
  public static class Invoker {

    private InvokerMode mode = InvokerMode.BYTECODE;

    public InvokerMode getMode() {
      return mode;
    }

    public void setMode(InvokerMode mode) {
      this.mode = mode;
    }
  }

  /** 导出白名单加载开关（fail-fast 默认 true）。 */
  public static class Export {

    private boolean failFast = true;
    private String location = ExportWhitelistLoader.DEFAULT_LOCATION;

    public boolean isFailFast() {
      return failFast;
    }

    public void setFailFast(boolean failFast) {
      this.failFast = failFast;
    }

    /** 白名单资源路径（默认 META-INF/export.txt，单份资源不跨 jar 合并）。 */
    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }
  }

  /** 内置启动编排（默认开启；业务自定义编排存在时自动互斥禁用）。 */
  public static class Orchestration {

    private boolean enabled = true;
    private String version = "1.0.0";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /** 契约版本（写入契约条目与实例极简标记，默认 1.0.0）。 */
    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }
  }
}
