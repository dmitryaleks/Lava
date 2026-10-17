/*
 * Copyright (C) 2012 The Guava Authors
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

package com.simplelibs.lava.cache;

import com.simplelibs.lava.testing.EqualsTester;
import com.simplelibs.lava.testing.LavaTestCase;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link RemovalNotification}.
 *
 * @author Ben Yu
 */
public class RemovalNotificationTest extends LavaTestCase {

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(
            RemovalNotification.create("one", 1, RemovalCause.EXPLICIT),
            RemovalNotification.create("one", 1, RemovalCause.REPLACED))
        .addEqualityGroup(RemovalNotification.create("1", 1, RemovalCause.EXPLICIT))
        .addEqualityGroup(RemovalNotification.create("one", 2, RemovalCause.EXPLICIT))
        .testEquals();
  }
}
