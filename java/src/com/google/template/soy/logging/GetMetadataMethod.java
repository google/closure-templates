/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.logging;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import java.util.List;

/** A Soy method to get the VE metadata from a {@code ve} object. */
@SoyMethodSignature(
    name = "getMetadata",
    baseType = "ve<any>",
    value = @Signature(returnType = "soy.LoggableElementMetadata"))
public final class GetMetadataMethod implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction {

  private static final class Methods {
    private static final MethodSignature METHOD =
        MethodSignature.create(
            "com.google.template.soy.logging.LoggingMethodsRuntime",
            "getMetadata",
            LoggableElementMetadata.class,
            SoyVisualElement.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    checkArgument(args.size() == 1);
    return factory.callStaticMethod(Methods.METHOD, args.get(0));
  }

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    checkArgument(args.size() == 1);
    return factory.callModuleFunction("soy.velog", "$$getMetadata", args.get(0));
  }
}
