package com.apicontract.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;

/**
 * 任务1（红-绿）测试：校验编译产物保留方法参数名。
 *
 * <p>背景：契约参数按名取值（GET query / POST 对象字段均以参数名匹配），依赖编译期
 * {@code -parameters}（maven-compiler-plugin {@code <parameters>true</parameters>}）。
 * 规格要求：编译参数缺失时启动告警 PARAM_NAME_MISSING（contract-starter-access spec）。
 *
 * <p>红阶段：未开启 -parameters 时参数名为 arg0/arg1，本测试失败；
 * 绿阶段：开启 -parameters 后参数名与源码一致，本测试通过。
 */
class ParameterNameRetentionTest {

  @SuppressWarnings("unused")
  void sampleMethod(String userName, int age) {
    // 编译产物参数名校验样本方法，无实现逻辑
  }

  @Test
  void compiledClassShouldRetainParameterNames() throws Exception {
    Method method =
        ParameterNameRetentionTest.class.getDeclaredMethod("sampleMethod", String.class, int.class);
    Parameter[] parameters = method.getParameters();

    assertEquals(2, parameters.length, "样本方法应有且仅有 2 个参数");

    for (Parameter parameter : parameters) {
      assertTrue(
          parameter.isNamePresent(),
          "方法参数名未保留：请为 maven-compiler-plugin 配置 <parameters>true</parameters>"
              + "（即 -parameters）；契约参数按名取值依赖该编译参数");
    }
    assertEquals("userName", parameters[0].getName(),
        "第一个参数名应为源码声明的 userName（-parameters 未开启时会退化为 arg0）");
    assertEquals("age", parameters[1].getName(),
        "第二个参数名应为源码声明的 age（-parameters 未开启时会退化为 arg1）");
  }
}
