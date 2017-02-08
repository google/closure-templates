/*
 * Copyright 2017 Google Inc.
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
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Soy special function for automatic coercion of an int into a float. */
@Singleton
@SoyPureFunction
public final class FloatFunction implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction {

  @Inject
  FloatFunction() {}

  @Override
  public String getName() {
    return "_soy_private_do_not_use_float"; // TODO(user): fix name
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg = args.get(0);
    if (arg instanceof FloatData) {
      return arg;
    }
    return FloatData.forValue(arg.longValue()); // non-IntegerData will throw
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // int -> float coercion is a no-op in javascript
    return args.get(0);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // int -> float coercion is a no-op in python
    return args.get(0);
  }
}
