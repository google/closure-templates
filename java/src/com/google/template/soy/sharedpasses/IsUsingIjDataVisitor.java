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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.SoytreeUtils.Shortcircuiter;

import java.util.Set;


/**
 * Visitor for determining whether any code in a Soy tree uses injected data.
 *
 * @author Kai Huang
 */
public class IsUsingIjDataVisitor {


  /**
   * Runs this pass on the given Soy tree.
   */
  public boolean exec(SoyFileSetNode soyTree) {

    FindUsedIjParamsInExprHelperVisitor helperVisitor = new FindUsedIjParamsInExprHelperVisitor();

    // We only care whether the result set is empty, so shortcircuit the pass as soon as the result
    // set is nonempty.
    SoytreeUtils.execOnAllV2ExprsShortcircuitably(
        soyTree,
        helperVisitor,
        new Shortcircuiter<Set<String>>() {
          @Override
          public boolean shouldShortcircuit(AbstractExprNodeVisitor<Set<String>> exprNodeVisitor) {
            return ((FindUsedIjParamsInExprHelperVisitor) exprNodeVisitor).getResult().size() > 0;
          }
        });

    return helperVisitor.getResult().size() > 0;
  }

}
