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

package com.google.template.soy.basicfunctions;

import static com.google.template.soy.plugin.java.restricted.JavaValueFactory.createMethod;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.plugin.java.internal.SoyJavaExternFunction;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.lang.reflect.Method;
import java.util.List;

/** Holds related methods for mutable lists. */
public final class MutableArrayMethods {
  private MutableArrayMethods() {}

  /** Array.fill(). */
  @SoyMethodSignature(
      name = "fill",
      baseType = "mutable_list<any>",
      value = {
        @Signature(
            parameterTypes = {"any"},
            returnType = "list<any>"),
        @Signature(
            parameterTypes = {"any", "number"},
            returnType = "list<any>"),
        @Signature(
            parameterTypes = {"any", "number", "number"},
            returnType = "list<any>"),
      })
  @SoyPureFunction
  public static class Fill implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("fill", args.subList(1, args.size()));
    }

    @Override
    public Method getExternJavaMethod(List<RuntimeType> argTypes) {
      if (argTypes.size() == 2) {
        return argTypes.get(0) == RuntimeType.SOY_VALUE
            ? Methods.LIST_FILL1_BOXED
            : Methods.LIST_FILL1_UNBOXED;
      } else if (argTypes.size() == 3) {
        return argTypes.get(0) == RuntimeType.SOY_VALUE
            ? Methods.LIST_FILL2_BOXED
            : Methods.LIST_FILL2_UNBOXED;
      } else {
        return argTypes.get(0) == RuntimeType.SOY_VALUE
            ? Methods.LIST_FILL3_BOXED
            : Methods.LIST_FILL3_UNBOXED;
      }
    }

    @Override
    public boolean adaptArgs() {
      return false;
    }
  }

  /** Array.push(). */
  @SoyMethodSignature(
      name = "push",
      baseType = "mutable_list<any>",
      value = {
        @Signature(
            parameterTypes = {"any"},
            returnType = "number"),
      })
  @SoyPureFunction
  public static class Push implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("push", args.subList(1, args.size()));
    }

    @Override
    public Method getExternJavaMethod(List<RuntimeType> argTypes) {
      return argTypes.get(0) == RuntimeType.SOY_VALUE
          ? Methods.LIST_PUSH_BOXED
          : Methods.LIST_PUSH_UNBOXED;
    }

    @Override
    public boolean adaptArgs() {
      return false;
    }
  }

  /** Array.pop(). */
  @SoyMethodSignature(
      name = "pop",
      baseType = "mutable_list<any>",
      value = {
        @Signature(returnType = "any"),
      })
  @SoyPureFunction
  public static class Pop implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("pop");
    }

    @Override
    public Method getExternJavaMethod(List<RuntimeType> argTypes) {
      return argTypes.get(0) == RuntimeType.SOY_VALUE
          ? Methods.LIST_POP_BOXED
          : Methods.LIST_POP_UNBOXED;
    }

    @Override
    public boolean adaptArgs() {
      return false;
    }
  }

  /** Array.push(). */
  @SoyMethodSignature(
      name = "unshift",
      baseType = "mutable_list<any>",
      value = {
        @Signature(
            parameterTypes = {"any"},
            returnType = "number"),
      })
  @SoyPureFunction
  public static class Unshift implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("unshift", args.subList(1, args.size()));
    }

    @Override
    public Method getExternJavaMethod(List<RuntimeType> argTypes) {
      return argTypes.get(0) == RuntimeType.SOY_VALUE
          ? Methods.LIST_UNSHIFT_BOXED
          : Methods.LIST_UNSHIFT_UNBOXED;
    }

    @Override
    public boolean adaptArgs() {
      return false;
    }
  }

  /** Array.shift(). */
  @SoyMethodSignature(
      name = "shift",
      baseType = "mutable_list<any>",
      value = {
        @Signature(returnType = "any"),
      })
  @SoyPureFunction
  public static class Shift implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("shift");
    }

    @Override
    public Method getExternJavaMethod(List<RuntimeType> argTypes) {
      return argTypes.get(0) == RuntimeType.SOY_VALUE
          ? Methods.LIST_SHIFT_BOXED
          : Methods.LIST_SHIFT_UNBOXED;
    }

    @Override
    public boolean adaptArgs() {
      return false;
    }
  }

  /** Array.splice(). */
  @SoyMethodSignature(
      name = "splice",
      baseType = "mutable_list<any>",
      value = {
        @Signature(
            parameterTypes = {"number"},
            returnType = "list<any>"),
        @Signature(
            parameterTypes = {"number", "number"},
            returnType = "list<any>"),
        @Signature(
            parameterTypes = {"number", "number", "any"},
            returnType = "list<any>"),
      })
  @SoyPureFunction
  public static class Splice implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("splice", args.subList(1, args.size()));
    }

    @Override
    public Method getExternJavaMethod(List<RuntimeType> argTypes) {
      if (argTypes.size() == 2) {
        return argTypes.get(0) == RuntimeType.SOY_VALUE
            ? Methods.LIST_SPLICE1_BOXED
            : Methods.LIST_SPLICE1_UNBOXED;
      } else if (argTypes.size() == 3) {
        return argTypes.get(0) == RuntimeType.SOY_VALUE
            ? Methods.LIST_SPLICE2_BOXED
            : Methods.LIST_SPLICE2_UNBOXED;
      } else {
        return argTypes.get(0) == RuntimeType.SOY_VALUE
            ? Methods.LIST_SPLICE3_BOXED
            : Methods.LIST_SPLICE3_UNBOXED;
      }
    }

    @Override
    public boolean adaptArgs() {
      return false;
    }
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method LIST_FILL1_BOXED =
        createMethod(BasicFunctionsRuntime.class, "listFill", SoyValue.class, Object.class);
    static final Method LIST_FILL1_UNBOXED =
        createMethod(BasicFunctionsRuntime.class, "listFill", List.class, Object.class);
    static final Method LIST_FILL2_BOXED =
        createMethod(
            BasicFunctionsRuntime.class, "listFill", SoyValue.class, Object.class, long.class);
    static final Method LIST_FILL2_UNBOXED =
        createMethod(BasicFunctionsRuntime.class, "listFill", List.class, Object.class, long.class);
    static final Method LIST_FILL3_BOXED =
        createMethod(
            BasicFunctionsRuntime.class,
            "listFill",
            SoyValue.class,
            Object.class,
            long.class,
            long.class);
    static final Method LIST_FILL3_UNBOXED =
        createMethod(
            BasicFunctionsRuntime.class,
            "listFill",
            List.class,
            Object.class,
            long.class,
            long.class);

    static final Method LIST_PUSH_BOXED =
        createMethod(BasicFunctionsRuntime.class, "listPush", SoyValue.class, Object.class);
    static final Method LIST_PUSH_UNBOXED =
        createMethod(BasicFunctionsRuntime.class, "listPush", List.class, Object.class);

    static final Method LIST_POP_BOXED =
        createMethod(BasicFunctionsRuntime.class, "listPop", SoyValue.class);
    static final Method LIST_POP_UNBOXED =
        createMethod(BasicFunctionsRuntime.class, "listPop", List.class);

    static final Method LIST_UNSHIFT_BOXED =
        createMethod(BasicFunctionsRuntime.class, "listUnshift", SoyValue.class, Object.class);
    static final Method LIST_UNSHIFT_UNBOXED =
        createMethod(BasicFunctionsRuntime.class, "listUnshift", List.class, Object.class);

    static final Method LIST_SHIFT_BOXED =
        createMethod(BasicFunctionsRuntime.class, "listShift", SoyValue.class);
    static final Method LIST_SHIFT_UNBOXED =
        createMethod(BasicFunctionsRuntime.class, "listShift", List.class);

    static final Method LIST_SPLICE1_BOXED =
        createMethod(BasicFunctionsRuntime.class, "listSplice", SoyValue.class, long.class);
    static final Method LIST_SPLICE1_UNBOXED =
        createMethod(BasicFunctionsRuntime.class, "listSplice", List.class, long.class);
    static final Method LIST_SPLICE2_BOXED =
        createMethod(
            BasicFunctionsRuntime.class, "listSplice", SoyValue.class, long.class, long.class);
    static final Method LIST_SPLICE2_UNBOXED =
        createMethod(BasicFunctionsRuntime.class, "listSplice", List.class, long.class, long.class);
    static final Method LIST_SPLICE3_BOXED =
        createMethod(
            BasicFunctionsRuntime.class,
            "listSplice",
            SoyValue.class,
            long.class,
            long.class,
            Object.class);
    static final Method LIST_SPLICE3_UNBOXED =
        createMethod(
            BasicFunctionsRuntime.class,
            "listSplice",
            List.class,
            long.class,
            long.class,
            Object.class);
  }
}
