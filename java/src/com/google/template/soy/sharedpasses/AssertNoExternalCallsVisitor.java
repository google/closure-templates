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
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;

// TODO(gboyer): Consider renaming to StrictDepsVisitor.
/**
 * Visitor to check that there are no external calls. Used by backends that disallow external calls,
 * such as the Tofu (JavaObj) backend.
 *
 * <p> {@link #exec} should be called on a {@code SoyFileSetNode} or a {@code SoyFileNode}. There is
 * no return value. A {@code SoySyntaxException} is thrown if an error is found.
 *
 */
public class AssertNoExternalCallsVisitor extends AbstractSoyNodeVisitor<Void> {

  /** Log of all found errors. */
  private StringBuilder errorBuffer;

  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;


  @Override public Void exec(SoyNode soyNode) {

    Preconditions.checkArgument(
        soyNode instanceof SoyFileSetNode || soyNode instanceof SoyFileNode);

    errorBuffer = new StringBuilder();
    templateRegistry = new TemplateRegistry(soyNode.getNearestAncestor(SoyFileSetNode.class));

    super.exec(soyNode);

    if (errorBuffer.length() != 0) {
      throw SoySyntaxException.createWithoutMetaInfo(errorBuffer.toString());
    }

    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  // TODO(gboyer): Consider some deltemplate checking, but it's hard to make a coherent case for
  // deltemplates since it's legitimate to have zero implementations, or to have the implementation
  // in a different part of the dependency graph (if it's late-bound).
  @Override protected void visitCallBasicNode(CallBasicNode node) {
    TemplateNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());

    if (callee == null) {
      addError(node,
          "Encountered call to undefined template '" + node.getCalleeName() + "'.");
    } else {
      SoyFileKind callerKind = node.getNearestAncestor(SoyFileNode.class).getSoyFileKind();
      SoyFileKind calleeKind = callee.getParent().getSoyFileKind();
      if (calleeKind == SoyFileKind.INDIRECT_DEP && callerKind == SoyFileKind.SRC) {
        addError(node,
            "Call to '" + callee.getTemplateNameForUserMsgs()
            + "' is satisfied only by indirect dependency "
            + callee.getSourceLocation().getFilePath()
            + ". Add it as a direct dependency, instead.");
      }

      // Double check if a dep calls a source. We shouldn't usually see this since the dependency
      // should fail due to unknown template, but it doesn't hurt to add this.
      if (calleeKind == SoyFileKind.SRC && callerKind != SoyFileKind.SRC) {
        addError(node,
            "Illegal call to '" + callee.getTemplateNameForUserMsgs()
            + "', because according to the dependency graph, "
            + callee.getSourceLocation().getFilePath() + " depends on "
            + node.getSourceLocation().getFilePath() + ", not the other way around.");
      }
    }

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);
  }

  private void addError(CallBasicNode node, String errorStr) {
    TemplateNode containingTemplateNode = node.getNearestAncestor(TemplateNode.class);

    String fullError = node.getSourceLocation() + ", template "
        + containingTemplateNode.getTemplateNameForUserMsgs() + ": " + errorStr + "\n";
    errorBuffer.append(fullError);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
