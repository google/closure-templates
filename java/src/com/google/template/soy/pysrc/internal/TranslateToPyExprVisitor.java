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

package com.google.template.soy.pysrc.internal;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Operand;
import com.google.template.soy.exprtree.Operator.SyntaxElement;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.targetexpr.ExprUtils;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;

import java.util.List;


/**
 * Visitor for translating a Soy expression (in the form of an {@code ExprNode}) into an
 * equivalent Python expression.
 *
 */
final class TranslateToPyExprVisitor extends AbstractReturningExprNodeVisitor<PyExpr> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  static interface TranslateToPyExprVisitorFactory {

    public TranslateToPyExprVisitor create();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected PyExpr visitExprRootNode(ExprRootNode<?> node) {
    return visit(node.getChild(0));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.


  @Override protected PyExpr visitPrimitiveNode(PrimitiveNode node) {
    // Note: ExprNode.toSourceString() technically returns a Soy expression. In the case of
    // primitives, the result is usually also the correct Python expression.
    return new PyExpr(node.toSourceString(), Integer.MAX_VALUE);
  }

  @Override protected PyExpr visitStringNode(StringNode node) {
    return new PyStringExpr(node.toSourceString());
  }

  @Override protected PyExpr visitNullNode(NullNode node) {
    // Nulls are represented as 'None' in Python.
    return new PyExpr("None", Integer.MAX_VALUE);
  }

  @Override protected PyExpr visitBooleanNode(BooleanNode node) {
    // Specifically set booleans to 'True' and 'False' given python's strict naming for booleans.
    return new PyExpr(node.getValue() ? "True" : "False", Integer.MAX_VALUE);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.


  @Override protected PyExpr visitListLiteralNode(ListLiteralNode node) {
    StringBuilder listContents = new StringBuilder();

    for (ExprNode child: node.getChildren()) {
      if (listContents.length() > 0) {
        listContents.append(", ");
      }
      listContents.append(visit(child).getText());
    }

    return new PyListExpr("[" + listContents + "]", Integer.MAX_VALUE);
  }

  @Override protected PyExpr visitMapLiteralNode(MapLiteralNode node) {
    StringBuilder mapContents = new StringBuilder();

    Preconditions.checkArgument(node.numChildren() % 2 == 0);
    for (int i = 0, n = node.numChildren(); i < n; i += 2) {
      ExprNode keyNode = node.getChild(i);
      ExprNode valueNode = node.getChild(i + 1);

      if (i > 0) {
        mapContents.append(", ");
      }

      mapContents.append(visit(keyNode).getText())
          .append(": ")
          .append(visit(valueNode).getText());
    }

    return new PyExpr("{" + mapContents + "}", Integer.MAX_VALUE);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.


  @Override protected PyExpr visitVarRefNode(VarRefNode node) {
    return visitNullSafeNode(node);
  }

  @Override protected PyExpr visitDataAccessNode(DataAccessNode node) {
    return visitNullSafeNode(node);
  }

  private PyExpr visitNullSafeNode(ExprNode node) {
    StringBuilder nullSafetyPrefix = new StringBuilder();
    String refText = visitNullSafeNodeRecurse(node, nullSafetyPrefix);

    if (nullSafetyPrefix.length() == 0) {
      return new PyExpr(refText, Integer.MAX_VALUE);
    } else {
      return new PyExpr(
          nullSafetyPrefix + refText,
          PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL));
    }
  }

  private String visitNullSafeNodeRecurse(ExprNode node, StringBuilder nullSafetyPrefix) {

    switch (node.getKind()) {
      case VAR_REF_NODE: {
        VarRefNode varRef = (VarRefNode) node;
        if (varRef.isInjected()) {
          // Case 1: Injected data reference.
          if (varRef.isNullSafeInjected()) {
            nullSafetyPrefix.append("None if opt_ijData is None else ");
          }
          return "opt_ijData" + genCodeForLiteralKeyAccess(varRef.getName());
        } else {
          // TODO(dcphillips): Add support for local variables.
          // Case 2: Data reference.
          return "opt_data" + genCodeForLiteralKeyAccess(varRef.getName());
        }
      }

      case FIELD_ACCESS_NODE:
      case ITEM_ACCESS_NODE: {
        DataAccessNode dataAccess = (DataAccessNode) node;
        // First recursively visit base expression.
        String refText = visitNullSafeNodeRecurse(dataAccess.getBaseExprChild(), nullSafetyPrefix);

        // Generate null safety check for base expression.
        if (dataAccess.isNullSafe()) {
          nullSafetyPrefix.append("None if " + refText + " is None else ");
        }

        // Generate access to field
        if (node.getKind() == ExprNode.Kind.FIELD_ACCESS_NODE) {
          FieldAccessNode fieldAccess = (FieldAccessNode) node;
          return refText + genCodeForFieldAccess(
              fieldAccess.getBaseExprChild().getType(), fieldAccess.getFieldName());
        } else {
          // Generate access to item.
          ItemAccessNode itemAccess = (ItemAccessNode) node;
          if (itemAccess.getKeyExprChild() instanceof IntegerNode) {
            return refText + "[" + ((IntegerNode) itemAccess.getKeyExprChild()).getValue() + "]";
          } else {
            PyExpr keyPyExpr = visit(itemAccess.getKeyExprChild());
            return refText + genCodeForKeyAccess(keyPyExpr.getText());
          }
        }
      }

      default: {
        PyExpr value = visit(node);
        return genMaybeProtect(value, Integer.MAX_VALUE);
      }
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.


  @Override protected PyExpr visitOperatorNode(OperatorNode node) {
    return genPyExprUsingSoySyntax(node);
  }

  @Override protected PyExpr visitEqualOpNode(EqualOpNode node) {
    // Python has stricter type casting rules during equality comparison. To get around this we
    // use our custom utility to emulate the behavior of Soy/JS.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr("runtime.type_safe_eq(" + operandPyExprs.get(0).getText() +
        ", " + operandPyExprs.get(1).getText() + ")", Integer.MAX_VALUE);
  }

  @Override protected PyExpr visitNotEqualOpNode(NotEqualOpNode node) {
    // Invert type_safe_eq.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr("not runtime.type_safe_eq(" + operandPyExprs.get(0).getText() +
        ", " + operandPyExprs.get(1).getText() + ")",
        PyExprUtils.pyPrecedenceForOperator(Operator.NOT));
  }

  @Override protected PyExpr visitPlusOpNode(PlusOpNode node) {
    // Python has stricter type casting between strings and other primitives than Soy, so addition
    // must be sent through the type_safe_add utility to emulate that behavior.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr("runtime.type_safe_add(" + operandPyExprs.get(0).getText() +
        ", " + operandPyExprs.get(1).getText() + ")", Integer.MAX_VALUE);
  }

  @Override protected PyExpr visitConditionalOpNode(ConditionalOpNode node) {
    // Retrieve the operands.
    Operator op = Operator.CONDITIONAL;
    List<SyntaxElement> syntax = op.getSyntax();
    List<PyExpr> operandExprs = visitChildren(node);

    Operand conditionalOperand = ((Operand)syntax.get(0));
    PyExpr conditionalExpr = operandExprs.get(conditionalOperand.getIndex());
    Operand trueOperand = ((Operand)syntax.get(4));
    PyExpr trueExpr = operandExprs.get(trueOperand.getIndex());
    Operand falseOperand = ((Operand)syntax.get(8));
    PyExpr falseExpr = operandExprs.get(falseOperand.getIndex());

    // Python's ternary operator switches the order from <conditional> ? <true> : <false> to
    // <true> if <conditional> else <false>.
    StringBuilder exprSb = new StringBuilder();
    exprSb.append(genMaybeProtect(trueExpr, PyExprUtils.pyPrecedenceForOperator(op)));
    exprSb.append(" if ");
    exprSb.append(genMaybeProtect(conditionalExpr, PyExprUtils.pyPrecedenceForOperator(op)));
    exprSb.append(" else ");
    exprSb.append(genMaybeProtect(falseExpr, PyExprUtils.pyPrecedenceForOperator(op)));

    return new PyExpr(exprSb.toString(), PyExprUtils.pyPrecedenceForOperator(op));
  }

  @Override protected PyExpr visitFunctionNode(FunctionNode node) {
    String fnName = node.getFunctionName();
    int numArgs = node.numChildren();

    // Handle nonplugin functions.
    NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(fnName);
    if (nonpluginFn != null) {
      if (numArgs != nonpluginFn.getNumArgs()) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Function '" + fnName + "' called with the wrong number of arguments" +
                " (function call \"" + node.toSourceString() + "\").");
      }
      switch (nonpluginFn) {
        // TODO(dcphillips): Add loop function support.
        case QUOTE_KEYS_IF_JS:
          // 'quoteKeysIfJs' is ignored in Python.
          return visitMapLiteralNode((MapLiteralNode) node.getChild(0));
        default:
          throw new AssertionError();
      }
    }

    // TODO(dcphillips): Handle plugin functions.
    throw new UnsupportedOperationException("Plugin functions are not yet supported.");
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper for {@code visitDataAccessNode()} to generate the code for a key access, e.g.
   * {@code .get('key')}.
   *
   * @param key The key.
   */
  private static String genCodeForLiteralKeyAccess(String key) {
    return genCodeForKeyAccess("'" + key + "'");
  }

  /**
   * Private helper for {@code visitDataAccessNode()} to generate the code for a key access, e.g.
   * {@code .get(key)}.
   *
   * @param key The key.
   */
  private static String genCodeForKeyAccess(String key) {
    // TODO(dcphillips): If we know that 'key' exists in the node, we can access it directly and
    // avoid a slow 'get()' call.
    return ".get(" + key + ")";
  }

  /**
   * Private helper for {@code visitDataAccessNode()} to generate the code for a field
   * name access, e.g. ".foo" or "['bar']". If the base type is an object type, then it delegates
   * the generation of the Python code to the type object.
   *
   * @param baseType The type of the object that contains the field.
   * @param fieldName The field name.
   */
  private static String genCodeForFieldAccess(SoyType baseType, String fieldName) {
    if (baseType != null && baseType.getKind() == SoyType.Kind.OBJECT) {
      SoyObjectType objType = (SoyObjectType) baseType;
      String accessExpr = objType.getFieldAccessor(fieldName, SoyBackendKind.PYTHON_SRC);
      if (accessExpr != null) {
        return accessExpr;
      }
    }
    return genCodeForLiteralKeyAccess(fieldName);
  }

  /**
   * Generates a Python expression for the given OperatorNode's subtree assuming that the Python
   * expression for the operator uses the same syntax format as the Soy operator.
   *
   * @param opNode The OperatorNode whose subtree to generate a Python expression for.
   * @return The generated Python expression.
   */
  private PyExpr genPyExprUsingSoySyntax(OperatorNode opNode) {
    List<PyExpr> operandPyExprs = visitChildren(opNode);
    String newExpr = ExprUtils.genExprWithNewToken(opNode.getOperator(), operandPyExprs, null);

    return new PyExpr(newExpr, PyExprUtils.pyPrecedenceForOperator(opNode.getOperator()));
  }

  private static String genMaybeProtect(PyExpr expr, int minSafePrecedence) {
    return (expr.getPrecedence() > minSafePrecedence) ? expr.getText() : "(" + expr.getText() + ")";
  }
}
