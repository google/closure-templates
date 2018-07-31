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

import com.google.template.soy.plugin.restricted.SoySourceFunction;
import java.util.List;

/**
 * A {@link SoySourceFunction} that generates code to be called at Java render time. All
 * SoyJavaSourceFunctions must be annotated with {@literal @}{@link
 * com.google.template.soy.shared.restricted.SoyFunctionSignature}.
 */
public interface SoyJavaSourceFunction extends SoySourceFunction {
  /**
   * Instructs Soy as to how to implement the function.
   *
   * <p>The {@code args} can only represent the types that can represent all values of the type
   * listed in the function signature. Additionally, the return value must represent a type
   * compatible with the returnType of the function signature.
   *
   * <p>For example:
   *
   * <pre>
   * @SoyFunctionSignature(
   *     name = "fn",
   *     value = @Signature(parameterTypes={"int", "?", "string|html", returnType="?"))
   * )
   * MyFn implements SoyJavaSourceFunction {
   *   private static final Method DO_FN = JavaValueFactory.createMethod(
   *       MyFn.class, "doFn", long.class, SoyValue.class, String.class);
   *
   *   public static String doFn(long a, SoyValue b, String c) {
   *     return a + b.toString() + c;
   *   }
   *
   *   @Override
   *   public JavaValue applyForJavaSource(
   *       JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
   *     return factory.callStaticMethod(DO_FN, args.get(0), args.get(1), args.get(2));
   *   }
   * }
   * </pre>
   *
   * <p>The above template will compile. The first parameter of {@code DO_FN} is a 'long', which is
   * compatible with the parameter soy type of {@code int}. The second is a 'SoyValue', which is the
   * only type compatible with the soy type of {@code ?}. The third is a 'String', which is
   * compatible with both soy types of {@code string} and {@code html}. The signature allows a
   * return of "?", which means any valid Soy type can be returned, so the return type of 'String'
   * is allowed.
   *
   * <p>If the parameters or return types do not match, the template will fail compilation. If it is
   * difficult to change the signature, the types can be specialized using {@code
   * JavaValue.asSoy[X]} or {@code JavaValue.coerceToSoy[X]}. See those methods for more
   * information.
   */
  JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context);
}
