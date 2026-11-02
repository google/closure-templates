/*
 * Copyright 2025 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AssignmentNode;
import com.google.template.soy.soytree.AutoImplNode;
import com.google.template.soy.soytree.BreakNode;
import com.google.template.soy.soytree.ContinueNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.ReturnNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.CommandNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.WhileNode;
import com.google.template.soy.types.SoyType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RunAfter(ResolveExpressionTypesPass.class)
class ValidateAutoJavaExternPass implements CompilerFilePass {

  private static final SoyErrorKind BAD_RETURN_TYPE =
      SoyErrorKind.of(
          "Provided type ''{0}'' is not assignable to declared type ''{1}''.",
          Impression.ERROR_VALIDATE_AUTO_JAVA_EXTERN_PASS_BAD_RETURN_TYPE);

  private static final SoyErrorKind DEAD_CODE =
      SoyErrorKind.of(
          "Unreachable code.", Impression.ERROR_VALIDATE_AUTO_JAVA_EXTERN_PASS_DEAD_CODE);

  private static final SoyErrorKind BAD_ASSIGNMENT =
      SoyErrorKind.of(
          "Assignment is only allowed on params or lets.",
          Impression.ERROR_VALIDATE_AUTO_JAVA_EXTERN_PASS_BAD_ASSIGNMENT);

  private static final SoyErrorKind MISSING_RETURN =
      SoyErrorKind.of(
          "Function lacks ending return statement.",
          Impression.ERROR_VALIDATE_AUTO_JAVA_EXTERN_PASS_MISSING_RETURN);

  private static final SoyErrorKind JUMP_OUTSIDE_LOOP =
      SoyErrorKind.of(
          "'{'{0}'}' is only allowed inside a loop.",
          Impression.ERROR_VALIDATE_AUTO_JAVA_EXTERN_PASS_JUMP_OUTSIDE_LOOP);

  private final ErrorReporter errorReporter;

  public ValidateAutoJavaExternPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    file.getExterns().stream()
        .map(ExternNode::getAutoImpl)
        .flatMap(Optional::stream)
        .forEach(this::process);
  }

  private void process(AutoImplNode impl) {
    new ReturnTypeChecker(impl.getParent().getType().getReturnType()).exec(impl);
    boolean returns = new FlowChecker().exec(impl);
    if (!returns) {
      errorReporter.report(impl.getOpenTagLocation(), MISSING_RETURN);
    }
    new AssignmentChecker().exec(impl);
  }

  private final class ReturnTypeChecker extends AbstractSoyNodeVisitor<Void> {

    private final SoyType returnType;

    public ReturnTypeChecker(SoyType returnType) {
      this.returnType = Preconditions.checkNotNull(returnType);
    }

    @Override
    protected void visitReturnNode(ReturnNode node) {
      SoyType type = node.getExpr().getType();
      // If type is null then an error was already thrown in ResolveExpressionTypesPass.
      if (type != null && !returnType.isAssignableFromStrict(type)) {
        errorReporter.report(node.getExpr().getSourceLocation(), BAD_RETURN_TYPE, type, returnType);
      }
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?> parentSoyNode) {
        visitChildren(parentSoyNode);
      }
    }
  }

  /** Records whether a loop is left by a {@code {break}}. */
  private static final class Loop {
    boolean hasBreak;
  }

  /**
   * Determines whether a node completes abruptly, that is whether every path through it ends in a
   * {@code {return}}, {@code {break}} or {@code {continue}}, or loops forever. This is the notion
   * of a terminal statement that the bytecode compiler relies on. Neither {@code {break}} nor
   * {@code {continue}} can leave an {@code {autoimpl}}, so its body completes abruptly only if
   * every path through it returns. Statements that follow one that completes abruptly are
   * unreachable and reported as dead code.
   */
  private final class FlowChecker extends AbstractReturningSoyNodeVisitor<Boolean> {

    /** The loops that enclose the node being visited, innermost first. */
    private final Deque<Loop> loops = new ArrayDeque<>();

    @Override
    protected Boolean visitReturnNode(ReturnNode node) {
      return true;
    }

    @Override
    protected Boolean visitBreakNode(BreakNode node) {
      if (loops.isEmpty()) {
        return reportJumpOutsideLoop(node);
      }
      loops.peek().hasBreak = true;
      return true;
    }

    @Override
    protected Boolean visitContinueNode(ContinueNode node) {
      if (loops.isEmpty()) {
        return reportJumpOutsideLoop(node);
      }
      return true;
    }

    private Boolean reportJumpOutsideLoop(CommandNode node) {
      errorReporter.report(node.getSourceLocation(), JUMP_OUTSIDE_LOOP, node.getCommandName());
      return false;
    }

    @Override
    protected Boolean visitIfNode(IfNode node) {
      List<Boolean> values = super.visitChildren(node);
      return node.hasElse() && values.stream().allMatch(b -> b);
    }

    @Override
    protected Boolean visitSwitchNode(SwitchNode node) {
      List<Boolean> values = super.visitChildren(node);
      return node.hasDefaultCase() && values.stream().allMatch(b -> b);
    }

    @Override
    protected Boolean visitIfCondNode(IfCondNode node) {
      return visitBlock(node);
    }

    @Override
    protected Boolean visitIfElseNode(IfElseNode node) {
      return visitBlock(node);
    }

    @Override
    protected Boolean visitSwitchCaseNode(SwitchCaseNode node) {
      return visitBlock(node);
    }

    @Override
    protected Boolean visitSwitchDefaultNode(SwitchDefaultNode node) {
      return visitBlock(node);
    }

    @Override
    protected Boolean visitAutoImplNode(AutoImplNode node) {
      return visitBlock(node);
    }

    @Override
    protected Boolean visitForNode(ForNode node) {
      boolean unused = visitLoopBody((ForNonemptyNode) node.getChild(0));
      // The list may be empty, in which case the body never runs.
      return false;
    }

    @Override
    protected Boolean visitWhileNode(WhileNode node) {
      boolean hasBreak = visitLoopBody(node);
      // The loop may not run at all, unless its condition is the literal `true`. Such a loop runs
      // forever unless a {break} leaves it.
      return node.hasLiteralTrueCondition() && !hasBreak;
    }

    /** Visits the body of a loop and returns whether a {@code {break}} leaves the loop. */
    private boolean visitLoopBody(ParentSoyNode<?> node) {
      Loop loop = new Loop();
      loops.push(loop);
      boolean unused = visitBlock(node);
      loops.pop();
      return loop.hasBreak;
    }

    private Boolean visitBlock(ParentSoyNode<?> node) {
      boolean returns = false;
      for (int i = 0; i < node.numChildren(); i++) {
        SoyNode child = node.getChild(i);
        boolean childValue = visit(child);
        if (!returns && childValue) {
          returns = true;
          if (node.numChildren() > i + 1) {
            SoyNode deadCode = node.getChild(i + 1);
            errorReporter.report(deadCode.getSourceLocation(), DEAD_CODE);
          }
        }
      }
      return returns;
    }

    @Override
    protected Boolean visitSoyNode(SoyNode node) {
      return false;
    }
  }

  private final class AssignmentChecker extends AbstractSoyNodeVisitor<Void> {

    private final Set<String> lets = new HashSet<>();

    @Override
    protected void visitAutoImplNode(AutoImplNode node) {
      for (VarDefn param : node.getParent().getParamVars()) {
        lets.add(param.refName());
      }
      super.visitAutoImplNode(node);
    }

    @Override
    protected void visitLetNode(LetNode node) {
      lets.add(node.getVarRefName());
    }

    @Override
    protected void visitAssignmentNode(AssignmentNode node) {
      if (node.getLhs().getRoot() instanceof VarRefNode lhs) {
        if (!lets.contains(lhs.getName())) {
          errorReporter.report(node.getSourceLocation(), BAD_ASSIGNMENT);
        }
      }
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?> parentSoyNode) {
        visitChildren(parentSoyNode);
      }
    }
  }
}
