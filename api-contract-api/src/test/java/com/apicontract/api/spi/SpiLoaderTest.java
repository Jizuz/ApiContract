package com.apicontract.api.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.spi.fixtures.BoomSpi;
import com.apicontract.api.spi.fixtures.BrokenSpi;
import com.apicontract.api.spi.fixtures.BuiltinGreeting;
import com.apicontract.api.spi.fixtures.ChainSpi;
import com.apicontract.api.spi.fixtures.CustomGreeting;
import com.apicontract.api.spi.fixtures.GreetingSpi;
import com.apicontract.api.spi.fixtures.OverrideGreeting;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SPI 统一装载测试（规格 contract-spi-extension：装载/覆盖/非法失败三组）。
 */
class SpiLoaderTest {

  @BeforeEach
  void resetSpiState() {
    SpiLoader.reset();
  }

  @Test
  void get_usesBuiltinDefault_whenNoBusinessOverrideDeclared() {
    SpiLoader.registerBuiltin(GreetingSpi.class, "builtin-default", new BuiltinGreeting());

    GreetingSpi impl = SpiLoader.get(GreetingSpi.class);

    assertInstanceOf(BuiltinGreeting.class, impl);
    assertEquals("builtin", impl.greet());
  }

  @Test
  void loadOverrides_loadsBusinessImplsFromMetaInfServices_byName() {
    SpiLoader.registerBuiltin(GreetingSpi.class, "builtin-default", new BuiltinGreeting());

    SpiLoader.loadOverrides(GreetingSpi.class);

    assertEquals(List.of("builtin-default", "custom"),
        SpiLoader.implNames(GreetingSpi.class));
    GreetingSpi custom = SpiLoader.get(GreetingSpi.class, "custom");
    assertInstanceOf(CustomGreeting.class, custom);
    assertEquals("custom", custom.greet());
  }

  @Test
  void loadOverrides_businessImplOverridesBuiltinOfSameName_startupReportsOverride() {
    SpiLoader.registerBuiltin(GreetingSpi.class, "builtin-default", new BuiltinGreeting());

    SpiLoader.loadOverrides(GreetingSpi.class);

    GreetingSpi winner = SpiLoader.get(GreetingSpi.class, "builtin-default");
    assertInstanceOf(OverrideGreeting.class, winner);
    assertEquals("override", winner.greet());
    assertEquals(List.of("builtin-default"),
        SpiLoader.overriddenNames(GreetingSpi.class));
  }

  @Test
  void get_unknownImplName_failsWithSpiImplNotFound_indicatingSpiAndName() {
    SpiLoader.registerBuiltin(GreetingSpi.class, "builtin-default", new BuiltinGreeting());

    SpiNotFoundException ex = assertThrows(SpiNotFoundException.class,
        () -> SpiLoader.get(GreetingSpi.class, "nope"));
    assertEquals("SPI_IMPL_NOT_FOUND", ex.getCode());
    assertTrue(ex.getMessage().contains("nope"),
        "消息应指明实现名: " + ex.getMessage());
    assertTrue(ex.getMessage().contains(GreetingSpi.class.getName()),
        "消息应指明扩展点: " + ex.getMessage());
  }

  @Test
  void get_chainSpiWithoutDefault_failsWithSpiImplNotFound() {
    SpiLoader.registerBuiltin(ChainSpi.class, "trace", () -> "trace");

    SpiNotFoundException ex = assertThrows(SpiNotFoundException.class,
        () -> SpiLoader.get(ChainSpi.class));
    assertEquals("SPI_IMPL_NOT_FOUND", ex.getCode());
  }

  @Test
  void loadOverrides_implMissingSpiImplAnnotation_failsNotSilently() {
    SpiLoadFailedException ex = assertThrows(SpiLoadFailedException.class,
        () -> SpiLoader.loadOverrides(BrokenSpi.class));
    assertEquals("SPI_LOAD_FAILED", ex.getCode());
    assertTrue(ex.getMessage().contains(BrokenSpi.class.getName()),
        "消息应指明扩展点: " + ex.getMessage());
  }

  @Test
  void loadOverrides_implConstructionThrows_failsNotSilently() {
    SpiLoadFailedException ex = assertThrows(SpiLoadFailedException.class,
        () -> SpiLoader.loadOverrides(BoomSpi.class));
    assertEquals("SPI_LOAD_FAILED", ex.getCode());
    assertTrue(ex.getMessage().contains("boom"),
        "消息应含失败原因: " + ex.getMessage());
  }

  @Test
  void get_returnsSingletonInstancePerSpiAndName() {
    SpiLoader.registerBuiltin(GreetingSpi.class, "builtin-default", new BuiltinGreeting());

    assertSame(SpiLoader.get(GreetingSpi.class, "builtin-default"),
        SpiLoader.get(GreetingSpi.class, "builtin-default"));
  }

  @Test
  void implNames_isEmptyAfterReset() {
    SpiLoader.registerBuiltin(GreetingSpi.class, "builtin-default", new BuiltinGreeting());
    SpiLoader.reset();

    assertEquals(List.of(), SpiLoader.implNames(GreetingSpi.class));
  }
}
