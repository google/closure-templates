/*
 * Copyright 2024 Google Inc.
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

import static com.google.template.soy.exprtree.ExprNodes.isNullishLiteral;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.AbstractOperatorNode;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleNotEqualOpNode;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyTypes;
import javax.annotation.Nullable;

/**
 * A pass that rewrites that rewrites null checks with literal true/false in cases where we know for
 * sure that the value is non-nullish. This is needed for correct jbcsrc code generation since
 * nullish checks (ie foo != null) can be pruned when `foo` is definitely never null. If the pruned
 * expression contains a SoyValueProvider and it is the first usage, TemplateAnalysis will
 * incorrectly find all subsequent usages to be immediately resolvable. See b/351090754 for an
 * example of this kind of bug.
 */
@RunAfter({ResolveExpressionTypesPass.class})
final class RewriteNullishChecksPass implements CompilerFilePass {

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.execOnAllV2Exprs(file, new RewriteNullishChecksVisitor());
  }

  /** Visitor for simplifying nullish checks on non-nullish values. */
  static final class RewriteNullishChecksVisitor extends AbstractExprNodeVisitor<Void> {

    RewriteNullishChecksVisitor() {}

    @Override
    protected void visitEqualOpNode(EqualOpNode node) {
      maybeRewriteNullishCheck(node);
    }

    @Override
    protected void visitNotEqualOpNode(NotEqualOpNode node) {
      maybeRewriteNullishCheck(node);
    }

    @Override
    protected void visitTripleEqualOpNode(TripleEqualOpNode node) {
      maybeRewriteNullishCheck(node);
    }

    @Override
    protected void visitTripleNotEqualOpNode(TripleNotEqualOpNode node) {
      maybeRewriteNullishCheck(node);
    }

    private void maybeRewriteNullishCheck(AbstractOperatorNode node) {
      BooleanNode replacement = maybeReplacement(node);
      if (replacement != null) {
        node.getParent().replaceChild(node, replacement);
      }
    }

    @Override
    protected void visitExprRootNode(ExprRootNode node) {
      visit(node.getRoot());
    }

    @Override
    protected void visitExprNode(ExprNode node) {

      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    @Nullable
    private BooleanNode maybeReplacement(AbstractOperatorNode node) {
      ExprNode lhs = node.getChild(0);
      ExprNode rhs = node.getChild(1);
      if (!isNullishLiteral(rhs) && !isNullishLiteral(lhs)) {
        return null;
      }
      ExprNode literal = isNullishLiteral(lhs) ? lhs : rhs;
      ExprNode nonLiteral = isNullishLiteral(lhs) ? rhs : lhs;
      if (!canTrustType(nonLiteral)) {
        return null;
      }
      switch (node.getKind()) {
        case EQUAL_OP_NODE:
          if (SoyTypes.isDefinitelyNonNullish(nonLiteral.getType())) {
            return new BooleanNode(false, SourceLocation.UNKNOWN);
          }
          break;
        case NOT_EQUAL_OP_NODE:
          if (SoyTypes.isDefinitelyNonNullish(nonLiteral.getType())) {
            return new BooleanNode(true, SourceLocation.UNKNOWN);
          }
          break;
        case TRIPLE_EQUAL_OP_NODE:
          if (!nonLiteral.getType().isAssignableFromStrict(literal.getType())) {
            return new BooleanNode(false, SourceLocation.UNKNOWN);
          }
          break;
        case TRIPLE_NOT_EQUAL_OP_NODE:
          if (!nonLiteral.getType().isAssignableFromStrict(literal.getType())) {
            return new BooleanNode(true, SourceLocation.UNKNOWN);
          }
          break;
        default:
      }
      return null;
    }

    private boolean canTrustType(ExprNode node) {
      if ((node instanceof MethodCallNode)
          && ((MethodCallNode) node).getSoyMethod() instanceof BuiltinMethod) {
        return ((MethodCallNode) node).getSoyMethod() != BuiltinMethod.GET_EXTENSION;
      }
      return false;
    }
  }
}
