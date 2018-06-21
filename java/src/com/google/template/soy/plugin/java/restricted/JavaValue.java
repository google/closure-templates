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

package com.google.template.soy.plugin.java.restricted;

import com.google.template.soy.plugin.restricted.SoySourceValue;

/** A value that resolves to a SoyValue or supported native type at runtime. */
public interface JavaValue extends SoySourceValue {
  /**
   * Returns the type of this value in the Soy type system. This allows plugin authors to optimize
   * their generated code. For example, a 'ceil' plugin could do something like:
   *
   * <pre>
   * switch (arg.soyType()) {
   *   case INT:
   *     // No calls necessary, the type is already ceil'd.
   *     return arg;
   *   case FLOAT:
   *     // Call a ceil method that expects a 'double' and returns a 'long'.
   *     return factory.callStaticMethod(CEIL_FN, arg);
   *   default:
   *     throw new AssertionError("ceil only allows number types, which are int|float");
   * }
   * </pre>
   */
  ValueSoyType soyType();

  /** The runtime types supported by JavaValue. */
  // Implementation note: This replicates some of the code from SoyType#Kind,
  // because this is exposed to users, and we don't want to expose Soy's internals
  // to users.
  enum ValueSoyType {
    /** Corresponds to the Soy type {@code null}. */
    NULL,
    /** Corresponds to the Soy type {@code bool}. */
    BOOLEAN,
    /** Corresponds to the Soy type {@code float}. */
    FLOAT,
    /** Corresponds to the Soy type {@code int}. */
    INTEGER,
    /** Corresponds to the Soy type {@code string}. */
    STRING,
    /** Corresponds to the Soy type {@code list}. */
    LIST,

    /** Corresponds to everything else. */
    OTHER,
  }
}
