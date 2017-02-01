/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.exprtree;

import com.google.common.base.Equivalence;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import java.util.Objects;

/**
 * An equivalence relation for expressions.
 *
 * <p>This equivalence relation is meant to identify identical subexpressions and as such we ignore
 * a number of {@link ExprNode} properties. For example:
 *
 * <ul>
 *   <li>{@link ExprNode#getSourceLocation()}
 *   <li>{@link ExprNode#getType()}
 *   <li>{@link ExprNode#getParent()}
 * </ul>
 */
public final class ExprEquivalence extends Equivalence<ExprNode> {
  private static final ExprEquivalence INSTANCE = new ExprEquivalence();

  public static ExprEquivalence get() {
    return INSTANCE;
  }

  private final AbstractReturningExprNodeVisitor<Integer> hashCodeVisitor =
      new AbstractReturningExprNodeVisitor<Integer>() {
        @Override
        protected Integer visitVarRefNode(VarRefNode node) {
          return Objects.hashCode(node.getDefnDecl());
        }

        @Override
        protected Integer visitFieldAccessNode(FieldAccessNode node) {
          return Objects.hash(
              wrap(node.getBaseExprChild()), node.getFieldName(), node.isNullSafe());
        }

        @Override
        protected Integer visitItemAccessNode(ItemAccessNode node) {
          return Objects.hash(
              wrap(node.getBaseExprChild()), wrap(node.getKeyExprChild()), node.isNullSafe());
        }

        @Override
        protected Integer visitFunctionNode(FunctionNode node) {
          return Objects.hash(pairwise().wrap(node.getChildren()), node.getFunctionName());
        }

        @Override
        protected Integer visitGlobalNode(GlobalNode node) {
          return node.getName().hashCode();
        }

        @Override
        protected Integer visitListLiteralNode(ListLiteralNode node) {
          return hashChildren(node);
        }

        @Override
        protected Integer visitMapLiteralNode(MapLiteralNode node) {
          return hashChildren(node);
        }

        // literals

        @Override
        protected Integer visitIntegerNode(IntegerNode node) {
          return Longs.hashCode(node.getValue());
        }

        @Override
        protected Integer visitFloatNode(FloatNode node) {
          return Doubles.hashCode(node.getValue());
        }

        @Override
        protected Integer visitStringNode(StringNode node) {
          return node.getValue().hashCode();
        }

        @Override
        protected Integer visitExprRootNode(ExprRootNode node) {
          return hashChildren(node);
        }

        @Override
        protected Integer visitOperatorNode(OperatorNode node) {
          // operators are determined entirely by their children
          return hashChildren(node);
        }

        @Override
        protected Integer visitNullNode(NullNode node) {
          return 0;
        }

        @Override
        protected Integer visitExprNode(ExprNode node) {
          return 0;
        }

        private int hashChildren(ParentExprNode node) {
          return pairwise().hash(node.getChildren());
        }
      };

  final class EqualsVisitor extends AbstractReturningExprNodeVisitor<Boolean> {
    private final ExprNode other;

    EqualsVisitor(ExprNode other) {
      this.other = other;
    }

    @Override
    protected Boolean visitVarRefNode(VarRefNode node) {
      VarRefNode typedOther = (VarRefNode) other;
      // VarRefs are considered equivalent if they have identical and non-null VarDefns.
      if (node.getDefnDecl() != null || typedOther.getDefnDecl() != null) {
        return typedOther.getDefnDecl() == node.getDefnDecl();
      }
      // When getDefnDecl() are null, we should not directly return true. Instead, we should compare
      // the names and if both are injected parameters.
      // This checking seems redundant but it is possible that getDefnDecl will return null if
      // we haven't assigned the VarDefns yet. This happens in unit tests and will potentially
      // happen in some passes before we assign the VarDefns.
      // Note that this might return true for VarRefNodes from different templates. Be careful when
      // you use this to compare ExprNodes among templates.
      return typedOther.getName().equals(node.getName())
          && typedOther.isDollarSignIjParameter() == node.isDollarSignIjParameter();
    }

    @Override
    protected Boolean visitFieldAccessNode(FieldAccessNode node) {
      FieldAccessNode typedOther = (FieldAccessNode) other;
      return equivalent(node.getBaseExprChild(), typedOther.getBaseExprChild())
          && node.getFieldName().equals(typedOther.getFieldName())
          && node.isNullSafe() == typedOther.isNullSafe();
    }

    @Override
    protected Boolean visitItemAccessNode(ItemAccessNode node) {
      ItemAccessNode typedOther = (ItemAccessNode) other;
      return equivalent(node.getBaseExprChild(), typedOther.getBaseExprChild())
          && equivalent(node.getKeyExprChild(), typedOther.getKeyExprChild())
          && node.isNullSafe() == typedOther.isNullSafe();
    }

    @Override
    protected Boolean visitFunctionNode(FunctionNode node) {
      FunctionNode typedOther = (FunctionNode) other;
      return compareChildren(node) && node.getFunctionName().equals(typedOther.getFunctionName());
    }

    @Override
    protected Boolean visitGlobalNode(GlobalNode node) {
      return node.getName().equals(((GlobalNode) other).getName());
    }

    @Override
    protected Boolean visitListLiteralNode(ListLiteralNode node) {
      return compareChildren(node);
    }

    @Override
    protected Boolean visitMapLiteralNode(MapLiteralNode node) {
      return compareChildren(node);
    }

    // literals

    @Override
    protected Boolean visitBooleanNode(BooleanNode node) {
      return node.getValue() == ((BooleanNode) other).getValue();
    }

    @Override
    protected Boolean visitIntegerNode(IntegerNode node) {
      return node.getValue() == ((IntegerNode) other).getValue();
    }

    @Override
    protected Boolean visitFloatNode(FloatNode node) {
      return node.getValue() == ((FloatNode) other).getValue();
    }

    @Override
    protected Boolean visitStringNode(StringNode node) {
      return node.getValue().equals(((StringNode) other).getValue());
    }

    @Override
    protected Boolean visitExprRootNode(ExprRootNode node) {
      return compareChildren(node);
    }

    @Override
    protected Boolean visitOperatorNode(OperatorNode node) {
      // operators are determined entirely by their children
      return compareChildren(node);
    }

    @Override
    protected Boolean visitNullNode(NullNode node) {
      return true;
    }

    @Override
    protected Boolean visitExprNode(ExprNode node) {
      // default
      return false;
    }

    private boolean compareChildren(ParentExprNode node) {
      return pairwise().equivalent(node.getChildren(), ((ParentExprNode) other).getChildren());
    }
  }

  private ExprEquivalence() {}

  @Override
  protected boolean doEquivalent(ExprNode a, ExprNode b) {
    return a.getKind() == b.getKind() && new EqualsVisitor(a).exec(b);
  }

  @Override
  protected int doHash(ExprNode t) {
    return 31 * t.getKind().hashCode() + hashCodeVisitor.exec(t);
  }
}
