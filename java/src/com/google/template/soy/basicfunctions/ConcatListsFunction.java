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

package com.google.template.soy.basicfunctions;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnionType;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy function that concatenates two or more lists together.
 *
 */
@SoyFunctionSignature(
    name = "concatLists",
    value = {
      // Note: These signatures exist solely to inform the # of parameters we allow.
      // The return type is overridden in ResolveExpressionTypePass.
      // ConcatLists would be varadic if soy allowed varadic functions. Instead we're giving the
      // function a high enough upper limit that it's close enough to being varadic in practice.
      @Signature(
          parameterTypes = {"list<?>"},
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {"list<?>", "list<?>"},
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {"list<?>", "list<?>", "list<?>"},
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {"list<?>", "list<?>", "list<?>", "list<?>"},
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {"list<?>", "list<?>", "list<?>", "list<?>", "list<?>"},
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {"list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>"},
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {
            "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>"
          },
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {
            "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>"
          },
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {
            "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>",
            "list<?>"
          },
          returnType = "list<?>"),
      @Signature(
          parameterTypes = {
            "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>", "list<?>",
            "list<?>", "list<?>"
          },
          returnType = "list<?>")
    })
@SoyPureFunction
public final class ConcatListsFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    ImmutableList.Builder<String> expTexts = ImmutableList.builder();
    for (JsExpr expr : args) {
      expTexts.add(expr.getText());
    }

    return new JsExpr(
        "Array.prototype.concat(" + Joiner.on(',').join(expTexts.build()) + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    ImmutableList.Builder<String> expTexts = ImmutableList.builder();
    for (PyExpr expr : args) {
      expTexts.add(expr.getText());
    }
    return new PyListExpr("(" + Joiner.on('+').join(expTexts.build()) + ")", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method CONCAT_LISTS_FN =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "concatLists", List.class);
    static final MethodRef CONCAT_LISTS_FN_REF = MethodRef.create(CONCAT_LISTS_FN);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.CONCAT_LISTS_FN, factory.listOf(args));
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    ImmutableSet.Builder<SoyType> elementTypes = ImmutableSet.builder();
    for (SoyExpression soyExpression : args) {
      SoyType elementType = ((ListType) soyExpression.soyType()).getElementType();
      if (elementType != null) { // Empty lists have no element type
        elementTypes.add(elementType);
      }
    }

    return SoyExpression.forList(
        ListType.of(UnionType.of(elementTypes.build())),
        Methods.CONCAT_LISTS_FN_REF.invoke(SoyExpression.asBoxedList(args)));
  }
}
