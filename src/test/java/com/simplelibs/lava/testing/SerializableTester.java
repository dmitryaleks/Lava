package com.simplelibs.lava.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Minimal replacement for guava-testlib's {@code SerializableTester}. */
public final class SerializableTester {
  private SerializableTester() {}

  /** Serializes and deserializes {@code object}, returning the copy. */
  @SuppressWarnings("unchecked")
  public static <T> T reserialize(T object) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
        out.writeObject(object);
      }
      try (ObjectInputStream in =
          new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        return (T) in.readObject();
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new AssertionError("Serialization round trip failed for " + object, e);
    }
  }

  /** Reserializes {@code object} and asserts the copy is equal with the same hash code. */
  public static <T> T reserializeAndAssert(T object) {
    T copy = reserialize(object);
    assertEquals(object, copy);
    assertEquals(copy, object);
    assertEquals(object.hashCode(), copy.hashCode());
    return copy;
  }
}
