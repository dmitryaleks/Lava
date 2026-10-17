/*
 * Copyright (C) 2008 The Guava Authors
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

package com.simplelibs.lava.collect;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * Provides static methods for serializing collection classes.
 *
 * <p>This class assists the implementation of collection classes. Do not use this class to
 * serialize collections that are defined elsewhere.
 *
 * @author Jared Levy
 */
final class Serialization {
  private Serialization() {}

  /**
   * Stores the contents of a multimap in an output stream, as part of serialization. It does not
   * support concurrent multimaps whose content may change while the method is running. The {@link
   * Multimap#asMap} view determines the ordering in which data is written to the stream.
   *
   * <p>The serialized output consists of the number of distinct keys, and then for each distinct
   * key: the key, the number of values for that key, and the key's values.
   */
  static <K, V> void writeMultimap(Multimap<K, V> multimap, ObjectOutputStream stream)
      throws IOException {
    stream.writeInt(multimap.asMap().size());
    for (Map.Entry<K, Collection<V>> entry : multimap.asMap().entrySet()) {
      stream.writeObject(entry.getKey());
      stream.writeInt(entry.getValue().size());
      for (V value : entry.getValue()) {
        stream.writeObject(value);
      }
    }
  }

  // Secret sauce for setting final fields; don't make it public.
  static <T> FieldSetter<T> getFieldSetter(final Class<T> clazz, String fieldName) {
    try {
      Field field = clazz.getDeclaredField(fieldName);
      return new FieldSetter<T>(field);
    } catch (NoSuchFieldException e) {
      throw new AssertionError(e); // programmer error
    }
  }

  // Secret sauce for setting final fields; don't make it public.
  static final class FieldSetter<T> {
    private final Field field;

    private FieldSetter(Field field) {
      this.field = field;
      field.setAccessible(true);
    }

    void set(T instance, Object value) {
      try {
        field.set(instance, value);
      } catch (IllegalAccessException impossible) {
        throw new AssertionError(impossible);
      }
    }

    void set(T instance, int value) {
      try {
        field.set(instance, value);
      } catch (IllegalAccessException impossible) {
        throw new AssertionError(impossible);
      }
    }
  }
}
