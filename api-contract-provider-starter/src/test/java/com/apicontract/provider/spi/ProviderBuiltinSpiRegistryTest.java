package com.apicontract.provider.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.serialization.ContractSerializer;
import com.apicontract.api.spi.SpiLoader;
import com.apicontract.api.spi.SpiNotFoundException;
import com.apicontract.provider.export.AllowAllExportFilter;
import com.apicontract.provider.export.ExportFilter;
import com.apicontract.provider.invoke.DefaultInvokerGenerator;
import com.apicontract.provider.invoke.InvokerGenerator;
import com.apicontract.provider.publish.ContractMetaPublisher;
import com.apicontract.provider.publish.LogOnlyMetaPublisher;
import com.apicontract.provider.serialization.JsonContractSerializer;
import com.apicontract.provider.web.ArgumentConverter;
import com.apicontract.provider.web.ArgumentInvalidException;
import com.apicontract.provider.web.ArgumentValidator;
import com.apicontract.provider.web.DefaultArgumentConverter;
import com.apicontract.provider.web.StandardArgumentValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Provider 端内置 SPI 注册测试（规格 contract-spi-extension：扩展点清单与内置实现注册）。
 */
class ProviderBuiltinSpiRegistryTest {

  @BeforeEach
  void resetSpiState() {
    SpiLoader.reset();
    ProviderBuiltinSpiRegistry.registerAll();
  }

  @Test
  void invokerGenerator_defaultIsBytecodeMode() {
    InvokerGenerator generator = SpiLoader.get(InvokerGenerator.class);

    assertThat(generator).isInstanceOf(DefaultInvokerGenerator.class);
    assertThat(SpiLoader.implNames(InvokerGenerator.class)).containsExactly("bytecode");
  }

  @Test
  void argumentConverter_defaultDelegatesToBuiltinRules() {
    ArgumentConverter converter = SpiLoader.get(ArgumentConverter.class);

    assertThat(converter).isInstanceOf(DefaultArgumentConverter.class);
    assertThat(converter.convert("123", Integer.class, "count", null))
        .isEqualTo(123);
    assertThat(converter.convert(null, Integer.class, "count", null)).isNull();
  }

  @Test
  void argumentValidator_standardRejectsArityMismatch() {
    ArgumentValidator validator = SpiLoader.get(ArgumentValidator.class);

    assertThat(validator).isInstanceOf(StandardArgumentValidator.class);
    ContractMeta meta = new ContractMeta("mt", "IFace", "m",
        List.of("a", "b"), List.of("java.lang.String", "java.lang.String"),
        "void", "svc", "1.0.0", null, "json", null, null);

    validator.validate(meta, new Object[] {"x", "y"});

    assertThatThrownBy(() -> validator.validate(meta, new Object[] {"x"}))
        .isInstanceOf(ArgumentInvalidException.class)
        .hasMessageContaining("个数");
  }

  @Test
  void exportFilter_defaultAllowsAll() {
    ExportFilter filter = SpiLoader.get(ExportFilter.class);

    assertThat(filter).isInstanceOf(AllowAllExportFilter.class);
    assertThat(filter.allow(null, "tenant-a", "mt")).isTrue();
  }

  @Test
  void serializer_jsonIsBuiltinDefaultOnProviderSide() {
    ContractSerializer serializer = SpiLoader.get(ContractSerializer.class, "json");

    assertThat(serializer).isSameAs(JsonContractSerializer.INSTANCE);
  }

  @Test
  void metaPublisher_requiresConfigurationBackedRegistration() {
    SpiLoader.loadOverrides(ContractMetaPublisher.class);

    assertThatThrownBy(() -> SpiLoader.get(ContractMetaPublisher.class))
        .isInstanceOfSatisfying(SpiNotFoundException.class,
            ex -> assertThat(ex.getCode()).isEqualTo("SPI_IMPL_NOT_FOUND"))
        .hasMessageContaining(ContractMetaPublisher.class.getName());
  }

  @Test
  void metaPublisher_instanceRegisteredByNameIsRetrievable() {
    ContractMetaPublisher logOnly = new LogOnlyMetaPublisher(line -> {
    });
    ProviderBuiltinSpiRegistry.registerMetaPublisher("log-only", logOnly);

    assertThat(SpiLoader.get(ContractMetaPublisher.class, "log-only"))
        .isSameAs(logOnly);
  }
}
