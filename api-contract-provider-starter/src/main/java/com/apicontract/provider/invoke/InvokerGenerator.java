package com.apicontract.provider.invoke;

import com.apicontract.api.spi.Spi;
import com.apicontract.provider.scan.ContractBinding;

/**
 * 调用器生成器扩展点（规格 contract-spi-extension）：调用器生成方式。
 *
 * <p>内置实现：字节码（默认，运行期不经反射）、反射（排障回退）。
 */
@Spi(name = "调用器生成器", defaultImpl = "bytecode")
public interface InvokerGenerator {

  /**
   * 按模式为契约绑定生成调用器。
   *
   * @param binding 契约绑定（方法与参数元数据）
   * @param mode 生成模式（字节码 / 反射）
   * @return 契约调用器（启动期生成，运行期只读）
   */
  ContractInvoker create(ContractBinding binding, InvokerMode mode);
}
