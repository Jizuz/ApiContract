package com.apicontract.provider.orchestration;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.provider.autoconfigure.ApiContractProperties;
import com.apicontract.provider.export.ExportWhitelistLoader;
import com.apicontract.provider.invoke.ContractInvoker;
import com.apicontract.provider.invoke.ContractInvokerFactory;
import com.apicontract.provider.invoke.ContractInvokerRegistry;
import com.apicontract.provider.publish.ContractMetaBundle;
import com.apicontract.provider.publish.ContractMetaPublisher;
import com.apicontract.provider.publish.ContractPublishPipeline;
import com.apicontract.provider.publish.InstanceRegistrar;
import com.apicontract.provider.publish.StartupSelfCheckLog;
import com.apicontract.provider.scan.ContractBinding;
import com.apicontract.provider.scan.ContractScanner;
import com.apicontract.provider.scan.ScanOptions;
import com.apicontract.provider.web.ContractRpcHandler;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.DisposableBean;

/**
 * 内置启动编排器（design 方案B 时序，与 demo 手抄编排逐点对齐）：
 *
 * <ul>
 *   <li>阶段一 {@link SmartInitializingSingleton}（容器单例就绪后、Runner 前）：
 *       白名单加载（fail-fast 快速失败）→ 容器扫描契约实现 Bean → 调用器生成
 *       （bytecode 优先、失败降级反射并告警）→ 对兜底 {@link ContractRpcHandler}
 *       执行一次性 {@link ContractRpcHandler#reset 后填充}；</li>
 *   <li>阶段二 {@link ApplicationRunner}（Web 端口已定）：先发布契约元数据、
 *       成功后实例注册（顺序不变量，任一步失败异常传播中断启动），
 *       完成后输出自检四段日志；空白名单正常启动、不发布不注册；</li>
 *   <li>阶段三 {@link DisposableBean}（容器关闭）：实例优雅反注册（幂等）。</li>
 * </ul>
 *
 * <p>与业务自定义编排互斥：容器存在任意 {@link ApiContractOrchestrator} Bean 时
 * 本编排器不装配（防双发布，见自动配置条件）。
 */
public final class DefaultApiContractOrchestrator
    implements ApiContractOrchestrator, SmartInitializingSingleton, ApplicationRunner,
    DisposableBean {

  private static final System.Logger LOG = System.getLogger("api-contract");

  private final ListableBeanFactory beanFactory;
  private final ApiContractProperties properties;
  private final ContractRpcHandler handler;
  private final ContractMetaPublisher publisher;
  private final ObjectProvider<InstanceRegistrar> registrarProvider;
  private final String serviceName;

  /** 编排产物（阶段一单写，阶段二/运行期只读；LinkedHashMap 保序发布）。 */
  private final Map<String, ContractMeta> metas = new LinkedHashMap<>();
  private final Map<String, ContractInvoker> invokers = new LinkedHashMap<>();
  private volatile int exportInterfaceCount;
  private volatile boolean registered;

  public DefaultApiContractOrchestrator(ListableBeanFactory beanFactory,
      ApiContractProperties properties, ContractRpcHandler handler,
      ContractMetaPublisher publisher, ObjectProvider<InstanceRegistrar> registrarProvider,
      String serviceName) {
    this.beanFactory = beanFactory;
    this.properties = properties;
    this.handler = handler;
    this.publisher = publisher;
    this.registrarProvider = registrarProvider;
    this.serviceName = serviceName;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<Class<?>> whitelist = ExportWhitelistLoader.load(
        properties.getExport().getLocation(), properties.getExport().isFailFast(),
        DefaultApiContractOrchestrator::warn);
    exportInterfaceCount = whitelist.size();

    List<ContractBinding> bindings = ContractScanner.scan(whitelist, beanFactory,
        new ScanOptions(serviceName, properties.getOrchestration().getVersion(), Map.of()),
        DefaultApiContractOrchestrator::warn);
    for (ContractBinding binding : bindings) {
      String contractName = binding.meta().contractName();
      metas.put(contractName, binding.meta());
      invokers.put(contractName, ContractInvokerFactory.create(
          binding, properties.getInvoker().getMode(), DefaultApiContractOrchestrator::warn));
    }
    if (metas.isEmpty()) {
      // 空白名单：不 reset（业务自定义 handler 的既有内容不受影响，旧手抄形态兼容）
      LOG.log(Level.INFO, "空白名单/无导出契约：跳过后填充与发布注册（不发布不注册入口）");
      return;
    }
    handler.reset(ContractInvokerRegistry.of(invokers), metas);
  }

  @Override
  public void run(ApplicationArguments args) {
    StartupSelfCheckLog selfCheck =
        new StartupSelfCheckLog(line -> LOG.log(Level.INFO, line));
    selfCheck.exportLoaded(exportInterfaceCount);
    selfCheck.contractReadyWithIds(metas.keySet());

    if (metas.isEmpty()) {
      LOG.log(Level.INFO,
          "空白名单/无导出契约：跳过发布与注册（不发布不注册入口，应用正常启动）");
      return;
    }

    String version = properties.getOrchestration().getVersion();
    ContractMetaBundle bundle = new ContractMetaBundle(serviceName, version,
        List.copyOf(metas.values()), aggregateIndexOf(metas, serviceName));

    InstanceRegistrar registrar = registrarProvider.getIfAvailable();
    if (registrar == null) {
      LOG.log(Level.INFO,
          "未提供实例注册桥（InstanceRegistrar）：跳过实例注册（logOnly 形态）");
    }
    InstanceRegistrar effectiveRegistrar =
        registrar != null ? registrar : instanceMark -> { };

    new ContractPublishPipeline(bundle, publisher, instanceMark -> {
      try {
        effectiveRegistrar.register(instanceMark);
        registered = true;
      } catch (RuntimeException failure) {
        throw new IllegalStateException("实例注册失败，中断启动", failure);
      }
    }, line -> LOG.log(Level.INFO, line)).run();

    selfCheck.metaPublished(serviceName, version, bundle.contracts().size());
    selfCheck.registryJoined(serviceName);
  }

  @Override
  public void destroy() {
    if (!registered) {
      return;
    }
    registered = false;
    InstanceRegistrar registrar = registrarProvider.getIfAvailable();
    if (registrar == null) {
      return;
    }
    try {
      registrar.deregister();
      LOG.log(Level.INFO, "实例反注册完成 service=" + serviceName);
    } catch (Exception failure) {
      LOG.log(Level.WARNING, "实例反注册失败（注册中心会按心跳超时摘除）: " + failure);
    }
  }

  /** 聚合索引：mt → 本服务名（与发布内容结构一致）。 */
  private static Map<String, String> aggregateIndexOf(
      Map<String, ContractMeta> metas, String serviceName) {
    Map<String, String> index = new LinkedHashMap<>();
    metas.keySet().forEach(name -> index.put(name, serviceName));
    return index;
  }

  private static void warn(String line) {
    LOG.log(Level.WARNING, line);
  }
}
