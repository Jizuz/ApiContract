package com.apicontract.api.spi.fixtures;

import com.apicontract.api.spi.Spi;

@Spi(name = "\u94fe\u5f0f", defaultImpl = "")
public interface ChainSpi {

  String chain();
}
