/*
 * Copyright 2009 Google Inc.
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

import static com.google.template.soy.javasrc.restricted.SoyJavaSrcFunctionUtils.toIntegerJavaExpr;
import static com.google.template.soy.shared.restricted.SoyJavaRuntimeFunctionUtils.toSoyData;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;

import java.util.List;
import java.util.Set;


/**
 * Soy function that gets the length of a list.
 *
 * @author Kai Huang
 */
@Singleton
class LengthFunction extends SoyAbstractTofuFunction
    implements SoyJsSrcFunction, SoyJavaSrcFunction {


  @Inject
  LengthFunction() {}


  @Override public String getName() {
    return "length";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }


  @Override public SoyData compute(List<SoyData> args) {
    SoyData arg = args.get(0);

    if (!(arg instanceof SoyListData)) {
      throw new IllegalArgumentException("Argument to length() function is not SoyListData.");
    }
    return toSoyData(((SoyListData) arg).length());
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);

    String exprText = arg.getPrecedence() == Integer.MAX_VALUE ?
                      arg.getText() + ".length" : "(" + arg.getText() + ").length";
    return new JsExpr(exprText, Integer.MAX_VALUE);
  }


  @Override public JavaExpr computeForJavaSrc(List<JavaExpr> args) {
    JavaExpr arg = args.get(0);

    return toIntegerJavaExpr(
        JavaCodeUtils.genNewIntegerData(
            "(" + JavaCodeUtils.genMaybeCast(arg, SoyListData.class) + ").length()"));
  }

}
