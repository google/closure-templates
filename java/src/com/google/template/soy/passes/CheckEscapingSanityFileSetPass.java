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

package com.google.template.soy.passes;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.SanitizedContentKind;
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
import com.google.template.soy.soytree.SoyNode.Kind;
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
final class CheckEscapingSanityFileSetPass extends CompilerFileSetPass {

  private static final SoyErrorKind ILLEGAL_PRINT_DIRECTIVE =
      SoyErrorKind.of("{0} can only be used internally by the Soy compiler.");

  private static final SoyErrorKind RENDER_UNIT_WITHOUT_KIND =
      SoyErrorKind.of(
          "In strict templates, '{'{0}'}'...'{'/{0}'}' blocks "
              + "require an explicit kind=\"<html|css|text|attributes>\".");

  private static final SoyErrorKind STRICT_TEXT_CALL_FROM_NONCONTEXTUAL_TEMPLATE =
      SoyErrorKind.of(
          "Calls to strict templates with ''kind=\"text\"'' are not allowed "
              + "in non-contextually autoescaped templates.");


  private final ErrorReporter errorReporter;

  CheckEscapingSanityFileSetPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileSetNode fileSet, TemplateRegistry registry) {
    new Visitor(errorReporter, registry).exec(fileSet);
  }

  private static final class Visitor extends AbstractSoyNodeVisitor<Void> {
    /** Current escaping mode. */
    AutoescapeMode autoescapeMode;

    final TemplateRegistry templateRegistry;
    final ErrorReporter errorReporter;

    Visitor(ErrorReporter errorReporter, TemplateRegistry templateRegistry) {
      this.errorReporter = errorReporter;
      this.templateRegistry = templateRegistry;
    }
    // --------------------------------------------------------------------------------------------
    // Implementations for specific nodes.

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
      visitRenderUnitNode(node);
    }

    @Override
    protected void visitCallBasicNode(CallBasicNode node) {
      if (autoescapeMode == AutoescapeMode.NONCONTEXTUAL) {
        TemplateNode callee = templateRegistry.getBasicTemplate((node).getCalleeName());
        // It's possible that the callee template is in another file, and Soy is being used to
        // compile one file at a time without context (not recommended, but supported). In this case
        // callee will be null.
        if (callee != null && callee.getContentKind() == SanitizedContentKind.TEXT) {
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
          if (callee.getContentKind() == SanitizedContentKind.TEXT) {
            errorReporter.report(
                node.getSourceLocation(), STRICT_TEXT_CALL_FROM_NONCONTEXTUAL_TEMPLATE);
          }
        }
      }
      visitChildren(node);
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      visitRenderUnitNode(node);
    }

    private void visitRenderUnitNode(RenderUnitNode node) {
      final AutoescapeMode oldMode = autoescapeMode;
      if (node.getContentKind() != null) {
        // Temporarily enter strict mode.
        autoescapeMode = AutoescapeMode.STRICT;
      } else if (autoescapeMode == AutoescapeMode.STRICT) {
        errorReporter.report(
            node.getSourceLocation(),
            RENDER_UNIT_WITHOUT_KIND,
            node.getKind() == Kind.LET_CONTENT_NODE ? "let" : "param");
      }
      visitChildren(node);
      // Pop out of strict mode if we entered it just for this unit.
      autoescapeMode = oldMode;
    }

    // --------------------------------------------------------------------------------------------
    // Fallback implementation.

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
