package com.apicontract.api.spi.fixtures;

import com.apicontract.api.spi.SpiImpl;

@SpiImpl("ok")
public class BoomBrokenSpi implements BoomSpi {

  public BoomBrokenSpi() {
    throw new IllegalStateException("boom");
  }

  @Override
  public String boom() {
    return "never";
  }
}
