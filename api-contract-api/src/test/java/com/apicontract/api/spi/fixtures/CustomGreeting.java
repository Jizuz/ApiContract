package com.apicontract.api.spi.fixtures;

import com.apicontract.api.spi.SpiImpl;

@SpiImpl("custom")
public class CustomGreeting implements GreetingSpi {

  @Override
  public String greet() {
    return "custom";
  }
}
