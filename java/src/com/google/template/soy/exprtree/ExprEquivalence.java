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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Equivalence;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Doubles;
import com.google.template.soy.exprtree.ExprNode.CallableExpr.ParamsStyle;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
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
public final class ExprEquivalence {

  /**
   * A map to cache the wrapper objects.
   *
   * <p>Due to how Exprs recursively hash each other by calling {@code wrap(expr).hashCode()}, if we
   * cache the wrappers we can ensure we only hash each node once, and that work on sub-expressions
   * is shared. This is important for some cases where the compiler needs to hash all subexpression,
   * like when ResolveExpressionTypesPass implements the 'type narrowing' logic.
   */
  private final IdentityHashMap<ExprNode, Wrapper> interningMap = new IdentityHashMap<>();

  private final Equivalence<ExprNode> equivalence =
      new Equivalence<>() {
        @Override
        protected boolean doEquivalent(ExprNode a, ExprNode b) {
          return a.getKind() == b.getKind() && new EqualsVisitor(a).exec(b);
          // return a.getKind() == b.getKind();
        }

        @Override
        protected int doHash(ExprNode t) {
          return 31 * t.getKind().hashCode() + hashCodeVisitor.exec(t);
        }
      };

  private final AbstractReturningExprNodeVisitor<Integer> hashCodeVisitor =
      new AbstractReturningExprNodeVisitor<>() {
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
        protected Integer visitMethodCallNode(MethodCallNode node) {
          return 31 * (node.getMethodName().identifier().hashCode() * 31 + hashChildren(node))
              + Boolean.hashCode(node.isNullSafe());
        }

        @Override
        protected Integer visitNullSafeAccessNode(NullSafeAccessNode node) {
          return hashChildren(node);
        }

        @Override
        protected Integer visitFunctionNode(FunctionNode node) {
          int hash = 1;
          if (node.hasStaticName()) {
            hash = hash * 31 + node.getStaticFunctionName().hashCode();
          } else {
            hash = hash * 31 + visit(node.getNameExpr());
          }
          if (node.getParamsStyle() == ParamsStyle.NAMED) {
            hash = hash * 31 + namedParamsMap(node).hashCode();
          } else {
            hash = hash * 31 + hashChildren(node);
          }
          return hash;
        }

        @Override
        protected Integer visitGlobalNode(GlobalNode node) {
          return node.getName().hashCode();
        }

        @Override
        protected Integer visitGroupNode(GroupNode node) {
          return hashChildren(node);
        }

        @Override
        protected Integer visitListLiteralNode(ListLiteralNode node) {
          return hashChildren(node);
        }

        @Override
        protected Integer visitListComprehensionNode(ListComprehensionNode node) {
          return Objects.hash(
              node.getListIterVar(),
              node.getIndexVar(),
              node.getListExpr(),
              node.getListItemTransformExpr(),
              node.getFilterExpr());
        }

        @Override
        protected Integer visitRecordLiteralNode(RecordLiteralNode node) {
          return recordLiteralFields(node).hashCode();
        }

        @Override
        protected Integer visitMapLiteralNode(MapLiteralNode node) {
          return mapLiteralFields(node).hashCode();
        }

        @Override
        protected Integer visitMapLiteralFromListNode(MapLiteralFromListNode node) {
          return hashChildren(node);
        }

        // literals

        @Override
        protected Integer visitTemplateLiteralNode(TemplateLiteralNode node) {
          return node.getResolvedName().hashCode();
        }

        @Override
        protected Integer visitBooleanNode(BooleanNode node) {
          return Booleans.hashCode(node.getValue());
        }

        @Override
        protected Integer visitNumberNode(NumberNode node) {
          return Doubles.hashCode(node.getValue());
        }

        @Override
        protected Integer visitStringNode(StringNode node) {
          return node.getValue().hashCode();
        }

        @Override
        protected Integer visitProtoEnumValueNode(ProtoEnumValueNode node) {
          return Objects.hash(node.getType(), node.getValue());
        }

        @Override
        protected Integer visitTypeLiteralNode(TypeLiteralNode node) {
          return node.toSourceString().hashCode();
        }

        @Override
        protected Integer visitExprRootNode(ExprRootNode node) {
          return hashChildren(node);
        }

        @Override
        protected Integer visitOperatorNode(OperatorNode node) {
          return node.getOperator().hashCode() * 31 + hashChildren(node);
        }

        @Override
        protected Integer visitNullNode(NullNode node) {
          return 0;
        }

        @Override
        protected Integer visitUndefinedNode(UndefinedNode node) {
          return -1;
        }

        @Override
        protected Integer visitExprNode(ExprNode node) {
          throw new UnsupportedOperationException(node.toSourceString());
        }

        private int hashChildren(ParentExprNode node) {
          return hash(node.getChildren());
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
        return node.getDefnDecl().isEquivalent(typedOther.getDefnDecl());
      }
      // When getDefnDecl() are null, we should not directly return true. Instead, we should compare
      // the names.
      // This checking seems redundant but it is possible that getDefnDecl will return null if
      // we haven't assigned the VarDefns yet. This happens in unit tests and will potentially
      // happen in some passes before we assign the VarDefns.
      // Note that this might return true for VarRefNodes from different templates. Be careful when
      // you use this to compare ExprNodes among templates.
      return typedOther.getName().equals(node.getName());
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
    protected Boolean visitMethodCallNode(MethodCallNode node) {
      MethodCallNode typedOther = (MethodCallNode) other;
      return node.getMethodName().identifier().equals(typedOther.getMethodName().identifier())
          && node.isNullSafe() == typedOther.isNullSafe()
          && compareChildren(node);
    }

    @Override
    protected Boolean visitNullSafeAccessNode(NullSafeAccessNode node) {
      return compareChildren(node);
    }

    @Override
    protected Boolean visitFunctionNode(FunctionNode node) {
      FunctionNode typedOther = (FunctionNode) other;
      // TODO(b/78775420): consider only allowing pure functions to be equal to each other.  Will
      // require refactoring templates relying on this to extract such expressions into local
      // variables which is probably the right call anyway.
      if (node.hasStaticName() != typedOther.hasStaticName()) {
        return false;
      }

      boolean ok =
          node.hasStaticName()
              ? node.getStaticFunctionName().equals(typedOther.getStaticFunctionName())
              : equivalent(node.getNameExpr(), typedOther.getNameExpr());

      if (node.getParamsStyle() == ParamsStyle.NAMED) {
        ok = ok && namedParamsMap(node).equals(namedParamsMap(typedOther));
      } else {
        ok = ok && compareChildren(node);
      }
      return ok;
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
    protected Boolean visitRecordLiteralNode(RecordLiteralNode node) {
      return recordLiteralFields(node).equals(recordLiteralFields((RecordLiteralNode) other));
    }

    @Override
    protected Boolean visitMapLiteralNode(MapLiteralNode node) {
      return mapLiteralFields(node).equals(mapLiteralFields((MapLiteralNode) other));
    }

    /**
     * As seen above in the hash implementation, two list comprehension nodes can only be equivalent
     * if they are the exact same object.
     */
    @Override
    protected Boolean visitListComprehensionNode(ListComprehensionNode node) {
      return node == other;
    }

    @Override
    protected Boolean visitMapLiteralFromListNode(MapLiteralFromListNode node) {
      return compareChildren(node);
    }

    // literals

    @Override
    protected Boolean visitTemplateLiteralNode(TemplateLiteralNode node) {
      TemplateLiteralNode otherNode = (TemplateLiteralNode) other;
      return node.getResolvedName().equals(otherNode.getResolvedName());
    }

    @Override
    protected Boolean visitBooleanNode(BooleanNode node) {
      return node.getValue() == ((BooleanNode) other).getValue();
    }

    @Override
    protected Boolean visitNumberNode(NumberNode node) {
      return node.getValue() == ((NumberNode) other).getValue();
    }

    @Override
    protected Boolean visitStringNode(StringNode node) {
      return node.getValue().equals(((StringNode) other).getValue());
    }

    @Override
    protected Boolean visitProtoEnumValueNode(ProtoEnumValueNode node) {
      return node.getType().equals(((ProtoEnumValueNode) other).getType())
          && node.getValue() == ((ProtoEnumValueNode) other).getValue();
    }

    @Override
    protected Boolean visitTypeLiteralNode(TypeLiteralNode node) {
      return node.getType().equals(other.getType());
    }

    @Override
    protected Boolean visitExprRootNode(ExprRootNode node) {
      return compareChildren(node);
    }

    @Override
    protected Boolean visitOperatorNode(OperatorNode node) {
      return node.getOperator() == ((OperatorNode) other).getOperator() && compareChildren(node);
    }

    @Override
    protected Boolean visitNullNode(NullNode node) {
      return true;
    }

    @Override
    protected Boolean visitUndefinedNode(UndefinedNode node) {
      return true;
    }

    @Override
    protected Boolean visitGroupNode(GroupNode node) {
      return compareChildren(node);
    }

    @Override
    protected Boolean visitExprNode(ExprNode node) {
      throw new UnsupportedOperationException(node.toSourceString());
    }

    private boolean compareChildren(ParentExprNode node) {
      return equivalent(node.getChildren(), ((ParentExprNode) other).getChildren());
    }
  }

  private HashMap<String, Wrapper> namedParamsMap(FunctionNode node) {
    HashMap<String, Wrapper> map = new HashMap<>();
    List<ExprNode> params = node.getParams();
    for (int i = 0; i < params.size(); i++) {
      map.put(node.getParamName(i).identifier(), wrap(params.get(i)));
    }
    return map;
  }

  private HashMap<String, Wrapper> recordLiteralFields(RecordLiteralNode node) {
    HashMap<String, Wrapper> map = new HashMap<>();
    List<ExprNode> children = node.getChildren();
    for (int i = 0; i < children.size(); i++) {
      map.put(node.getKey(i).identifier(), wrap(children.get(i)));
    }
    return map;
  }

  private HashMap<Wrapper, Wrapper> mapLiteralFields(MapLiteralNode node) {
    // both of these nodes store keys and values as alternating children.  We don't want order to
    // matter so we store in a map
    HashMap<Wrapper, Wrapper> map = new HashMap<>();
    List<ExprNode> children = node.getChildren();
    for (int i = 0; i < children.size(); i += 2) {
      map.put(wrap(children.get(i)), wrap(children.get(i + 1)));
    }
    return map;
  }

  public boolean equivalent(ExprNode a, ExprNode b) {
    return equivalence.equivalent(a, b);
  }

  public boolean equivalent(List<ExprNode> a, List<ExprNode> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!equivalent(a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  public int hash(ExprNode a) {
    return wrap(a).hashCode();
  }

  public int hash(List<ExprNode> a) {
    int result = 1;
    for (ExprNode element : a) {
      result = 31 * result + wrap(element).hashCode();
    }
    return result;
  }

  public Wrapper wrap(ExprNode expr) {
    return interningMap.computeIfAbsent(expr, this::createWrapper);
  }

  private Wrapper createWrapper(ExprNode expr) {
    return new Wrapper(equivalence, expr);
  }

  /** A wrapper type that provides value semantics to ExprNode. */
  public static final class Wrapper {
    private final Equivalence<ExprNode> equivalence;
    private final ExprNode expr;
    private final int hashCode;

    Wrapper(Equivalence<ExprNode> equivalence, ExprNode expr) {
      this.equivalence = equivalence;
      this.expr = checkNotNull(expr);
      this.hashCode = equivalence.hash(expr);
    }

    public ExprNode get() {
      return expr;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other instanceof Wrapper) {
        Wrapper otherWrapper = (Wrapper) other;
        if (otherWrapper.equivalence != equivalence) {
          return false;
        }
        return equivalence.equivalent(this.expr, otherWrapper.expr);
      }
      return false;
    }

    @Override
    public String toString() {
      return expr.toSourceString();
    }
  }
}
