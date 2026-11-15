package com.apicontract.api.spi.fixtures;

import com.apicontract.api.spi.SpiImpl;

@SpiImpl("builtin-default")
public class OverrideGreeting implements GreetingSpi {

  @Override
  public String greet() {
    return "override";
  }
}
