/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.plugin.python.restricted;

import com.google.common.collect.Iterables;
import com.google.template.soy.plugin.restricted.SoySourceValue;

/** A value that resolves to a SoyValue or supported native type at runtime. */
public interface PythonValue extends SoySourceValue {
  PythonValue isNull();

  PythonValue isNonNull();

  PythonValue call(PythonValue... args);

  /** See {@link #call(PythonValue...)}. */
  default PythonValue call(Iterable<PythonValue> args) {
    return call(Iterables.toArray(args, PythonValue.class));
  }

  PythonValue getProp(String ident);

  PythonValue plus(PythonValue value);

  PythonValue coerceToString();

  /** Tests if {@code this in other}. */
  PythonValue in(PythonValue other);

  PythonValue getItem(PythonValue key);

  PythonValue and(PythonValue value);
}
