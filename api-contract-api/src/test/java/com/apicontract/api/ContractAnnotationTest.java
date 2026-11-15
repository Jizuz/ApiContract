package com.apicontract.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 任务2（红-绿）测试：@Contract 注解声明约束。
 *
 * <p>规格（contract-definition）：契约标识只允许标注在接口方法上；一个接口方法最多一个契约标识。
 */
class ContractAnnotationTest {

  @Test
  void contractAnnotationShouldOnlyTargetMethods() {
    Target target = Contract.class.getAnnotation(Target.class);
    assertArrayEquals(new ElementType[] {ElementType.METHOD}, target.value(),
        "@Contract 只允许标注在方法上（规格：契约标识只允许标注在接口方法上）");
  }

  @Test
  void contractAnnotationShouldBeRetainedAtRuntime() {
    Retention retention = Contract.class.getAnnotation(Retention.class);
    assertEquals(RetentionPolicy.RUNTIME, retention.value(),
        "@Contract 必须运行期保留：启动扫描依赖反射读取注解");
  }

  @Test
  void contractValueShouldBeMandatoryWithoutDefault() throws Exception {
    Method value = Contract.class.getDeclaredMethod("value");
    assertNull(value.getDefaultValue(),
        "@Contract#value 为契约标识（mt），必填且无默认值：不提供 value 的标注应在编译期报错");
    assertEquals(String.class, value.getReturnType(), "契约标识应为文本类型");
  }
}
