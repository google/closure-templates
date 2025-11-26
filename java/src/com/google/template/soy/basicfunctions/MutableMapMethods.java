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

import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.plugin.java.internal.SoyJavaExternFunction;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import java.lang.reflect.Method;
import java.util.List;

/** Holds related methods for the mutable_map type, used in externs. */
final class MutableMapMethods {
  private MutableMapMethods() {}

  /** Map.clear(). */
  @SoyMethodSignature(
      name = "clear",
      baseType = "mutable_map<any, any>",
      value = {
        @Signature(
            parameterTypes = {},
            returnType = "null"),
      })
  public static class Clear implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("clear");
    }

    @Override
    public boolean adaptArgs() {
      return false;
    }

    @Override
    public Method getExternJavaMethod() {
      return Methods.MAP_CLEAR;
    }
  }

  /** Map.delete(). */
  @SoyMethodSignature(
      name = "delete",
      baseType = "mutable_map<any, any>",
      value = {
        @Signature(
            parameterTypes = {"any"},
            returnType = "bool"),
      })
  public static class Delete implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("delete", args.get(1));
    }

    @Override
    public boolean adaptArgs() {
      return true;
    }

    @Override
    public Method getExternJavaMethod() {
      return Methods.MAP_DELETE;
    }
  }

  /** Map.set(). */
  @SoyMethodSignature(
      name = "set",
      baseType = "mutable_map<any, any>",
      value = {
        @Signature(
            parameterTypes = {"any", "any"},
            returnType = "mutable_map<any, any>"),
      })
  public static class Set implements SoyJavaExternFunction, SoyJavaScriptSourceFunction {
    @Override
    public JavaScriptValue applyForJavaScriptSource(
        JavaScriptValueFactory factory,
        List<JavaScriptValue> args,
        JavaScriptPluginContext context) {
      return args.get(0).invokeMethod("set", args.get(1), args.get(2));
    }

    @Override
    public boolean adaptArgs() {
      return true;
    }

    @Override
    public Method getExternJavaMethod() {
      return Methods.MAP_SET;
    }
  }

  private static final class Methods {
    static final Method MAP_CLEAR = JavaValueFactory.createMethod(SoyMap.class, "clear");
    static final Method MAP_DELETE =
        JavaValueFactory.createMethod(SoyMap.class, "delete", SoyValue.class);
    static final Method MAP_SET =
        JavaValueFactory.createMethod(SoyMap.class, "set", SoyValue.class, SoyValue.class);
  }
}
