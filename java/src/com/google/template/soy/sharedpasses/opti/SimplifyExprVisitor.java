/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.sharedpasses.opti;

import static com.google.template.soy.exprtree.ExprNodes.isNullishLiteral;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internalutils.InternalValueUtils;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.NumberNode;
import com.google.template.soy.exprtree.OperatorNodes.AmpAmpOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AsOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BarBarOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.InstanceOfOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.UndefinedNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.sharedpasses.render.Environment;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.NumberType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.StringType;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * Visitor for simplifying expressions based on constant values known at compile time.
 *
 * <p>Package-private helper for {@link SimplifyVisitor}.
 */
final class SimplifyExprVisitor extends AbstractExprNodeVisitor<Void> {

  private static final SoyErrorKind SOY_DATA_ERROR = SoyErrorKind.of("Invalid value: {0}.");

  /** The PreevalVisitor for this instance (can reuse). */
  private final PreevalVisitor preevalVisitor;

  private final ErrorReporter errorReporter;

  SimplifyExprVisitor(ErrorReporter errorReporter) {
    this.preevalVisitor = new PreevalVisitor(Environment.prerenderingEnvironment());
    this.errorReporter = errorReporter;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementation for root node.

  @Override
  protected void visitExprRootNode(ExprRootNode node) {
    visit(node.getRoot());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collection nodes.

  @Override
  protected void visitListLiteralNode(ListLiteralNode node) {
    // Visit children only. We cannot simplify the list literal itself.
    visitChildren(node);
  }

  @Override
  protected void visitRecordLiteralNode(RecordLiteralNode node) {
    // Visit children only. We cannot simplify the record literal itself.
    visitChildren(node);
  }

  @Override
  protected void visitMapLiteralNode(MapLiteralNode node) {
    // Visit children only. We cannot simplify the map literal itself.
    visitChildren(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override
  protected void visitAmpAmpOpNode(AmpAmpOpNode node) {
    // Recurse.
    visitChildren(node);

    // Can simplify if either child is constant. We assume no side-effects.
    SoyValue operand0 = getConstantOrNull(node.getChild(0));
    if (operand0 != null) {
      ExprNode replacementNode = operand0.coerceToBoolean() ? node.getChild(1) : node.getChild(0);
      node.getParent().replaceChild(node, replacementNode);
    }
  }

  @Override
  protected void visitBarBarOpNode(BarBarOpNode node) {
    // Recurse.
    visitChildren(node);

    // Can simplify if either child is constant. We assume no side-effects.
    SoyValue operand0 = getConstantOrNull(node.getChild(0));
    if (operand0 != null) {
      ExprNode replacementNode = operand0.coerceToBoolean() ? node.getChild(0) : node.getChild(1);
      node.getParent().replaceChild(node, replacementNode);
    }
  }

  static ExprNode booleanCoerce(ExprNode expr) {
    SoyValue operand = getConstantOrNull(expr);
    if (operand != null) {
      return new BooleanNode(operand.coerceToBoolean(), expr.getSourceLocation());
    }
    FunctionNode func =
        FunctionNode.newPositional(
            Identifier.create("Boolean", expr.getSourceLocation()),
            BuiltinFunction.BOOLEAN,
            expr.getSourceLocation());
    func.addChild(expr);
    func.setType(BoolType.getInstance());
    func.setAllowedParamTypes(ImmutableList.of(AnyType.getInstance()));
    return func;
  }

  @Override
  protected void visitConditionalOpNode(ConditionalOpNode node) {
    // Recurse.
    visitChildren(node);

    // Can simplify if operand0 is constant. We assume no side-effects.
    SoyValue operand0 = getConstantOrNull(node.getChild(0));
    if (operand0 == null) {
      return; // cannot simplify
    }

    ExprNode replacementNode = operand0.coerceToBoolean() ? node.getChild(1) : node.getChild(2);
    node.getParent().replaceChild(node, replacementNode);
  }

  // these cases may seem weird, but they tend to get introduced with @state parameter defaults and
  // let variable inlining
  @Override
  protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    // Recurse.
    visitChildren(node);

    // Can simplify if operand0 is constant. We assume no side-effects.
    SoyValue operand0 = getConstantOrNull(node.getChild(0));
    if (operand0 != null) {
      if (operand0.isNullish()) {
        node.getParent().replaceChild(node, node.getChild(1));
      } else {
        // all other constants are non=null, so use that
        node.getParent().replaceChild(node, node.getChild(0));
      }
    } else {
      // even if the first child isn't a constant, there are some values that we know are
      // non-nullable.
      switch (node.getChild(0).getKind()) {
        case LIST_LITERAL_NODE:
        case RECORD_LITERAL_NODE:
          // these are always non-null
          node.getParent().replaceChild(node, node.getChild(0));
          break;
        default:
          // do nothing.
      }
    }
  }

  // Optimize accessing fields of record and proto literals: ['a': 'b'].a => 'b'
  // This is unlikely to occur by chance, but things like the msgWithId() function may introduce
  // this code since it desugars into a record literal.
  @Override
  protected void visitFieldAccessNode(FieldAccessNode node) {
    this.visitDataAccessNodeInternal(node, SimplifyExprVisitor::visitFieldAccessNode);
  }

  @Nullable
  private static ExprNode visitFieldAccessNode(FieldAccessNode node, ExprNode baseExpr) {
    if (baseExpr instanceof RecordLiteralNode) {
      RecordLiteralNode recordLiteral = (RecordLiteralNode) baseExpr;
      for (int i = 0; i < recordLiteral.numChildren(); i++) {
        if (recordLiteral.getKey(i).identifier().equals(node.getFieldName())) {
          return recordLiteral.getChild(i);
        }
      }
      // replace with null?  this should have been a compiler error.
    }
    return null;
    // NOTE: we don't constant fold field accesses of ProtoInitNodes because protos implement
    // complex null semantics. e.g. if we assign `null` to a field and then access it we may get a
    // default value instead of `null`.
  }

  private <T extends DataAccessNode> void visitDataAccessNodeInternal(
      T node, BiFunction<T, ExprNode, ExprNode> delegate) {
    // simplify children first
    visitExprNode(node);
    if (node.getParent() == null) {
      // must have already been optimized by visitExprNode and replaced in the AST
      return;
    }
    ExprNode baseExpr = node.getChild(0);
    ExprNode replacement = delegate.apply(node, baseExpr);
    if (replacement != null) {
      node.getParent().replaceChild(node, replacement);
    }
  }

  // Optimize accessing map and list literals.  This covers expressions like [1,2,3][1] and
  // map('a':1)['a']
  // This is fairly unlikely to happen in practice, but is being done for consistency with the other
  // aggregate literals above.  This might happen for things like pure functions that return
  // collections
  @Override
  protected void visitItemAccessNode(ItemAccessNode node) {
    this.visitDataAccessNodeInternal(node, SimplifyExprVisitor::visitItemAccessNode);
  }

  @Nullable
  private static ExprNode visitItemAccessNode(ItemAccessNode node, ExprNode baseExpr) {
    ExprNode keyExpr = node.getChild(1);
    if (baseExpr instanceof ListLiteralNode
        && !((ListLiteralNode) baseExpr).containsSpreads()
        && keyExpr instanceof NumberNode) {
      ListLiteralNode listLiteral = (ListLiteralNode) baseExpr;
      long index = (long) ((NumberNode) keyExpr).getValue();
      if (index >= 0 && index < listLiteral.numChildren()) {
        return listLiteral.getChild((int) index);
      } else {
        // out of range
        return new UndefinedNode(node.getSourceLocation());
      }
    }
    return null;
  }

  @Override
  protected void visitMethodCallNode(MethodCallNode node) {
    this.visitDataAccessNodeInternal(node, SimplifyExprVisitor::visitMethodCallNode);
  }

  @Nullable
  private static ExprNode visitMethodCallNode(MethodCallNode node, ExprNode baseExpr) {
    if (baseExpr instanceof MapLiteralNode
        && node.isMethodResolved()
        && node.getSoyMethod() == BuiltinMethod.MAP_GET) {
      MapLiteralNode mapLiteral = (MapLiteralNode) baseExpr;
      ExprNode keyExpr = node.getParam(0);
      boolean areAllKeysConstants = true;
      ExprEquivalence exprEquivalence = new ExprEquivalence();
      for (int i = 0; i < mapLiteral.numChildren(); i += 2) {
        ExprNode key = mapLiteral.getChild(i);
        ExprNode value = mapLiteral.getChild(i + 1);
        if (exprEquivalence.equivalent(keyExpr, key)) {
          return value;
        }
        areAllKeysConstants = areAllKeysConstants && isConstant(key);
      }
      if (isConstant(keyExpr) && areAllKeysConstants) {
        // no matching key, and since everything was a bunch of constants, it should have matched.
        // in this case we can evaluate at compile time.
        return new UndefinedNode(node.getSourceLocation());
      }
    }
    return null;
  }

  @Override
  protected void visitNullSafeAccessNode(NullSafeAccessNode node) {
    while (true) {
      visit(node.getBase());
      ExprNode base = node.getBase();
      if (isNullishLiteral(base)) {
        // This is a null safe access on null, which evaluates to null. So replace the whole
        // expression with null and exit.
        node.getParent().replaceChild(node, base);
        return;
      }
      ExprNode dataAccessChild = node.getDataAccess();
      switch (dataAccessChild.getKind()) {
        case ASSERT_NON_NULL_OP_NODE:
          // This can happen for an access chain like {@code $foo?.bar!}. In this case, don't
          // attempt to optimize chains past non-null assertion operators. This is possible, but
          // unclear how valuable it is, especially since this is already so complex.
          return;
        case NULL_SAFE_ACCESS_NODE:
          {
            // This null safe access is followed by another null safe access, which means the access
            // details are stored in the base of the next null safe access node.
            NullSafeAccessNode nullSafeAccessChild = (NullSafeAccessNode) dataAccessChild;
            DataAccessNode dataAccessChain = (DataAccessNode) nullSafeAccessChild.getBase();
            DataAccessNode dataAccessChainBase = findBaseDataAccess(dataAccessChain);
            ExprNode replacement = findReplacement(dataAccessChainBase, base);
            if (replacement == null) {
              // There are no optimizations, so nothing else later in the chain can be optimized
              // either. Exit.
              return;
            } else {
              // We found a replacement for the null safe access at the beginning of this chain,
              // that means we can get rid of this null safe access node and stick the replacement
              // expression in the base of the next null safe access.
              node.getParent().replaceChild(node, nullSafeAccessChild);
              if (dataAccessChainBase == dataAccessChain) {
                // The null safe access base is just this one access, so replace it with the
                // simplified replacement value.
                nullSafeAccessChild.replaceChild(nullSafeAccessChild.getBase(), replacement);
              } else {
                // There are some normal data accesses before the next null safe data access, so
                // replace just this data access in the chain with the simplified replacement value.
                dataAccessChainBase.getParent().replaceChild(dataAccessChainBase, replacement);
              }
              node = nullSafeAccessChild;
            }
            break;
          }
        case FIELD_ACCESS_NODE:
        case ITEM_ACCESS_NODE:
        case METHOD_CALL_NODE:
          {
            // This is the last null safe access in the chain, so the access details are stored
            // directly in the data access child of this null safe access.
            DataAccessNode dataAccessChainBase =
                findBaseDataAccess((DataAccessNode) dataAccessChild);
            ExprNode replacement = findReplacement(dataAccessChainBase, base);
            if (replacement == null) {
              // There are no optimizations, so nothing else later in the chain can be optimized
              // either. Exit.
              return;
            } else if (dataAccessChild == dataAccessChainBase) {
              // The null safe access base is just this one access, so replace it with the
              // simplified replacement value.
              node.getParent().replaceChild(node, replacement);
              visit(replacement);
            } else {
              // There are some normal (non null safe) data accesses at the end off the access
              // chain, so replace just this data access in the chain with the simplified
              // replacement value.
              dataAccessChainBase.getParent().replaceChild(dataAccessChainBase, replacement);
              node.getParent().replaceChild(node, dataAccessChild);
              visit(dataAccessChild);
            }
            return;
          }
        default:
          throw new AssertionError(dataAccessChild.getKind());
      }
    }
  }

  @Nullable
  private static ExprNode findReplacement(DataAccessNode dataAccessChainBase, ExprNode base) {
    switch (dataAccessChainBase.getKind()) {
      case FIELD_ACCESS_NODE:
        return visitFieldAccessNode((FieldAccessNode) dataAccessChainBase, base);
      case ITEM_ACCESS_NODE:
        return visitItemAccessNode((ItemAccessNode) dataAccessChainBase, base);
      case METHOD_CALL_NODE:
        return visitMethodCallNode((MethodCallNode) dataAccessChainBase, base);
      default:
        throw new AssertionError(dataAccessChainBase.getKind());
    }
  }

  private static DataAccessNode findBaseDataAccess(DataAccessNode node) {
    if (node.getBaseExprChild() instanceof DataAccessNode) {
      return findBaseDataAccess((DataAccessNode) node.getBaseExprChild());
    }
    return node;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.

  @Override
  protected void visitFunctionNode(FunctionNode node) {

    // Cannot simplify nonplugin functions.
    // TODO(user): we can actually simplify checkNotNull.
    if (node.getSoyFunction() instanceof BuiltinFunction) {
      switch ((BuiltinFunction) node.getSoyFunction()) {
        case BOOLEAN:
        case EMPTY_TO_UNDEFINED:
        case UNDEFINED_TO_NULL:
        case UNDEFINED_TO_NULL_SSR:
          visitExprNode(node);
          break;
        default:
          // we could eliminate checkNotNull and protoInit and maybe some others
          break;
      }
      return;
    }
    if (node.getSoyFunction() instanceof LoggingFunction) {
      return;
    }
    // Default to fallback implementation.
    visitExprNode(node);
  }

  @Override
  protected void visitAsOpNode(AsOpNode node) {
    visit(node.getChild(0));
  }

  @Override
  protected void visitInstanceOfOpNode(InstanceOfOpNode node) {
    ExprNode lhs = node.getChild(0);
    visit(lhs);

    Boolean staticValue = null;
    SoyType rhs = node.getChild(1).getType();
    switch (lhs.getKind()) {
      case STRING_NODE:
        staticValue = rhs.equals(StringType.getInstance());
        break;
      case NUMBER_NODE:
        staticValue = rhs.equals(NumberType.getInstance());
        break;
      case BOOLEAN_NODE:
        staticValue = rhs.equals(BoolType.getInstance());
        break;
      case LIST_LITERAL_NODE:
      case LIST_COMPREHENSION_NODE:
        staticValue = rhs.getKind() == SoyType.Kind.LIST || rhs.getKind() == SoyType.Kind.ITERABLE;
        break;
      case FUNCTION_NODE:
        FunctionNode fNode = (FunctionNode) lhs;
        if (fNode.isResolved() && fNode.getSoyFunction() == BuiltinFunction.NEW_SET) {
          staticValue = rhs.getKind() == SoyType.Kind.SET || rhs.getKind() == SoyType.Kind.ITERABLE;
        }
        break;
      case MAP_LITERAL_NODE:
      case MAP_LITERAL_FROM_LIST_NODE:
        staticValue = rhs.getKind() == SoyType.Kind.MAP;
        break;
      case RECORD_LITERAL_NODE:
        staticValue = rhs.getKind() == SoyType.Kind.RECORD;
        break;
      default:
        break;
    }
    if (staticValue == null && lhs instanceof PrimitiveNode) {
      staticValue = false;
    }

    if (staticValue != null) {
      node.getParent().replaceChild(node, new BooleanNode(staticValue, node.getSourceLocation()));
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitExprNode(ExprNode node) {

    if (!(node instanceof ParentExprNode)) {
      return;
    }
    ParentExprNode nodeAsParent = (ParentExprNode) node;

    // Recurse.
    visitChildren(nodeAsParent);

    // If all children are constants, we attempt to preevaluate this node and replace it with a
    // constant.
    if (!childrenAreConstant(nodeAsParent)) {
      return;
    }
    attemptPreeval(nodeAsParent);
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  /**
   * Attempts to preevaluate a node. If successful, the node is replaced with a new constant node in
   * the tree. If unsuccessful, the tree is not changed.
   */
  private void attemptPreeval(ExprNode node) {

    // Note that we need to catch RenderException because preevaluation may fail, e.g. when
    // (a) the expression uses a bidi function that needs bidiGlobalDir to be in scope, but the
    //     apiCallScope is not currently active,
    // (b) the expression uses an external function (Soy V1 syntax),
    // (c) other cases I haven't thought up.

    SoyValue preevalResult;
    try {
      preevalResult = preevalVisitor.exec(node);
    } catch (RenderException e) {
      return; // failed to preevaluate
    } catch (SoyDataException e) {
      errorReporter.report(node.getSourceLocation(), SOY_DATA_ERROR, e.getMessage());
      return;
    }

    // It is possible for this to be an arbitrary SoyValue if the exprNode calls a pure soy function
    // TODO(lukes): consider adding compiler builtins for sanitized content literals and consider
    // expanding support for collection literals (lists/maps) to expand scope here.
    if (preevalResult instanceof PrimitiveData) {
      PrimitiveNode newNode =
          InternalValueUtils.convertPrimitiveDataToExpr(
              (PrimitiveData) preevalResult, node.getSourceLocation());
      if (newNode != null) {
        node.getParent().replaceChild(node, newNode);
      }
    }
  }

  private static boolean childrenAreConstant(ParentExprNode parent) {
    if (parent.getKind() == Kind.NULL_SAFE_ACCESS_NODE) {
      NullSafeAccessNode nullSafe = (NullSafeAccessNode) parent;
      return isConstant(nullSafe.getBase())
          && childrenAreConstant((ParentExprNode) nullSafe.getDataAccess());
    }
    for (ExprNode child : parent.getChildren()) {
      if (!isConstant(child)) {
        return false;
      }
    }
    return true;
  }

  static boolean isConstant(ExprNode expr) {
    return expr instanceof PrimitiveNode;
  }

  /** Returns the value of the given expression if it's constant, else returns null. */
  @Nullable
  static SoyValue getConstantOrNull(ExprNode expr) {
    switch (expr.getKind()) {
      case NULL_NODE:
        return NullData.INSTANCE;
      case UNDEFINED_NODE:
        return UndefinedData.INSTANCE;
      case BOOLEAN_NODE:
        return BooleanData.forValue(((BooleanNode) expr).getValue());
      case NUMBER_NODE:
        return FloatData.forValue(((NumberNode) expr).getValue());
      case STRING_NODE:
        return StringData.forValue(((StringNode) expr).getValue());
      case PROTO_ENUM_VALUE_NODE:
        // Delay resolving proto enums to their values to support strong typing in JS.
        // See b/255452370 for background
        return null;
      case FUNCTION_NODE:
        FunctionNode func = (FunctionNode) expr;
        if (func.getFunctionName().equals("Boolean")) {
          SoyValue b = getConstantOrNull(func.getChild(0));
          if (b != null) {
            return BooleanData.forValue(b.coerceToBoolean());
          }
        }
        return null;
      default:
        return null;
    }
  }
}
