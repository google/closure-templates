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

import com.google.common.base.CaseFormat;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateNode;

/**
 * Visitor class which converts package-relative CSS class names to absolute names.
 */
public class ResolvePackageRelativeCssNamesVisitor extends AbstractSoyNodeVisitor<Void> {

  private String packagePrefix = null;

  @Override protected void visitTemplateNode(TemplateNode node) {
    // Compute the CSS package prefix for this template. The search order is:
    // 1) cssbase on the template
    // 2) cssbase on the namespace
    // 3) first requirecss on the namespace
    packagePrefix = null;
    if (node.getCssBaseNamespace() != null) {
      packagePrefix = toCamelCase(node.getCssBaseNamespace());
    } else if (node.getParent().getCssBaseNamespace() != null) {
      packagePrefix = toCamelCase(node.getParent().getCssBaseNamespace());
    } else if (node.getParent().getRequiredCssNamespaces().size() > 0) {
      packagePrefix = toCamelCase(node.getParent().getRequiredCssNamespaces().get(0));
    }

    super.visitTemplateNode(node);
  }

  @Override protected void visitCssNode(CssNode node) {
    // Determine if this is a package-relative selector, do nothing if it's not.
    String selectorText = node.getSelectorText();
    if (!selectorText.startsWith("%")) {
      return;
    }

    // Don't apply renaming to nodes with a component name.
    if (node.getComponentNameExpr() != null) {
      throw SoySyntaxExceptionUtils.createWithNode(
          "Package-relative class name '" + selectorText +
          "' cannot be used with a component expression", node);
    }

    if (packagePrefix == null) {
      throw SoySyntaxExceptionUtils.createWithNode(
          "No CSS package defined for package-relative class name '" + selectorText + "'", node);
    }

    // Remove this CssNode. Save the index because we'll need it for inserting the new nodes.
    BlockNode parent = node.getParent();
    int indexInParent = parent.getChildIndex(node);
    parent.removeChild(indexInParent);

    CssNode newNode = new CssNode(node, packagePrefix + selectorText.substring(1));
    parent.addChild(indexInParent, newNode);
  }

  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }

  private static String toCamelCase(String packageName) {
    String packageNameWithDashes = packageName.replace('.', '-');
    return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, packageNameWithDashes);
  }
}
