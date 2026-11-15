package com.apicontract.api.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SPI 扩展点声明（规格 contract-spi-extension）：标注在扩展点接口上，
 * 声明扩展点显示名与默认实现名。
 *
 * <p>链式扩展点（如调用过滤器）无单选默认实现，{@code defaultImpl} 留空，
 * 由装配方按名称集合装载。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Spi {

  /** 扩展点显示名（如"负载均衡器"）。 */
  String name();

  /** 默认实现名（@SpiImpl 的 value）；链式扩展点留空。 */
  String defaultImpl() default "";
}
