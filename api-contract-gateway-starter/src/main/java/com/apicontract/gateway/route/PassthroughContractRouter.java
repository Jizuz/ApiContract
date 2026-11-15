package com.apicontract.gateway.route;

import com.apicontract.api.spi.SpiImpl;
import java.util.List;

/**
 * 内置路由器：默认放行全部候选实例（版本路由在索引层已完成，
 * 标签等二次过滤由业务实现扩展点叠加）。
 */
@SpiImpl("passthrough")
public class PassthroughContractRouter implements ContractRouter {

  @Override
  public List<String> filter(String contractName, List<String> instances) {
    return instances;
  }
}
