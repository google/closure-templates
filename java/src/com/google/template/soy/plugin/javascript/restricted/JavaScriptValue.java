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

package com.google.template.soy.plugin.javascript.restricted;

import com.google.common.base.Optional;
import com.google.template.soy.plugin.restricted.SoySourceValue;

/** A value that resolves to a SoyValue or supported native type at runtime. */
public interface JavaScriptValue extends SoySourceValue {

  /**
   * Returns a JavaValue that evaluates to 'true' if this JavaScriptValue is not null (false
   * otherwise).
   */
  JavaScriptValue isNonNull();

  /** Returns a JavaValue that evaluates to 'true' if this JavaValue is null (false otherwise). */
  JavaScriptValue isNull();

  /** Returns the literal value of this value if it is a string literal. */
  Optional<String> asStringLiteral();

  /** Coerce this value to a string. */
  JavaScriptValue coerceToString();

  /** Invokes a method on the given object. Useful for accessing String or Array methods. */
  JavaScriptValue invokeMethod(String ident, JavaScriptValue... args);

  /** Accesses a property on the given object. */
  JavaScriptValue accessProperty(String ident);
}
