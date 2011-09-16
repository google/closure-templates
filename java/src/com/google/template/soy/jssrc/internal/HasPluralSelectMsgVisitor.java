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

package com.google.template.soy.jssrc.internal;

import com.google.common.base.Preconditions;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;


/**
 * Visitor for checking whether any template in a file has a plural/select message.
 *
 * <p> {@link #exec} should be called on a {@code SoyFileNode}. It returns whether the file
 * has at least one template with a plural/select message.
 *
 * @author Umesh Nair
 *
 */
public class HasPluralSelectMsgVisitor extends AbstractSoyNodeVisitor<Boolean> {


  /** Indicates whether a file has at least one template with a plural/select message. */
  private boolean hasPluralSelectMsg;


  @Override public Boolean exec(SoyNode soyNode) {
    Preconditions.checkArgument(soyNode instanceof SoyFileNode);

    hasPluralSelectMsg = false;
    visit(soyNode);
    return hasPluralSelectMsg;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitMsgPluralNode(MsgPluralNode node) {
    hasPluralSelectMsg = true;
  }


  @Override protected void visitMsgSelectNode(MsgSelectNode node) {
    hasPluralSelectMsg = true;
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ParentSoyNode<?>) {
      for (SoyNode child : ((ParentSoyNode<?>) node).getChildren()) {
        // If the file already had plural/select messages, there is no need to continue.
        if (hasPluralSelectMsg) {
          return;
        }
        visit(child);
      }
    }
  }

}
