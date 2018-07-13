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

package com.google.template.soy.sharedpasses.render;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.ibm.icu.util.ULocale;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** Adapts JavaValueFactory to work with Tofu, wrapping the JavaValues in TofuJavaValues. */
// TODO(b/19252021): Add unit tests after things shape up.
class TofuValueFactory extends JavaValueFactory {
  private final String fnName;
  private final ImmutableMap<String, Supplier<Object>> pluginInstances;

  TofuValueFactory(String fnName, ImmutableMap<String, Supplier<Object>> pluginInstances) {
    this.fnName = fnName;
    this.pluginInstances = pluginInstances;
  }

  SoyValue computeForJava(
      SoyJavaSourceFunction fn, List<SoyValue> args, TofuPluginContext context) {
    List<JavaValue> javaArgs =
        Lists.transform(
            args,
            new Function<SoyValue, JavaValue>() {
              @Override
              public JavaValue apply(SoyValue soyArg) {
                return TofuJavaValue.forSoyValue(soyArg);
              }
            });
    TofuJavaValue result = (TofuJavaValue) fn.applyForJavaSource(this, javaArgs, context);
    if (!result.hasSoyValue()) {
      throw RenderException.create(
          "applyForJavaSource must return either an 'args' parameter or the result of "
              + "JavaValueFactory method.");
    }
    return result.soyValue();
  }

  @Override
  public TofuJavaValue callStaticMethod(Method method, JavaValue... params) {
    try {
      return wrapInTofuValue(method, method.invoke(null, adaptParams(method, params)));
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw RenderException.create("Unexpected exception", e);
    }
  }

  @Override
  public TofuJavaValue callInstanceMethod(Method method, JavaValue... params) {
    Supplier<Object> instanceSupplier = pluginInstances.get(fnName);
    if (instanceSupplier == null) {
      throw RenderException.create("No plugin instance registered for function '" + fnName + "'");
    }
    try {
      return wrapInTofuValue(
          method, method.invoke(instanceSupplier.get(), adaptParams(method, params)));
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw RenderException.create("Unexpected exception", e);
    }
  }

  @Override
  public TofuJavaValue listOf(List<JavaValue> args) {
    List<SoyValue> values =
        Lists.transform(
            args,
            new Function<JavaValue, SoyValue>() {
              @Override
              public SoyValue apply(JavaValue soyArg) {
                TofuJavaValue tjv = (TofuJavaValue) soyArg;
                if (!tjv.hasSoyValue()) {
                  throw RenderException.create(
                      "listOf may only be called with the 'arg' parameters to "
                          + "JavaValueFactory methods");
                }
                return tjv.soyValue();
              }
            });
    return TofuJavaValue.forSoyValue(SoyValueConverter.INSTANCE.convert(values).resolve());
  }

  private static TofuJavaValue wrapInTofuValue(Method method, Object object) {
    if (object instanceof SoyValue) {
      return TofuJavaValue.forSoyValue((SoyValue) object);
    }
    try {
      return TofuJavaValue.forSoyValue(SoyValueConverter.INSTANCE.convert(object).resolve());
    } catch (SoyDataException e) {
      throw RenderException.create("Invalid return value from `" + method + "`", e);
    }
  }

  /**
   * Adapts the values in {@code tofuValues} to fit the types of the method's parameters, returning
   * an {@code Object[]} of the adapted types. This essentially unboxes SoyValues into their native
   * counterparts if the method wants a non-SoyValue parameter.
   */
  private static Object[] adaptParams(Method method, JavaValue[] tofuValues) {
    Class<?>[] paramTypes = method.getParameterTypes();
    if (tofuValues.length != paramTypes.length) {
      throw RenderException.create(
          "Parameters ["
              + Arrays.asList(paramTypes)
              + " don't match values ["
              + Arrays.asList(tofuValues)
              + "], calling method: "
              + method);
    }
    Object[] params = new Object[tofuValues.length];
    for (int i = 0; i < tofuValues.length; i++) {
      TofuJavaValue tofuVal = (TofuJavaValue) tofuValues[i];
      Class<?> type = Primitives.unwrap(paramTypes[i]);
      if (type == BidiGlobalDir.class) {
        params[i] = tofuVal.bidiGlobalDir();
      } else if (type == ULocale.class) {
        params[i] = tofuVal.locale();
      } else {
        if (!tofuVal.hasSoyValue()) {
          throw RenderException.create("Invalid parameter: " + tofuVal);
        }
        SoyValue value = tofuVal.soyValue();
        // TODO(b/19252021): Deal with null values
        if (type.isInstance(value)) {
          params[i] = value;
        } else if (type == boolean.class) {
          params[i] = value.booleanValue();
        } else if (type == int.class) {
          params[i] = value.integerValue();
        } else if (type == long.class) {
          params[i] = value.longValue();
        } else if (type == double.class) {
          params[i] = value.numberValue();
        } else if (type == String.class) {
          params[i] = value.stringValue();
        } else if (type == List.class) {
          params[i] = ((SoyList) value).asJavaList();
        } else {
          // TODO(b/19252021): Map, Iterable, Future, SafeHtml, etc..?
          throw new UnsupportedOperationException(
              "cannot call method "
                  + method.getDeclaringClass().getName()
                  + "."
                  + method.getName()
                  + " because parameter["
                  + i
                  + "] expects a "
                  + type
                  + ", but actual value is a `"
                  + value
                  + "`");
        }
      }
    }
    return params;
  }
}
