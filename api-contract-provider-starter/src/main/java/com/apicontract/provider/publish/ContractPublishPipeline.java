package com.apicontract.provider.publish;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 发布顺序不变量编排器（design 一致性策略 1，CR 阻断项）：
 * 先发布契约元数据、后注册实例；任一步失败中断启动，
 * 杜绝"实例已上线但契约缺失"的中间态。
 *
 * <p>空契约集合不发布不注册入口（白名单空清单语义延续）。
 */
public final class ContractPublishPipeline {

  private final ContractMetaBundle bundle;
  private final ContractMetaPublisher publisher;
  private final InstanceRegistrar registrar;
  private final Consumer<String> logLine;

  public ContractPublishPipeline(ContractMetaBundle bundle, ContractMetaPublisher publisher,
      InstanceRegistrar registrar) {
    this(bundle, publisher, registrar, line -> { });
  }

  public ContractPublishPipeline(ContractMetaBundle bundle, ContractMetaPublisher publisher,
      InstanceRegistrar registrar, Consumer<String> logLine) {
    this.bundle = bundle;
    this.publisher = publisher;
    this.registrar = registrar;
    this.logLine = logLine;
  }

  /** 执行发布与注册（失败异常向上传播中断启动）。 */
  public void run() {
    bundle.validate();
    publisher.publish(bundle);
    logLine.accept("meta published service=" + bundle.serviceName()
        + " version=" + bundle.version() + " contracts=" + bundle.contracts().size());
    Map<String, String> mark = new LinkedHashMap<>();
    mark.put(NacosMetadataMetaPublisher.KEY_CONTRACT_VERSION, bundle.version());
    mark.put(NacosMetadataMetaPublisher.KEY_CONTRACT_COUNT,
        String.valueOf(bundle.contracts().size()));
    registrar.register(mark);
    logLine.accept("registry joined service=" + bundle.serviceName()
        + " mark=" + mark);
  }
}
