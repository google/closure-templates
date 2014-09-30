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
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyEasyDict;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Soy function that creates a new map equivalent to augmenting an existing map with additional
 * mappings.
 *
 */
@Singleton
@SoyPureFunction
class AugmentMapFunction implements SoyJavaFunction, SoyJsSrcFunction {


  /** The SoyValueHelper instance to use internally. */
  private final SoyValueHelper valueHelper;


  @Inject
  AugmentMapFunction(SoyValueHelper valueHelper) {
    this.valueHelper = valueHelper;
  }


  @Override public String getName() {
    return "augmentMap";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(2);
  }


  @SuppressWarnings("ConstantConditions")  // IntelliJ
  @Override public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg0 = args.get(0);
    SoyValue arg1 = args.get(1);

    Preconditions.checkArgument(arg0 instanceof SoyMap,
        "First argument to augmentMap() function is not SoyMap.");
    Preconditions.checkArgument(arg1 instanceof SoyMap,
        "Second argument to augmentMap() function is not SoyMap.");

    // TODO: Support map with nonstring key.
    Preconditions.checkArgument(arg0 instanceof SoyDict,
        "First argument to augmentMap() function is not SoyDict. Currently, augmentMap() doesn't" +
            " support maps that are not dicts (it is a todo).");
    Preconditions.checkArgument(arg1 instanceof SoyDict,
        "Second argument to augmentMap() function is not SoyDict. Currently, augmentMap() doesn't" +
            " support maps that are not dicts (it is a todo).");

    SoyEasyDict resultDict = valueHelper.newEasyDict();
    resultDict.setItemsFromDict((SoyDict) arg0);
    resultDict.setItemsFromDict((SoyDict) arg1);
    return resultDict;
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg0 = args.get(0);
    JsExpr arg1 = args.get(1);

    String exprText = "soy.$$augmentMap(" + arg0.getText() + ", " + arg1.getText() + ")";
    return new JsExpr(exprText, Integer.MAX_VALUE);
  }

}
