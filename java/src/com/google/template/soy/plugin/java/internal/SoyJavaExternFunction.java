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

  /** Whether a jbcsrc value is boxed. */
  enum Boxedness {
    BOXED,
    UNBOXED
  }

  Method getExternJavaMethod(List<Boxedness> argsBoxed);

  default boolean bypassParamAdapt(int paramIndex, List<Boxedness> argsBoxed) {
    return false;
  }
}
