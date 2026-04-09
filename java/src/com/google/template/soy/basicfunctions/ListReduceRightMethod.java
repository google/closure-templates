/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.basicfunctions;

import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.plugin.java.internal.SoyJavaExternFunction;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.lang.reflect.Method;
import java.util.List;

/** Soy method for reducing a list from right to left to a single value. */
@SoyMethodSignature(
    name = "reduceRight",
    baseType = "list<any>",
    value = {
      @Signature(
          parameterTypes = {"(acc:any, val:any, index:int, arr:list<any>) => any"},
          returnType = "any"),
      @Signature(
          parameterTypes = {"(acc:any, val:any, index:int, arr:list<any>) => any", "any"},
          returnType = "any"),
    })
@SoyPureFunction
public final class ListReduceRightMethod
    implements SoyJavaScriptSourceFunction, SoyJavaExternFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    if (args.size() == 2) {
      return args.get(0).invokeMethod("reduceRight", args.get(1));
    } else {
      return args.get(0).invokeMethod("reduceRight", args.get(1), args.get(2));
    }
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method REDUCE_RIGHT_1 =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class,
            "listReduceRight",
            SoyList.class,
            BasicFunctionsRuntime.ArrayReduceCallback.class);
    static final Method REDUCE_RIGHT_2 =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class,
            "listReduceRight",
            SoyList.class,
            BasicFunctionsRuntime.ArrayReduceCallback.class,
            SoyValue.class);
  }

  @Override
  public Method getExternJavaMethod(List<RuntimeType> argTypes) {
    return argTypes.size() == 2 ? Methods.REDUCE_RIGHT_1 : Methods.REDUCE_RIGHT_2;
  }
}
