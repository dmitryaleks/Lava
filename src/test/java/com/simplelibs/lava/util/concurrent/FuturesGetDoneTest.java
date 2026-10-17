/*
 * Copyright (C) 2016 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.util.concurrent;

import static com.simplelibs.lava.util.concurrent.Futures.getDone;
import static com.simplelibs.lava.util.concurrent.Futures.immediateCancelledFuture;
import static com.simplelibs.lava.util.concurrent.Futures.immediateFailedFuture;
import static com.simplelibs.lava.util.concurrent.Futures.immediateFuture;
import static org.assertj.core.api.Assertions.assertThat;

import com.simplelibs.lava.testing.LavaTestCase;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Futures#getDone}. */
public class FuturesGetDoneTest extends LavaTestCase {
  @Test
  public void testSuccessful() throws ExecutionException {
    assertThat(getDone(immediateFuture("a"))).isEqualTo("a");
  }

  @Test
  public void testSuccessfulNull() throws ExecutionException {
    assertThat(getDone(immediateFuture((String) null))).isEqualTo(null);
  }

  @Test
  public void testFailed() {
    Exception failureCause = new Exception();
    try {
      getDone(immediateFailedFuture(failureCause));
      fail();
    } catch (ExecutionException expected) {
      assertThat(expected.getCause()).isEqualTo(failureCause);
    }
  }

  @Test
  public void testCancelled() throws ExecutionException {
    try {
      getDone(immediateCancelledFuture());
      fail();
    } catch (CancellationException expected) {
    }
  }

  @Test
  public void testPending() throws ExecutionException {
    try {
      getDone(ListenableFutureTask.create(() -> "never run"));
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
