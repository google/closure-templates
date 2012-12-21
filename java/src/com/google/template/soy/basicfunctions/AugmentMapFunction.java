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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.internal.AugmentedSoyMapData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;

import java.util.List;
import java.util.Set;


/**
 * Soy function that creates a new map equivalent to augmenting an existing map with additional
 * mappings.
 *
 * @author Kai Huang
 */
@Singleton
@SoyPureFunction
class AugmentMapFunction extends SoyAbstractTofuFunction implements SoyJsSrcFunction {


  @Inject
  AugmentMapFunction() {}


  @Override public String getName() {
    return "augmentMap";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(2);
  }


  @Override public SoyData compute(List<SoyData> args) {
    SoyData arg0 = args.get(0);
    SoyData arg1 = args.get(1);

    Preconditions.checkArgument(arg0 instanceof SoyMapData,
        "First argument to augmentMap() function is not SoyMapData.");
    Preconditions.checkArgument(arg1 instanceof SoyMapData,
        "Second argument to augmentMap() function is not SoyMapData.");

    AugmentedSoyMapData augmentedMap = new AugmentedSoyMapData((SoyMapData) arg0);
    SoyMapData additionalMap = (SoyMapData) arg1;
    for (String key : additionalMap.getKeys()) {
      augmentedMap.putSingle(key, additionalMap.getSingle(key));
    }
    return augmentedMap;
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg0 = args.get(0);
    JsExpr arg1 = args.get(1);

    String exprText = "soy.$$augmentMap(" + arg0.getText() + ", " + arg1.getText() + ")";
    return new JsExpr(exprText, Integer.MAX_VALUE);
  }

}
