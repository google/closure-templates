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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.template.soy.types.SoyTypes.getMapKeysType;
import static com.google.template.soy.types.SoyTypes.getMapValuesType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.primitives.Primitives;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyValueUnconverter;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.GbigintData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.plugin.internal.JavaPluginExecContext;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.plugin.java.SharedExternRuntime;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.types.AbstractIterableType;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Adapts JavaValueFactory to work with Tofu, wrapping the JavaValues in TofuJavaValues. */
// TODO(b/19252021): Add unit tests after things shape up.
class TofuValueFactory extends JavaValueFactory {
  private final SourceLocation fnSourceLocation;
  private final String instanceKey;
  private final PluginInstances pluginInstances;
  @Nullable private final FunctionType externSig;
  private final boolean produceRawTofuValues;
  @Nullable private final FunctionAdapter functionAdapter;

  TofuValueFactory(JavaPluginExecContext fn, PluginInstances pluginInstances) {
    this(fn.getSourceLocation(), fn.getFunctionName(), pluginInstances, null, false, null);
  }

  TofuValueFactory(
      SourceLocation fnSourceLocation,
      String instanceKey,
      PluginInstances pluginInstances,
      FunctionType externSig,
      boolean produceRawTofuValues,
      FunctionAdapter functionAdapter) {
    this.fnSourceLocation = fnSourceLocation;
    this.instanceKey = instanceKey;
    this.pluginInstances = pluginInstances;
    this.externSig = externSig;
    this.produceRawTofuValues = produceRawTofuValues;
    this.functionAdapter = functionAdapter;
  }

  SoyValue computeForJava(
      SoyJavaSourceFunction srcFn, List<SoyValue> args, TofuPluginContext context) {
    List<JavaValue> javaArgs =
        Lists.transform(args, soyArg -> TofuJavaValue.forSoyValue(soyArg, fnSourceLocation));
    TofuJavaValue result = (TofuJavaValue) srcFn.applyForJavaSource(this, javaArgs, context);
    if (!result.hasSoyValue()) {
      throw RenderException.create(
          "applyForJavaSource must return either an 'args' parameter or the result of "
              + "JavaValueFactory method.");
    }
    return result.soyValue();
  }

  TofuJavaValue callStaticMethod(
      MethodSignature methodSig, @Nullable SoyType returnType, JavaValue... params) {
    return callStaticMethod(toMethod(methodSig), returnType, params);
  }

  @Override
  public TofuJavaValue callStaticMethod(MethodSignature methodSig, JavaValue... params) {
    return callStaticMethod(methodSig, null, params);
  }

  TofuJavaValue callStaticMethod(Method method, @Nullable SoyType returnType, JavaValue... params) {
    try {
      return wrapInTofuValue(method, method.invoke(null, adaptParams(method, params)), returnType);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw RenderException.create("Unexpected exception", e);
    }
  }

  @Override
  public TofuJavaValue callStaticMethod(Method method, JavaValue... params) {
    return callStaticMethod(method, null, params);
  }

  @Override
  public JavaValue callJavaValueMethod(Method method, JavaValue instance, JavaValue... params) {
    SoyValue soyValue = ((TofuJavaValue) instance).soyValue();
    try {
      return wrapInTofuValue(method, method.invoke(soyValue, adaptParams(method, params)), null);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw RenderException.create("Unexpected exception", e);
    }
  }

  TofuJavaValue callInstanceMethod(
      MethodSignature methodSig, @Nullable SoyType returnType, JavaValue... params) {
    return callInstanceMethod(toMethod(methodSig), returnType, params);
  }

  @Override
  public TofuJavaValue callInstanceMethod(MethodSignature methodSig, JavaValue... params) {
    return callInstanceMethod(toMethod(methodSig), null, params);
  }

  TofuJavaValue callInstanceMethod(
      Method method, @Nullable SoyType returnType, JavaValue... params) {
    Supplier<Object> instanceSupplier = pluginInstances.get(instanceKey);
    if (instanceSupplier == null) {
      throw RenderException.create(
          "No plugin instance registered for key '"
              + instanceKey
              + "'. Available keys are: "
              + pluginInstances.keys());
    }
    return callInstanceMethod(method, returnType, instanceSupplier.get(), params);
  }

  TofuJavaValue callInstanceMethod(
      Method method, @Nullable SoyType returnType, Object target, JavaValue[] params) {
    try {
      return wrapInTofuValue(
          method, method.invoke(target, adaptParams(method, params)), returnType);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw RenderException.create("Unexpected exception", e);
    }
  }

  @Override
  public TofuJavaValue callInstanceMethod(Method method, JavaValue... params) {
    return callInstanceMethod(method, null, params);
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
        SoyValueConverter.INSTANCE.convert(values).resolve(), fnSourceLocation);
  }

  @Override
  public TofuJavaValue varArgsOf(List<JavaValue> args) {
    if (args.isEmpty()) {
      return listOf(ImmutableList.of());
    }
    return TofuJavaValue.forSoyValue(
        ((TofuJavaValue) args.get(0)).soyValue(), SourceLocation.UNKNOWN);
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

  private TofuJavaValue wrapInTofuValue(
      Method method, Object object, @Nullable SoyType returnType) {
    if (produceRawTofuValues) {
      return TofuJavaValue.forRaw(object);
    }
    if (object instanceof SoyValue) {
      return TofuJavaValue.forSoyValue((SoyValue) object, fnSourceLocation);
    }
    if (returnType != null && returnType.getKind() == SoyType.Kind.MAP && object instanceof Map) {
      // When Soy sees a map, it defaults to thinking it's a legacy_object_map, which only allow
      // string keys. We know that's not the case here (because the Soy return type of the extern
      // is "map") so mark this as a "map" and not a "legacy_object_map".
      object = SoyValueConverter.markAsSoyMap((Map) object);
    }
    try {
      return TofuJavaValue.forSoyValue(
          SoyValueConverter.INSTANCE.convert(object).resolve(), fnSourceLocation);
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
  private Object[] adaptParams(Method method, JavaValue[] tofuValues) {
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

    int numSoyParams = externSig != null ? externSig.getParameters().size() : 0;
    for (int i = 0; i < tofuValues.length; i++) {
      params[i] =
          adaptParam(
              (TofuJavaValue) tofuValues[i],
              paramTypes[i],
              i < numSoyParams ? externSig.getParameters().get(i).getType() : null);
      if (params[i] == ERROR) {
        throw RenderException.create(
            String.format(
                "cannot call method %s.%s because parameter[%s] expects a primitive type [%s], but"
                    + " actual value is null [%s]",
                method.getDeclaringClass().getName(),
                method.getName(),
                i,
                paramTypes[i],
                tofuValues[i]));
      }
    }
    return params;
  }

  private static final Object ERROR = new Object();

  private Object adaptParam(TofuJavaValue tofuVal, Class<?> type, @Nullable SoyType soyType) {
    boolean isExternApi = externSig != null;
    if (isExternApi && tofuVal.hasSoyValue() && tofuVal.soyValue() instanceof TofuFunctionValue) {
      TofuFunctionValue functionPtr = (TofuFunctionValue) tofuVal.soyValue();
      return functionAdapter.adapt(functionPtr, type);
    }
    return adaptValueToJava(tofuVal, type, soyType, isExternApi);
  }

  static Object adaptValueToJava(
      TofuJavaValue tofuVal, Class<?> type, @Nullable SoyType soyType, boolean isExternApi) {
    if (!tofuVal.hasSoyValue()) {
      return tofuVal.rawValue();
    } else {
      SoyValue value = tofuVal.soyValue();

      if (value.isNullish()) {
        if (Primitives.allPrimitiveTypes().contains(type)) {
          return ERROR;
        }
        // Always pass NullData as java null. Pass UndefinedData as UndefinedData unless that
        // would be a CCE in the java implementation, otherwise pass null.
        if (!value.isNull() && type.isAssignableFrom(value.getClass())) {
          return value;
        }
        return null;
      } else if (isExternApi && type == Object.class) {
        return SoyValueUnconverter.unconvert(value);
      } else if (isExternApi && type == Number.class) {
        return value instanceof IntegerData ? value.longValue() : value.floatValue();
      } else if (type.isInstance(value)) {
        return value;
      }

      Class<?> primitiveType = Primitives.unwrap(type);
      if (primitiveType == boolean.class) {
        return value.booleanValue();
      } else if (primitiveType == int.class) {
        return value.integerValue();
      } else if (primitiveType == long.class) {
        return value.longValue();
      } else if (primitiveType == double.class) {
        return value.floatValue();
      } else if (primitiveType == float.class) {
        return (float) value.floatValue();
      } else if (type == String.class) {
        return value.stringValue();
      } else if (type == BigInteger.class) {
        return ((GbigintData) value).getValue();
      } else if (Iterable.class.isAssignableFrom(type)) {
        if (isExternApi) {
          return value.asJavaList().stream()
              .map(
                  item ->
                      adaptCollectionValueToJava(
                          item, ((AbstractIterableType) soyType).getElementType()))
              .collect(toImmutableList());
        } else {
          return value.asJavaList();
        }
      } else if (Map.class.isAssignableFrom(type) && isExternApi) {
        if (soyType.getKind() == Kind.RECORD || !(value instanceof SoyMap)) {
          if (ImmutableMap.class.isAssignableFrom(type)) {
            return SharedExternRuntime.recordToImmutableMap(value);
          } else {
            return SharedExternRuntime.recordToMap(value);
          }
        }
        SoyType mapKeysType = getMapKeysType(soyType);
        SoyType mapValuesType = getMapValuesType(soyType);
        return ((SoyMap) value)
            .entrySet().stream()
                .collect(
                    toImmutableMap(
                        e -> adaptCollectionValueToJava(e.getKey(), mapKeysType),
                        e -> adaptCollectionValueToJava(e.getValue(), mapValuesType)));
      } else if (type.isEnum() && ProtocolMessageEnum.class.isAssignableFrom(type)) {
        try {
          return type.getDeclaredMethod("forNumber", int.class).invoke(null, value.integerValue());
        } catch (ReflectiveOperationException roe) {
          throw RenderException.create("Invalid parameter: " + tofuVal, roe);
        }
      } else if (type == SafeHtml.class) {
        return ((SanitizedContent) value).toSafeHtml();
      } else if (type == SafeScript.class) {
        return ((SanitizedContent) value).toSafeScript();
      } else if (type == SafeStyle.class) {
        return ((SanitizedContent) value).toSafeStyle();
      } else if (type == SafeStyleSheet.class) {
        return ((SanitizedContent) value).toSafeStyleSheet();
      } else if (type == SafeUrl.class) {
        return ((SanitizedContent) value).toSafeUrl();
      } else if (type == TrustedResourceUrl.class) {
        return ((SanitizedContent) value).toTrustedResourceUrl();
      } else if (type == SafeHtmlProto.class) {
        return ((SanitizedContent) value).toSafeHtmlProto();
      } else if (type == SafeScriptProto.class) {
        return ((SanitizedContent) value).toSafeScriptProto();
      } else if (type == SafeStyleProto.class) {
        return ((SanitizedContent) value).toSafeStyleProto();
      } else if (type == SafeStyleSheetProto.class) {
        return ((SanitizedContent) value).toSafeStyleSheetProto();
      } else if (type == SafeUrlProto.class) {
        return ((SanitizedContent) value).toSafeUrlProto();
      } else if (type == TrustedResourceUrlProto.class) {
        return ((SanitizedContent) value).toTrustedResourceUrlProto();
      } else if (Message.class.isAssignableFrom(type)) {
        return type.cast(value.getProto());
      } else {
        return ERROR;
      }
    }
  }

  private static Object adaptCollectionValueToJava(SoyValueProvider item, SoyType elmType) {
    SoyValue val = item.resolve();
    switch (elmType.getKind()) {
      case INT:
        return val.longValue();
      case FLOAT:
      case NUMBER:
        return val.floatValue();
      case STRING:
        return val.coerceToString();
      case BOOL:
        return val.coerceToBoolean();
      case GBIGINT:
        return ((GbigintData) val).getValue();
      case PROTO:
        return val.getProto();
      case PROTO_ENUM:
        String javaClass =
            JavaQualifiedNames.getClassName(((SoyProtoEnumType) elmType).getDescriptor());
        try {
          return Class.forName(javaClass)
              .getDeclaredMethod("forNumber", int.class)
              .invoke(null, val.integerValue());
        } catch (ReflectiveOperationException roe) {
          throw RenderException.create("Invalid parameter: " + item, roe);
        }
      default:
        return SoyValueUnconverter.unconvert(item);
    }
  }
}
