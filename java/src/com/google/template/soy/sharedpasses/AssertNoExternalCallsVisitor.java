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

package com.google.template.soy.sharedpasses;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;


/**
 * Visitor to check that there are no external calls. Used by backends that disallow external calls,
 * such as the Tofu (JavaObj) backend.
 *
 * <p> {@link #exec} should be called on a {@code SoyFileSetNode} or a {@code SoyFileNode}. There is
 * no return value. A {@code SoySyntaxException} is thrown if an error is found.
 *
 * @author Kai Huang
 */
public class AssertNoExternalCallsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;


  @Override public Void exec(SoyNode soyNode) {

    Preconditions.checkArgument(
        soyNode instanceof SoyFileSetNode || soyNode instanceof SoyFileNode);

    templateRegistry = new TemplateRegistry(soyNode.getNearestAncestor(SoyFileSetNode.class));

    return super.exec(soyNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitCallBasicNode(CallBasicNode node) {

    if (templateRegistry.getBasicTemplate(node.getCalleeName()) == null) {
      String currFilePath = node.getNearestAncestor(SoyFileNode.class).getFilePath();
      String currTemplateNameForErrorMsg =
          node.getNearestAncestor(TemplateNode.class).getTemplateNameForUserMsgs();
      throw new SoySyntaxException(
          "In Soy file " + currFilePath + ", template " + currTemplateNameForErrorMsg +
          ": Encountered call to undefined template '" + node.getCalleeName() + "'.");
    }

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
