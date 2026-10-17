# Scope

Implement single threaded equivalents to Guava 27.0.1.

# Packages/classes to cover

1) com.google.common.primitives.Doubles;
2) com.google.common.cache.Cache (and CacheBuilder and more helper classes);
3) com.google.common.eventbus.Subscribe (and EventBus) and related classes;
4) org.checkerframework.checker.nullness.qual.NonNull;
5) com.google.common.base.Enums;
6) com.google.common.collect.ImmutableMap;
7) com.google.common.collect.Maps;
8) com.google.common.collect.Lists;
 
## Methodology

Keep the interface and public helper classes. Target runtime will use these Guava-independent classes in a single threaded manner.

Add proper unit tests. Use the package root of:

```
com.simplelibs.lava.*
```

Do adversarial code review from a dedicated sub-agent, negotiate usefulness of review comments, address useful ones.

## Reference

Interfaces and source of inspiration (under this project root):
```
...\guava-27.0.1\guava-27.0.1
```

## Target runtime

Java 17.

## Build system

Gradle.