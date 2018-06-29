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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Soy function for checking if an item is contained in a list.
 *
 * <p>Usage: {@code listContains(list, item)}
 *
 * <ul>
 *   <li>list: The list in which to look for the item.
 *   <li>item: The item to search for in the list.
 * </ul>
 */
@SoyFunctionSignature(
    name = "listContains",
    value =
        @Signature(
            parameterTypes = {"list<any>", "any"},
            returnType = "bool"))
@Singleton
public class ListContainsSoyFunction extends TypedSoyFunction
    implements SoyJavaFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Inject
  public ListContainsSoyFunction() {}

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    return new JsExpr(
        "soy.$$listContains(" + args.get(0).getText() + "," + args.get(1).getText() + ")",
        Integer.MAX_VALUE);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    return BooleanData.forValue(
        BasicFunctionsRuntime.listContains((SoyList) args.get(0), args.get(1)));
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    ImmutableList.Builder<String> expTexts = ImmutableList.builder();
    for (PyExpr expr : args) {
      expTexts.add(expr.getText());
    }
    return new PyListExpr(
        "any(runtime.type_safe_eq(el, "
            + args.get(1).getText()
            + ") for el in "
            + args.get(0).getText()
            + ")",
        Integer.MAX_VALUE);
  }
  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef LIST_CONTAINS_FN =
        MethodRef.create(
            BasicFunctionsRuntime.class, "listContains", SoyList.class, SoyValue.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression list = args.get(0);
    SoyExpression value = args.get(1);
    return SoyExpression.forBool(
        list.box().checkedCast(SoyList.class).invoke(JbcSrcMethods.LIST_CONTAINS_FN, value.box()));
  }
}
