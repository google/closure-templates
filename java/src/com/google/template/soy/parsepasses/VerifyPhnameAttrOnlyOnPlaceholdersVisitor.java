/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialContentNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;


/**
 * Visitor to verify that all occurrences of the 'phname' attribute are on message placeholders.
 *
 * <p> Note: Doesn't check HTML tags since we don't parse HTML tags outside of messages anyway. Only
 * checks PrintNode and CallNode.
 *
 */
public class VerifyPhnameAttrOnlyOnPlaceholdersVisitor extends AbstractSoyNodeVisitor<Void> {


  @Override protected void visitPrintNode(PrintNode node) {
    visitMsgPlaceholderInitialContentNodeHelper(node);
  }


  @Override protected void visitCallNode(CallNode node) {
    visitMsgPlaceholderInitialContentNodeHelper(node);
    visitChildren(node);
  }


  private void visitMsgPlaceholderInitialContentNodeHelper(MsgPlaceholderInitialContentNode node) {
    if (node.getUserSuppliedPlaceholderName() != null &&
        ! (node.getParent() instanceof MsgPlaceholderNode)) {
      throw new SoySyntaxException(
          "Found 'phname' attribute not on a msg placeholder (tag " + node.toSourceString() + ").");
    }
  }


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
