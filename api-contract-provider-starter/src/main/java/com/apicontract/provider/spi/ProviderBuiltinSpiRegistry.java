package com.apicontract.provider.spi;

import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.spi.SpiLoader;
import com.apicontract.provider.export.AllowAllExportFilter;
import com.apicontract.provider.export.ExportFilter;
import com.apicontract.provider.invoke.DefaultInvokerGenerator;
import com.apicontract.provider.invoke.InvokerGenerator;
import com.apicontract.provider.publish.ContractMetaPublisher;
import com.apicontract.provider.serialization.JsonContractSerializer;
import com.apicontract.provider.web.ArgumentConverter;
import com.apicontract.provider.web.ArgumentValidator;
import com.apicontract.provider.web.DefaultArgumentConverter;
import com.apicontract.provider.web.StandardArgumentValidator;

/**
 * Provider 端内置 SPI 注册表（规格 contract-spi-extension）：启动期一次性注册。
 *
 * <p>注册范围：调用器生成器、参数转换器、参数校验器、导出过滤器、序列化器（json）。
 * 元数据发布器依赖运行配置（配置中心客户端等），由自动装配在配置就绪后经
 * {@link #registerMetaPublisher} 注册（内置名：nacos-config / nacos-metadata / log-only）。
 */
public final class ProviderBuiltinSpiRegistry {

  private ProviderBuiltinSpiRegistry() {
  }

  /** 注册可无参构造的内置实现（应用启动期调用一次）。 */
  public static void registerAll() {
    SpiLoader.registerBuiltin(InvokerGenerator.class, "bytecode", new DefaultInvokerGenerator());
    SpiLoader.registerBuiltin(ArgumentConverter.class, "default", new DefaultArgumentConverter());
    SpiLoader.registerBuiltin(ArgumentValidator.class, "standard", new StandardArgumentValidator());
    SpiLoader.registerBuiltin(ExportFilter.class, "allow-all", new AllowAllExportFilter());
    SpiLoader.registerBuiltin(ContractSerializer.class, "json", JsonContractSerializer.INSTANCE);
  }

  /**
   * 注册元数据发布器实例（配置就绪后由自动装配调用；同名重复注册以最后一次为准）。
   */
  public static void registerMetaPublisher(String implName, ContractMetaPublisher publisher) {
    SpiLoader.registerBuiltin(ContractMetaPublisher.class, implName, publisher);
  }
}
