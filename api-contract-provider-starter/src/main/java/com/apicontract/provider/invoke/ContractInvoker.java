package com.apicontract.provider.invoke;

/**
 * 契约调用器：启动期生成，运行期只读（规格 contract-provider-invoke）。
 *
 * <p>实现方式二选一：字节码直调（默认，运行期不经反射）或反射模式（排障回退）；
 * 双模式对外行为一致：参数按契约顺序透传、null 保持语义、
 * 目标方法异常剥离包装后原样抛出。
 */
public interface ContractInvoker {

  /** 契约标识（mt），统一入口按标识分发。 */
  String contractName();

  /**
   * 调用目标方法。
   *
   * @param args 契约参数数组（按契约参数名与类型顺序；无参契约为空数组）
   * @return 目标方法返回值（原始类型自动装箱；可为 null）
   * @throws Throwable 目标方法真实异常（已剥离反射/代理包装）
   */
  Object invoke(Object[] args) throws Throwable;
}
