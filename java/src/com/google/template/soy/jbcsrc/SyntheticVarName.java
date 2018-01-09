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
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SwitchNode;

/**
 * A simple representation of a synthetic variable name.
 *
 * <p>This class centralizes all the logic for managing and constructing synthetic names.
 */
@AutoValue
abstract class SyntheticVarName {
  static SyntheticVarName forSwitch(SwitchNode node) {
    return new AutoValue_SyntheticVarName("switch", node);
  }

  static SyntheticVarName foreachLoopRangeStart(ForNonemptyNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_start", forNode);
  }

  static SyntheticVarName foreachLoopRangeStep(ForNonemptyNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_step", forNode);
  }

  static SyntheticVarName foreachLoopRangeEnd(ForNonemptyNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_end", forNode);
  }

  static SyntheticVarName foreachLoopList(ForNonemptyNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_list", forNode);
  }

  static SyntheticVarName foreachLoopIndex(ForNonemptyNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_index", forNode);
  }

  static SyntheticVarName foreachLoopLength(ForNonemptyNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_length", forNode);
  }

  abstract String name();

  abstract SoyNode declaringNode();
}
