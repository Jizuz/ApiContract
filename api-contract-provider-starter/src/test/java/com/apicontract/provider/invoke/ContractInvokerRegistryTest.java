package com.apicontract.provider.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.apicontract.provider.scan.ContractBinding;
import com.apicontract.provider.scan.ContractScanner;
import com.apicontract.provider.scan.ScanOptions;
import com.apicontract.testapi.OrderContract;
import com.apicontract.testapi.OrderContractImpl;
import com.apicontract.testapi.UserContract;
import com.apicontract.testapi.UserContractImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务5（红-绿）测试：调用器注册表（mt -&gt; 调用器）。
 *
 * <p>规格（contract-provider-invoke）：调用器常驻内存，随应用生命周期；
 * 运行期不生成新调用器（启动期一次性构建，运行期只读）；
 * 未命中直接返回契约不存在，不进入任何兜底调用。
 */
class ContractInvokerRegistryTest {

  private final List<String> warnings = new ArrayList<>();

  private Map<String, ContractInvoker> buildInvokers() {
    Map<String, ContractInvoker> invokers = new HashMap<>();
    for (ContractBinding binding : ContractScanner.scanInterface(
        UserContract.class, new UserContractImpl(), ScanOptions.of("user-provider"), warnings::add)) {
      invokers.put(binding.meta().contractName(),
          ContractInvokerFactory.create(binding, InvokerMode.REFLECTION));
    }
    for (ContractBinding binding : ContractScanner.scanInterface(
        OrderContract.class, new OrderContractImpl(), ScanOptions.of("user-provider"), warnings::add)) {
      invokers.put(binding.meta().contractName(),
          ContractInvokerFactory.create(binding, InvokerMode.REFLECTION));
    }
    return invokers;
  }

  @Test
  void shouldLookupInvokerByContractName() {
    ContractInvokerRegistry registry = ContractInvokerRegistry.of(buildInvokers());
    assertEquals("apollo.getuser", registry.get("apollo.getuser").contractName(),
        "应按契约标识命中调用器");
    assertEquals("order.create", registry.get("order.create").contractName(),
        "多契约注册表均按标识命中");
  }

  @Test
  void shouldReturnNullForUnknownContract() {
    ContractInvokerRegistry registry = ContractInvokerRegistry.of(buildInvokers());
    assertNull(registry.get("not.exists"),
        "未命中返回 null：由统一入口转 CONTRACT_NOT_FOUND，不进入任何兜底");
  }

  @Test
  void shouldSnapshotSourceMapAtBuildTime() {
    Map<String, ContractInvoker> source = buildInvokers();
    ContractInvokerRegistry registry = ContractInvokerRegistry.of(source);
    source.put("late.added", source.get("apollo.getuser"));
    assertNull(registry.get("late.added"),
        "注册表构建后源 Map 变更不影响注册表（防御性拷贝，运行期只读）");
  }

  @Test
  void shouldExposeRegisteredContractCount() {
    ContractInvokerRegistry registry = ContractInvokerRegistry.of(buildInvokers());
    assertEquals(2, registry.size(), "注册表应能报告契约数量（发布自检日志 contract ready 计数依据）");
  }
}
