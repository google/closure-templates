/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.common.collect.Iterables;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateDivision;

import java.util.Set;

/**
 * Visitor performing escaping sanity checks over all input -- not just input affected by the
 * contextual autoescaping inference engine.
 *
 * Checks that typed {@code {param}} and {@code {let}} nodes only appear in contextually
 * autoescaped templates.
 *
 * Checks that internal-only directives such as {@code |text} are not used.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} should be called on a full parse tree.
 *
 */
final class CheckEscapingSanityVisitor extends AbstractSoyNodeVisitor<Void> {
  /** Current escaping mode. */
  private AutoescapeMode autoescapeMode;

  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;
  private final ErrorReporter errorReporter;

  public CheckEscapingSanityVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    // Build templateRegistry.
    templateRegistry = new TemplateRegistry(node, errorReporter);
    visitChildren(node);
    templateRegistry = null;
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    autoescapeMode = node.getAutoescapeMode();
    visitChildren(node);
  }

  @Override protected void visitPrintDirectiveNode(PrintDirectiveNode node) {
    EscapingMode escapingMode = EscapingMode.fromDirective(node.getName());
    if (escapingMode != null && escapingMode.isInternalOnly) {
       throw SoyAutoescapeException.createWithNode(
           "Print directive " + node.getName() + " is only for internal use by the Soy compiler.",
           node);
    }
  }

  @Override protected void visitLetContentNode(LetContentNode node) {
    visitRenderUnitNode(node, "let", "{let $x: $y /}");
  }

  @Override protected void visitCallBasicNode(CallBasicNode node) {
    if (autoescapeMode == AutoescapeMode.NONCONTEXTUAL) {
      TemplateNode callee = templateRegistry.getBasicTemplate((node).getCalleeName());
      // It's possible that the callee template is in another file, and Soy is being used to compile
      // one file at a time without context (not recommended, but supported). In this case callee
      // will be null.
      if (callee != null && callee.getContentKind() == SanitizedContent.ContentKind.TEXT) {
        throw SoyAutoescapeException.createWithNode(
            "Calls to strict templates with 'kind=\"text\"' attribute is not permitted in "
            + "non-contextually autoescaped templates: " + node.toSourceString(),
            node);
      }
    }
    visitChildren(node);
  }

  @Override protected void visitCallDelegateNode(CallDelegateNode node) {
    if (autoescapeMode == AutoescapeMode.NONCONTEXTUAL) {
      TemplateNode callee;
      Set<DelegateTemplateDivision> divisions =
          templateRegistry.getDelTemplateDivisionsForAllVariants((node).getDelCalleeName());
      if (divisions != null && !divisions.isEmpty()) {
        // As the callee is required only to know the kind of the content and as all templates in
        // delPackage are of the same kind it is sufficient to choose only the first template.
        DelegateTemplateDivision division = Iterables.getFirst(divisions, null);
        callee = Iterables.get(
            division.delPackageNameToDelTemplateMap.values(), 0);
        if (callee.getContentKind() == SanitizedContent.ContentKind.TEXT) {
          throw SoyAutoescapeException.createWithNode(
              "Calls to strict templates with 'kind=\"text\"' attribute is not permitted in "
              + "non-contextually autoescaped templates: " + node.toSourceString(),
              node);
        }
      }
    }
    visitChildren(node);
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    visitRenderUnitNode(node, "param", "{param x: $y /}");
  }

  private void visitRenderUnitNode(
      RenderUnitNode node, String nodeName, String selfClosingExample) {
    final AutoescapeMode oldMode = autoescapeMode;
    if (node.getContentKind() != null) {
      if (autoescapeMode == AutoescapeMode.NOAUTOESCAPE) {
        throw SoyAutoescapeException.createWithNode(
            "{" + nodeName + "} node with 'kind' attribute is not permitted in non-autoescaped "
            + "templates: " + node.toSourceString(),
            node);
      }
      // Temporarily enter strict mode.
      autoescapeMode = AutoescapeMode.STRICT;
    } else if (autoescapeMode == AutoescapeMode.STRICT) {
      throw SoyAutoescapeException.createWithNode(
          "In strict templates, {" + nodeName + "}...{/" + nodeName + "} blocks require an "
           + "explicit kind=\"<type>\". This restriction will be lifted soon once a reasonable "
           + "default is chosen. (Note that " + selfClosingExample + " is NOT subject to this "
           + "restriction). Cause: " + node.getTagString(),
          node);
    }
    visitChildren(node);
    // Pop out of strict mode if we entered it just for this unit.
    autoescapeMode = oldMode;
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
