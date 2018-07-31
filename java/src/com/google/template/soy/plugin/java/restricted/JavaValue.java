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

package com.google.template.soy.plugin.java.restricted;

import com.google.template.soy.plugin.restricted.SoySourceValue;

/** A value that resolves to a SoyValue or supported native type at runtime. */
public interface JavaValue extends SoySourceValue {

  /**
   * Returns a JavaValue that evaluates to 'true' if this JavaValue is not null (false otherwise).
   */
  JavaValue isNonNull();

  /** Returns a JavaValue that evaluates to 'true' if this JavaValue is null (false otherwise). */
  JavaValue isNull();

  /**
   * Asserts that this JavaValue is a boolean. This method is only useful if your signature is
   * overly broad (e.g, 'any' or '?'). Updating the {@code SoyFunctionSignature} is a better option
   * than using this method, but if updating the signature is too difficult then this method will
   * allow templates using this function to fail at template render time if they pass something
   * other than a boolean. If Soy knows at compile time that this JavaValue is incompatible with a
   * boolean, then compilation will fail.
   *
   * <p>For example, a function with a parameter type of {@code ?} could use {@code asSoyBoolean()}
   * to pass the parameter to a method accepting a 'boolean'.
   *
   * <pre>{@code
   * @SoyFunctionSignature(name = "fn", value = @Signature(parameterTypes="?", returnType="?")))
   * MyFn implements SoyJavaSourceFunction {
   *   private static final Method DO_FN = JavaValueFactory.createMethod(
   *       MyFn.class, "doFn", boolean.class);
   *
   *   public static String doFn(boolean param) { return param ? "Yes" : "No"; }
   *
   *   @Override
   *   public JavaValue applyForJavaSource(
   *       JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
   *     return factory.callStaticMethod(DO_FN, args.get(0).asSoyBoolean());
   *   }
   * }
   * }</pre>
   *
   * However, if the parameterType was {@code string}, then the above code would fail to compile
   * because string is incompatible with boolean. (Note that this is different than {@link
   * #coerceToSoyBoolean}, which effectively calls {@link
   * com.google.template.soy.data.SoyValue#coereceToBoolean} and will work with any parameter type
   * and coerce it to a boolean.
   */
  JavaValue asSoyBoolean();

  /**
   * Asserts that this JavaValue is a soy string. This method is only useful if your signature is
   * overly broad (e.g, 'any' or '?'). Updating the {@code SoyFunctionSignature} is a better option
   * than using this method, but if updating the signature is too difficult then this method will
   * allow templates using this function to fail at template render time if they pass something
   * other than a soy string. If Soy knows at compile time that this JavaValue is incompatible with
   * a soy string, then compilation will fail.
   *
   * <p>For example, a function with a parameter type of {@code ?} could use {@code asSoyString()}
   * to pass the parameter to a method accepting a 'soy string'.
   *
   * <pre>{@code
   * @SoyFunctionSignature(name = "fn", value = @Signature(parameterTypes="?", returnType="?")))
   * MyFn implements SoyJavaSourceFunction {
   *   private static final Method DO_FN = JavaValueFactory.createMethod(
   *       MyFn.class, "doFn", String.class);
   *
   *   public static String doFn(String param) { return param + "_"; }
   *
   *   @Override
   *   public JavaValue applyForJavaSource(
   *       JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
   *     return factory.callStaticMethod(DO_FN, args.get(0).asSoyString());
   *   }
   * }
   * }</pre>
   *
   * However, if the parameterType was {@code boolean}, then the above code would fail to compile
   * because boolean is incompatible with a soy string. (Note that this is different than {@link
   * #coerceToSoyString}, which effectively calls {@link
   * com.google.template.soy.data.SoyValue#coereceToString} and will work with any parameter type
   * and coerce it to a soy string.
   */
  JavaValue asSoyString();

  /**
   * Asserts that this JavaValue is a soy int. This method is only useful if your signature is
   * overly broad (e.g, 'any' or '?'). Updating the {@code SoyFunctionSignature} is a better option
   * than using this method, but if updating the signature is too difficult then this method will
   * allow templates using this function to fail at template render time if they pass something
   * other than a soy int. If Soy knows at compile time that this JavaValue is incompatible with a
   * soy int, then compilation will fail.
   *
   * <p>For example, a function with a parameter type of {@code ?} could use {@code asSoyInt()} to
   * pass the parameter to a method accepting a 'soy int'.
   *
   * <pre>{@code
   * @SoyFunctionSignature(name = "fn", value = @Signature(parameterTypes="?", returnType="?")))
   * MyFn implements SoyJavaSourceFunction {
   *   private static final Method DO_FN = JavaValueFactory.createMethod(
   *       MyFn.class, "doFn", long.class);
   *
   *   public static long doFn(long param) { return param + 1; }
   *
   *   @Override
   *   public JavaValue applyForJavaSource(
   *       JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
   *     return factory.callStaticMethod(DO_FN, args.get(0).asSoyInt());
   *   }
   * }
   * }</pre>
   *
   * However, if the parameterType was {@code boolean}, then the above code would fail to compile
   * because boolean is incompatible with a soy int.
   */
  JavaValue asSoyInt();

  /**
   * Asserts that this JavaValue is a soy float. This method is only useful if your signature is
   * overly broad (e.g, 'any' or '?'). Updating the {@code SoyFunctionSignature} is a better option
   * than using this method, but if updating the signature is too difficult then this method will
   * allow templates using this function to fail at template render time if they pass something
   * other than a soy float. If Soy knows at compile time that this JavaValue is incompatible with a
   * soy float, then compilation will fail.
   *
   * <p>For example, a function with a parameter type of {@code ?} could use {@code asSoyFloat()} to
   * pass the parameter to a method accepting a 'soy float'.
   *
   * <pre>{@code
   * @SoyFunctionSignature(name = "fn", value = @Signature(parameterTypes="?", returnType="?")))
   * MyFn implements SoyJavaSourceFunction {
   *   private static final Method DO_FN = JavaValueFactory.createMethod(
   *       MyFn.class, "doFn", double.class);
   *
   *   public static double doFn(double param) { return param + 1; }
   *
   *   @Override
   *   public JavaValue applyForJavaSource(
   *       JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
   *     return factory.callStaticMethod(DO_FN, args.get(0).asSoyFloat());
   *   }
   * }
   * }</pre>
   *
   * However, if the parameterType was {@code boolean}, then the above code would fail to compile
   * because boolean is incompatible with a soy float.
   */
  JavaValue asSoyFloat();

  /**
   * Coerces this JavaValue to a soy boolean. Using this method can avoid overhead if Soy knows the
   * type is already a boolean (or is compatible with 'boolean'), because it can avoid boxing the
   * type into a SoyValue. See {@link #asSoyBoolean} for more details.
   */
  JavaValue coerceToSoyBoolean();

  /**
   * Coerces this JavaValue to a soy string. Using this method can avoid overhead if Soy knows the
   * type is already a string (or is compatible with 'String'), because it can avoid boxing the type
   * into a SoyValue. See {@link #asSoyString} for more details.
   */
  JavaValue coerceToSoyString();
}
