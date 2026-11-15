package com.apicontract.gateway.route;

import com.apicontract.api.spi.Spi;
import java.util.List;

/**
 * 路由器扩展点（规格 contract-spi-extension）：实例过滤（版本路由、标签路由，链式）。
 *
 * <p>内置实现：passthrough（透传，即当前主链路行为）；版本路由已在契约索引层
 * （{@code VersionRouter}）完成，标签路由由业务实现本扩展点叠加。
 */
@Spi(name = "路由器", defaultImpl = "passthrough")
public interface ContractRouter {

  /**
   * 过滤候选实例列表。
   *
   * @param contractName 契约标识
   * @param instances 候选实例地址列表（非空）
   * @return 过滤后实例列表（顺序保持）
   */
  List<String> filter(String contractName, List<String> instances);
}
