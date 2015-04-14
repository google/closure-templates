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

import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;

/**
 * A simple representation of a synthetic variable name.
 * 
 * <p>This class centralizes all the logic for managing and constructing synthetic names.
 */
final class SyntheticVarName {
  static SyntheticVarName forSwitch() {
    return new SyntheticVarName("switch");
  }

  static SyntheticVarName forLoopIncrement(ForNode forNode) {
    return new SyntheticVarName(forNode.getVarName() + "_increment");
  }

  static SyntheticVarName forLoopLimit(ForNode forNode) {
    return new SyntheticVarName(forNode.getVarName() + "_limit");
  }
  
  static SyntheticVarName foreachLoopList(ForeachNonemptyNode foreachNode) {
    return new SyntheticVarName(foreachNode.getVarName() + "_list");
  }

  static SyntheticVarName foreachLoopIndex(ForeachNonemptyNode foreachNode) {
    return new SyntheticVarName(foreachNode.getVarName() + "_index");
  }
  
  static SyntheticVarName foreachLoopLength(ForeachNonemptyNode foreachNode) {
    return new SyntheticVarName(foreachNode.getVarName() + "_length");
  }
  
  static SyntheticVarName foreachLoopItemProvider(ForeachNonemptyNode foreachNode) {
    return new SyntheticVarName(foreachNode.getVarName() + "_itemProvider");
  }

  private final String name;

  private SyntheticVarName(String name) {
    this.name = name;
  }
  
  @Override public String toString() {
    return "SyntheticVarName{" + name() + "}";
  }

  String name() {
    return name;
  }
}