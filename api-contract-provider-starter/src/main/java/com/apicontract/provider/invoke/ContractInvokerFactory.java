package com.apicontract.provider.invoke;

import com.apicontract.provider.scan.ContractBinding;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * 调用器工厂：按模式生成调用器（规格 contract-provider-invoke）。
 *
 * <p>字节码模式为默认（运行期不经反射）；生成失败时单个契约降级反射模式并告警，
 * 不影响其他契约；反射模式可直接指定（排障整体回退）。
 */
public final class ContractInvokerFactory {

  private static final AtomicLong GENERATED_CLASS_SEQ = new AtomicLong();

  private ContractInvokerFactory() {
  }

  /** 按模式生成调用器。 */
  public static ContractInvoker create(ContractBinding binding, InvokerMode mode) {
    return create(binding, mode, ContractInvokerFactory::buildBytecodeInvoker, message -> {
    });
  }

  /**
   * 按模式生成调用器（内置编排用：降级告警可观测）。
   *
   * @param binding 契约绑定
   * @param mode 调用器模式
   * @param warnSink 告警输出（生成失败降级时）
   * @return 调用器（字节码或降级反射）
   */
  public static ContractInvoker create(ContractBinding binding, InvokerMode mode,
      Consumer<String> warnSink) {
    return create(binding, mode, ContractInvokerFactory::buildBytecodeInvoker, warnSink);
  }

  /**
   * 按模式生成调用器（可注入字节码生成器，用于降级路径测试）。
   *
   * @param binding 契约绑定
   * @param mode 调用器模式
   * @param bytecodeGenerator 字节码生成器
   * @param warnSink 告警输出（生成失败降级时）
   * @return 调用器（字节码或降级反射）
   */
  public static ContractInvoker create(ContractBinding binding, InvokerMode mode,
      Function<ContractBinding, ContractInvoker> bytecodeGenerator, Consumer<String> warnSink) {
    if (mode == InvokerMode.REFLECTION) {
      return new ReflectionContractInvoker(binding);
    }
    try {
      return bytecodeGenerator.apply(binding);
    } catch (Throwable generationFailure) {
      warnSink.accept("调用器字节码生成失败，降级反射模式: " + binding.meta().contractName()
          + " @ " + binding.meta().interfaceName() + "#" + binding.meta().methodName()
          + "（原因: " + generationFailure + "）");
      return new ReflectionContractInvoker(binding);
    }
  }

  /**
   * ByteBuddy 生成直调调用器：生成类持有目标 Bean，
   * {@code invoke(Object[])} 直接强转参数并调用目标方法（含拆装箱），
   * 不经反射。
   */
  private static ContractInvoker buildBytecodeInvoker(ContractBinding binding) {
    try {
      Method targetMethod = binding.method();
      Class<?> contractInterface = targetMethod.getDeclaringClass();
      String generatedName =
          contractInterface.getName() + "$ApiContractInvoker$" + GENERATED_CLASS_SEQ.incrementAndGet();
      DynamicType.Builder<?> builder = new ByteBuddy()
          .subclass(Object.class)
          .name(generatedName)
          .implement(ContractInvoker.class)
          .defineField("target", contractInterface, Visibility.PRIVATE, FieldManifestation.FINAL)
          .defineConstructor(Visibility.PUBLIC)
          .withParameters(contractInterface)
          .intercept(FieldAccessor.ofField("target").setsArgumentAt(0))
          .method(ElementMatchers.named("contractName"))
          .intercept(FixedValue.reference(binding.meta().contractName()))
          .method(ElementMatchers.named("invoke"))
          .intercept(MethodCall.invoke(targetMethod)
              .onField("target")
              .withArgumentArrayElements(0, targetMethod.getParameterCount()));
      Class<?> generated = builder.make()
          .load(classLoaderOf(contractInterface), ClassLoadingStrategy.Default.WRAPPER)
          .getLoaded();
      Constructor<?> constructor = generated.getDeclaredConstructor(contractInterface);
      constructor.setAccessible(true);
      return (ContractInvoker) constructor.newInstance(binding.targetBean());
    } catch (Throwable failure) {
      throw new IllegalStateException(
          "字节码调用器生成失败: " + binding.meta().contractName(), failure);
    }
  }

  private static ClassLoader classLoaderOf(Class<?> type) {
    ClassLoader loader = type.getClassLoader();
    return loader != null ? loader : ContractInvokerFactory.class.getClassLoader();
  }
}
