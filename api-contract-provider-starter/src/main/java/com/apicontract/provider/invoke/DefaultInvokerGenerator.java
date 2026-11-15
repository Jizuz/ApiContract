package com.apicontract.provider.invoke;

import com.apicontract.api.spi.SpiImpl;
import com.apicontract.provider.scan.ContractBinding;

/**
 * 内置调用器生成器：委托 {@link ContractInvokerFactory}（字节码默认 + 反射回退）。
 */
@SpiImpl("bytecode")
public class DefaultInvokerGenerator implements InvokerGenerator {

  @Override
  public ContractInvoker create(ContractBinding binding, InvokerMode mode) {
    return ContractInvokerFactory.create(binding, mode);
  }
}
