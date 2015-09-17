/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.passes;

import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoytreeUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helpers for determining whether any code in a Soy tree uses injected data.
 *
 */
public final class IjDataQueries {

  /**
   * Runs this pass on the given Soy tree.
   */
  public static boolean isUsingIj(Node soyTree) {
    final AtomicBoolean usingIjs = new AtomicBoolean();
    SoytreeUtils.visitAllNodes(soyTree, new NodeVisitor<Node, Boolean>() {
      @Override public Boolean exec(Node node) {
        if (isIj(node)) {
          usingIjs.set(true);
          return false;  // short circuit
        }
        return true;
      }
    });
    return usingIjs.get();
  }

  /**
   * Runs this pass on the given Soy tree.
   */
  public static Set<String> getAllIjs(Node soyTree) {
    final Set<String> ijs = new HashSet<>();
    SoytreeUtils.visitAllNodes(soyTree, new NodeVisitor<Node, Boolean>() {
      @Override public Boolean exec(Node node) {
        if (isIj(node)) {
          ijs.add(((VarRefNode) node).getName());
        }
        return true;
      }
    });
    return ijs;
  }

  private static boolean isIj(Node node) {
    if (node instanceof VarRefNode) {
      VarRefNode varRef = (VarRefNode) node;
      if (varRef.isInjected()) {
        return true;
      }
    }
    return false;
  }
}
