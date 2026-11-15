package com.apicontract.provider.web;

import com.apicontract.api.spi.Spi;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 参数转换器扩展点（规格 contract-spi-extension）：参数类型转换规则。
 *
 * <p>内置实现：默认转换（基础类型、日期、枚举、金额、集合与 DTO）。
 */
@Spi(name = "参数转换器", defaultImpl = "default")
public interface ArgumentConverter {

  /**
   * 转换单个参数值到契约参数类型。
   *
   * @param raw 原始值（null 保持 null）
   * @param target 目标类型
   * @param paramName 参数名（错误信息定位用）
   * @param mapper 容器 Jackson 映射器（DTO 转换用，可为 null）
   * @return 转换后参数值
   * @throws ArgumentInvalidException 转换失败（指明参数位置与原因）
   */
  Object convert(Object raw, Class<?> target, String paramName, ObjectMapper mapper);
}
