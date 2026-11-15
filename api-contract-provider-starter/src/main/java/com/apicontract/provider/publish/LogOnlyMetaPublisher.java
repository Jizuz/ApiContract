package com.apicontract.provider.publish;

import com.apicontract.api.spi.SpiImpl;
import java.util.function.Consumer;

/**
 * logOnly 发布模式（排障/灰度试点用）：不写任何外部载体，
 * 仅输出结构化自检日志 {@code meta published}（design 灰度策略首步试点模式）。
 */
@SpiImpl("log-only")
public final class LogOnlyMetaPublisher implements ContractMetaPublisher {

  private final Consumer<String> logLine;

  public LogOnlyMetaPublisher(Consumer<String> logLine) {
    this.logLine = logLine;
  }

  @Override
  public void publish(ContractMetaBundle bundle) {
    bundle.validate();
    logLine.accept("meta published [mode=logOnly] service=" + bundle.serviceName()
        + " version=" + bundle.version()
        + " contracts=" + bundle.contracts().size()
        + " ids=" + bundle.aggregateIndex().keySet());
  }
}
