package com.apicontract.provider.invoke;

import com.apicontract.provider.scan.ContractBinding;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 反射模式调用器：备用调用方式（排障回退 / 字节码生成失败降级）。
 *
 * <p>异常语义与字节码模式一致：剥离 {@link InvocationTargetException} 包装，
 * 抛出目标方法真实异常。
 */
final class ReflectionContractInvoker implements ContractInvoker {

  private final String contractName;
  private final Object targetBean;
  private final Method method;

  ReflectionContractInvoker(ContractBinding binding) {
    this.contractName = binding.meta().contractName();
    this.targetBean = binding.targetBean();
    this.method = binding.method();
  }

  @Override
  public String contractName() {
    return contractName;
  }

  @Override
  public Object invoke(Object[] args) throws Throwable {
    try {
      return method.invoke(targetBean, args);
    } catch (InvocationTargetException exception) {
      // 剥离反射包装，抛出目标方法真实异常（规格：剥离包装取真实异常）
      throw exception.getTargetException();
    }
  }
}
