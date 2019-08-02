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

import com.google.common.collect.Iterables;

/** A factory for instructing soy how to implement a {@link SoyJavaScriptSourceFunction}. */
public abstract class JavaScriptValueFactory {

  /**
   * Instructs Soy to reference the given export which is provided by a {@code goog.module}. This
   * also works for {@code goog.provide}.
   *
   * <p>For example, given this JavaScript file:<code><pre>
   * goog.module('foo.bar');
   * exports.baz = {quz: ...};
   * </pre></code>
   *
   * <p>You would arrange to have Soy call it by invoking {@code factory.moduleExport("foo.bar",
   * "baz.qux")}
   */
  public abstract JavaScriptValue getModuleExport(String moduleName, String export);

  /**
   * Instructs Soy to call the given function which is provided by a {@code goog.module} with the
   * given params at runtime.
   *
   * <p>For example, given this JavaScript file:<code><pre>
   * goog.module('foo.bar');
   * exports.baz = function(...){...}
   * </pre></code>
   *
   * <p>You would arrange to have Soy call it by invoking {@code
   * factory.callModuleFunction("foo.bar", "baz", ....)}
   *
   * <p>Because the Soy compiler does not have access to the JavaScript being referenced there is
   * little validation that parameters match or have the correct types. So errors in plugin
   * implementations may not be caught until Javascript compilation time or runtime.
   */
  public abstract JavaScriptValue callModuleFunction(
      String moduleName, String functionName, JavaScriptValue... params);

  /** See {@link #callModuleFunction(String, String, JavaScriptValue...)}. */
  public final JavaScriptValue callModuleFunction(
      String moduleName, String functionName, Iterable<JavaScriptValue> params) {
    return callModuleFunction(
        moduleName, functionName, Iterables.toArray(params, JavaScriptValue.class));
  }

  /**
   * Instructs Soy to call the given function which is provided by the {@code goog.provide}
   * namespace with the given parameters at runtime.
   *
   * <p>For example, given this JavaScript file:<code><pre>
   * goog.provide('foo.bar');
   * foo.bar.baz = function(...){...}
   * </pre></code>
   *
   * <p>You would arrange to have Soy call it by invoking {@code
   * factory.callNamespaceFunction("foo.bar", "foo.bar.baz", ...)}.
   *
   * <p>Because the Soy compiler does not have access to the JavaScript being referenced there is
   * little validation that parameters match or have the correct types. So errors in plugin
   * implementations may not be caught until Javascript compilation time or runtime.
   */
  public abstract JavaScriptValue callNamespaceFunction(
      String googProvide, String fullFunctionName, JavaScriptValue... params);

  /** See {@link #callNamespaceFunction(String, String, JavaScriptValue...)}. */
  public final JavaScriptValue callNamespaceFunction(
      String googProvide, String fullFunctionName, Iterable<JavaScriptValue> params) {
    return callNamespaceFunction(
        googProvide, fullFunctionName, Iterables.toArray(params, JavaScriptValue.class));
  }

  /** Creates an integer constant. */
  public abstract JavaScriptValue constant(long num);

  /** Creates a floating point constant. */
  public abstract JavaScriptValue constant(double num);

  /** Creates a String constant. */
  public abstract JavaScriptValue constant(String str);

  /** Creates a boolean constant. */
  public abstract JavaScriptValue constant(boolean bool);

  /** Creates a null constant. */
  public abstract JavaScriptValue constantNull();

  /** Creates a reference to a global symbol, e.g. {@code Math}. */
  public abstract JavaScriptValue global(String globalSymbol);

  /**
   * Creates an unsafe javascript expression.
   *
   * <p>Useful for migrating from the legacy SoyJsSrcFunction interface.
   */
  @Deprecated
  public abstract JavaScriptValue unsafeUncheckedExpression(String jsExpr);
}
