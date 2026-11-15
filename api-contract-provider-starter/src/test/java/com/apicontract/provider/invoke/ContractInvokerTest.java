package com.apicontract.provider.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.provider.scan.ContractBinding;
import com.apicontract.provider.scan.ScanOptions;import com.apicontract.testapi.BoomContract;
import com.apicontract.testapi.NullableContract;
import com.apicontract.testapi.OrderContract;
import com.apicontract.testapi.OrderContractImpl;
import com.apicontract.testapi.UserContract;
import com.apicontract.testapi.UserContractImpl;
import com.apicontract.testapi.UserDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务5（红-绿）测试：调用器双模式（字节码/反射）一致性。
 *
 * <p>规格（contract-provider-invoke）：启动期生成调用器，运行期不经反射（字节码模式）；
 * 字节码模式可整体切换回反射（排障）；目标方法抛出异常时剥离包装取真实异常；
 * 空参数值保持 null 不做默认值填充；调用器生成失败单个契约降级备用方式。
 */
class ContractInvokerTest {

  private final List<String> warnings = new ArrayList<>();

  private ContractBinding binding(Class<?> iface, Object impl) {
    List<ContractBinding> bindings = com.apicontract.provider.scan.ContractScanner.scanInterface(
        iface, impl, ScanOptions.of("user-provider"), warnings::add);
    assertEquals(1, bindings.size(), "测试前置：应扫描出 1 个契约绑定");
    return bindings.get(0);
  }

  private ContractBinding userBinding() {
    return binding(UserContract.class, new UserContractImpl());
  }

  @Test
  void bothModesShouldReturnSameValue() throws Throwable {
    Object byReflection =
        ContractInvokerFactory.create(userBinding(), InvokerMode.REFLECTION).invoke(new Object[] {42L});
    Object byBytecode =
        ContractInvokerFactory.create(userBinding(), InvokerMode.BYTECODE).invoke(new Object[] {42L});
    assertEquals(byReflection, byBytecode, "双模式调用结果必须一致");
    assertEquals(new UserDTO("user-42"), byBytecode, "字节码模式应正确装箱参数并直调目标方法");
  }

  @Test
  void bothModesShouldKeepNullArgumentSemantics() throws Throwable {
    Object byReflection =
        ContractInvokerFactory.create(userBinding(), InvokerMode.REFLECTION).invoke(new Object[] {null});
    Object byBytecode =
        ContractInvokerFactory.create(userBinding(), InvokerMode.BYTECODE).invoke(new Object[] {null});
    assertEquals(new UserDTO("user-null"), byReflection, "反射模式 null 参数透传");
    assertEquals(byReflection, byBytecode, "双模式 null 语义一致（空参数值保持 null）");
  }

  @Test
  void bothModesShouldPassPrimitiveParameter() throws Throwable {
    ContractBinding binding = binding(OrderContract.class, new OrderContractImpl());
    Object byReflection =
        ContractInvokerFactory.create(binding, InvokerMode.REFLECTION).invoke(new Object[] {"o1", 5L});
    Object byBytecode =
        ContractInvokerFactory.create(binding, InvokerMode.BYTECODE).invoke(new Object[] {"o1", 5L});
    assertEquals("o1:5", byReflection, "反射模式原始类型拆箱");
    assertEquals("o1:5", byBytecode, "字节码模式 Long -> long 拆箱直调");
  }

  @Test
  void bothModesShouldPassThroughNullReturn() throws Throwable {
    ContractBinding binding = binding(NullableContract.class, (NullableContract) input -> null);
    assertNull(ContractInvokerFactory.create(binding, InvokerMode.REFLECTION).invoke(new Object[] {"x"}),
        "反射模式 null 返回透传");
    assertNull(ContractInvokerFactory.create(binding, InvokerMode.BYTECODE).invoke(new Object[] {"x"}),
        "字节码模式 null 返回透传");
  }

  @Test
  void bothModesShouldThrowSameBusinessException() {
    ContractBinding binding =
        binding(BoomContract.class, (BoomContract) seed -> {
          throw new IllegalStateException("boom-" + seed);
        });
    for (InvokerMode mode : InvokerMode.values()) {
      IllegalStateException exception = assertThrows(IllegalStateException.class,
          () -> ContractInvokerFactory.create(binding, mode).invoke(new Object[] {"x"}),
          mode + " 模式应抛出目标方法真实异常（剥离包装）");
      assertEquals("boom-x", exception.getMessage(),
          mode + " 模式异常消息应为目标方法原始消息");
    }
  }

  @Test
  void bytecodeGenerationFailureShouldFallbackToReflectionWithWarning() throws Throwable {
    ContractBinding binding = userBinding();
    ContractInvoker fallback = ContractInvokerFactory.create(
        binding, InvokerMode.BYTECODE, failingGenerator(), warnings::add);
    assertEquals(new UserDTO("user-42"), fallback.invoke(new Object[] {42L }),
        "生成失败应降级备用调用方式，功能不缺失");
    assertEquals(1, warnings.size(), "降级应告警");
    assertTrue(warnings.get(0).contains("apollo.getuser"),
        "告警应定位到契约：实际=" + warnings.get(0));
  }

  /** 注入恒失败的字节码生成器，模拟生成失败场景。 */
  private static java.util.function.Function<ContractBinding, ContractInvoker> failingGenerator() {
    return b -> {
      throw new IllegalStateException("mock: bytecode generation unavailable");
    };
  }

  @Test
  void invokerShouldExposeContractNameForRouting() {
    ContractInvoker invoker = ContractInvokerFactory.create(userBinding(), InvokerMode.REFLECTION);
    assertEquals("apollo.getuser", invoker.contractName(), "调用器应携带契约标识供入口分发");
  }
}
