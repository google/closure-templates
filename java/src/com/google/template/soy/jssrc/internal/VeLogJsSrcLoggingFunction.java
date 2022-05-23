/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import java.util.List;

/**
 * Soy special function for internal usages.
 *
 * <p>This function is explicitly not registered with {@link BasicFunctionsModule}. It exists for
 * client-side VE logging, and should not be used by Soy users.
 */
public final class VeLogJsSrcLoggingFunction implements SoyJavaScriptSourceFunction {
  // $$ prefix ensures that the function cannot be used directly
  public static final String NAME = "$$loggingFunction";

  public static final VeLogJsSrcLoggingFunction INSTANCE = new VeLogJsSrcLoggingFunction();

  // Do not @Inject; should not be used externally.
  private VeLogJsSrcLoggingFunction() {}

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return factory.callModuleFunction(
        "soy.velog",
        "$$getLoggingFunctionAttribute",
        factory.callNamespaceFunction("xid", "xid", args.get(0)),
        args.get(1),
        args.get(2));
  }
}
