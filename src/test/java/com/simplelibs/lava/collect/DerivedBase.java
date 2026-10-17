package com.simplelibs.lava.collect;

import java.io.Serializable;

/** Port of the {@code Base} helper from Guava's {@code SetsTest}; see {@link Derived}. */
class DerivedBase implements Comparable<DerivedBase>, Serializable {
  private final String s;

  DerivedBase(String s) {
    this.s = s;
  }

  @Override
  public int hashCode() { // delegate to 's'
    return s.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    } else if (other instanceof DerivedBase) {
      return s.equals(((DerivedBase) other).s);
    } else {
      return false;
    }
  }

  @Override
  public int compareTo(DerivedBase o) {
    return s.compareTo(o.s);
  }

  private static final long serialVersionUID = 0;
}
