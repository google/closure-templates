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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValue.ValueSoyType;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SoyType;
import java.lang.reflect.Method;
import java.util.List;
import org.objectweb.asm.Type;

/** Adapts JavaValueFactory to working with Expressions for jbc src. */
final class JbcSrcValueFactory extends JavaValueFactory {

  /** Interface that looks up an expression for a given plugin name. */
  interface PluginInstanceLookup {
    /** Returns the runtime this function uses. */
    Expression getFunctionRuntime(String pluginName);
  }

  private final FunctionNode fnNode;
  private final JavaPluginContext context;
  private final PluginInstanceLookup pluginInstanceLookup;

  JbcSrcValueFactory(
      FunctionNode fnNode, JavaPluginContext context, PluginInstanceLookup pluginInstanceLookup) {
    this.fnNode = fnNode;
    this.context = context;
    this.pluginInstanceLookup = pluginInstanceLookup;
  }

  SoyExpression computeForJavaSource(List<SoyExpression> args) {
    // Transform and copy to an ImmutableList to avoid exposing a mutable list to user code.
    List<JavaValue> javaArgs =
        ImmutableList.copyOf(
            Lists.transform(
                args,
                new Function<SoyExpression, JavaValue>() {
                  @Override
                  public JavaValue apply(SoyExpression expr) {
                    return JbcSrcJavaValue.of(expr);
                  }
                }));
    SoyJavaSourceFunction javaSrcFn = (SoyJavaSourceFunction) fnNode.getSoyFunction();
    JavaValue result = javaSrcFn.applyForJavaSource(this, javaArgs, context);
    return toSoyExpression((JbcSrcJavaValue) result);
  }

  @Override
  public JbcSrcJavaValue callStaticMethod(Method method, JavaValue... params) {
    // Attempt to eagerly convert the result to a SoyExpression to make life easier for ourselves.
    // (We can take various shortcuts if things are SoyExpressions.)
    // This lets us more easily support users who want to compose multiple callXMethod calls, e.g:
    //   callXMethod(METHOD1, callXMethod(METHOD2, arg1), callXMethod(METHOD3, arg2));
    // ... which would call METHOD1 with the results of METHOD2 & METHOD3.
    return JbcSrcJavaValue.of(
        tryToWrapInSoyExpression(MethodRef.create(method).invoke(adaptParams(method, params))),
        method);
  }

  @Override
  public JbcSrcJavaValue callRuntimeMethod(Method method, JavaValue... params) {
    // We need to cast to the method's declaring class in order for the owner type
    // to be correct when calling the method, otherwise the JVM won't be able to dispatch
    // the method because the type will just be 'JavaPluginRuntime'.
    Expression runtime =
        pluginInstanceLookup
            .getFunctionRuntime(fnNode.getFunctionName())
            .checkedCast(method.getDeclaringClass());
    // See the note in callStaticMethod for why we eagerly try to wrap the result into a SoyExpr.
    return JbcSrcJavaValue.of(
        tryToWrapInSoyExpression(
            runtime.invoke(MethodRef.create(method), adaptParams(method, params))),
        method);
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
    return JbcSrcJavaValue.of(SoyExpression.asBoxedList(soyExprs));
  }

  private Expression[] adaptParams(Method method, JavaValue[] userParams) {
    Class<?>[] methodParams = method.getParameterTypes();
    if (methodParams.length != userParams.length) {
      throw PluginCodegenException.invalidParameterLength(fnNode, method, userParams);
    }

    Expression[] params = new Expression[userParams.length];
    for (int i = 0; i < userParams.length; i++) {
      Class<?> methodParam = methodParams[i];
      Expression expr = ((JbcSrcJavaValue) userParams[i]).expr();
      // TODO(sameb): This could probably do with a whole lot more checking and user-friendly
      // exceptions.  Things to check for:
      //   * Method arg wants non-Soy-supported type
      //   * Support SoyValueProvider too?
      // Note: We expect most parameters to be a SoyExpression, however there are some cases where
      // they won't be.  For example:
      //    * If the user passed the result of JavaValueFactory.asList.
      //      (The list is just an Expression of type List.  It's not a SoyExpression, because
      //       we don't know the parameter types.  This could change if we want to use 'unknown'
      //       as the parameter types.)
      //   * If the user composed multiple callXMethod calls together and the result of an inner
      //     one was a Soy type that required parameters.  (This could also change if we want
      //     to eagerly use 'unknown' as the parameter types.)
      //   * If the user passed the result of a JavaPluginContext call, like the BidiGlobalDir,
      //     which has no SoyExpression that can wrap it.
      if (expr instanceof SoyExpression) {
        params[i] = adaptParameter(method, i, methodParam, (SoyExpression) expr);
      } else {
        if (!BytecodeUtils.isDefinitelyAssignableFrom(
            Type.getType(methodParam), expr.resultType())) {
          throw PluginCodegenException.invalidParameterType(
              fnNode, method, i + 1, methodParam, expr);
        }
        params[i] = expr;
      }
    }
    return params;
  }

  private Expression adaptParameter(
      Method method, int paramIdx, Class<?> expectedParamType, SoyExpression actualParam) {
    if (expectedParamType == SoyValue.class) {
      // If expecting a bland 'SoyValue', just box the expr.
      return actualParam.box();
    } else if (SoyValue.class.isAssignableFrom(expectedParamType)) {
      // If we expect a specific SoyValue subclass (e.g, SoyDict), then box + cast.
      // Ideally we'd also do some kind of compiler-time check here
      // (like isDefinitelyAssignableFrom(getType(expected), actual.resultType)
      // but the actual type of a boxed dict is SoyRecord, not SoyDict.... so oh well.)
      return actualParam.box().checkedCast(expectedParamType);
    }

    // Otherwise we want an unboxed param, so inspect a little more closely...
    switch (valueForKind(actualParam.soyType().getKind())) {
      case NULL:
        if (!Primitives.allPrimitiveTypes().contains(expectedParamType)) {
          // TODO(sameb): No guarantee we can unbox as the expected type...
          return actualParam.unboxAs(expectedParamType);
        }
        break;
      case BOOLEAN:
        if (expectedParamType == boolean.class) {
          return actualParam.unboxAs(boolean.class);
        }
        break;
      case FLOAT:
        if (expectedParamType == double.class) {
          return actualParam.unboxAs(double.class);
        }
        break;
      case INTEGER:
        if (expectedParamType == long.class) {
          return actualParam.unboxAs(long.class);
        } else if (expectedParamType == int.class) {
          // TODO(sameb): This could overflow -- ideally we should generate bounds checks like
          // IntegerData.integerValue() has.
          return BytecodeUtils.numericConversion(actualParam.unboxAs(long.class), Type.INT_TYPE);
        } else if (expectedParamType == double.class) {
          // long can be represented as a double, e.g IntegerData.floatValue works fine.
          return actualParam.coerceToDouble();
        }
        break;
      case LIST:
        // TODO(sameb): This doesn't validate the generic types.  I'm not sure we can even do that.
        // Should we require SoyList (boxed) instead when a list?
        if (expectedParamType == List.class || expectedParamType == FluentIterable.class) {
          return actualParam.unboxAs(List.class);
        }
        break;
      case STRING:
        if (expectedParamType == String.class) {
          return actualParam.unboxAs(String.class);
        }
        break;
      case OTHER:
        // TODO(sameb): Figure this out.  Fail for now.
    }

    throw PluginCodegenException.invalidParameterType(
        fnNode, method, paramIdx + 1, expectedParamType, actualParam);
  }

  /**
   * Tries to wrap the Expression in a {@link SoyExpression}. We can only do this for types we
   * statically know are Soy Expressions and don't require additional runtime type args.
   */
  private Expression tryToWrapInSoyExpression(Expression expr) {
    Class<?> type = BytecodeUtils.classFromAsmType(expr.resultType());
    // TODO(sameb): We could theoretically deal with wrapper types too, we probably
    // just need to generate bytecode to unbox them & then wrap in the SoyExpression.
    if (type == boolean.class) {
      return SoyExpression.forBool(expr);
    } else if (type == int.class) {
      return SoyExpression.forInt(BytecodeUtils.numericConversion(expr, Type.LONG_TYPE));
    } else if (type == long.class) {
      return SoyExpression.forInt(expr);
    } else if (type == float.class || type == double.class) {
      return SoyExpression.forFloat(expr);
    } else if (type == String.class) {
      return SoyExpression.forString(expr);
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
      Class<?> type = BytecodeUtils.classFromAsmType(expr.resultType());
      if (List.class.isAssignableFrom(type) || FluentIterable.class.isAssignableFrom(type)) {
        soyExpr = SoyExpression.forList((ListType) expectedType, expr);
      } else if (SoyValue.class.isAssignableFrom(type)) {
        soyExpr =
            SoyExpression.forSoyValue(
                expectedType,
                expr.checkedCast(SoyRuntimeType.getBoxedType(expectedType).runtimeType()));
      } else {
        // TODO(sameb): Support more types, like map, proto, etc..
        throw PluginCodegenException.invalidReturnType(
            fnNode, type, pluginReturnValue.methodInfo());
      }
    }
    if (!expectedType.isAssignableFrom(soyExpr.soyType())) {
      throw PluginCodegenException.incompatibleReturnType(
          fnNode, soyExpr.soyType(), pluginReturnValue.methodInfo());
    }
    return soyExpr;
  }

  static ValueSoyType valueForKind(SoyType.Kind kind) {
    switch (kind) {
      case BOOL:
        return ValueSoyType.BOOLEAN;
      case FLOAT:
        return ValueSoyType.FLOAT;
      case INT:
        return ValueSoyType.INTEGER;
      case LIST:
        return ValueSoyType.LIST;
      case NULL:
        return ValueSoyType.NULL;
      case STRING:
        return ValueSoyType.STRING;

      case ANY:
      case ATTRIBUTES:
      case CSS:
      case ERROR:
      case HTML:
      case JS:
      case LEGACY_OBJECT_MAP:
      case MAP:
      case PROTO:
      case PROTO_ENUM:
      case RECORD:
      case TRUSTED_RESOURCE_URI:
      case UNION:
      case UNKNOWN:
      case URI:
        return ValueSoyType.OTHER;
    }
    throw new AssertionError("above switch is exhaustive");
  }
}
