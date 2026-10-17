/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Lava: single-threaded, Guava-independent port of Guava 27.0.1.
 */

package com.simplelibs.lava.util.concurrent;

import static com.simplelibs.lava.util.concurrent.MoreExecutors.directExecutor;

import com.simplelibs.lava.testing.LavaTestCase;
import com.simplelibs.lava.testing.NullPointerTester;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExecutionList}.
 *
 * @author Nishant Thakkar
 * @author Sven Mawson
 */
public class ExecutionListTest extends LavaTestCase {

  private final ExecutionList list = new ExecutionList();

  @Test
  public void testRunOnPopulatedList() throws Exception {
    Executor exec = Executors.newCachedThreadPool();
    CountDownLatch countDownLatch = new CountDownLatch(3);
    list.add(new MockRunnable(countDownLatch), exec);
    list.add(new MockRunnable(countDownLatch), exec);
    list.add(new MockRunnable(countDownLatch), exec);
    assertEquals(3L, countDownLatch.getCount());

    list.execute();

    // Verify that all of the runnables execute in a reasonable amount of time.
    assertTrue(countDownLatch.await(1L, TimeUnit.SECONDS));
  }

  @Test
  public void testExecute_idempotent() {
    final AtomicInteger runCalled = new AtomicInteger();
    list.add(
        new Runnable() {
          @Override
          public void run() {
            runCalled.getAndIncrement();
          }
        },
        directExecutor());
    list.execute();
    assertEquals(1, runCalled.get());
    list.execute();
    assertEquals(1, runCalled.get());
  }

  @Test
  public void testAddAfterRun() throws Exception {
    // Run the previous test
    testRunOnPopulatedList();

    // If it passed, then verify an Add will be executed without calling run
    CountDownLatch countDownLatch = new CountDownLatch(1);
    list.add(new MockRunnable(countDownLatch), Executors.newCachedThreadPool());
    assertTrue(countDownLatch.await(1L, TimeUnit.SECONDS));
  }

  @Test
  public void testOrdering() throws Exception {
    final AtomicInteger integer = new AtomicInteger();
    for (int i = 0; i < 10; i++) {
      final int expectedCount = i;
      list.add(
          new Runnable() {
            @Override
            public void run() {
              integer.compareAndSet(expectedCount, expectedCount + 1);
            }
          },
          MoreExecutors.directExecutor());
    }
    list.execute();
    assertEquals(10, integer.get());
  }

  private class MockRunnable implements Runnable {
    CountDownLatch countDownLatch;

    MockRunnable(CountDownLatch countDownLatch) {
      this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
      countDownLatch.countDown();
    }
  }

  @Test
  public void testExceptionsCaught() {
    list.add(THROWING_RUNNABLE, directExecutor());
    list.execute();
    list.add(THROWING_RUNNABLE, directExecutor());
  }

  @Test
  public void testNulls() {
    new NullPointerTester().testAllPublicInstanceMethods(new ExecutionList());
  }

  private static final Runnable THROWING_RUNNABLE =
      new Runnable() {
        @Override
        public void run() {
          throw new RuntimeException();
        }
      };

  /** Lava: a listener that adds another listener while executing; the new one runs at once. */
  @Test
  public void testAddDuringExecute_runsImmediately() {
    StringBuilder order = new StringBuilder();
    list.add(() -> order.append('A'), directExecutor());
    list.add(
        () -> {
          order.append('B');
          list.add(() -> order.append('b'), directExecutor());
        },
        directExecutor());
    list.add(() -> order.append('C'), directExecutor());
    list.execute();
    assertEquals("ABbC", order.toString());
    list.add(() -> order.append('D'), directExecutor());
    assertEquals("ABbCD", order.toString());
  }

  /** Lava: a listener that calls execute() again while executing is a no-op. */
  @Test
  public void testExecuteDuringExecute_isNoOp() {
    AtomicInteger runs = new AtomicInteger();
    list.add(
        () -> {
          runs.incrementAndGet();
          list.execute();
        },
        directExecutor());
    list.add(runs::incrementAndGet, directExecutor());
    list.execute();
    assertEquals(2, runs.get());
  }

  /** Lava: RuntimeExceptions from executors are logged and the remaining listeners still run. */
  @Test
  public void testRuntimeExceptionFromExecutor_otherListenersStillRun() {
    AtomicInteger runs = new AtomicInteger();
    list.add(runs::incrementAndGet, directExecutor());
    list.add(
        () -> {},
        command -> {
          throw new IllegalStateException("rejected");
        });
    list.add(runs::incrementAndGet, directExecutor());
    list.execute();
    assertEquals(2, runs.get());
  }

  /** Lava: Errors are not swallowed; they propagate out of execute(), as in Guava. */
  @Test
  public void testErrorFromListener_propagates() {
    AtomicInteger runs = new AtomicInteger();
    list.add(
        () -> {
          throw new AssertionError("listener error");
        },
        directExecutor());
    list.add(runs::incrementAndGet, directExecutor());
    try {
      list.execute();
      fail("Expected AssertionError");
    } catch (AssertionError expected) {
      assertEquals("listener error", expected.getMessage());
    }
    assertEquals(0, runs.get());
  }
}
