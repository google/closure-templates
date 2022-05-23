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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** A factory for instructing soy how to implement a {@link SoyJavaSourceFunction}. */
public abstract class JavaValueFactory {

  /**
   * Instructs Soy to call the given static {@code method} with the given params at runtime.
   *
   * <p>Warning: This method requires a compile-time dependency on the runtime logic and may bloat
   * your build actions. Prefer using the variant that takes a {@link MethodSignature} instead.
   */
  public abstract JavaValue callStaticMethod(Method method, JavaValue... params);

  /** Instructs Soy to call the given static {@code method} with the given params at runtime. */
  public abstract JavaValue callStaticMethod(MethodSignature methodSignature, JavaValue... params);

  /**
   * Instructs Soy to call the given {@code method} with the given params on the registered plugin
   * instance at runtime. In the SoySauce backend, instances are registered in the
   * SoySauce.Renderer, in the Tofu backend, instances are registered in the SoyTofu.Renderer.
   *
   * <p>Warning: This method requires a compile-time dependency on the runtime logic and may bloat
   * your build actions. Prefer using the variant that takes a {@link MethodSignature} instead.
   */
  public abstract JavaValue callInstanceMethod(Method method, JavaValue... params);

  /**
   * Instructs Soy to call the given {@code method} with the given params on the registered plugin
   * instance at runtime. In the SoySauce backend, instances are registered in the
   * SoySauce.Renderer, in the Tofu backend, instances are registered in the SoyTofu.Renderer.
   */
  public abstract JavaValue callInstanceMethod(
      MethodSignature methodSignature, JavaValue... params);

  /**
   * Returns a JavaValue that corresponds to a list containing each of the values. The values will
   * be wrapped in {@code SoyValue} instances if they are not already SoyValues.
   */
  public abstract JavaValue listOf(List<JavaValue> args);

  /** Returns a JavaValue that corresponds to the given constant. */
  public abstract JavaValue constant(double value);

  /** Returns a JavaValue that corresponds to the given constant. */
  public abstract JavaValue constant(long value);

  /** Returns a JavaValue that corresponds to the given constant. */
  public abstract JavaValue constant(String value);

  /** Returns a JavaValue that corresponds to the given constant. */
  public abstract JavaValue constant(boolean value);

  /** Returns a JavaValue that corresponds to null. */
  public abstract JavaValue constantNull();

  /**
   * Convenience method for retrieving a {@link Method} from a class.
   *
   * @see Class#getMethod(String, Class...)
   */
  public static Method createMethod(Class<?> clazz, String methodName, Class<?>... params) {
    try {
      return clazz.getMethod(methodName, params);
    } catch (NoSuchMethodException e) {
      if (params.length == 0) {
        throw new IllegalArgumentException(
            String.format(
                "No such public method: %s.%s (with no parameters)", clazz.getName(), methodName),
            e);
      } else {
        throw new IllegalArgumentException(
            String.format(
                "No such public method: %s.%s(%s)",
                clazz.getName(),
                methodName,
                Arrays.stream(params).map(Class::getName).collect(Collectors.joining(","))),
            e);
      }
    } catch (SecurityException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
