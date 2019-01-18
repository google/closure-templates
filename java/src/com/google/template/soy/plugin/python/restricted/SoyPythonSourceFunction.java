/*
 * Copyright 2019 Google Inc.
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

import com.google.template.soy.plugin.restricted.SoySourceFunction;
import java.util.List;

/**
 * A {@link SoySourceFunction} that generates code to be called at Python render time. All
 * SoyPythonFunction implementations must be annotated with {@literal @}{@link
 * com.google.template.soy.shared.restricted.SoyFunctionSignature}.
 */
public interface SoyPythonSourceFunction extends SoySourceFunction {
  /**
   * Instructs Soy as to how to implement the function when compiling to Python.
   *
   * <p>The {@code args} can only represent the types that can represent all values of the type
   * listed in the function signature. Additionally, the return value must represent a type
   * compatible with the returnType of the function signature.
   */
  PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context);
}
