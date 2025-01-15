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

import com.google.template.soy.plugin.restricted.SoySourceFunction;
import java.util.List;

/**
 * A {@link SoySourceFunction} that generates code to be called at JavaScript render time. All
 * SoyJavaScriptSourceFunction implementations must be annotated with {@literal @}{@link
 * com.google.template.soy.shared.restricted.SoyFunctionSignature}.
 */
public interface SoyJavaScriptSourceFunction extends SoySourceFunction {
  /**
   * Instructs Soy as to how to implement the function when compiling to JavaScript.
   *
   * <p>The {@code args} can only represent the types that can represent all values of the type
   * listed in the function signature. Additionally, the return value must represent a type
   * compatible with the returnType of the function signature.
   */
  JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context);
}
