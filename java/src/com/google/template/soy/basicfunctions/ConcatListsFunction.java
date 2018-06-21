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
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnionType;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Soy function that concatenates two or more lists together.
 *
 * <p>NOTE: this function has special support in the type checker for calculating the return type
 *
 */
@Singleton
@SoyPureFunction
public final class ConcatListsFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  // ConcatLists would be varadic if soy allowed varadic functions. Instead we're giving the
  // function a high enough upper limit that it's close enough to being varadic in practice.
  private static final int ARGUMENT_SIZE_LIMIT = 10;

  @Inject
  ConcatListsFunction() {}

  @Override
  public String getName() {
    return "concatLists";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    ImmutableSet.Builder<Integer> argSizes = ImmutableSet.builder();
    for (int i = 2; i <= ARGUMENT_SIZE_LIMIT; i++) {
      argSizes.add(i);
    }
    return argSizes.build();
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    ImmutableList.Builder<SoyList> asSoyLists = ImmutableList.builder();
    for (SoyValue soyList : args) {
      asSoyLists.add((SoyList) soyList);
    }
    return ListImpl.forProviderList(BasicFunctionsRuntime.concatLists(asSoyLists.build()));
  }

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
  private static final class JbcSrcMethods {
    static final MethodRef CONCAT_LISTS_FN =
        MethodRef.create(BasicFunctionsRuntime.class, "concatLists", List.class);
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
        JbcSrcMethods.CONCAT_LISTS_FN.invoke(SoyExpression.asBoxedList(args)));
  }
}
