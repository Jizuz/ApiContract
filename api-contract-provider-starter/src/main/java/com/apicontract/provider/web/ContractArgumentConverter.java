package com.apicontract.provider.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 契约参数统一转换规则（规格 contract-provider-invoke 主流程第 4 步）。
 *
 * <p>覆盖：基础类型（含字符串数字解析）、日期（ISO-8601）、枚举（名称）、
 * 金额（BigDecimal）、集合与 DTO（Map -&gt; POJO，经容器内 Jackson）；
 * null 保持 null 不做默认值填充；转换失败抛 {@link ArgumentInvalidException}
 * 指明参数位置与原因。
 */
public final class ContractArgumentConverter {

  private ContractArgumentConverter() {
  }

  /** 转换单个参数值到契约参数类型。 */
  public static Object convert(Object raw, Class<?> target, String paramName, ObjectMapper mapper) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number number) {
      return convertNumber(number, target, paramName);
    }
    if (raw instanceof String text) {
      return convertString(text.trim(), target, paramName, mapper);
    }
    if (target.isInstance(raw)) {
      return raw;
    }
    try {
      return mapper.convertValue(raw, target);
    } catch (IllegalArgumentException conversionFailure) {
      throw new ArgumentInvalidException(
          "参数 [" + paramName + "] 类型转换失败: 期望 " + target.getSimpleName()
              + "，实际 " + raw.getClass().getSimpleName(), conversionFailure);
    }
  }

  private static Object convertNumber(Number number, Class<?> target, String paramName) {
    if (target == long.class || target == Long.class) {
      return number.longValue();
    }
    if (target == int.class || target == Integer.class) {
      return number.intValue();
    }
    if (target == short.class || target == Short.class) {
      return number.shortValue();
    }
    if (target == byte.class || target == Byte.class) {
      return number.byteValue();
    }
    if (target == double.class || target == Double.class) {
      return number.doubleValue();
    }
    if (target == float.class || target == Float.class) {
      return number.floatValue();
    }
    if (target == BigDecimal.class) {
      return new BigDecimal(number.toString());
    }
    if (target == String.class) {
      return number.toString();
    }
    throw new ArgumentInvalidException(
        "参数 [" + paramName + "] 类型转换失败: 期望 " + target.getSimpleName()
            + "，实际数值 " + number);
  }

  private static Object convertString(String text, Class<?> target, String paramName,
      ObjectMapper mapper) {
    try {
      if (target == String.class) {
        return text;
      }
      if (target == long.class || target == Long.class) {
        return Long.parseLong(text);
      }
      if (target == int.class || target == Integer.class) {
        return Integer.parseInt(text);
      }
      if (target == double.class || target == Double.class) {
        return Double.parseDouble(text);
      }
      if (target == float.class || target == Float.class) {
        return Float.parseFloat(text);
      }
      if (target == boolean.class || target == Boolean.class) {
        return Boolean.parseBoolean(text);
      }
      if (target == BigDecimal.class) {
        return new BigDecimal(text);
      }
      if (target == LocalDateTime.class) {
        return LocalDateTime.parse(text);
      }
      if (target == LocalDate.class) {
        return LocalDate.parse(text);
      }
      if (target.isEnum()) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object enumValue = Enum.valueOf((Class) target, text);
        return enumValue;
      }
      return mapper.convertValue(text, target);
    } catch (IllegalArgumentException conversionFailure) {
      if (conversionFailure instanceof ArgumentInvalidException) {
        throw conversionFailure;
      }
      throw new ArgumentInvalidException(
          "参数 [" + paramName + "] 类型转换失败: 期望 " + target.getSimpleName()
              + "，无法解析值 [" + text + "]", conversionFailure);
    }
  }
}
