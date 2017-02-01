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

import static com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper.AUTOESCAPE_ERROR_PREFIX;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;

/**
 * Visitor performing escaping sanity checks over all input -- not just input affected by the
 * contextual autoescaping inference engine.
 *
 * <p>Checks that typed {@code {param}} and {@code {let}} nodes only appear in contextually
 * autoescaped templates.
 *
 * <p>Checks that internal-only directives such as {@code |text} are not used.
 *
 * <p>{@link #exec} should be called on a full parse tree.
 *
 */
final class CheckEscapingSanityVisitor extends AbstractSoyNodeVisitor<Void> {

  // TODO(user): AUTOESCAPE_ERROR_PREFIX (and the following hyphen-space)
  // is just used by ContextualAutoescaperTest to parse and throw errors. Remove them.
  private static final SoyErrorKind ILLEGAL_PRINT_DIRECTIVE =
      SoyErrorKind.of(
          AUTOESCAPE_ERROR_PREFIX + "- {0} can only be used internally by the Soy compiler.");
  private static final SoyErrorKind LET_WITHOUT_KIND =
      SoyErrorKind.of(
          AUTOESCAPE_ERROR_PREFIX
              + "- In strict templates, '{'let'}'...'{'/let'}' blocks "
              + "require an explicit kind=\"<html|css|text|attributes>\".");
  private static final SoyErrorKind PARAM_WITHOUT_KIND =
      SoyErrorKind.of(
          AUTOESCAPE_ERROR_PREFIX
              + "- In strict templates, '{'param'}'...'{'/param'}' blocks "
              + "require an explicit kind=\"<html|css|text|attributes>\".");
  private static final SoyErrorKind STRICT_TEXT_CALL_FROM_NONCONTEXTUAL_TEMPLATE =
      SoyErrorKind.of(
          AUTOESCAPE_ERROR_PREFIX
              + "- Calls to strict templates with ''kind=\"text\"'' are not allowed "
              + "in non-contextually autoescaped templates.");

  /** Current escaping mode. */
  private AutoescapeMode autoescapeMode;

  private final TemplateRegistry templateRegistry;
  private final ErrorReporter errorReporter;

  CheckEscapingSanityVisitor(TemplateRegistry templateRegistry, ErrorReporter errorReporter) {
    this.templateRegistry = templateRegistry;
    this.errorReporter = errorReporter;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    visitChildren(node);
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    autoescapeMode = node.getAutoescapeMode();
    visitChildren(node);
  }

  @Override
  protected void visitPrintDirectiveNode(PrintDirectiveNode node) {
    EscapingMode escapingMode = EscapingMode.fromDirective(node.getName());
    if (escapingMode != null && escapingMode.isInternalOnly) {
      errorReporter.report(node.getSourceLocation(), ILLEGAL_PRINT_DIRECTIVE, node.getName());
    }
  }

  @Override
  protected void visitLetContentNode(LetContentNode node) {
    visitRenderUnitNode(node, LET_WITHOUT_KIND);
  }

  @Override
  protected void visitCallBasicNode(CallBasicNode node) {
    if (autoescapeMode == AutoescapeMode.NONCONTEXTUAL) {
      TemplateNode callee = templateRegistry.getBasicTemplate((node).getCalleeName());
      // It's possible that the callee template is in another file, and Soy is being used to compile
      // one file at a time without context (not recommended, but supported). In this case callee
      // will be null.
      if (callee != null && callee.getContentKind() == SanitizedContent.ContentKind.TEXT) {
        errorReporter.report(
            node.getSourceLocation(), STRICT_TEXT_CALL_FROM_NONCONTEXTUAL_TEMPLATE);
      }
    }
    visitChildren(node);
  }

  @Override
  protected void visitCallDelegateNode(CallDelegateNode node) {
    if (autoescapeMode == AutoescapeMode.NONCONTEXTUAL) {
      ImmutableList<TemplateDelegateNode> divisions =
          templateRegistry
              .getDelTemplateSelector()
              .delTemplateNameToValues()
              .get(node.getDelCalleeName());
      if (!divisions.isEmpty()) {
        // As the callee is required only to know the kind of the content and as all templates in
        // delPackage are of the same kind it is sufficient to choose only the first template.
        TemplateNode callee = divisions.get(0);
        if (callee.getContentKind() == SanitizedContent.ContentKind.TEXT) {
          errorReporter.report(
              node.getSourceLocation(), STRICT_TEXT_CALL_FROM_NONCONTEXTUAL_TEMPLATE);
        }
      }
    }
    visitChildren(node);
  }

  @Override
  protected void visitCallParamContentNode(CallParamContentNode node) {
    visitRenderUnitNode(node, PARAM_WITHOUT_KIND);
  }

  private void visitRenderUnitNode(RenderUnitNode node, SoyErrorKind errorKind) {
    final AutoescapeMode oldMode = autoescapeMode;
    if (node.getContentKind() != null) {
      // Temporarily enter strict mode.
      autoescapeMode = AutoescapeMode.STRICT;
    } else if (autoescapeMode == AutoescapeMode.STRICT) {
      errorReporter.report(node.getSourceLocation(), errorKind);
    }
    visitChildren(node);
    // Pop out of strict mode if we entered it just for this unit.
    autoescapeMode = oldMode;
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
