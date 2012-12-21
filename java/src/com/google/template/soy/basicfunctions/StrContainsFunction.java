/*
 * Copyright 2012 Google Inc.
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

import static com.google.template.soy.javasrc.restricted.SoyJavaSrcFunctionUtils.toBooleanJavaExpr;
import static com.google.template.soy.shared.restricted.SoyJavaRuntimeFunctionUtils.toSoyData;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;

import java.util.List;
import java.util.Set;


/**
 * A function that determines if a given string contains another given string.
 *
 * <p><code>strContains(expr1, expr2)</code> requires <code>expr1</code> and
 * <code>expr2</code> to be of type string.  It evaluates to <code>true</code>
 * iff <code>expr1</code> contains <code>expr2</code>.  <code>strContains</code>
 * is case sensitive.
 *
 * @author Felix Arends
 */
@Singleton
@SoyPureFunction
class StrContainsFunction extends SoyAbstractTofuFunction
    implements SoyJsSrcFunction, SoyJavaSrcFunction {


  @Inject
  StrContainsFunction() {}


  @Override public String getName() {
    return "strContains";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(2);
  }


  @Override public SoyData compute(List<SoyData> args) {
    SoyData arg0 = args.get(0);
    SoyData arg1 = args.get(1);

    Preconditions.checkArgument(arg0 instanceof StringData, String.format(
        "First argument to strContains() function is not StringData: %s",
        arg0.stringValue()));

    Preconditions.checkArgument(arg1 instanceof StringData, String.format(
        "Second argument to strContains() function is not StringData: %s",
        arg1.stringValue()));

    String strArg0 = arg0.stringValue();
    String strArg1 = arg1.stringValue();

    return toSoyData(strArg0.contains(strArg1));
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg0 = args.get(0);
    JsExpr arg1 = args.get(1);

    String arg0Text = arg0.getPrecedence() == Integer.MAX_VALUE
        ? arg0.getText() : "(" + arg0.getText() + ")";
    String arg1Text = arg1.getText();

    String exprText = arg0Text + ".indexOf(" + arg1Text + ") != -1";

    return new JsExpr(exprText, Operator.NOT_EQUAL.getPrecedence());
  }


  @Override public JavaExpr computeForJavaSrc(List<JavaExpr> args) {
    JavaExpr arg0 = args.get(0);
    JavaExpr arg1 = args.get(1);

    return toBooleanJavaExpr(
        JavaCodeUtils.genNewBooleanData(
            "(" + arg0.getText() + ").contains(" + arg1.getText() + ")"));
  }
}
