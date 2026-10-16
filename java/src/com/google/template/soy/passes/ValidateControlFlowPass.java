/*
 * Copyright 2026 Google Inc.
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
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.BreakNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.ContinueNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.WhileNode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates control flow statements:
 *
 * <ul>
 *   <li>'{break}' and '{continue}' must be inside an enclosing loop.
 *   <li>Labeled break/continue must target an existing enclosing loop with that label.
 *   <li>Loops cannot have duplicate labels if nested within a loop with the same label.
 * </ul>
 */
final class ValidateControlFlowPass implements CompilerFilePass {

  static final SoyErrorKind BREAK_OUTSIDE_LOOP =
      SoyErrorKind.of(
          "'{'break'}' or '{'break <label>'}' must be inside a loop.",
          Impression.ERROR_VALIDATE_CONTROL_FLOW_PASS_BREAK_OUTSIDE_LOOP);

  static final SoyErrorKind CONTINUE_OUTSIDE_LOOP =
      SoyErrorKind.of(
          "'{'continue'}' or '{'continue <label>'}' must be inside a loop.",
          Impression.ERROR_VALIDATE_CONTROL_FLOW_PASS_CONTINUE_OUTSIDE_LOOP);

  static final SoyErrorKind UNKNOWN_LOOP_LABEL =
      SoyErrorKind.of(
          "Undefined loop label ''{0}''. No enclosing loop found with this label.",
          Impression.ERROR_VALIDATE_CONTROL_FLOW_PASS_UNKNOWN_LOOP_LABEL);

  static final SoyErrorKind DUPLICATE_LOOP_LABEL =
      SoyErrorKind.of(
          "Duplicate loop label ''{0}''. An enclosing loop already has this label.",
          Impression.ERROR_VALIDATE_CONTROL_FLOW_PASS_DUPLICATE_LOOP_LABEL);

  private final ErrorReporter errorReporter;

  ValidateControlFlowPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new Visitor(errorReporter).exec(file);
  }

  private static final class Visitor extends AbstractSoyNodeVisitor<Void> {
    private final ErrorReporter errorReporter;
    private final Deque<LoopNode> loopStack = new ArrayDeque<>();
    private final Set<String> activeLabels = new HashSet<>();

    Visitor(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      loopStack.clear();
      activeLabels.clear();
      visitChildren(node);
      loopStack.clear();
      activeLabels.clear();
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      Deque<LoopNode> savedStack = new ArrayDeque<>(loopStack);
      Set<String> savedLabels = new HashSet<>(activeLabels);
      loopStack.clear();
      activeLabels.clear();
      visitChildren(node);
      loopStack.clear();
      loopStack.addAll(savedStack);
      activeLabels.clear();
      activeLabels.addAll(savedLabels);
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      Deque<LoopNode> savedStack = new ArrayDeque<>(loopStack);
      Set<String> savedLabels = new HashSet<>(activeLabels);
      loopStack.clear();
      activeLabels.clear();
      visitChildren(node);
      loopStack.clear();
      loopStack.addAll(savedStack);
      activeLabels.clear();
      activeLabels.addAll(savedLabels);
    }

    @Override
    protected void visitForNode(ForNode node) {
      visitLoopNode(node);
    }

    @Override
    protected void visitWhileNode(WhileNode node) {
      visitLoopNode(node);
    }

    private void visitLoopNode(LoopNode loopNode) {
      Identifier label = loopNode.getLabel();
      boolean addedLabel = false;
      if (label != null) {
        if (!activeLabels.add(label.identifier())) {
          errorReporter.report(label.location(), DUPLICATE_LOOP_LABEL, label.identifier());
        } else {
          addedLabel = true;
        }
      }
      loopStack.push(loopNode);
      try {
        visitChildren((SoyNode.ParentSoyNode<?>) loopNode);
      } finally {
        loopStack.pop();
        if (addedLabel) {
          activeLabels.remove(label.identifier());
        }
      }
    }

    @Override
    protected void visitBreakNode(BreakNode node) {
      if (loopStack.isEmpty()) {
        errorReporter.report(node.getSourceLocation(), BREAK_OUTSIDE_LOOP);
        return;
      }
      Identifier label = node.getLabel();
      if (label != null) {
        validateLabel(label);
      }
    }

    @Override
    protected void visitContinueNode(ContinueNode node) {
      if (loopStack.isEmpty()) {
        errorReporter.report(node.getSourceLocation(), CONTINUE_OUTSIDE_LOOP);
        return;
      }
      Identifier label = node.getLabel();
      if (label != null) {
        validateLabel(label);
      }
    }

    private void validateLabel(Identifier label) {
      for (LoopNode loop : loopStack) {
        if (loop.getLabel() != null && loop.getLabel().identifier().equals(label.identifier())) {
          return;
        }
      }
      errorReporter.report(label.location(), UNKNOWN_LOOP_LABEL, label.identifier());
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof SoyNode.ParentSoyNode) {
        visitChildren((SoyNode.ParentSoyNode<?>) node);
      }
    }
  }
}
