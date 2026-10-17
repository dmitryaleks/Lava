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

package com.simplelibs.lava.base;

import com.simplelibs.lava.testing.GcFinalization;
import com.simplelibs.lava.testing.LavaTestCase;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@code AbstractIterator}.
 *
 * @author Kevin Bourrillion
 */
public class AbstractIteratorTest extends LavaTestCase {

  @Test
  public void testDefaultBehaviorOfNextAndHasNext() {

    // This sample AbstractIterator returns 0 on the first call, 1 on the
    // second, then signals that it's reached the end of the data
    Iterator<Integer> iter =
        new AbstractIterator<Integer>() {
          private int rep;

          @Override
          public Integer computeNext() {
            switch (rep++) {
              case 0:
                return 0;
              case 1:
                return 1;
              case 2:
                return endOfData();
              default:
                fail("Should not have been invoked again");
                return null;
            }
          }
        };

    assertTrue(iter.hasNext());
    assertEquals(0, (int) iter.next());

    // verify idempotence of hasNext()
    assertTrue(iter.hasNext());
    assertTrue(iter.hasNext());
    assertTrue(iter.hasNext());
    assertEquals(1, (int) iter.next());

    assertFalse(iter.hasNext());

    // Make sure computeNext() doesn't get invoked again
    assertFalse(iter.hasNext());

    try {
      iter.next();
      fail("no exception thrown");
    } catch (NoSuchElementException expected) {
    }
  }

  @Test
  public void testSneakyThrow() throws Exception {
    Iterator<Integer> iter =
        new AbstractIterator<Integer>() {
          boolean haveBeenCalled;

          @Override
          public Integer computeNext() {
            if (haveBeenCalled) {
              fail("Should not have been called again");
            } else {
              haveBeenCalled = true;
              sneakyThrow(new SomeCheckedException());
            }
            return null; // never reached
          }
        };

    // The first time, the sneakily-thrown exception comes out
    try {
      iter.hasNext();
      fail("No exception thrown");
    } catch (Exception e) {
      if (!(e instanceof SomeCheckedException)) {
        throw e;
      }
    }

    // But the second time, AbstractIterator itself throws an ISE
    try {
      iter.hasNext();
      fail("No exception thrown");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testException() {
    final SomeUncheckedException exception = new SomeUncheckedException();
    Iterator<Integer> iter =
        new AbstractIterator<Integer>() {
          @Override
          public Integer computeNext() {
            throw exception;
          }
        };

    // It should pass through untouched
    try {
      iter.hasNext();
      fail("No exception thrown");
    } catch (SomeUncheckedException e) {
      assertSame(exception, e);
    }
  }

  @Test
  public void testExceptionAfterEndOfData() {
    Iterator<Integer> iter =
        new AbstractIterator<Integer>() {
          @Override
          public Integer computeNext() {
            endOfData();
            throw new SomeUncheckedException();
          }
        };
    try {
      iter.hasNext();
      fail("No exception thrown");
    } catch (SomeUncheckedException expected) {
    }
  }

  @Test
  public void testCantRemove() {
    Iterator<Integer> iter =
        new AbstractIterator<Integer>() {
          boolean haveBeenCalled;

          @Override
          public Integer computeNext() {
            if (haveBeenCalled) {
              endOfData();
            }
            haveBeenCalled = true;
            return 0;
          }
        };

    assertEquals(0, (int) iter.next());

    try {
      iter.remove();
      fail("No exception thrown");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testFreesNextReference() {
    Iterator<Object> itr =
        new AbstractIterator<Object>() {
          @Override
          public Object computeNext() {
            return new Object();
          }
        };
    WeakReference<Object> ref = new WeakReference<>(itr.next());
    GcFinalization.awaitClear(ref);
  }

  @Test
  public void testReentrantHasNext() {
    Iterator<Integer> iter =
        new AbstractIterator<Integer>() {
          @Override
          protected Integer computeNext() {
            boolean unused = hasNext();
            return null;
          }
        };
    try {
      iter.hasNext();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  // Technically we should test other reentrant scenarios (4 combinations of
  // hasNext/next), but we'll cop out for now, knowing that
  // next() both start by invoking hasNext() anyway.

  /** Throws a undeclared checked exception. */
  private static void sneakyThrow(Throwable t) {
    class SneakyThrower<T extends Throwable> {
      @SuppressWarnings("unchecked") // intentionally unsafe for test
      void throwIt(Throwable t) throws T {
        throw (T) t;
      }
    }
    new SneakyThrower<Error>().throwIt(t);
  }

  private static class SomeCheckedException extends Exception {}

  private static class SomeUncheckedException extends RuntimeException {}
}
