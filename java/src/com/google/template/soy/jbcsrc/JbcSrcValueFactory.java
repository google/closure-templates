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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.plugin.java.internal.JavaPluginValidator;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.UnknownType;
import java.lang.reflect.Method;
import java.util.List;
import org.objectweb.asm.Type;

/** Adapts JavaValueFactory to working with Expressions for jbc src. */
final class JbcSrcValueFactory extends JavaValueFactory {

  /** Interface that looks up an expression for a given plugin name. */
  interface PluginInstanceLookup {
    /** Returns the runtime this function uses. */
    Expression getPluginInstance(String pluginName);
  }

  private final FunctionNode fnNode;
  private final JavaPluginContext context;
  private final PluginInstanceLookup pluginInstanceLookup;
  private final JavaPluginValidator pluginValidator;
  private final SoyTypeRegistry registry;
  private final ErrorReporter errorReporter;

  JbcSrcValueFactory(
      FunctionNode fnNode,
      final JbcSrcPluginContext jbcPluginContext,
      PluginInstanceLookup pluginInstanceLookup,
      ErrorReporter errorReporter,
      SoyTypeRegistry registry) {
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
          public JavaValue getRenderedCssNamespaces() {
            return JbcSrcJavaValue.of(jbcPluginContext.getRenderedCssNamespaces());
          }
        };
  }

  SoyExpression computeForJavaSource(List<SoyExpression> args) {
    ErrorReporter.Checkpoint checkpoint = errorReporter.checkpoint();
    checkState(fnNode.getAllowedParamTypes() != null, "allowed param types must be set");
    checkState(
        fnNode.getAllowedParamTypes().size() == args.size(),
        "wrong # of allowed param types (%s), expected %s",
        fnNode.getAllowedParamTypes(),
        args.size());
    pluginValidator.validate(
        fnNode.getFunctionName(),
        (SoyJavaSourceFunction) fnNode.getSoyFunction(),
        fnNode.getAllowedParamTypes(),
        fnNode.getType(),
        fnNode.getSourceLocation(),
        /* includeTriggeredInTemplateMsg= */ true);
    if (errorReporter.errorsSince(checkpoint)) {
      return SoyExpression.NULL_BOXED;
    }
    SoyJavaSourceFunction javaSrcFn = (SoyJavaSourceFunction) fnNode.getSoyFunction();
    return toSoyExpression(
        (JbcSrcJavaValue)
            javaSrcFn.applyForJavaSource(
                this, args.stream().map(JbcSrcJavaValue::of).collect(toImmutableList()), context));
  }

  @Override
  public JbcSrcJavaValue callStaticMethod(Method method, JavaValue... params) {
    return callPluginMethod(/* instance= */ false, toMethodSignature(method), params);
  }

  @Override
  public JbcSrcJavaValue callStaticMethod(MethodSignature methodSignature, JavaValue... params) {
    return callPluginMethod(/* instance= */ false, methodSignature, params);
  }

  @Override
  public JbcSrcJavaValue callInstanceMethod(Method method, JavaValue... params) {
    return callPluginMethod(/* instance= */ true, toMethodSignature(method), params);
  }

  @Override
  public JbcSrcJavaValue callInstanceMethod(MethodSignature methodSignature, JavaValue... params) {
    return callPluginMethod(/* instance= */ true, methodSignature, params);
  }

  /**
   * Adapts the parameters to Expressions and generates the correct invocation for calling the
   * plugin.
   */
  private JbcSrcJavaValue callPluginMethod(
      boolean instance, MethodSignature methodSignature, JavaValue... params) {
    // Attempt to eagerly convert the result to a SoyExpression to make life easier for ourselves.
    // (We can take various shortcuts if things are SoyExpressions.)
    // This lets us more easily support users who want to compose multiple callXMethod calls, e.g:
    //   callXMethod(METHOD1, callXMethod(METHOD2, arg1), callXMethod(METHOD3, arg2));
    // ... which would call METHOD1 with the results of METHOD2 & METHOD3.
    Expression[] adapted = adaptParams(methodSignature, params);
    TypeInfo owner = TypeInfo.create(methodSignature.fullyQualifiedClassName());
    org.objectweb.asm.commons.Method asmMethod =
        new org.objectweb.asm.commons.Method(
            methodSignature.methodName(),
            Type.getType(methodSignature.returnType()),
            methodSignature.arguments().stream()
                .map(Type::getType)
                .collect(toImmutableList())
                .toArray(new Type[0]));
    Expression methodCall;
    if (instance) {
      // We need to cast to the method's declaring class in order for the owner type
      // to be correct when calling the method, otherwise the JVM won't be able to dispatch
      // the method because the type will just be 'Object'.
      Expression runtime =
          pluginInstanceLookup
              .getPluginInstance(fnNode.getFunctionName())
              .checkedCast(owner.type());
      MethodRef methodRef =
          methodSignature.inInterface()
              ? MethodRef.createInterfaceMethod(owner, asmMethod)
              : MethodRef.createInstanceMethod(owner, asmMethod);
      methodCall = runtime.invoke(methodRef, adapted);
    } else {
      methodCall = MethodRef.createStaticMethod(owner, asmMethod).invoke(adapted);
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
    return JbcSrcJavaValue.of(SoyExpression.asBoxedList(soyExprs));
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
    return JbcSrcJavaValue.of(SoyExpression.NULL);
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
    if (actualParam.soyRuntimeType().soyType().equals(NullType.getInstance())) {
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
      MethodSignature method = pluginReturnValue.methodInfo();
      if (method != null) {
        type = method.returnType();
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
          throw new IllegalStateException("Invalid type: " + expectedType);
        }
      } else if (SoyValue.class.isAssignableFrom(type)) {
        soyExpr =
            SoyExpression.forSoyValue(
                expectedType,
                expr.checkedCast(SoyRuntimeType.getBoxedType(expectedType).runtimeType()));
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
                    MethodRef.PROTOCOL_ENUM_GET_NUMBER.invoke(expr), Type.LONG_TYPE));
      } else {
        throw new IllegalStateException("invalid type: " + type);
      }
    }
    return soyExpr;
  }

  /** Returns the SoyType for a proto or proto enum. */
  private SoyType soyTypeForProtoOrEnum(Class<?> type) {
    return registry.getType(nameFromDescriptor(type));
  }
}
