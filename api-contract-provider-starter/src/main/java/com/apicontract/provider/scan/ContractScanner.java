package com.apicontract.provider.scan;

import com.apicontract.api.Contract;
import com.apicontract.api.meta.ContractMeta;
import com.apicontract.api.meta.ContractNameValidator;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;

/**
 * 契约扫描器（规格 contract-definition 主流程第 4 步）。
 *
 * <p>对白名单接口逐一扫描：仅导出标注 {@link Contract} 的方法；提取契约条目
 * （标识、接口全名、方法名、参数名与类型、返回类型、版本、序列化方式）；
 * 完成唯一性校验（CONTRACT_CONFLICT）与多实现消解（IMPL_AMBIGUOUS）；
 * 契约方法存在重载扫描阶段拒绝（CONTRACT_OVERLOAD_FORBIDDEN）；
 * 实现方法签名与接口不一致跳过并告警（SIGNATURE_MISMATCH）。
 *
 * <p>方法约束：public、非 static、非 default；AOP 代理 Bean 解析真实类型。
 */
public final class ContractScanner {

  private ContractScanner() {
  }

  /**
   * Spring 集成入口：按白名单接口在容器内匹配实现 Bean 并提取契约绑定。
   *
   * @param whitelistInterfaces 白名单接口列表
   * @param beanFactory Spring Bean 工厂
   * @param options 扫描选项（服务名、版本、显式实现指定）
   * @param warnSink 告警输出
   * @return 契约绑定列表
   * @throws ContractConflictException 契约标识重复
   * @throws ImplAmbiguousException 多实现未显式指定唯一实现
   */
  public static List<ContractBinding> scan(List<Class<?>> whitelistInterfaces,
      ListableBeanFactory beanFactory, ScanOptions options, Consumer<String> warnSink) {
    List<ContractBinding> bindings = new ArrayList<>();
    Set<String> seenContractNames = new HashSet<>();
    for (Class<?> contractInterface : whitelistInterfaces) {
      Map<String, ?> implBeans = beanFactory.getBeansOfType(contractInterface);
      Object targetBean = resolveImpl(contractInterface, implBeans, options);
      if (targetBean == null) {
        warnSink.accept("白名单接口在容器内无实现 Bean，跳过契约导出: " + contractInterface.getName());
        continue;
      }
      for (ContractBinding binding :
          scanInterface(contractInterface, targetBean, options, warnSink)) {
        String contractName = binding.meta().contractName();
        if (!seenContractNames.add(contractName)) {
          throw new ContractConflictException(
              "契约标识重复(CONTRACT_CONFLICT): " + contractName + " 已存在，全局唯一不允许重复: "
                  + binding.meta().interfaceName() + "#" + binding.meta().methodName());
        }
        bindings.add(binding);
      }
    }
    return bindings;
  }

  /**
   * 单接口扫描（纯函数）：仅导出标注方法，返回契约绑定。
   *
   * @param contractInterface 契约接口
   * @param targetBean 实现 Bean（可为代理）
   * @param options 扫描选项
   * @param warnSink 告警输出
   * @return 契约绑定列表（未标注/不合规方法不导出）
   */
  public static List<ContractBinding> scanInterface(Class<?> contractInterface, Object targetBean,
      ScanOptions options, Consumer<String> warnSink) {
    Class<?> implType = AopUtils.getTargetClass(targetBean);
    List<Method> declaredMethods = new ArrayList<>(Arrays.asList(contractInterface.getDeclaredMethods()));
    Map<String, Long> methodNameCounts = declaredMethods.stream()
        .collect(Collectors.groupingBy(Method::getName, Collectors.counting()));
    List<ContractBinding> bindings = new ArrayList<>();
    for (Method method : declaredMethods) {
      Contract contract = method.getAnnotation(Contract.class);
      if (contract == null) {
        continue;
      }
      ContractNameValidator.validate(contract.value());
      int modifiers = method.getModifiers();
      if (method.isDefault() || Modifier.isStatic(modifiers)) {
        warnSink.accept("契约方法不满足约束(需 public 且非 static 非 default)，跳过: "
            + contract.value() + " @ " + contractInterface.getName() + "#" + method.getName());
        continue;
      }
      if (methodNameCounts.get(method.getName()) > 1) {
        throw new ContractOverloadForbiddenException(
            "契约方法存在重载，禁止导出(CONTRACT_OVERLOAD_FORBIDDEN): "
                + contractInterface.getName() + "#" + method.getName() + "（mt=" + contract.value() + "）");
      }
      if (findImplMethod(implType, method) == null) {
        warnSink.accept("实现方法签名与接口不一致，跳过契约(SIGNATURE_MISMATCH): "
            + contract.value() + " @ " + implType.getName() + "#" + method.getName());
        continue;
      }
      bindings.add(new ContractBinding(
          buildMeta(contract.value(), contractInterface, method, options), targetBean, method));
    }
    return bindings;
  }

  private static Object resolveImpl(Class<?> contractInterface, Map<String, ?> implBeans,
      ScanOptions options) {
    if (implBeans.isEmpty()) {
      return null;
    }
    if (implBeans.size() == 1) {
      return implBeans.values().iterator().next();
    }
    String explicitBeanName = options.explicitImplBeans().get(contractInterface);
    if (explicitBeanName != null) {
      Object explicitBean = implBeans.get(explicitBeanName);
      if (explicitBean == null) {
        throw new ImplAmbiguousException("显式指定的实现 Bean 不存在(IMPL_AMBIGUOUS): "
            + contractInterface.getName() + " -> " + explicitBeanName
            + "，实际候选: " + implBeans.keySet());
      }
      return explicitBean;
    }
    throw new ImplAmbiguousException("白名单接口存在多个实现 Bean 且未显式指定唯一实现(IMPL_AMBIGUOUS): "
        + contractInterface.getName() + "，候选: " + implBeans.keySet());
  }

  private static Method findImplMethod(Class<?> implType, Method interfaceMethod) {
    try {
      return implType.getMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes());
    } catch (NoSuchMethodException exception) {
      return null;
    }
  }

  private static ContractMeta buildMeta(String contractName, Class<?> contractInterface,
      Method method, ScanOptions options) {
    Parameter[] parameters = method.getParameters();
    List<String> paramNames = new ArrayList<>(parameters.length);
    List<String> paramTypes = new ArrayList<>(parameters.length);
    for (Parameter parameter : parameters) {
      paramNames.add(parameter.getName());
      paramTypes.add(parameter.getType().getTypeName());
    }
    String version = options.version() == null || options.version().isBlank()
        ? ScanOptions.DEFAULT_VERSION : options.version();
    return new ContractMeta(
        contractName,
        contractInterface.getName(),
        method.getName(),
        paramNames,
        paramTypes,
        method.getReturnType().getTypeName(),
        options.serviceName(),
        version,
        List.of(),
        ContractMeta.SERIALIZER_JSON,
        null,
        null);
  }

  static String describe(ContractBinding binding) {
    return String.format(Locale.ROOT, "%s@%s#%s",
        binding.meta().contractName(), binding.meta().interfaceName(), binding.meta().methodName());
  }
}
