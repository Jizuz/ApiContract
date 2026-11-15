package com.apicontract.api.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SPI 实现命名声明：标注在扩展点实现类上，作为统一按名称获取的键。
 *
 * <p>与内置实现同名的业务实现按覆盖语义处理（业务优先，启动日志提示）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SpiImpl {

  /** 实现名（扩展点内唯一；如 round-robin、json、nacos-config）。 */
  String value();
}
