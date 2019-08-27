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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.ibm.icu.util.ULocale;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** Adapts JavaValueFactory to work with Tofu, wrapping the JavaValues in TofuJavaValues. */
// TODO(b/19252021): Add unit tests after things shape up.
class TofuValueFactory extends JavaValueFactory {
  private final FunctionNode fn;
  private final ImmutableMap<String, Supplier<Object>> pluginInstances;

  TofuValueFactory(FunctionNode fn, ImmutableMap<String, Supplier<Object>> pluginInstances) {
    this.fn = fn;
    this.pluginInstances = pluginInstances;
  }

  SoyValue computeForJava(
      SoyJavaSourceFunction srcFn, List<SoyValue> args, TofuPluginContext context) {
    List<JavaValue> javaArgs =
        Lists.transform(args, soyArg -> TofuJavaValue.forSoyValue(soyArg, fn.getSourceLocation()));
    TofuJavaValue result = (TofuJavaValue) srcFn.applyForJavaSource(this, javaArgs, context);
    if (!result.hasSoyValue()) {
      throw RenderException.create(
          "applyForJavaSource must return either an 'args' parameter or the result of "
              + "JavaValueFactory method.");
    }
    return result.soyValue();
  }

  @Override
  public JavaValue callStaticMethod(MethodSignature methodSig, JavaValue... params) {
    return callStaticMethod(toMethod(methodSig), params);
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
  public JavaValue callInstanceMethod(MethodSignature methodSig, JavaValue... params) {
    return callInstanceMethod(toMethod(methodSig), params);
  }

  @Override
  public TofuJavaValue callInstanceMethod(Method method, JavaValue... params) {
    Supplier<Object> instanceSupplier = pluginInstances.get(fn.getFunctionName());
    if (instanceSupplier == null) {
      throw RenderException.create(
          "No plugin instance registered for function '" + fn.getFunctionName() + "'");
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
            soyArg -> {
              TofuJavaValue tjv = (TofuJavaValue) soyArg;
              if (!tjv.hasSoyValue()) {
                throw RenderException.create(
                    "listOf may only be called with the 'arg' parameters to "
                        + "JavaValueFactory methods");
              }
              return tjv.soyValue();
            });
    return TofuJavaValue.forSoyValue(
        SoyValueConverter.INSTANCE.convert(values).resolve(), fn.getSourceLocation());
  }

  @Override
  public TofuJavaValue constant(boolean value) {
    return TofuJavaValue.forSoyValue(BooleanData.forValue(value), SourceLocation.UNKNOWN);
  }

  @Override
  public TofuJavaValue constant(double value) {
    return TofuJavaValue.forSoyValue(FloatData.forValue(value), SourceLocation.UNKNOWN);
  }

  @Override
  public TofuJavaValue constant(long value) {
    return TofuJavaValue.forSoyValue(IntegerData.forValue(value), SourceLocation.UNKNOWN);
  }

  @Override
  public TofuJavaValue constant(String value) {
    return TofuJavaValue.forSoyValue(StringData.forValue(value), SourceLocation.UNKNOWN);
  }

  @Override
  public TofuJavaValue constantNull() {
    return TofuJavaValue.forSoyValue(NullData.INSTANCE, SourceLocation.UNKNOWN);
  }

  private TofuJavaValue wrapInTofuValue(Method method, Object object) {
    if (object instanceof SoyValue) {
      return TofuJavaValue.forSoyValue((SoyValue) object, fn.getSourceLocation());
    }
    try {
      return TofuJavaValue.forSoyValue(
          SoyValueConverter.INSTANCE.convert(object).resolve(), fn.getSourceLocation());
    } catch (SoyDataException e) {
      throw RenderException.create("Invalid return value from `" + method + "`", e);
    }
  }

  private static Method toMethod(MethodSignature methodSig) {
    try {
      Class<?> clazz = Class.forName(methodSig.fullyQualifiedClassName());
      Method method =
          clazz.getMethod(methodSig.methodName(), methodSig.arguments().toArray(new Class<?>[0]));
      if (!method.getReturnType().equals(methodSig.returnType())) {
        throw RenderException.create(
            "Invalid methodSig: '"
                + methodSig
                + "'. Return type is '"
                + method.getReturnType().getName()
                + "', not '"
                + methodSig.returnType()
                + "'.");
      }
      return method;
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
      throw RenderException.create("Invalid methodSig: '" + methodSig + "'.", e);
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
        if (value instanceof NullData || value instanceof UndefinedData) {
          if (Primitives.allPrimitiveTypes().contains(type)) {
            throw RenderException.create(
                "cannot call method "
                    + method.getDeclaringClass().getName()
                    + "."
                    + method.getName()
                    + " because parameter["
                    + i
                    + "] expects a primitive type ["
                    + type
                    + "], but actual value is null [ "
                    + tofuVal
                    + "]");
          }
          params[i] = null;
        } else if (type.isInstance(value)) {
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
        } else if (Message.class.isAssignableFrom(type)) {
          params[i] = type.cast(((SoyProtoValue) value).getProto());
        } else if (type.isEnum() && ProtocolMessageEnum.class.isAssignableFrom(type)) {
          try {
            params[i] =
                type.getDeclaredMethod("forNumber", int.class).invoke(null, value.integerValue());
          } catch (ReflectiveOperationException roe) {
            throw RenderException.create("Invalid parameter: " + tofuVal, roe);
          }
        } else {
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
