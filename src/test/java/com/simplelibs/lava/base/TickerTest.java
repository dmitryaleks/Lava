package com.simplelibs.lava.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TickerTest {

  @Test
  void systemTickerIsMonotonicAndShared() {
    Ticker ticker = Ticker.systemTicker();
    assertThat(Ticker.systemTicker()).isSameAs(ticker);
    long first = ticker.read();
    long second = ticker.read();
    assertThat(second).isGreaterThanOrEqualTo(first);
  }

  @Test
  void subclassesCanSupplyTime() {
    Ticker fixed =
        new Ticker() {
          @Override
          public long read() {
            return 42L;
          }
        };
    assertThat(fixed.read()).isEqualTo(42L);
  }
}
