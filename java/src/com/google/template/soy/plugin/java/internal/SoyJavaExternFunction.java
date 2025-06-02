/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.plugin.java.internal;

import com.google.template.soy.plugin.restricted.SoySourceFunction;
import java.lang.reflect.Method;
import java.util.List;

/** A SoySourceFunction that can be executed in JBCSRC via the extern compilation pipeline. */
public interface SoyJavaExternFunction extends SoySourceFunction {

  /** The runtime type of an incoming argument. */
  enum RuntimeType {
    LONG,
    DOUBLE,
    BOOLEAN,
    SOY_VALUE,
    OBJECT
  }

  default Method getExternJavaMethod(List<RuntimeType> argTypes) {
    return getExternJavaMethod();
  }

  default Method getExternJavaMethod() {
    throw new AbstractMethodError();
  }

  /**
   * Returns whether args should be adapted via the standard extern pipeline, or passed as-is. If
   * this method returns false then typically getExternJavaMethod will return a method for boxed
   * SoyValues and another method for unboxed values.
   */
  default boolean adaptArgs() {
    return true;
  }
}
