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
import com.google.common.collect.Lists;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Soy function that generates a random integer in the range [0, n-1].
 *
 */
@Singleton
public final class RandomIntFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction {

  @Inject
  RandomIntFunction() {}

  @Override
  public String getName() {
    return "randomInt";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg = args.get(0);

    long longValue = arg.longValue();
    return IntegerData.forValue(randomInt(longValue));
  }

  /** Returns a random integer between {@code 0} and the provided argument. */
  public static long randomInt(long longValue) {
    return (long) Math.floor(Math.random() * longValue);
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);

    JsExpr random = new JsExpr("Math.random()", Integer.MAX_VALUE);
    JsExpr randomTimesArg =
        SoyJsPluginUtils.genJsExprUsingSoySyntax(Operator.TIMES, Lists.newArrayList(random, arg));
    return new JsExpr("Math.floor(" + randomTimesArg.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr arg = args.get(0);
    // Subtract 1 from the argument as the python randint function is inclusive on both sides.
    return new PyExpr("random.randint(0, " + arg.getText() + " - 1)", Integer.MAX_VALUE);
  }
}
