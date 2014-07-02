/*
 * Copyright 2014 Google Inc.
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
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;

/**
 * Visitor for checking the visibility of a template.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public class CheckTemplateVisibility extends AbstractSoyNodeVisitor<Void> {

  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;

  /** Save the name of the file and template currently being visited. */
  private String currentFileName;
  private String currentTemplateName;

  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    templateRegistry = new TemplateRegistry(node);
    visitChildren(node);
    templateRegistry = null;
  }

  @Override protected void visitSoyFileNode(SoyFileNode node) {
    currentFileName = node.getSourceLocation().getFileName();
    visitChildren(node);
    currentFileName = null;
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    currentTemplateName = node.getTemplateName();
    visitChildren(node);
    currentTemplateName = null;
  }

  @Override protected void visitCallNode(CallNode node) {
    if (node instanceof CallBasicNode) {
      handleBasicNode((CallBasicNode) node);
    }
    visitChildren(node);
  }

  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  private void handleBasicNode(CallBasicNode node) {
    String calleeName = node.getCalleeName();
    TemplateNode definition = templateRegistry.getBasicTemplate(calleeName);
    if (definition != null && !isVisible(definition)) {
      throw SoySyntaxException.createWithMetaInfo(
          calleeName
          + " [visibility=\""
          + definition.getVisibility().getAttributeValue()
          + "\"] not visible from here",
          node.getSourceLocation(),
          null /* srcLoc and filePath can't both be nonnull */,
          currentTemplateName);
    }
  }

  private boolean isVisible(TemplateNode definition) {
    // The only visibility level that this pass currently cares about is PRIVATE.
    if (definition.getVisibility() != Visibility.PRIVATE) {
      return true;
    }
    // Templates marked visibility="private" are only visible to other templates in the same file.
    return currentFileName.equals(definition.getSourceLocation().getFileName());
  }
}

