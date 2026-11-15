package com.apicontract.api.spi.fixtures;

import com.apicontract.api.spi.Spi;

@Spi(name = "\u574f\u5b9e\u73b0", defaultImpl = "ok")
public interface BrokenSpi {

  void ping();
}
