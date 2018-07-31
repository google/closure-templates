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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.protobuf.Message;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import java.lang.reflect.Method;
import java.util.List;
import org.objectweb.asm.Type;

/** Adapts JavaValueFactory to working with Expressions for jbc src. */
final class JbcSrcValueFactory extends JavaValueFactory {

  // List of classes that are allowed as parameter types for each soy types.

  private static final ImmutableSet<Class<?>> UNKNOWN_TYPES = ImmutableSet.of(SoyValue.class);

  private static final ImmutableSet<Class<?>> SANITIZED_TYPES =
      ImmutableSet.of(SoyValue.class, SanitizedContent.class, String.class);

  private static final ImmutableSet<Class<?>> BOOL_TYPES =
      ImmutableSet.of(SoyValue.class, boolean.class, BooleanData.class);

  private static final ImmutableSet<Class<?>> FLOAT_TYPES =
      ImmutableSet.of(SoyValue.class, double.class, FloatData.class, NumberData.class);

  // We allow 'double' for soy int types because double has more precision than soy guarantees
  // for its int type.
  private static final ImmutableSet<Class<?>> INT_TYPES =
      ImmutableSet.of(
          SoyValue.class, long.class, IntegerData.class, NumberData.class, int.class, double.class);

  @SuppressWarnings("deprecation") // SoyLegacyObjectMap is deprecated
  private static final ImmutableSet<Class<?>> LEGACY_OBJECT_MAP_TYPES =
      ImmutableSet.of(SoyValue.class, SoyLegacyObjectMap.class, SoyDict.class);

  // TODO(sameb): Remove List?  We can't validate it's generic type.
  private static final ImmutableSet<Class<?>> LIST_TYPES =
      ImmutableSet.of(SoyValue.class, SoyList.class, List.class);

  private static final ImmutableSet<Class<?>> MAP_TYPES =
      ImmutableSet.of(SoyValue.class, SoyMap.class, SoyDict.class, SoyRecord.class);

  private static final ImmutableSet<Class<?>> RECORD_TYPES =
      ImmutableSet.of(SoyValue.class, SoyRecord.class);

  private static final ImmutableSet<Class<?>> STRING_TYPES =
      ImmutableSet.of(SoyValue.class, String.class, StringData.class);

  private static final ImmutableSet<Class<?>> NULL_TYPES =
      ImmutableSet.of(SoyValue.class, NullData.class);

  private static final ImmutableSet<Class<?>> PROTO_TYPES =
      ImmutableSet.of(SoyValue.class, Message.class, SoyProtoValue.class);

  private static final ImmutableSet<Class<?>> PROTO_ENUM_TYPES =
      ImmutableSet.of(SoyValue.class, int.class);

  /** Interface that looks up an expression for a given plugin name. */
  interface PluginInstanceLookup {
    /** Returns the runtime this function uses. */
    Expression getPluginInstance(String pluginName);
  }

  private final FunctionNode fnNode;
  private final JavaPluginContext context;
  private final PluginInstanceLookup pluginInstanceLookup;
  private final JbcSrcValueErrorReporter reporter;

  JbcSrcValueFactory(
      FunctionNode fnNode,
      final JbcSrcPluginContext jbcPluginContext,
      PluginInstanceLookup pluginInstanceLookup,
      ErrorReporter errorReporter) {
    this.fnNode = fnNode;
    this.pluginInstanceLookup = pluginInstanceLookup;
    this.reporter = new JbcSrcValueErrorReporter(errorReporter, fnNode);
    this.context =
        new JavaPluginContext() {
          @Override
          public JavaValue getULocale() {
            return JbcSrcJavaValue.of(jbcPluginContext.getULocale(), reporter);
          }

          @Override
          public JavaValue getBidiDir() {
            return JbcSrcJavaValue.of(jbcPluginContext.getBidiGlobalDir(), reporter);
          }
        };
  }

  SoyExpression computeForJavaSource(List<SoyExpression> args) {
    ErrorReporter.Checkpoint checkpoint = reporter.checkpoint();

    checkState(fnNode.getAllowedParamTypes() != null, "allowed param types must be set");
    checkState(
        fnNode.getAllowedParamTypes().size() == args.size(),
        "wrong # of allowed param types (%s), expected %s",
        fnNode.getAllowedParamTypes(),
        args.size());

    ImmutableList.Builder<JavaValue> jvBuilder = ImmutableList.builder();
    for (int i = 0; i < args.size(); i++) {
      jvBuilder.add(
          JbcSrcJavaValue.of(args.get(i), fnNode.getAllowedParamTypes().get(i), reporter));
    }
    SoyJavaSourceFunction javaSrcFn = (SoyJavaSourceFunction) fnNode.getSoyFunction();

    JavaValue result = javaSrcFn.applyForJavaSource(this, jvBuilder.build(), context);
    if (result == null) {
      reporter.nullReturn();
      result = errorValue();
    }

    Optional<SoyExpression> soyExpr = toSoyExpression((JbcSrcJavaValue) result);
    if (reporter.errorsSince(checkpoint)) {
      return SoyExpression.NULL_BOXED;
    }

    return soyExpr.get(); // We expect a value to exist in the optional if there were no errors.
  }

  @Override
  public JbcSrcJavaValue callStaticMethod(Method method, JavaValue... params) {
    if (method == null) {
      reporter.nullMethod("callStaticMethod");
      return errorValue();
    }
    // Attempt to eagerly convert the result to a SoyExpression to make life easier for ourselves.
    // (We can take various shortcuts if things are SoyExpressions.)
    // This lets us more easily support users who want to compose multiple callXMethod calls, e.g:
    //   callXMethod(METHOD1, callXMethod(METHOD2, arg1), callXMethod(METHOD3, arg2));
    // ... which would call METHOD1 with the results of METHOD2 & METHOD3.
    Optional<Expression[]> adapted = adaptParams(method, params, "callStaticMethod");
    if (!adapted.isPresent()) {
      return errorValue();
    }
    return JbcSrcJavaValue.of(
        tryToWrapInSoyExpression(MethodRef.create(method).invoke(adapted.get())), method, reporter);
  }

  @Override
  public JbcSrcJavaValue callInstanceMethod(Method method, JavaValue... params) {
    if (method == null) {
      reporter.nullMethod("callInstanceMethod");
      return errorValue();
    }

    // We need to cast to the method's declaring class in order for the owner type
    // to be correct when calling the method, otherwise the JVM won't be able to dispatch
    // the method because the type will just be 'Object'.
    Expression runtime =
        pluginInstanceLookup
            .getPluginInstance(fnNode.getFunctionName())
            .checkedCast(method.getDeclaringClass());
    // See the note in callStaticMethod for why we eagerly try to wrap the result into a SoyExpr.
    Optional<Expression[]> adapted = adaptParams(method, params, "callInstanceMethod");
    if (!adapted.isPresent()) {
      return errorValue();
    }
    return JbcSrcJavaValue.of(
        tryToWrapInSoyExpression(runtime.invoke(MethodRef.create(method), adapted.get())),
        method,
        reporter);
  }

  @Override
  public JbcSrcJavaValue listOf(List<JavaValue> args) {
    List<SoyExpression> soyExprs =
        Lists.transform(
            args,
            new Function<JavaValue, SoyExpression>() {
              @Override
              public SoyExpression apply(JavaValue value) {
                return (SoyExpression) ((JbcSrcJavaValue) value).expr();
              }
            });
    return JbcSrcJavaValue.of(SoyExpression.asBoxedList(soyExprs), reporter);
  }

  private Optional<Expression[]> adaptParams(
      Method method, JavaValue[] userParams, String callerMethodName) {
    if (userParams == null) {
      reporter.nullParamArray(method, callerMethodName);
      return Optional.absent();
    }

    Class<?>[] methodParams = method.getParameterTypes();
    if (methodParams.length != userParams.length) {
      reporter.invalidParameterLength(method, userParams);
      return Optional.absent();
    }

    Expression[] params = new Expression[userParams.length];
    for (int i = 0; i < userParams.length; i++) {
      Class<?> methodParam = methodParams[i];
      if (userParams[i] == null) {
        reporter.nullParam(method, i + 1, methodParam);
        params[i] = stubExpression(methodParam);
        continue;
      }
      JbcSrcJavaValue jbcJv = (JbcSrcJavaValue) userParams[i];
      Expression expr = jbcJv.expr();
      if (expr instanceof SoyExpression) {
        params[i] =
            adaptParameter(method, i, methodParam, (SoyExpression) expr, jbcJv.getAllowedType());
      } else {
        if (!BytecodeUtils.isDefinitelyAssignableFrom(
            Type.getType(methodParam), expr.resultType())) {
          reporter.invalidParameterType(method, i + 1, methodParam, expr);
          expr = stubExpression(methodParam);
        }
        params[i] = expr;
      }
    }
    return Optional.of(params);
  }

  private Expression adaptParameter(
      Method method,
      int paramIdx,
      Class<?> expectedParamType,
      SoyExpression actualParam,
      SoyType allowedType) {

    // First we validate that the type is allowed based on the function's signature (if any).
    if (!isValidClassForType(expectedParamType, allowedType)) {
      reporter.invalidParameterType(method, paramIdx + 1, expectedParamType, allowedType);
      return stubExpression(expectedParamType);
    }

    // Then adapt the expression to fit the parameter type.  We know the below calls are all
    // safe because we've already validated the parameter type against the allowed soy types.

    // If expecting a bland 'SoyValue', just box the expr.
    if (expectedParamType == SoyValue.class) {
      return actualParam.box();
    }
    // If we expect a specific SoyValue subclass, then box + cast.
    if (SoyValue.class.isAssignableFrom(expectedParamType)) {
      return actualParam.box().checkedCast(expectedParamType);
    }

    // Otherwise, we're an unboxed type (non-SoyValue).

    // int needs special-casing for overflow, and because we can't unboxAs(int.class)
    if (expectedParamType == int.class) {
      // We box + invoke rather than unboxAs(long.class) + numericConversion so that we get
      // overflow checking (built into integerValue()).
      return actualParam.box().invoke(MethodRef.SOY_VALUE_INTEGER_VALUE);
    }
    // double needs special casing since we allow soy int -> double conversions (since double
    // has enough precision to hold soy int data).  We can't unbox longs as double, so we coerce.
    if (expectedParamType == double.class) {
      return actualParam.coerceToDouble();
    }

    return actualParam.unboxAs(expectedParamType);
  }

  /** Returns true if the clazz is allowed as a parameter type for the given soy type. */
  private boolean isValidClassForType(Class<?> clazz, SoyType type) {
    // Exit early if the class is primitive and the type is nullable -- that's not allowed.
    // Then remove null from the type.  This allows us to accept precise params for nullable
    // types, e.g, for int|null we can allow IntegerData (which will be passed as 'null').
    if (SoyTypes.isNullable(type) && Primitives.allPrimitiveTypes().contains(clazz)) {
      return false;
    }
    type = SoyTypes.tryRemoveNull(type);
    switch (type.getKind()) {
      case ANY:
      case UNKNOWN:
        return UNKNOWN_TYPES.contains(clazz);
      case ATTRIBUTES:
      case CSS:
      case HTML:
      case URI:
      case TRUSTED_RESOURCE_URI:
      case JS:
        return SANITIZED_TYPES.contains(clazz);
      case BOOL:
        return BOOL_TYPES.contains(clazz);
      case FLOAT:
        return FLOAT_TYPES.contains(clazz);
      case INT:
        return INT_TYPES.contains(clazz);
      case LEGACY_OBJECT_MAP:
        return LEGACY_OBJECT_MAP_TYPES.contains(clazz);
      case LIST:
        return LIST_TYPES.contains(clazz);
      case MAP:
        return MAP_TYPES.contains(clazz);
      case RECORD:
        return RECORD_TYPES.contains(clazz);
      case STRING:
        return STRING_TYPES.contains(clazz);
      case NULL:
        return NULL_TYPES.contains(clazz);
      case PROTO:
        return PROTO_TYPES.contains(clazz);
      case PROTO_ENUM:
        return PROTO_ENUM_TYPES.contains(clazz);
      case UNION:
        // If this is a union, make sure the type is valid for every member.
        // If the type isn't valid for any member, then there's no guarantee this will work
        // for an arbitrary template at runtime.
        for (SoyType member : ((UnionType) type).getMembers()) {
          if (!isValidClassForType(clazz, member)) {
            return false;
          }
        }
        return true;
      case ERROR:
        throw new IllegalStateException("Cannot have error type from function signature");
    }

    throw new AssertionError("above switch is exhaustive");
  }

  /**
   * Returns a stub error value, for use in continuing in scenarios where we don't know expected
   * type.
   */
  private JbcSrcJavaValue errorValue() {
    return JbcSrcJavaValue.error(stubExpression(boolean.class), reporter);
  }

  /**
   * Returns an expression stub (that points to a non-existing method) whose return type is the
   * given class. Useful for scenarios where we're reporting an error and just need an Expression of
   * the appropriate type to be able to continue.
   */
  static Expression stubExpression(Class<?> clazz) {
    return MethodRef.createStaticMethod(
            TypeInfo.create(
                "if.you.see.this.please.report.a.bug.because.an.error.message.was.swallowed"),
            new org.objectweb.asm.commons.Method("oops", Type.getType(clazz), new Type[0]))
        .invoke();
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

  private Optional<SoyExpression> toSoyExpression(JbcSrcJavaValue pluginReturnValue) {
    // Don't bother doing anything if this is an error value, we already recorded errors.
    if (pluginReturnValue.isError()) {
      return Optional.absent();
    }

    SoyType expectedType = fnNode.getType();
    Expression expr = pluginReturnValue.expr();
    SoyExpression soyExpr = null;

    // Note: All expressions that were able to be converted in tryToWrapInSoyExpression
    // will be a SoyExpression.  Everything else will just be an Expression.
    if (expr instanceof SoyExpression) {
      soyExpr = (SoyExpression) expr;
    } else {
      // All expressions are guaranteed to be on the classpath because they're
      // from an Expression wrapped around a Method.
      Class<?> type = BytecodeUtils.classFromAsmType(expr.resultType());
      if (List.class.isAssignableFrom(type)) {
        if (expectedType instanceof ListType) {
          soyExpr = SoyExpression.forList((ListType) expectedType, expr);
        } else if (expectedType.getKind() == SoyType.Kind.UNKNOWN
            || expectedType.getKind() == SoyType.Kind.ANY) {
          soyExpr = SoyExpression.forList(ListType.of(UnknownType.getInstance()), expr);
        } else {
          reporter.invalidReturnType(type, pluginReturnValue.methodInfo());
          return Optional.absent();
        }
      } else if (SoyValue.class.isAssignableFrom(type)) {
        soyExpr =
            SoyExpression.forSoyValue(
                expectedType,
                expr.checkedCast(SoyRuntimeType.getBoxedType(expectedType).runtimeType()));
      } else {
        // TODO(sameb): Support more types, like map, proto, etc..
        reporter.invalidReturnType(type, pluginReturnValue.methodInfo());
        return Optional.absent();
      }
    }
    if (!expectedType.isAssignableFrom(soyExpr.soyType())) {
      reporter.incompatibleReturnType(soyExpr.soyType(), pluginReturnValue.methodInfo());
      return Optional.absent();
    }
    return Optional.of(soyExpr);
  }
}
