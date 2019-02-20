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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.base.internal.BaseUtils;
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
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
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

  private static final ImmutableSet<Class<?>> NUMBER_TYPES =
      ImmutableSet.of(SoyValue.class, double.class, NumberData.class);

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
  private final SoyTypeRegistry registry;

  JbcSrcValueFactory(
      FunctionNode fnNode,
      final JbcSrcPluginContext jbcPluginContext,
      PluginInstanceLookup pluginInstanceLookup,
      ErrorReporter errorReporter,
      SoyTypeRegistry registry) {
    this.fnNode = fnNode;
    this.pluginInstanceLookup = pluginInstanceLookup;
    this.registry = registry;
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
    JavaValue result;
    try {
      result = javaSrcFn.applyForJavaSource(this, jvBuilder.build(), context);
      if (result == null) {
        reporter.nullReturn();
        result = errorValue();
      }
    } catch (Throwable t) {
      BaseUtils.trimStackTraceTo(t, getClass());
      reporter.unexpectedError(t);
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
        Lists.transform(args, value -> (SoyExpression) ((JbcSrcJavaValue) value).expr());
    return JbcSrcJavaValue.of(SoyExpression.asBoxedList(soyExprs), reporter);
  }

  @Override
  public JbcSrcJavaValue constant(double value) {
    return JbcSrcJavaValue.of(SoyExpression.forFloat(BytecodeUtils.constant(value)), reporter);
  }

  @Override
  public JbcSrcJavaValue constant(long value) {
    return JbcSrcJavaValue.of(SoyExpression.forInt(BytecodeUtils.constant(value)), reporter);
  }

  @Override
  public JbcSrcJavaValue constant(String value) {
    return JbcSrcJavaValue.of(SoyExpression.forString(BytecodeUtils.constant(value)), reporter);
  }

  @Override
  public JbcSrcJavaValue constant(boolean value) {
    return JbcSrcJavaValue.of(value ? SoyExpression.TRUE : SoyExpression.FALSE, reporter);
  }

  @Override
  public JbcSrcJavaValue constantNull() {
    return JbcSrcJavaValue.ofConstantNull(reporter);
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
        params[i] = adaptParameter(method, i, methodParam, jbcJv);
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
      Method method, int paramIdx, Class<?> expectedParamType, JbcSrcJavaValue value) {
    // First we validate that the type is allowed based on the function's signature (if any).
    boolean validType;
    SoyType allowedType;
    if (value.isConstantNull()) {
      // If the value is for our "constant null", then we special-case things to allow
      // any valid type (expect primitives).
      // TODO(sameb): Limit the allowed types to ones that valid for real soy types, e.g
      // the union of all the values the *_TYPES constants + protos + proto enums - primitives.
      validType = !Primitives.allPrimitiveTypes().contains(expectedParamType);
      allowedType = NullType.getInstance();
    } else {
      validType = isValidClassForType(expectedParamType, value.getAllowedType());
      allowedType = value.getAllowedType();
    }
    if (!validType) {
      reporter.invalidParameterType(method, paramIdx + 1, expectedParamType, allowedType);
      return stubExpression(expectedParamType);
    }

    // Then adapt the expression to fit the parameter type.  We know the below calls are all
    // safe because we've already validated the parameter type against the allowed soy types.
    SoyExpression actualParam = (SoyExpression) value.expr();

    // For "constant null", we can just cast w/o doing any other work.
    // We already validated that it isn't primitive types.
    if (value.isConstantNull()) {
      return actualParam.checkedCast(expectedParamType);
    }

    // If expecting a bland 'SoyValue', just box the expr.
    if (expectedParamType == SoyValue.class) {
      return actualParam.box();
    }
    // If we expect a specific SoyValue subclass, then box + cast.
    if (SoyValue.class.isAssignableFrom(expectedParamType)) {
      return actualParam.box().checkedCast(expectedParamType);
    }

    // Otherwise, we're an unboxed type (non-SoyValue).

    // int needs special-casing for overflow, and because we can't unbox as int
    if (expectedParamType == int.class) {
      // We box + invoke rather than unboxAsLong() + numericConversion so that we get overflow
      // checking (built into integerValue()).
      return actualParam.box().invoke(MethodRef.SOY_VALUE_INTEGER_VALUE);
    }
    // double needs special casing since we allow soy int -> double conversions (since double
    // has enough precision to hold soy int data).  We can't unbox longs as double, so we coerce.
    if (expectedParamType == double.class) {
      return actualParam.coerceToDouble();
    }
    // For protos, we need to unbox as Message & then cast.
    if (Message.class.isAssignableFrom(expectedParamType)) {
      if (expectedParamType.equals(Message.class)) {
        return actualParam.unboxAsMessage();
      }
      return actualParam.unboxAsMessage().checkedCast(expectedParamType);
    }
    // For protocol enums, we need to call forNumber on the type w/ the param (as casted to an int).
    // This is because Soy internally stores enums as ints. We know this is safe because we
    // already validated that the enum type matches the signature.
    if (expectedParamType.isEnum()
        && ProtocolMessageEnum.class.isAssignableFrom(expectedParamType)) {
      return MethodRef.create(expectedParamType, "forNumber", int.class)
          .invoke(BytecodeUtils.numericConversion(actualParam.unboxAsLong(), Type.INT_TYPE));
    }

    if (expectedParamType.equals(boolean.class)) {
      return actualParam.unboxAsBoolean();
    } else if (expectedParamType.equals(long.class)) {
      return actualParam.unboxAsLong();
    } else if (expectedParamType.equals(String.class)) {
      return actualParam.unboxAsString();
    } else if (expectedParamType.equals(List.class)) {
      return actualParam.unboxAsList();
    }

    throw new AssertionError("Unable to convert parameter to " + expectedParamType);
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
        return PROTO_TYPES.contains(clazz)
            || matchesProtoDescriptor(Message.class, clazz, ((SoyProtoType) type).getDescriptor());
      case PROTO_ENUM:
        return PROTO_ENUM_TYPES.contains(clazz)
            || (clazz.isEnum()
                && matchesProtoDescriptor(
                    ProtocolMessageEnum.class, clazz, ((SoyProtoEnumType) type).getDescriptor()));
      case UNION:
        // number is a special case, it should work for double and NumberData
        if (type.equals(SoyTypes.NUMBER_TYPE)) {
          return NUMBER_TYPES.contains(clazz);
        }
        // If this is a union, make sure the type is valid for every member.
        // If the type isn't valid for any member, then there's no guarantee this will work
        // for an arbitrary template at runtime.
        for (SoyType member : ((UnionType) type).getMembers()) {
          if (!isValidClassForType(clazz, member)) {
            return false;
          }
        }
        return true;
      case VE:
      case VE_DATA:
        reporter.veParam();
        // Return true here because we've already reported an error and returning false would report
        // an additional confusing type error.
        return true;
      case ERROR:
        throw new IllegalStateException("Cannot have error type from function signature");
    }

    throw new AssertionError("above switch is exhaustive");
  }

  private boolean matchesProtoDescriptor(
      Class<?> expectedSupertype, Class<?> actualParamClass, GenericDescriptor expectedDescriptor) {
    if (!expectedSupertype.isAssignableFrom(actualParamClass)) {
      return false;
    }
    return nameFromDescriptor(actualParamClass).or("").equals(expectedDescriptor.getFullName());
  }

  private static Optional<String> nameFromDescriptor(Class<?> protoType) {
    GenericDescriptor actualDescriptor;
    try {
      actualDescriptor =
          (GenericDescriptor) protoType.getDeclaredMethod("getDescriptor").invoke(null);
    } catch (ReflectiveOperationException roe) {
      return Optional.absent();
    }
    return Optional.of(actualDescriptor.getFullName());
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
      Class<?> type;
      // Preferentially try to get the type from the method of Expr, since classFromAsmType
      // uses BytecodeUtils' classloader, which may not include the return value's type.
      Method method = pluginReturnValue.methodInfo();
      if (method != null) {
        type = method.getReturnType();
      } else {
        type = BytecodeUtils.classFromAsmType(expr.resultType());
      }
      if (List.class.isAssignableFrom(type)) {
        if (expectedType instanceof ListType) {
          soyExpr = SoyExpression.forList((ListType) expectedType, expr);
        } else if (expectedType.getKind() == SoyType.Kind.UNKNOWN
            || expectedType.getKind() == SoyType.Kind.ANY) {
          soyExpr = SoyExpression.forList(ListType.of(UnknownType.getInstance()), expr);
        } else {
          reporter.invalidReturnType(type, method);
          return Optional.absent();
        }
      } else if (SoyValue.class.isAssignableFrom(type)) {
        // TODO(sameb): This could validate that the boxed soy type is valid for the return type
        // at compile time too.
        soyExpr =
            SoyExpression.forSoyValue(
                expectedType,
                expr.checkedCast(SoyRuntimeType.getBoxedType(expectedType).runtimeType()));
      } else if (Message.class.isAssignableFrom(type)) {
        Optional<SoyType> returnType = soyTypeForProtoOrEnum(type, method);
        if (!returnType.isPresent()) {
          return Optional.absent(); // error already reported
        }
        soyExpr =
            SoyExpression.forProto(SoyRuntimeType.getUnboxedType(returnType.get()).get(), expr);
      } else if (type.isEnum() && ProtocolMessageEnum.class.isAssignableFrom(type)) {
        Optional<SoyType> returnType = soyTypeForProtoOrEnum(type, method);
        if (!returnType.isPresent()) {
          return Optional.absent(); // error already reported
        }
        // Eagerly check compatibility, so we can avoid boxing the int in a SoyValue.
        if (!expectedType.isAssignableFrom(returnType.get())) {
          reporter.incompatibleReturnType(returnType.get(), method);
          return Optional.absent();
        }
        // We need to get the # out of the enum & cast to a long.
        // Note that this causes the return expr to lose its enum info.
        // TODO(lukes): SoyExpression should have a way to track type information with an unboxed
        // int that is actually a proto enum.  Like we do with SanitizedContents
        soyExpr =
            SoyExpression.forInt(
                BytecodeUtils.numericConversion(
                    MethodRef.PROTOCOL_ENUM_GET_NUMBER.invoke(expr), Type.LONG_TYPE));
      } else {
        reporter.invalidReturnType(type, method);
        return Optional.absent();
      }
    }

    // We special-case proto enums when the return expression is an INT, to allow someone to return
    // an 'int' representing the enum.
    boolean isPossibleProtoEnum =
        soyExpr.soyType().getKind() == SoyType.Kind.INT
            && isOrContains(expectedType, SoyType.Kind.PROTO_ENUM);
    if (!isPossibleProtoEnum && !expectedType.isAssignableFrom(soyExpr.soyType())) {
      reporter.incompatibleReturnType(soyExpr.soyType(), pluginReturnValue.methodInfo());
      return Optional.absent();
    }
    return Optional.of(soyExpr);
  }

  /**
   * Attempts to discover the SoyType for a proto or proto enum, reporting an error if unable to.
   */
  private Optional<SoyType> soyTypeForProtoOrEnum(Class<?> type, Method method) {
    // Message isn't supported because we can't get a descriptor from it.
    if (type == Message.class) {
      reporter.invalidReturnType(Message.class, method);
      return Optional.absent();
    }
    Optional<String> fullName = nameFromDescriptor(type);
    if (!fullName.isPresent()) {
      reporter.incompatibleReturnType(type, method);
      return Optional.absent();
    }
    SoyType returnType = registry.getType(fullName.get());
    if (returnType == null) {
      reporter.incompatibleReturnType(type, method);
      return Optional.absent();
    }
    return Optional.of(returnType);
  }

  /** Returns true if the type is the given kind or contains the given kind. */
  private boolean isOrContains(SoyType type, SoyType.Kind kind) {
    if (type.getKind() == kind) {
      return true;
    }
    if (type.getKind() == SoyType.Kind.UNION) {
      for (SoyType member : ((UnionType) type).getMembers()) {
        if (member.getKind() == kind) {
          return true;
        }
      }
    }
    return false;
  }
}
