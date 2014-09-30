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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Soy function that takes the ceiling of a number.
 *
 */
@Singleton
@SoyPureFunction
class CeilingFunction implements SoyJavaFunction, SoyJsSrcFunction {


  @Inject
  CeilingFunction() {}


  @Override public String getName() {
    return "ceiling";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }


  @Override public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg = args.get(0);

    if (arg instanceof IntegerData) {
      return arg;
    } else {
      return IntegerData.forValue((int) Math.ceil(arg.floatValue()));
    }
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);

    return new JsExpr("Math.ceil(" + arg.getText() + ")", Integer.MAX_VALUE);
  }

}
