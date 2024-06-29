/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

import com.google.auto.value.AutoValue;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.SwitchNode;
import javax.annotation.Nullable;

/**
 * A simple representation of a synthetic variable name.
 *
 * <p>This class centralizes all the logic for managing and constructing synthetic names.
 */
@AutoValue
abstract class SyntheticVarName {

  static SyntheticVarName renderee() {
    return new AutoValue_SyntheticVarName(StandardNames.CURRENT_RENDEREE, null);
  }

  static SyntheticVarName appendable() {
    return new AutoValue_SyntheticVarName(StandardNames.CURRENT_APPENDABLE, null);
  }

  static SyntheticVarName params() {
    return new AutoValue_SyntheticVarName(StandardNames.CURRENT_PARAMS, null);
  }

  static SyntheticVarName dataExpr() {
    return new AutoValue_SyntheticVarName(StandardNames.CURRENT_DATA_EXPR, null);
  }

  static SyntheticVarName forSwitch(SwitchNode node) {
    return new AutoValue_SyntheticVarName("switch", node);
  }

  static SyntheticVarName foreachLoopIterator(ForNonemptyNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_iterator", forNode);
  }

  static SyntheticVarName forParam(CallParamNode param) {
    return new AutoValue_SyntheticVarName(param.getKey().identifier(), param);
  }

  abstract String name();

  @Nullable
  abstract Node declaringNode();
}
