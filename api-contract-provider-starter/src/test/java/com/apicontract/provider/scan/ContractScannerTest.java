package com.apicontract.provider.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicontract.api.meta.ContractMeta;
import com.apicontract.testapi.BadNameContract;
import com.apicontract.testapi.DefaultMethodContract;
import com.apicontract.testapi.DupContract;
import com.apicontract.testapi.MismatchContract;
import com.apicontract.testapi.OrderContract;
import com.apicontract.testapi.OrderContractImpl;
import com.apicontract.testapi.OverloadContract;
import com.apicontract.testapi.QuietContract;
import com.apicontract.testapi.UserContract;
import com.apicontract.testapi.UserContractImpl;
import com.apicontract.testapi.UserDTO;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * 任务4（红-绿）测试：契约扫描器。
 *
 * <p>规格矩阵（contract-definition）：白名单接口仅导出标注 @Contract 的方法；
 * 未标注方法不导出；无标注接口生效但无契约导出；契约方法重载扫描阶段拒绝
 * （CONTRACT_OVERLOAD_FORBIDDEN）；签名不一致跳过并告警（SIGNATURE_MISMATCH）；
 * 多实现未显式指定唯一实现启动失败（IMPL_AMBIGUOUS）；契约标识重复中断启动
 * （CONTRACT_CONFLICT）；方法约束 public/非 static/非 default；AOP 代理解析真实类型。
 */
class ContractScannerTest {

  private final List<String> warnings = new ArrayList<>();

  private ScanOptions options() {
    return new ScanOptions("user-provider", "1.0.0", Map.of());
  }

  // ---------- 纯函数：接口 × 实现 扫描矩阵 ----------

  @Test
  void shouldExportOnlyAnnotatedMethodsWithFullMeta() {
    List<ContractBinding> bindings =
        ContractScanner.scanInterface(UserContract.class, new UserContractImpl(), options(), warnings::add);
    assertEquals(1, bindings.size(), "仅导出标注 @Contract 的方法，未标注方法不导出");
    ContractBinding binding = bindings.get(0);
    ContractMeta meta = binding.meta();
    assertEquals("apollo.getuser", meta.contractName());
    assertEquals(UserContract.class.getName(), meta.interfaceName());
    assertEquals("getUser", meta.methodName());
    assertEquals(List.of("userId"), meta.paramNames(), "参数名按 -parameters 提取");
    assertEquals(List.of("java.lang.Long"), meta.paramTypes());
    assertEquals(UserDTO.class.getName(), meta.returnType());
    assertEquals("user-provider", meta.serviceName());
    assertEquals("1.0.0", meta.version());
    assertEquals("json", meta.serializer(), "默认序列化 json");
    assertNotNull(binding.targetBean(), "绑定实现 Bean 供调用器生成");
    assertNotNull(binding.method(), "绑定接口方法供调用器分发");
  }

  @Test
  void shouldKeepParameterOrderForMultiParamContract() {
    List<ContractBinding> bindings =
        ContractScanner.scanInterface(OrderContract.class, new OrderContractImpl(), options(), warnings::add);
    assertEquals(1, bindings.size());
    assertEquals(List.of("orderId", "amount"), bindings.get(0).meta().paramNames());
    assertEquals(List.of("java.lang.String", "long"), bindings.get(0).meta().paramTypes());
  }

  @Test
  void shouldReturnEmptyWhenInterfaceHasNoAnnotatedMethod() {
    List<ContractBinding> bindings =
        ContractScanner.scanInterface(QuietContract.class, new QuietContract() {
          @Override
          public String doSomething(String input) {
            return input;
          }
        }, options(), warnings::add);
    assertTrue(bindings.isEmpty(), "接口列入白名单但无任何标注方法：无契约导出");
  }

  @Test
  void shouldRejectOverloadedContractMethod() {
    ContractOverloadForbiddenException exception = assertThrows(
        ContractOverloadForbiddenException.class,
        () -> ContractScanner.scanInterface(OverloadContract.class, new OverloadContract() {
          @Override
          public void update(String id) {
          }

          @Override
          public void update(String id, int times) {
          }
        }, options(), warnings::add),
        "契约方法存在重载：扫描阶段拒绝并明确报错");
    assertTrue(exception.getMessage().contains("update"), "异常应指明重载方法名");
    assertTrue(exception.getMessage().contains("order.update"), "异常应含契约标识");
  }

  @Test
  void shouldSkipDefaultMethodWithWarning() {
    List<ContractBinding> bindings =
        ContractScanner.scanInterface(DefaultMethodContract.class, new Object(), options(), warnings::add);
    assertTrue(bindings.isEmpty(), "default 方法不符合方法约束，不导出");
    assertEquals(1, warnings.size(), "跳过时应告警");
    assertTrue(warnings.get(0).contains("default"), "告警应说明 default 方法约束：实际=" + warnings.get(0));
  }

  @Test
  void shouldSkipSignatureMismatchWithWarning() {
    List<ContractBinding> bindings =
        ContractScanner.scanInterface(MismatchContract.class, new Object(), options(), warnings::add);
    assertTrue(bindings.isEmpty(), "实现类缺少对应方法（签名不一致）：跳过该契约");
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("SIGNATURE_MISMATCH"),
        "告警应携带 SIGNATURE_MISMATCH 错误码：实际=" + warnings.get(0));
  }

  @Test
  void shouldRejectIllegalContractName() {
    assertThrows(IllegalArgumentException.class,
        () -> ContractScanner.scanInterface(BadNameContract.class, new Object(), options(), warnings::add),
        "非法命名（大写）应被命名规范校验拒绝");
  }

  // ---------- Spring 集成：容器发现与多实现消解 ----------

  private DefaultListableBeanFactory beanFactory(Object... beans) {
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    for (Object bean : beans) {
      factory.registerSingleton(bean.getClass().getSimpleName() + "@" + System.identityHashCode(bean), bean);
    }
    return factory;
  }

  @Test
  void shouldDiscoverImplBeanAndExportContracts() {
    DefaultListableBeanFactory factory = beanFactory(new UserContractImpl(), new OrderContractImpl());
    List<ContractBinding> bindings = ContractScanner.scan(
        List.of(UserContract.class, OrderContract.class), factory, options(), warnings::add);
    assertEquals(2, bindings.size(), "两个白名单接口各导出一个契约");
    assertTrue(bindings.stream().anyMatch(b -> "apollo.getuser".equals(b.meta().contractName())));
    assertTrue(bindings.stream().anyMatch(b -> "order.create".equals(b.meta().contractName())));
  }

  @Test
  void shouldFailOnDuplicateContractNameAcrossInterfaces() {
    // DupContract 需有实现 Bean，扫描才会走到契约判重（无实现的接口按规格跳过）
    DupContract dupImpl = new DupContract() {
      @Override
      public String duplicate(Long userId) {
        return "dup";
      }
    };
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    factory.registerSingleton("userImpl", new UserContractImpl());
    factory.registerSingleton("dupImpl", dupImpl);
    ContractConflictException exception = assertThrows(ContractConflictException.class,
        () -> ContractScanner.scan(List.of(UserContract.class, DupContract.class), factory, options(), warnings::add),
        "契约标识重复（跨接口）：发布校验失败，中断启动");
    assertTrue(exception.getMessage().contains("apollo.getuser"), "异常应包含冲突标识");
  }

  @Test
  void shouldFailWhenMultipleImplsAndNoExplicitSelection() {
    DefaultListableBeanFactory factory = beanFactory(new UserContractImpl(), new UserContractImpl());
    ImplAmbiguousException exception = assertThrows(ImplAmbiguousException.class,
        () -> ContractScanner.scan(List.of(UserContract.class), factory, options(), warnings::add),
        "多实现未显式指定唯一实现：启动失败（IMPL_AMBIGUOUS）");
    assertTrue(exception.getMessage().contains(UserContract.class.getName()),
        "异常应指明接口：实际=" + exception.getMessage());
  }

  @Test
  void shouldSelectExplicitImplBeanWhenSpecified() {
    UserContractImpl preferred = new UserContractImpl();
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    factory.registerSingleton("userImplA", new UserContractImpl());
    factory.registerSingleton("userImplB", preferred);
    ScanOptions explicit = new ScanOptions("user-provider", "1.0.0",
        Map.of(UserContract.class, "userImplB"));
    List<ContractBinding> bindings =
        ContractScanner.scan(List.of(UserContract.class), factory, explicit, warnings::add);
    assertEquals(1, bindings.size(), "显式指定唯一实现后正常发布");
    assertEquals(preferred, bindings.get(0).targetBean(), "应绑定显式指定的 Bean");
  }

  @Test
  void shouldWarnWhenWhitelistInterfaceHasNoImplBean() {
    DefaultListableBeanFactory factory = beanFactory(new OrderContractImpl());
    List<ContractBinding> bindings =
        ContractScanner.scan(List.of(UserContract.class), factory, options(), warnings::add);
    assertTrue(bindings.isEmpty(), "无实现 Bean 的白名单接口不导出契约");
    assertEquals(1, warnings.size(), "应告警提示无实现");
    assertTrue(warnings.get(0).contains(UserContract.class.getName()));
  }

  @Test
  void shouldResolveJdkProxyBeanTargetClass() {
    Object proxy = Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class<?>[] {UserContract.class},
        (p, method, args) -> {
          String name = method.getName();
          if ("equals".equals(name)) {
            return p == args[0];
          }
          if ("hashCode".equals(name)) {
            return System.identityHashCode(p);
          }
          if ("toString".equals(name)) {
            return "userContractProxy";
          }
          return new UserDTO("proxy");
        });
    DefaultListableBeanFactory factory = beanFactory(proxy);
    List<ContractBinding> bindings =
        ContractScanner.scan(List.of(UserContract.class), factory, options(), warnings::add);
    assertEquals(1, bindings.size(), "JDK 代理 Bean 应解析真实类型并正常导出契约");
    assertEquals(proxy, bindings.get(0).targetBean());
  }
}
