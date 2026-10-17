/*
 * Copyright (C) 2014 The Guava Authors
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

package com.simplelibs.lava.cache;

import java.util.Arrays;

/**
 * Package-private stand-in for Guava's {@code MoreObjects.ToStringHelper}, producing the same
 * {@code ClassName{name=value, bareValue}} format for the {@code toString} methods of this package.
 */
final class ToStringHelper {
  private final StringBuilder builder;
  private String separator = "";

  private ToStringHelper(String className) {
    builder = new StringBuilder(32).append(className).append('{');
  }

  /** Starts a helper named after the simple name of {@code self}'s class, as Guava does. */
  static ToStringHelper of(Object self) {
    return new ToStringHelper(self.getClass().getSimpleName());
  }

  ToStringHelper add(String name, Object value) {
    builder.append(separator).append(name).append('=');
    append(value);
    return this;
  }

  ToStringHelper addValue(Object value) {
    builder.append(separator);
    append(value);
    return this;
  }

  private void append(Object value) {
    separator = ", ";
    if (value != null && value.getClass().isArray()) {
      String arrayString = Arrays.deepToString(new Object[] {value});
      builder.append(arrayString, 1, arrayString.length() - 1);
    } else {
      builder.append(value);
    }
  }

  @Override
  public String toString() {
    return builder.toString() + '}';
  }
}
