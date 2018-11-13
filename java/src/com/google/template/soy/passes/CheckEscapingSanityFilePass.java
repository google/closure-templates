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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.TemplateNode;

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
final class CheckEscapingSanityFilePass extends CompilerFilePass {

  private static final SoyErrorKind ILLEGAL_PRINT_DIRECTIVE =
      SoyErrorKind.of("{0} can only be used internally by the Soy compiler.");

  private static final SoyErrorKind RENDER_UNIT_WITHOUT_KIND =
      SoyErrorKind.of(
          "In strict templates, '{'{0}'}'...'{'/{0}'}' blocks require an explicit kind=\"\".");

  private final Visitor visitor;

  CheckEscapingSanityFilePass(ErrorReporter errorReporter) {
    this.visitor = new Visitor(errorReporter);
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    visitor.exec(file);
  }

  private static final class Visitor extends AbstractSoyNodeVisitor<Void> {
    /** Current escaping mode. */
    AutoescapeMode autoescapeMode;

    final ErrorReporter errorReporter;

    Visitor(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
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
