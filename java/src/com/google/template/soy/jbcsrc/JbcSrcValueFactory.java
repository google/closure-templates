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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.newLabel;
import static com.google.template.soy.jbcsrc.runtime.JbcSrcPluginRuntime.BOX_JAVA_MAP_AS_SOY_LEGACY_OBJECT_MAP;
import static com.google.template.soy.jbcsrc.runtime.JbcSrcPluginRuntime.BOX_JAVA_MAP_AS_SOY_MAP;
import static com.google.template.soy.jbcsrc.runtime.JbcSrcPluginRuntime.BOX_JAVA_MAP_AS_SOY_RECORD;
import static com.google.template.soy.jbcsrc.runtime.JbcSrcPluginRuntime.CONVERT_FUTURE_TO_SOY_VALUE_PROVIDER;
import static com.google.template.soy.jbcsrc.runtime.JbcSrcPluginRuntime.JAVA_NULL_TO_SOY_NULL;
import static com.google.template.soy.jbcsrc.runtime.JbcSrcPluginRuntime.NULLISH_TO_JAVA_NULL;
import static com.google.template.soy.jbcsrc.runtime.JbcSrcPluginRuntime.NULL_TO_JAVA_NULL;
import static com.google.template.soy.types.SoyTypes.isUnknownOrAny;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.PartialSoyTemplate;
import com.google.template.soy.data.SoyTemplate;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef.MethodPureness;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.plugin.internal.JavaPluginExecContext;
import com.google.template.soy.plugin.java.internal.JavaPluginValidator;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SetType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.UnknownType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/** Adapts JavaValueFactory to working with Expressions for jbc src. */
final class JbcSrcValueFactory extends JavaValueFactory {

  /** Interface that looks up an expression for a given plugin name. */
  interface PluginInstanceLookup {
    /** Returns the runtime this function uses. */
    Expression getPluginInstance(String pluginName);
  }

  private final JavaPluginExecContext fnNode;
  private final JavaPluginContext context;
  private final PluginInstanceLookup pluginInstanceLookup;
  private final JavaPluginValidator pluginValidator;
  private final SoyTypeRegistry registry;
  private final ErrorReporter errorReporter;
  private final ExpressionDetacher detacher;

  JbcSrcValueFactory(
      JavaPluginExecContext fnNode,
      JbcSrcPluginContext jbcPluginContext,
      PluginInstanceLookup pluginInstanceLookup,
      ErrorReporter errorReporter,
      SoyTypeRegistry registry,
      ExpressionDetacher detacher) {
    this.fnNode = fnNode;
    this.pluginInstanceLookup = pluginInstanceLookup;
    this.registry = registry;
    this.errorReporter = errorReporter;
    this.pluginValidator = new JavaPluginValidator(errorReporter, registry);
    this.context =
        new JavaPluginContext() {
          @Override
          public JavaValue getULocale() {
            return JbcSrcJavaValue.of(jbcPluginContext.getULocale());
          }

          @Override
          public JavaValue getBidiDir() {
            return JbcSrcJavaValue.of(jbcPluginContext.getBidiGlobalDir());
          }

          @Override
          public JavaValue getAllRequiredCssNamespaces(JavaValue template) {
            JbcSrcJavaValue exprTemplate = (JbcSrcJavaValue) template;
            SoyExpression soyExpression = (SoyExpression) exprTemplate.expr();
            return JbcSrcJavaValue.of(jbcPluginContext.getAllRequiredCssNamespaces(soyExpression));
          }

          @Override
          public JavaValue getAllRequiredCssPaths(JavaValue template) {
            JbcSrcJavaValue exprTemplate = (JbcSrcJavaValue) template;
            SoyExpression soyExpression = (SoyExpression) exprTemplate.expr();
            return JbcSrcJavaValue.of(jbcPluginContext.getAllRequiredCssPaths(soyExpression));
          }
        };
    this.detacher = detacher;
  }

  SoyExpression computeForJavaSource(List<SoyExpression> args) {
    ErrorReporter.Checkpoint checkpoint = errorReporter.checkpoint();
    checkState(fnNode.getParamTypes() != null, "allowed param types must be set");
    checkState(
        fnNode.getParamTypes().size() == args.size(),
        "wrong # of allowed param types (%s), expected %s",
        fnNode.getParamTypes(),
        args.size());
    pluginValidator.validate(
        fnNode.getFunctionName(),
        fnNode.getSourceFunction(),
        fnNode.getParamTypes(),
        fnNode.getReturnType(),
        fnNode.getSourceLocation(),
        /* includeTriggeredInTemplateMsg= */ true);
    if (errorReporter.errorsSince(checkpoint)) {
      return SoyExpression.SOY_NULL;
    }
    SoyJavaSourceFunction javaSrcFn = fnNode.getSourceFunction();
    return toSoyExpression(
        (JbcSrcJavaValue)
            javaSrcFn.applyForJavaSource(
                this, args.stream().map(JbcSrcJavaValue::of).collect(toImmutableList()), context));
  }

  @Override
  public JbcSrcJavaValue callStaticMethod(Method method, JavaValue... params) {
    return callPluginMethod(
        PluginCallType.STATIC,
        MethodRef.create(method, MethodPureness.NON_PURE),
        toMethodSignature(method),
        null,
        params);
  }

  @Override
  public JbcSrcJavaValue callStaticMethod(MethodSignature methodSignature, JavaValue... params) {
    return callPluginMethod(
        PluginCallType.STATIC,
        getMethodRef(/* isInstance= */ false, methodSignature),
        methodSignature,
        null,
        params);
  }

  @Override
  public JavaValue callJavaValueMethod(Method method, JavaValue instance, JavaValue... params) {
    MethodSignature methodSignature = toMethodSignature(method);
    return callPluginMethod(
        PluginCallType.JAVA_VALUE_INSTANCE,
        getMethodRef(/* isInstance= */ true, methodSignature),
        methodSignature,
        instance,
        params);
  }

  @Override
  public JbcSrcJavaValue callInstanceMethod(Method method, JavaValue... params) {
    return callPluginMethod(
        PluginCallType.PLUGIN_INSTANCE,
        MethodRef.create(method, MethodPureness.NON_PURE),
        toMethodSignature(method),
        null,
        params);
  }

  @Override
  public JbcSrcJavaValue callInstanceMethod(MethodSignature methodSignature, JavaValue... params) {
    return callPluginMethod(
        PluginCallType.PLUGIN_INSTANCE,
        getMethodRef(/* isInstance= */ true, methodSignature),
        methodSignature,
        null,
        params);
  }

  private MethodRef getMethodRef(boolean isInstance, MethodSignature methodSignature) {
    TypeInfo owner =
        TypeInfo.create(methodSignature.fullyQualifiedClassName(), methodSignature.inInterface());
    org.objectweb.asm.commons.Method asmMethod =
        new org.objectweb.asm.commons.Method(
            methodSignature.methodName(),
            Type.getType(methodSignature.returnType()),
            methodSignature.arguments().stream().map(Type::getType).toArray(Type[]::new));
    if (isInstance) {
      return methodSignature.inInterface()
          ? MethodRef.createInterfaceMethod(owner, asmMethod, MethodPureness.NON_PURE)
          : MethodRef.createInstanceMethod(owner, asmMethod, MethodPureness.NON_PURE);
    }
    return MethodRef.createStaticMethod(owner, asmMethod, MethodPureness.NON_PURE);
  }

  enum PluginCallType {
    STATIC,
    PLUGIN_INSTANCE,
    JAVA_VALUE_INSTANCE
  }

  /**
   * Adapts the parameters to Expressions and generates the correct invocation for calling the
   * plugin.
   */
  private JbcSrcJavaValue callPluginMethod(
      PluginCallType type,
      MethodRef methodRef,
      MethodSignature methodSignature,
      @Nullable JavaValue instance,
      JavaValue... params) {
    Preconditions.checkArgument((instance != null) == (type == PluginCallType.JAVA_VALUE_INSTANCE));
    // Attempt to eagerly convert the result to a SoyExpression to make life easier for ourselves.
    // (We can take various shortcuts if things are SoyExpressions.)
    // This lets us more easily support users who want to compose multiple callXMethod calls, e.g.:
    //   callXMethod(METHOD1, callXMethod(METHOD2, arg1), callXMethod(METHOD3, arg2));
    // ... which would call METHOD1 with the results of METHOD2 & METHOD3.
    Expression[] adapted = adaptParams(methodSignature, params);
    TypeInfo owner = methodRef.owner();
    Expression methodCall;
    if (type == PluginCallType.PLUGIN_INSTANCE) {
      // We need to cast to the method's declaring class in order for the owner type
      // to be correct when calling the method, otherwise the JVM won't be able to dispatch
      // the method because the type will just be 'Object'.
      Expression runtime =
          pluginInstanceLookup
              .getPluginInstance(fnNode.getFunctionName())
              .checkedCast(owner.type());
      methodCall = runtime.invoke(methodRef, adapted);
    } else if (type == PluginCallType.JAVA_VALUE_INSTANCE) {
      Expression receiver = ((JbcSrcJavaValue) instance).expr();
      if (receiver instanceof SoyExpression) {
        receiver = ((SoyExpression) receiver).box();
      }
      receiver = receiver.checkedCast(owner.type());
      methodCall = receiver.invoke(methodRef, adapted);
    } else {
      methodCall = methodRef.invoke(adapted);
    }
    return JbcSrcJavaValue.of(tryToWrapInSoyExpression(methodCall), methodSignature);
  }

  private static MethodSignature toMethodSignature(Method method) {
    if (method.getDeclaringClass().isInterface()) {
      return MethodSignature.createInterfaceMethod(
          method.getDeclaringClass().getName(),
          method.getName(),
          method.getReturnType(),
          method.getParameterTypes());
    }
    return MethodSignature.create(
        method.getDeclaringClass().getName(),
        method.getName(),
        method.getReturnType(),
        method.getParameterTypes());
  }

  @Override
  public JbcSrcJavaValue listOf(List<JavaValue> args) {
    List<SoyExpression> soyExprs =
        Lists.transform(args, value -> (SoyExpression) ((JbcSrcJavaValue) value).expr());
    return JbcSrcJavaValue.of(SoyExpression.boxListWithSoyNullAsJavaNull(soyExprs));
  }

  @Override
  public JbcSrcJavaValue constant(double value) {
    return JbcSrcJavaValue.of(SoyExpression.forFloat(BytecodeUtils.constant(value)));
  }

  @Override
  public JbcSrcJavaValue constant(long value) {
    return JbcSrcJavaValue.of(SoyExpression.forInt(BytecodeUtils.constant(value)));
  }

  @Override
  public JbcSrcJavaValue constant(String value) {
    return JbcSrcJavaValue.of(SoyExpression.forString(BytecodeUtils.constant(value)));
  }

  @Override
  public JbcSrcJavaValue constant(boolean value) {
    return JbcSrcJavaValue.of(value ? SoyExpression.TRUE : SoyExpression.FALSE);
  }

  @Override
  public JbcSrcJavaValue constantNull() {
    return JbcSrcJavaValue.of(SoyExpression.SOY_NULL);
  }

  private static Expression maybeSoyNullToJavaNull(Expression expr) {
    if (expr.isNonSoyNullish()) {
      return expr;
    }
    return NULL_TO_JAVA_NULL.invoke(expr);
  }

  private static Expression maybeSoyNullishToJavaNull(Expression expr) {
    if (expr.isNonSoyNullish()) {
      return expr;
    }
    return NULLISH_TO_JAVA_NULL.invoke(expr);
  }

  private static Expression[] adaptParams(MethodSignature method, JavaValue[] userParams) {
    ImmutableList<Class<?>> methodParams = method.arguments();
    Expression[] params = new Expression[userParams.length];
    for (int i = 0; i < userParams.length; i++) {
      Class<?> methodParam = methodParams.get(i);
      JbcSrcJavaValue jbcJv = (JbcSrcJavaValue) userParams[i];
      Expression expr = jbcJv.expr();
      if (expr instanceof SoyExpression) {
        params[i] = adaptParameter(methodParam, jbcJv);
      } else if (BytecodeUtils.isDefinitelyAssignableFrom(
          BytecodeUtils.SOY_VALUE_TYPE, expr.resultType())) {
        params[i] = maybeSoyNullToJavaNull(expr);
      } else {
        params[i] = expr;
      }
    }
    return params;
  }

  private static Expression adaptParameter(Class<?> expectedParamType, JbcSrcJavaValue value) {
    // Then adapt the expression to fit the parameter type.  We know the below calls are all
    // safe because we've already validated the parameter type against the allowed soy types.
    SoyExpression actualParam = (SoyExpression) value.expr();

    // For explicit null types, we can just cast w/o doing any other work.
    // We already validated that it isn't primitive types.
    if (actualParam.soyRuntimeType().soyType().isEffectivelyEqual(NullType.getInstance())) {
      return BytecodeUtils.constantNull(Type.getType(expectedParamType))
          .withSourceLocation(actualParam.location());
    }

    // If expecting a bland 'SoyValue', just box the expr.
    if (expectedParamType == SoyValue.class) {
      // NullData -> null, UndefinedData -> UndefinedData
      return maybeSoyNullToJavaNull(actualParam.box());
    }
    if (expectedParamType == PrimitiveData.class) {
      // NullData -> null, UndefinedData -> UndefinedData
      return maybeSoyNullToJavaNull(actualParam.box()).checkedCast(expectedParamType);
    }
    // If we expect a specific SoyValue subclass, then box + cast.
    if (SoyValue.class.isAssignableFrom(expectedParamType)) {
      // NullData -> null, UndefinedData -> null
      return maybeSoyNullishToJavaNull(actualParam.box()).checkedCast(expectedParamType);
    }

    // Otherwise, we're an unboxed type (non-SoyValue).

    // int needs special-casing for overflow, and because we can't unbox as int
    if (expectedParamType == int.class) {
      // We box + invoke rather than unboxAsLong() + numericConversion so that we get overflow
      // checking (built into integerValue()).
      return actualParam.unboxAsInt();
    }
    // double needs special casing since we allow soy int -> double conversions (since double
    // has enough precision to hold soy int data).  We can't unbox longs as double, so we coerce.
    if (expectedParamType == double.class) {
      return actualParam.coerceToDouble();
    }
    // For protos, we need to unbox as Message & then cast.
    if (Message.class.isAssignableFrom(expectedParamType)) {
      if (expectedParamType.equals(Message.class)) {
        return actualParam.unboxAsMessageOrJavaNull(BytecodeUtils.MESSAGE_TYPE);
      }
      return actualParam.unboxAsMessageOrJavaNull(Type.getType(expectedParamType));
    }
    // For protocol enums, we need to call forNumber on the type w/ the param (as casted to an int).
    // This is because Soy internally stores enums as ints. We know this is safe because we
    // already validated that the enum type matches the signature.
    if (expectedParamType.isEnum()
        && ProtocolMessageEnum.class.isAssignableFrom(expectedParamType)) {

      // Don't check for null for a primitive, since it can't be null.
      if (actualParam.isNonSoyNullish()) {
        return convertToEnum(expectedParamType, actualParam);
      }

      MethodRef forNumber = getForNumberMethod(expectedParamType);
      return new Expression(forNumber.returnType()) {
        @Override
        protected void doGen(CodeBuilder mv) {
          Label end = newLabel();
          actualParam.gen(mv);
          BytecodeUtils.coalesceSoyNullishToJavaNull(mv, actualParam.resultType(), end);
          MethodRefs.SOY_VALUE_LONG_VALUE.invokeUnchecked(mv);
          mv.cast(Type.LONG_TYPE, Type.INT_TYPE);
          forNumber.invokeUnchecked(mv);
          mv.mark(end);
        }
      };
    }

    if (expectedParamType.equals(boolean.class)) {
      return actualParam.unboxAsBoolean();
    } else if (expectedParamType.equals(long.class)) {
      return actualParam.unboxAsLong();
    } else if (expectedParamType.equals(String.class)) {
      return actualParam.unboxAsStringOrJavaNull();
    } else if (expectedParamType.equals(List.class)) {
      return actualParam.unboxAsListOrJavaNull();
    }

    throw new AssertionError("Unable to convert parameter to " + expectedParamType);
  }

  private static Expression convertToEnum(Class<?> enumType, SoyExpression e) {
    return getForNumberMethod(enumType)
        .invoke(BytecodeUtils.numericConversion(e.unboxAsLong(), Type.INT_TYPE));
  }

  private static MethodRef getForNumberMethod(Class<?> enumType) {
    return MethodRef.createPure(enumType, "forNumber", int.class);
  }

  private static String nameFromDescriptor(Class<?> protoType) {
    try {
      return ((GenericDescriptor) protoType.getDeclaredMethod("getDescriptor").invoke(null))
          .getFullName();
    } catch (ReflectiveOperationException roe) {
      throw new IllegalStateException("Invalid protoType: " + protoType, roe);
    }
  }

  /**
   * Tries to wrap the Expression in a {@link SoyExpression}. We can only do this for types we
   * statically know are Soy Expressions and don't require additional runtime type args.
   */
  private Expression tryToWrapInSoyExpression(Expression expr) {
    switch (expr.resultType().getSort()) {
      case Type.BOOLEAN:
        return SoyExpression.forBool(expr);
      case Type.INT:
        return SoyExpression.forInt(BytecodeUtils.numericConversion(expr, Type.LONG_TYPE));
      case Type.LONG:
        return SoyExpression.forInt(expr);
      case Type.DOUBLE:
        return SoyExpression.forFloat(expr);
      case Type.OBJECT:
        if (expr.resultType().equals(BytecodeUtils.STRING_TYPE)) {
          return SoyExpression.forString(expr);
        }
        break;
      default:
        break;
    }
    // TODO(sameb): Maybe wrap List/SoyValue types too, as 'unknown' parameter types?
    return expr;
  }

  private SoyExpression toSoyExpression(JbcSrcJavaValue pluginReturnValue) {
    SoyType expectedType = fnNode.getReturnType().getEffectiveType();
    Expression expr = pluginReturnValue.expr();
    SoyExpression soyExpr;

    // Note: All expressions that were able to be converted in tryToWrapInSoyExpression
    // will be a SoyExpression.  Everything else will just be an Expression.
    if (expr instanceof SoyExpression) {
      soyExpr = (SoyExpression) expr;
    } else {
      Class<?> type;
      // Preferentially try to get the type from the method of Expr, since classFromAsmType
      // uses BytecodeUtils' classloader, which may not include the return value's type.
      MethodSignature method = pluginReturnValue.methodInfo();
      if (method != null) {
        type = method.returnType();
      } else {
        type = BytecodeUtils.classFromAsmType(expr.resultType());
      }
      if (List.class.isAssignableFrom(type)) {
        if (expectedType instanceof ListType) {
          soyExpr = SoyExpression.forList((ListType) expectedType, expr);
        } else if (isUnknownOrAny(expectedType)) {
          soyExpr = SoyExpression.forList(ListType.of(UnknownType.getInstance()), expr);
        } else {
          throw new IllegalStateException("Invalid type: " + expectedType);
        }
      } else if (Set.class.isAssignableFrom(type)) {
        if (expectedType instanceof SetType) {
          soyExpr = SoyExpression.forSet((SetType) expectedType, expr);
        } else if (isUnknownOrAny(expectedType)) {
          soyExpr = SoyExpression.forSet(SetType.of(UnknownType.getInstance()), expr);
        } else {
          throw new IllegalStateException("Invalid type: " + expectedType);
        }
      } else if (Map.class.isAssignableFrom(type)) {
        // We could implement more precise coercions depending on the static types, for now we
        // dispatch to generic conversion functions.
        // TODO(lukes): it would be especially nice to reuse the logic in GenInvocationBuilders, but
        // that seems fairly hard.
        if (expectedType instanceof MapType) {
          soyExpr = SoyExpression.forSoyValue(expectedType, BOX_JAVA_MAP_AS_SOY_MAP.invoke(expr));
        } else if (expectedType instanceof LegacyObjectMapType) {
          soyExpr =
              SoyExpression.forSoyValue(
                  expectedType, BOX_JAVA_MAP_AS_SOY_LEGACY_OBJECT_MAP.invoke(expr));
        } else if (expectedType instanceof RecordType) {
          soyExpr =
              SoyExpression.forSoyValue(expectedType, BOX_JAVA_MAP_AS_SOY_RECORD.invoke(expr));
        } else {
          throw new IllegalStateException("java map cannot be converted to: " + expectedType);
        }
      } else if (SoyValue.class.isAssignableFrom(type)) {
        soyExpr = SoyExpression.forSoyValue(expectedType, nullGuard(expr));
      } else if (Future.class.isAssignableFrom(type)) {
        soyExpr =
            SoyExpression.forSoyValue(
                expectedType,
                detacher.resolveSoyValueProvider(
                    expr.invoke(CONVERT_FUTURE_TO_SOY_VALUE_PROVIDER)));
      } else if (Message.class.isAssignableFrom(type)) {
        soyExpr =
            SoyExpression.forProto(
                SoyRuntimeType.getUnboxedType(soyTypeForProtoOrEnum(type)).get(), expr);
      } else if (type.isEnum() && ProtocolMessageEnum.class.isAssignableFrom(type)) {
        // We need to get the # out of the enum & cast to a long.
        // Note that this causes the return expr to lose its enum info.
        // TODO(lukes): SoyExpression should have a way to track type information with an unboxed
        // int that is actually a proto enum.  Like we do with SanitizedContents
        soyExpr =
            SoyExpression.forInt(
                BytecodeUtils.numericConversion(
                    MethodRefs.PROTOCOL_ENUM_GET_NUMBER.invoke(expr), Type.LONG_TYPE));
      } else if (PartialSoyTemplate.class.isAssignableFrom(type)
          || SoyTemplate.class.isAssignableFrom(type)) {

        soyExpr =
            SoyExpression.forSoyValue(
                expectedType, MethodRefs.CREATE_TEMPLATE_VALUE_FROM_TEMPLATE.invoke(expr));
      } else {
        throw new IllegalStateException("invalid type: " + type);
      }
    }
    return soyExpr;
  }

  /** Returns the SoyType for a proto or proto enum. */
  private SoyType soyTypeForProtoOrEnum(Class<?> type) {
    return registry.getProtoRegistry().getProtoType(nameFromDescriptor(type));
  }

  /**
   * Plugins may return null rather than NullData from methods of types assignable from SoyValue.
   */
  private Expression nullGuard(Expression delegate) {
    Preconditions.checkArgument(
        BytecodeUtils.isDefinitelyAssignableFrom(
            BytecodeUtils.SOY_VALUE_TYPE, delegate.resultType()));
    if (delegate.isNonJavaNullable()) {
      return delegate;
    }
    return JAVA_NULL_TO_SOY_NULL.invoke(delegate);
  }
}
