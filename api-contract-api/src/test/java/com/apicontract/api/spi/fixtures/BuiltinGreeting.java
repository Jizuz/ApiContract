package com.apicontract.api.spi.fixtures;

import com.apicontract.api.spi.SpiImpl;

@SpiImpl("builtin-default")
public class BuiltinGreeting implements GreetingSpi {

  @Override
  public String greet() {
    return "builtin";
  }
}
