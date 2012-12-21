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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsCodeUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;

import java.util.List;
import java.util.Set;


/**
 * Soy function that checks whether its argument is a defined nonnull value.
 *
 * @author Kai Huang
 */
@Singleton
@SoyPureFunction
class IsNonnullFunction extends SoyAbstractTofuFunction
    implements SoyJsSrcFunction, SoyJavaSrcFunction {


  @Inject
  IsNonnullFunction() {}


  @Override public String getName() {
    return "isNonnull";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }


  @Override public SoyData compute(List<SoyData> args) {
    SoyData arg = args.get(0);
    return BooleanData.forValue(! (arg instanceof UndefinedData || arg instanceof NullData));
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);
    JsExpr nullJsExpr = new JsExpr("null", Integer.MAX_VALUE);
    // Note: In JavaScript, "x != null" is equivalent to "x !== undefined && x !== null".
    return SoyJsCodeUtils.genJsExprUsingSoySyntax(
        Operator.NOT_EQUAL, Lists.<JsExpr>newArrayList(arg, nullJsExpr));
  }


  @Override public JavaExpr computeForJavaSrc(List<JavaExpr> args) {
    JavaExpr arg = args.get(0);
    return toBooleanJavaExpr(
        JavaCodeUtils.genFunctionCall(JavaCodeUtils.UTILS_LIB + ".$$isNonnull", arg.getText()));
  }

}
