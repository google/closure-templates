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

/**
 * Soy special function for automatic coercion of an int into a float.
 *
 * <p>This function is explicitly not registered with {@link BasicFunctionsModule}. It exists mostly
 * to enable adding return types to commonly used functions without breaking type-checking for
 * existing templates. It is not meant to be used directly in Soy templates.
 */
@SoyPureFunction
public final class FloatFunction implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction {

  // $$ prefix ensures that the function cannot be used directly
  public static final String NAME = "$$float";

  public static final FloatFunction INSTANCE = new FloatFunction();

  // Do not @Inject; should not be used outside of {@link CheckTemplateCallsPass}.
  private FloatFunction() {}

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    return FloatData.forValue(args.get(0).longValue()); // non-IntegerData will throw on longValue()
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
