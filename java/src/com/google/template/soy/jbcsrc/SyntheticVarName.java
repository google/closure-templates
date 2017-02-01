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
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
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

  static SyntheticVarName forLoopIncrement(ForNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_increment", forNode);
  }

  static SyntheticVarName forLoopLimit(ForNode forNode) {
    return new AutoValue_SyntheticVarName(forNode.getVarName() + "_limit", forNode);
  }

  static SyntheticVarName foreachLoopList(ForeachNonemptyNode foreachNode) {
    return new AutoValue_SyntheticVarName(foreachNode.getVarName() + "_list", foreachNode);
  }

  static SyntheticVarName foreachLoopIndex(ForeachNonemptyNode foreachNode) {
    return new AutoValue_SyntheticVarName(foreachNode.getVarName() + "_index", foreachNode);
  }

  static SyntheticVarName foreachLoopLength(ForeachNonemptyNode foreachNode) {
    return new AutoValue_SyntheticVarName(foreachNode.getVarName() + "_length", foreachNode);
  }

  abstract String name();

  abstract SoyNode declaringNode();
}
