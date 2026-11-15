package com.apicontract.testapi;

/** 扫描测试：接口列入白名单但无任何标注方法（接口生效，无契约导出）。 */
public interface QuietContract {

  String doSomething(String input);
}
