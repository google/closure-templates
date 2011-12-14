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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.CommandNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SyntaxVersion;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.TemplateNode;


/**
 * Visitor for asserting that all the nodes in a parse tree or subtree conform to Soy V2 syntax.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} may be called on any node. There is no return value. However, a
 * {@code SoySyntaxException} is thrown if the given node or a descendent is not in Soy V2 syntax.
 *
 */
public class AssertSyntaxVersionV2Visitor extends AbstractSoyNodeVisitor<Void> {


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If the given node or a descendent is not in Soy V2 syntax.
   */
  @Override protected void visitPrintDirectiveNode(PrintDirectiveNode node) {
    // Temporarily allow deprecated directives.
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If the given node or a descendent is not in Soy V2 syntax.
   */
  @Override protected void visitSoyNode(SoyNode node) {

    if (node.getSyntaxVersion() == SyntaxVersion.V1) {

      // Specific error message for missing SoyDoc.
      if (node instanceof TemplateNode && ((TemplateNode) node).getSoyDocParams() == null) {
        throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
            "Not all code is in Soy V2 syntax (missing SoyDoc for template " +
            ((TemplateNode) node).getTagString() + ").", null, node);
      }

      // General error message.
      String nodeStringForErrorMsg =
          (node instanceof CommandNode) ? "tag " + ((CommandNode) node).getTagString() :
          (node instanceof SoyFileNode) ? "file " + ((SoyFileNode) node).getFileName():
          "node " + node.toString();
      throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
          "Not all code is in Soy V2 syntax (found " + nodeStringForErrorMsg +
          " not in Soy V2 syntax).", null, node);
    }

    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
