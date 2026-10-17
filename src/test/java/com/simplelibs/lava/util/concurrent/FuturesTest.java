package com.simplelibs.lava.util.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simplelibs.lava.testing.NullPointerTester;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Tests for the {@code Futures.immediate*} subset, {@link MoreExecutors} and the exceptions. */
class FuturesTest {

  @Test
  void immediateFuture_returnsValueAndIsDone() throws Exception {
    ListenableFuture<String> future = Futures.immediateFuture("a");
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.cancel(true)).isFalse();
    assertThat(future.get()).isEqualTo("a");
    assertThat(future.get(0, TimeUnit.SECONDS)).isEqualTo("a");
    assertThat(Futures.getDone(future)).isEqualTo("a");
    assertThat(future.toString()).endsWith("[status=SUCCESS, result=[a]]");
  }

  @Test
  void immediateFuture_nullIsSingleton() throws Exception {
    assertThat(Futures.immediateFuture(null)).isSameAs(Futures.immediateFuture(null));
    assertThat(Futures.<String>immediateFuture(null).get()).isNull();
  }

  @Test
  void immediateFuture_timedGetRejectsNullUnit() {
    assertThatThrownBy(() -> Futures.immediateFuture("a").get(0, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void immediateFailedFuture_throwsExecutionException() {
    RuntimeException cause = new RuntimeException("boom");
    ListenableFuture<String> future = Futures.immediateFailedFuture(cause);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class).hasCause(cause);
    assertThatThrownBy(() -> Futures.getDone(future))
        .isInstanceOf(ExecutionException.class)
        .hasCause(cause);
    assertThat(future.toString()).contains("[status=FAILURE, cause=[java.lang.RuntimeException: boom]]");
  }

  @Test
  void immediateCancelledFuture_isCancelled() {
    ListenableFuture<String> future = Futures.immediateCancelledFuture();
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isTrue();
    assertThatThrownBy(future::get)
        .isInstanceOf(CancellationException.class)
        .hasMessage("Task was cancelled.");
    assertThatThrownBy(() -> Futures.getDone(future)).isInstanceOf(CancellationException.class);
    assertThat(future.toString()).endsWith("[status=CANCELLED]");
  }

  @Test
  void listenersOnCompletedFuturesRunImmediately() {
    List<String> calls = new ArrayList<>();
    Futures.immediateFuture("a").addListener(() -> calls.add("success"), MoreExecutors.directExecutor());
    Futures.immediateFailedFuture(new Exception())
        .addListener(() -> calls.add("failure"), MoreExecutors.directExecutor());
    Futures.immediateCancelledFuture()
        .addListener(() -> calls.add("cancelled"), MoreExecutors.directExecutor());
    assertThat(calls).containsExactly("success", "failure", "cancelled");
  }

  @Test
  void listenerRuntimeExceptionIsLoggedNotThrown() {
    Logger logger = Logger.getLogger(ImmediateFuture.class.getName());
    List<LogRecord> records = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            records.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    boolean useParent = logger.getUseParentHandlers();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
    try {
      Executor rejecting =
          command -> {
            throw new IllegalStateException("rejected");
          };
      Futures.immediateFuture("a").addListener(() -> {}, rejecting);
    } finally {
      logger.removeHandler(handler);
      logger.setUseParentHandlers(useParent);
    }
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getThrown()).hasMessage("rejected");
  }

  @Test
  void addListenerRejectsNulls() {
    ListenableFuture<String> future = Futures.immediateFuture("a");
    assertThatThrownBy(() -> future.addListener(null, MoreExecutors.directExecutor()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Runnable was null.");
    assertThatThrownBy(() -> future.addListener(() -> {}, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Executor was null.");
  }

  @Test
  void getDone_rejectsPendingFuture() {
    ListenableFutureTask<String> task = ListenableFutureTask.create(() -> "x");
    assertThatThrownBy(() -> Futures.getDone(task))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Future was expected to be done: ");
  }

  @Test
  void getDone_retriesAfterInterruptAndRestoresFlag() throws Exception {
    // A done future whose first get() is interrupted, like a Future racing an interrupt.
    java.util.concurrent.Future<String> future =
        new java.util.concurrent.CompletableFuture<String>() {
          private boolean interruptedOnce;

          @Override
          public String get() throws InterruptedException, ExecutionException {
            if (!interruptedOnce) {
              interruptedOnce = true;
              throw new InterruptedException();
            }
            return super.get();
          }
        };
    ((java.util.concurrent.CompletableFuture<String>) future).complete("x");
    try {
      assertThat(Futures.getDone(future)).isEqualTo("x");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void immediateFailedAndCancelledFutures_honourInterruptLikeGuava() {
    for (ListenableFuture<?> future :
        List.of(
            Futures.immediateFailedFuture(new Exception()), Futures.immediateCancelledFuture())) {
      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(future::get).isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
      } finally {
        Thread.interrupted();
      }
    }
    // Successful immediate futures ignore the flag, as in Guava.
    Thread.currentThread().interrupt();
    try {
      assertThat(Futures.immediateFuture("a").isDone()).isTrue();
      assertThat(Futures.getDone(Futures.immediateFuture("a"))).isEqualTo("a");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } catch (ExecutionException e) {
      throw new AssertionError(e);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void directExecutor_runsInlineAndHasGuavaToString() {
    List<String> calls = new ArrayList<>();
    Executor executor = MoreExecutors.directExecutor();
    executor.execute(() -> calls.add(Thread.currentThread().getName()));
    assertThat(calls).containsExactly(Thread.currentThread().getName());
    assertThat(executor).hasToString("MoreExecutors.directExecutor()");
    assertThat(MoreExecutors.directExecutor()).isSameAs(executor);
  }

  @Test
  void nullPointers() {
    new NullPointerTester().testAllPublicStaticMethods(Futures.class);
    new NullPointerTester().testAllPublicStaticMethods(MoreExecutors.class);
  }

  @Test
  void uncheckedExecutionException_constructors() {
    Throwable cause = new Exception("cause");
    assertThat(new UncheckedExecutionException(cause)).hasCause(cause);
    assertThat(new UncheckedExecutionException("msg", cause)).hasMessage("msg").hasCause(cause);
    assertThat(new UncheckedExecutionException() {}).hasNoCause();
    assertThat(new UncheckedExecutionException("m") {}).hasMessage("m");
    assertThat(new UncheckedExecutionException(cause)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void executionError_constructors() {
    Error cause = new AssertionError("cause");
    assertThat(new ExecutionError(cause)).hasCause(cause).isInstanceOf(Error.class);
    assertThat(new ExecutionError("msg", cause)).hasMessage("msg").hasCause(cause);
    assertThat(new ExecutionError() {}).hasNoCause();
    assertThat(new ExecutionError("m") {}).hasMessage("m");
  }
}
