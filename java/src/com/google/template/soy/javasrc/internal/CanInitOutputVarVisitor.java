/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.javasrc.internal;

import com.google.inject.Inject;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.SoyJavaSrcOptions.CodeStyle;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyNode;


/**
 * Visitor for determining whther the code generated from a given node's subtree can be made to
 * also initialize the current variable (if not already initialized).
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * @author Kai Huang
 */
class CanInitOutputVarVisitor extends AbstractReturningSoyNodeVisitor<Boolean> {


  /** The options for generating Java source code. */
  private final SoyJavaSrcOptions javaSrcOptions;

  /** The IsComputableAsJavaExprsVisitor used by this instance (when needed). */
  private final IsComputableAsJavaExprsVisitor isComputableAsJavaExprsVisitor;


  /**
   * @param javaSrcOptions The options for generating Java source code.
   * @param computableAsJavaExprsVisitor The IsComputableAsJavaExprsVisitor used by this instance
   *     (when needed).
   */
  @Inject
  public CanInitOutputVarVisitor(SoyJavaSrcOptions javaSrcOptions,
                                 IsComputableAsJavaExprsVisitor computableAsJavaExprsVisitor) {
    this.javaSrcOptions = javaSrcOptions;
    this.isComputableAsJavaExprsVisitor = computableAsJavaExprsVisitor;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected Boolean visitCallNode(CallNode node) {
    // If we're generating code in the 'concat' style, then the call is a Java expression that
    // returns its output as a string. However, if we're generating code in the 'stringbuilder'
    // style, then the call is a full statement that returns no value (instead, the output is
    // directly appended to the StringBuilder we pass to the callee).
    return javaSrcOptions.getCodeStyle() == CodeStyle.CONCAT;
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected Boolean visitSoyNode(SoyNode node) {
    // For the vast majority of nodes, the return value of this visitor should be the same as the
    // return value of IsComputableAsJavaExprsVisitor.
    return isComputableAsJavaExprsVisitor.exec(node);
  }

}
