package com.apicontract.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 契约标识注解：声明对外可调用的能力单位（mt）。
 *
 * <p>只允许标注在接口方法上（规格 contract-definition：实现类为普通 Bean，
 * 不承担契约声明职责）；一个接口方法最多一个契约标识。
 *
 * <p>命名规范：{@code 业务域.动作}，全小写、点分层、无中文与空格，全局唯一。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Contract {

  /** 契约标识（mt），全局唯一，一经发布不得变更（变更视同新契约上线）。 */
  String value();
}
