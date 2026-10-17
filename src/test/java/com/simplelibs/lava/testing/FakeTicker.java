package com.simplelibs.lava.testing;

import com.simplelibs.lava.base.Ticker;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Replacement for guava-testlib's {@code FakeTicker}: a {@link Ticker} whose time only changes
 * when the test advances it (optionally by a fixed step on every read).
 */
public class FakeTicker extends Ticker {
  private long nanos;
  private long autoIncrementStepNanos;

  /** Advances the ticker value by {@code time} in {@code timeUnit}. */
  public FakeTicker advance(long time, TimeUnit timeUnit) {
    return advance(timeUnit.toNanos(time));
  }

  /** Advances the ticker value by {@code nanoseconds}. */
  public FakeTicker advance(long nanoseconds) {
    nanos += nanoseconds;
    return this;
  }

  /** Advances the ticker value by {@code duration}. */
  public FakeTicker advance(Duration duration) {
    return advance(duration.toNanos());
  }

  /** Sets the increment applied to the ticker whenever it is queried. */
  public FakeTicker setAutoIncrementStep(long autoIncrementStep, TimeUnit timeUnit) {
    if (autoIncrementStep < 0) {
      throw new IllegalArgumentException("May not auto-increment by a negative amount");
    }
    this.autoIncrementStepNanos = timeUnit.toNanos(autoIncrementStep);
    return this;
  }

  @Override
  public long read() {
    long result = nanos;
    nanos += autoIncrementStepNanos;
    return result;
  }
}
