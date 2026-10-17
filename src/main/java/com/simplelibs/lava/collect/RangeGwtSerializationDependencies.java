/*
 * Copyright (C) 2016 The Guava Authors
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

import java.io.Serializable;

/**
 * The superclass of {@link Range}, kept so that {@code Range} has the same supertypes as Guava's.
 * In Guava, this empty class supports GWT serialization of the element type; in Lava it has no
 * other purpose.
 */
abstract class RangeGwtSerializationDependencies<C extends Comparable> implements Serializable {}
