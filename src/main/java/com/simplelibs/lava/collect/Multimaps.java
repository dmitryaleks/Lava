/*
 * Copyright (C) 2007 The Guava Authors
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

package com.simplelibs.lava.collect;

import com.simplelibs.lava.checker.nullness.qual.Nullable;
import java.util.AbstractCollection;
import java.util.Map;
import java.util.Map.Entry;

/** Package-private subset of Guava's {@code Multimaps} used by Lava's multimaps. */
final class Multimaps {
  private Multimaps() {}

  /** A skeleton implementation of {@link Multimap#entries()}. */
  abstract static class Entries<K, V> extends AbstractCollection<Map.Entry<K, V>> {
    abstract Multimap<K, V> multimap();

    @Override
    public int size() {
      return multimap().size();
    }

    @Override
    public boolean contains(@Nullable Object o) {
      if (o instanceof Map.Entry) {
        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
        return multimap().containsEntry(entry.getKey(), entry.getValue());
      }
      return false;
    }

    @Override
    public boolean remove(@Nullable Object o) {
      if (o instanceof Map.Entry) {
        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
        return multimap().remove(entry.getKey(), entry.getValue());
      }
      return false;
    }

    @Override
    public void clear() {
      multimap().clear();
    }
  }

  static boolean equalsImpl(Multimap<?, ?> multimap, @Nullable Object object) {
    if (object == multimap) {
      return true;
    }
    if (object instanceof Multimap) {
      Multimap<?, ?> that = (Multimap<?, ?>) object;
      return multimap.asMap().equals(that.asMap());
    }
    return false;
  }
}

