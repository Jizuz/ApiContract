package com.apicontract.testapi;

import com.apicontract.api.Contract;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 泛化入口测试：日期、枚举、金额、集合、DTO 参数统一转换规则。 */
public interface RichContract {

  @Contract("rich.call")
  String call(BigDecimal amount, LocalDateTime time, OrderStatus status, List<String> tags, UserDTO extra);
}
