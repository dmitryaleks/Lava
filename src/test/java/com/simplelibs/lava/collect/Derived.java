package com.simplelibs.lava.collect;

/**
 * Port of the {@code Derived} helper from Guava's {@code SetsTest}: a subclass whose natural
 * ordering is inherited from its superclass ({@code Derived implements Comparable<DerivedBase>}),
 * used to check the generic bounds of factories such as {@code Maps.newTreeMap()}.
 */
class Derived extends DerivedBase {
  Derived(String s) {
    super(s);
  }

  private static final long serialVersionUID = 0;
}
