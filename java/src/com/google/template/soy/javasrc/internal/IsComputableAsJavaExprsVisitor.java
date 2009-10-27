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

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.SoyJavaSrcOptions.CodeStyle;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;


/**
 * Visitor to determine whether the output string for the subtree rooted at a given node is
 * computable as the concatenation of one or more Java expressions. If this is false, it means the
 * generated code for computing the node's output must include one or more full Java statements.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * <p> Important: This class is in {@link ApiCallScope} because it memoizes results that are
 * reusable for the same parse tree. If we change the parse tree between uses of the scoped
 * instance, then the results may not be correct. (In that case, we would need to take this class
 * out of {@code ApiCallScope} and rewrite the code somehow to still take advantage of the
 * memoized results to the extent that they remain correct.)
 *
 * @author Kai Huang
 */
@ApiCallScope
class IsComputableAsJavaExprsVisitor extends AbstractSoyNodeVisitor<Boolean> {


  /** The options for generating Java source code. */
  private final SoyJavaSrcOptions javaSrcOptions;

  /** The memoized results of past visits to nodes. */
  private final Map<SoyNode, Boolean> memoizedResults;

  /** Stack of partial results (during run). */
  private Deque<Boolean> resultStack;


  /**
   * @param javaSrcOptions The options for generating Java source code.
   */
  @Inject
  public IsComputableAsJavaExprsVisitor(SoyJavaSrcOptions javaSrcOptions) {
    this.javaSrcOptions = javaSrcOptions;
    memoizedResults = Maps.newHashMap();
  }


  @Override protected void setup() {
    resultStack = new ArrayDeque<Boolean>();
  }


  @Override protected void visit(SoyNode node) {

    if (memoizedResults.containsKey(node)) {
      resultStack.push(memoizedResults.get(node));

    } else {
      super.visit(node);
      memoizedResults.put(node, resultStack.peek());
    }
  }


  @Override protected Boolean getResult() {
    return resultStack.peek();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(TemplateNode node) {
    resultStack.push(areChildrenComputableAsJavaExprs(node));
  }


  @Override protected void visitInternal(RawTextNode node) {
    resultStack.push(true);
  }


  @Override protected void visitInternal(MsgHtmlTagNode node) {
    resultStack.push(areChildrenComputableAsJavaExprs(node));
  }


  @Override protected void visitInternal(PrintNode node) {
    resultStack.push(true);
  }


  @Override protected void visitInternal(CssNode node) {
    resultStack.push(true);
  }


  @Override protected void visitInternal(IfNode node) {
    // If all children are computable as Java expressions, then this 'if' statement can be written
    // as an expression as well, using the ternary conditional operator ("? :").
    resultStack.push(areChildrenComputableAsJavaExprs(node));
  }


  @Override protected void visitInternal(IfCondNode node) {
    resultStack.push(areChildrenComputableAsJavaExprs(node));
  }


  @Override protected void visitInternal(IfElseNode node) {
    resultStack.push(areChildrenComputableAsJavaExprs(node));
  }


  @Override protected void visitInternal(SwitchNode node) {
    resultStack.push(false);
  }


  @Override protected void visitInternal(ForeachNode node) {
    resultStack.push(false);
  }


  @Override protected void visitInternal(ForNode node) {
    resultStack.push(false);
  }


  @Override protected void visitInternal(CallNode node) {

    resultStack.push(javaSrcOptions.getCodeStyle() == CodeStyle.CONCAT &&
                     areChildrenComputableAsJavaExprs(node));
  }


  @Override protected void visitInternal(CallParamValueNode node) {
    resultStack.push(true);
  }


  @Override protected void visitInternal(CallParamContentNode node) {
    resultStack.push(areChildrenComputableAsJavaExprs(node));
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper to check whether all children of a given parent node satisfy
   * IsComputableAsJavaExprsVisitor.
   * @param node The parent node whose children to check.
   * @return True if all children satisfy IsComputableAsJavaExprsVisitor.
   */
  private boolean areChildrenComputableAsJavaExprs(ParentSoyNode<? extends SoyNode> node) {

    for (SoyNode child : node.getChildren()) {
      // Note: Save time by not visiting RawTextNode and PrintNode children.
      if (!(child instanceof RawTextNode) && !(child instanceof PrintNode)) {
        visit(child);
        boolean childResult = resultStack.pop();
        if (!childResult) {
          return false;
        }
      }
    }

    return true;
  }

}
