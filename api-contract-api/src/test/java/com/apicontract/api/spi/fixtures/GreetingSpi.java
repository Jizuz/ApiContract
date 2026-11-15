package com.apicontract.api.spi.fixtures;

import com.apicontract.api.spi.Spi;

@Spi(name = "\u95ee\u5019", defaultImpl = "builtin-default")
public interface GreetingSpi {

  String greet();
}
