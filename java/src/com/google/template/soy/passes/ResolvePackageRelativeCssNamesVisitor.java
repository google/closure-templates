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

package com.google.template.soy.passes;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Iterables;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;

/** Converts package-relative CSS class names to absolute names. */
final class ResolvePackageRelativeCssNamesVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final String RELATIVE_SELECTOR_PREFIX = "%";

  private static final SoyErrorKind PACKAGE_RELATIVE_CLASS_NAME_USED_WITH_COMPONENT_NAME =
      SoyErrorKind.of(
          "Package-relative class name ''{0}'' cannot be used with component expression.");
  private static final SoyErrorKind NO_CSS_PACKAGE =
      SoyErrorKind.of("No CSS package defined for package-relative class name ''{0}''.");

  private final ErrorReporter errorReporter;
  private String packagePrefix = null;

  ResolvePackageRelativeCssNamesVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    // Compute the CSS package prefix for this template. The search order is:
    // 1) cssbase on the template
    // 2) cssbase on the namespace
    // 3) first requirecss on the namespace
    if (node.getCssBaseNamespace() != null) {
      packagePrefix = toCamelCase(node.getCssBaseNamespace());
    } else if (node.getParent().getCssBaseNamespace() != null) {
      packagePrefix = toCamelCase(node.getParent().getCssBaseNamespace());
    } else if (!node.getParent().getRequiredCssNamespaces().isEmpty()) {
      packagePrefix = toCamelCase(node.getParent().getRequiredCssNamespaces().get(0));
    }

    // TODO(user): remove.
    List<CssNode> cssNodes = SoyTreeUtils.getAllNodesOfType(node, CssNode.class);
    for (CssNode css : cssNodes) {
      resolveSelector(css);
    }

    List<FunctionNode> fnNodes = SoyTreeUtils.getAllNodesOfType(node, FunctionNode.class);
    for (FunctionNode fn : fnNodes) {
      resolveSelector(fn);
    }
  }

  private void resolveSelector(CssNode node) {
    // Determine if this is a package-relative selector, do nothing if it's not.
    String selectorText = node.getSelectorText();
    if (!selectorText.startsWith(RELATIVE_SELECTOR_PREFIX)) {
      return;
    }

    if (packagePrefix == null) {
      errorReporter.report(node.getSourceLocation(), NO_CSS_PACKAGE, selectorText);
    }

    // Replace this CssNode with a new node with the resolved selector text.
    String prefixed = packagePrefix + selectorText.substring(RELATIVE_SELECTOR_PREFIX.length());
    CssNode newNode = new CssNode(node, prefixed, new CopyState());
    ParentSoyNode<StandaloneNode> parent = node.getParent();
    parent.replaceChild(node, newNode);
  }

  private void resolveSelector(FunctionNode node) {
    if (node.getSoyFunction() != BuiltinFunction.CSS) {
      return;
    }

    ExprNode lastChild = Iterables.getLast(node.getChildren());
    if (!(lastChild instanceof StringNode)) {
      // this will generate an error in CheckFunctionCallsVisitor
      return;
    }

    StringNode selector = (StringNode) Iterables.getLast(node.getChildren());
    String selectorText = selector.getValue();
    if (!selectorText.startsWith(RELATIVE_SELECTOR_PREFIX)) {
      return;
    }

    if (node.numChildren() > 1) {
      errorReporter.report(
          selector.getSourceLocation(),
          PACKAGE_RELATIVE_CLASS_NAME_USED_WITH_COMPONENT_NAME,
          selectorText);
    }

    if (packagePrefix == null) {
      errorReporter.report(selector.getSourceLocation(), NO_CSS_PACKAGE, selectorText);
    }

    // Replace the selector text with resolved selector text
    String prefixed = packagePrefix + selectorText.substring(RELATIVE_SELECTOR_PREFIX.length());
    StringNode newSelector = new StringNode(prefixed, selector.getSourceLocation());
    node.replaceChild(selector, newSelector);
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }

  private static String toCamelCase(String packageName) {
    String packageNameWithDashes = packageName.replace('.', '-');
    return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, packageNameWithDashes);
  }
}
