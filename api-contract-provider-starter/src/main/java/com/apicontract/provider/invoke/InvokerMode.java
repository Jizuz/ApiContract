package com.apicontract.provider.invoke;

/**
 * 调用器模式（规格：调用器模式配置项，默认字节码，可切回反射）。
 */
public enum InvokerMode {

  /** 字节码直调（默认）：运行期不经反射；生成失败自动降级反射模式。 */
  BYTECODE,

  /** 反射模式：排障回退 / 一键切换。 */
  REFLECTION
}
