/*
 * Copyright (C) 2009 The Guava Authors
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link ListenableFutureTask}.
 *
 * @author Sven Mawson
 */
public class ListenableFutureTaskTest extends LavaTestCase {

  /**
   * Lava: the only way to observe a task that is still running is to run it on another thread. The
   * task is handed over after its listener is added, so it is used by one thread at a time; the
   * test thread only reads {@code FutureTask}'s own thread-safe state.
   */
  private ExecutorService exec;

  protected final CountDownLatch runLatch = new CountDownLatch(1);
  protected final CountDownLatch taskLatch = new CountDownLatch(1);
  protected final CountDownLatch listenerLatch = new CountDownLatch(1);

  protected volatile boolean throwException = false;

  protected final ListenableFutureTask<Integer> task =
      ListenableFutureTask.create(
          new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
              runLatch.countDown();
              taskLatch.await();
              if (throwException) {
                throw new IllegalStateException("Fail");
              }
              return 25;
            }
          });

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    exec = Executors.newCachedThreadPool();

    task.addListener(
        new Runnable() {
          @Override
          public void run() {
            listenerLatch.countDown();
          }
        },
        directExecutor());
  }

  @Override
  protected void tearDown() throws Exception {
    if (exec != null) {
      exec.shutdown();
    }

    super.tearDown();
  }

  @Test
  public void testListenerDoesNotRunUntilTaskCompletes() throws Exception {

    // Test default state of not started.
    assertEquals(1, listenerLatch.getCount());
    assertFalse(task.isDone());
    assertFalse(task.isCancelled());

    // Start the task to put it in the RUNNING state.  Have to use a separate
    // thread because the task will block on the task latch after unblocking
    // the run latch.
    exec.execute(task);
    runLatch.await();
    assertEquals(1, listenerLatch.getCount());
    assertFalse(task.isDone());
    assertFalse(task.isCancelled());

    // Finish the task by unblocking the task latch.  Then wait for the
    // listener to be called by blocking on the listener latch.
    taskLatch.countDown();
    assertEquals(25, task.get().intValue());
    assertTrue(listenerLatch.await(5, TimeUnit.SECONDS));
    assertTrue(task.isDone());
    assertFalse(task.isCancelled());
  }

  @Test
  public void testListenerCalledOnException() throws Exception {
    throwException = true;

    // Start up the task and unblock the latch to finish the task.
    exec.execute(task);
    runLatch.await();
    taskLatch.countDown();

    try {
      task.get(5, TimeUnit.SECONDS);
      fail("Should have propagated the failure.");
    } catch (ExecutionException e) {
      assertEquals(IllegalStateException.class, e.getCause().getClass());
    }

    assertTrue(listenerLatch.await(5, TimeUnit.SECONDS));
    assertTrue(task.isDone());
    assertFalse(task.isCancelled());
  }

  @Test
  public void testListenerCalledOnCancelFromNotRunning() throws Exception {
    task.cancel(false);
    assertTrue(task.isDone());
    assertTrue(task.isCancelled());
    assertEquals(1, runLatch.getCount());

    // Wait for the listeners to be called, don't rely on the same-thread exec.
    listenerLatch.await(5, TimeUnit.SECONDS);
    assertTrue(task.isDone());
    assertTrue(task.isCancelled());

    // Make sure we didn't run anything.
    assertEquals(1, runLatch.getCount());
  }

  @Test
  public void testListenerCalledOnCancelFromRunning() throws Exception {
    exec.execute(task);
    runLatch.await();

    // Task has started up, cancel it while it's running.
    task.cancel(true);
    assertTrue(task.isDone());
    assertTrue(task.isCancelled());
    assertEquals(1, taskLatch.getCount());

    // Wait for the listeners to be called.
    listenerLatch.await(5, TimeUnit.SECONDS);
    assertTrue(task.isDone());
    assertTrue(task.isCancelled());
    assertEquals(1, taskLatch.getCount());
  }
}
