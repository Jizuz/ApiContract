package com.apicontract.api.spi.fixtures;

import com.apicontract.api.spi.Spi;

@Spi(name = "\u6784\u9020\u5931\u8d25", defaultImpl = "ok")
public interface BoomSpi {

  String boom();
}
