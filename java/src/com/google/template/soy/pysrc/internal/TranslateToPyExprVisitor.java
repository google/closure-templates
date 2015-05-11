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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
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
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.targetexpr.ExprUtils;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    TranslateToPyExprVisitor create(LocalVariableStack localVarExprs);
  }


  private final LocalVariableStack localVarExprs;

  /** Map of all SoyPySrcFunctions (name to function). */
  private final ImmutableMap<String, SoyPySrcFunction> soyPySrcFunctionsMap;


  @AssistedInject
  TranslateToPyExprVisitor(
      ImmutableMap<String, SoyPySrcFunction> soyPySrcFunctionsMap,
      @Assisted LocalVariableStack localVarExprs,
      ErrorReporter errorReporter) {
    super(errorReporter);
    this.localVarExprs = localVarExprs;
    this.soyPySrcFunctionsMap = soyPySrcFunctionsMap;
  }

  /**
   * Helper mapper to apply {@link #visit} to an iterable of ExprNode.
   */
  private final Function<ExprNode, PyExpr> VISIT_MAPPER = new Function<ExprNode, PyExpr>() {
    @Override
    public PyExpr apply(ExprNode node) {
      return visit(node);
    }
  };


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected PyExpr visitExprRootNode(ExprRootNode node) {
    return visit(node.getRoot());
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
    return PyExprUtils.convertIterableToPyListExpr(
        Iterables.transform(node.getChildren(), VISIT_MAPPER));
  }

  @Override protected PyExpr visitMapLiteralNode(MapLiteralNode node) {
    Preconditions.checkArgument(node.numChildren() % 2 == 0);
    Map<PyExpr, PyExpr> dict = new LinkedHashMap<>();

    for (int i = 0, n = node.numChildren(); i < n; i += 2) {
      ExprNode keyNode = node.getChild(i);
      ExprNode valueNode = node.getChild(i + 1);
      dict.put(visit(keyNode), visit(valueNode));
    }

    return PyExprUtils.convertMapToPyExpr(dict);
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
          return genCodeForLiteralKeyAccess("opt_ijData", varRef.getName());
        } else {
          PyExpr translation = localVarExprs.getVariableExpression(varRef.getName());
          if (translation != null) {
            // Case 2: In-scope local var.
            return translation.getText();
          } else {
            // Case 3: Data reference.
            return genCodeForLiteralKeyAccess("opt_data", varRef.getName());
          }
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
          return genCodeForFieldAccess(
              fieldAccess.getBaseExprChild().getType(), refText, fieldAccess.getFieldName());
        } else {
          ItemAccessNode itemAccess = (ItemAccessNode) node;
          Kind baseKind = itemAccess.getBaseExprChild().getType().getKind();
          PyExpr keyPyExpr = visit(itemAccess.getKeyExprChild());
          if (baseKind == Kind.MAP || baseKind == Kind.RECORD) {
            return genCodeForKeyAccess(refText, keyPyExpr.getText());
          } else {
            return new PyFunctionExprBuilder("runtime.key_safe_data_access")
                .addArg(new PyExpr(refText, Integer.MAX_VALUE))
                .addArg(keyPyExpr).build();
          }
        }
      }

      default: {
        PyExpr value = visit(node);
        return PyExprUtils.maybeProtect(value, Integer.MAX_VALUE).getText();
      }
    }
  }

  @Override protected PyExpr visitGlobalNode(GlobalNode node) {
    return new PyExpr(node.toSourceString(), Integer.MAX_VALUE);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.


  @Override protected PyExpr visitOperatorNode(OperatorNode node) {
    return genPyExprUsingSoySyntax(node);
  }

  @Override protected PyExpr visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    Operator op = Operator.CONDITIONAL;
    int conditionalPrecedence = PyExprUtils.pyPrecedenceForOperator(op);
    List<PyExpr> children = visitChildren(node);

    PyExpr conditionalExpr = PyExprUtils.genPyNotNullCheck(children.get(0));
    PyExpr trueExpr = children.get(0);
    PyExpr falseExpr = children.get(1);
    // TODO(dcphillips): unlike jssrc,Tofu and jbcsrc pysrc evaluates the condition twice.  It would
    // be nice to avoid that. Obvious solutions include.
    // 1. Introduce a local variable:
    // tmp = <left hand side>
    // if tmp is None:
    //   tmp = <right hand side>
    //
    // 2. Use a lambda to defer evaluation of the right hand side.
    // lambda x=<left hand side> : <right hand side> if x is None else x

    StringBuilder exprSb = new StringBuilder();
    exprSb.append(PyExprUtils.maybeProtect(trueExpr, conditionalPrecedence).getText());
    exprSb.append(" if ");
    exprSb.append(PyExprUtils.maybeProtect(conditionalExpr, conditionalPrecedence).getText());
    exprSb.append(" else ");
    exprSb.append(PyExprUtils.maybeProtect(falseExpr, conditionalPrecedence).getText());

    return new PyExpr(exprSb.toString(), conditionalPrecedence);
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
    int conditionalPrecedence = PyExprUtils.pyPrecedenceForOperator(op);
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
    exprSb.append(PyExprUtils.maybeProtect(trueExpr, conditionalPrecedence).getText());
    exprSb.append(" if ");
    exprSb.append(PyExprUtils.maybeProtect(conditionalExpr, conditionalPrecedence).getText());
    exprSb.append(" else ");
    exprSb.append(PyExprUtils.maybeProtect(falseExpr, conditionalPrecedence).getText());

    return new PyExpr(exprSb.toString(), conditionalPrecedence);
  }

  /**
   * Visits function nodes and generates code for build-in function and plugin functions. Guice
   * builds the look-up map for functions through {@link SharedModule#provideSoyFunctionsMap}.
   *
   * @see NonpluginFunction
   * @see SoyPySrcFunction
   */
  @Override protected PyExpr visitFunctionNode(FunctionNode node)  {
    String fnName = node.getFunctionName();
    int numArgs = node.numChildren();

    // Handle nonplugin functions.
    NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(fnName);
    if (nonpluginFn != null) {
      if (numArgs != nonpluginFn.getNumArgs()) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Function '" + fnName + "' called with the wrong number of arguments"
                + " (function call \"" + node.toSourceString() + "\").");
      }
      switch (nonpluginFn) {
        case IS_FIRST:
          return visitForEachFunction(node, "__isFirst");
        case IS_LAST:
          return visitForEachFunction(node, "__isLast");
        case INDEX:
          return visitForEachFunction(node, "__index");
        case QUOTE_KEYS_IF_JS:
          // 'quoteKeysIfJs' is ignored in Python.
          return visitMapLiteralNode((MapLiteralNode) node.getChild(0));
        case CHECK_NOT_NULL:
          return visitCheckNotNull(node);
        default:
          throw new AssertionError();
      }
    }

    // Handle plugin functions.
    SoyPySrcFunction fn = soyPySrcFunctionsMap.get(fnName);
    if (fn != null) {
      if (!fn.getValidArgsSizes().contains(numArgs)) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Function '" + fnName + "' called with the wrong number of arguments" +
                " (function call \"" + node.toSourceString() + "\").");
      }
      List<PyExpr> args = visitChildren(node);
      try {
        return fn.computeForPySrc(args);
      } catch (Exception e) {
        throw SoySyntaxException.createCausedWithoutMetaInfo(
            "Error in function call \"" + node.toSourceString() + "\": " + e.getMessage(), e);
      }
    }

    // Function not found.
    throw SoySyntaxException.createWithoutMetaInfo(
        "Failed to find function with name '" + fnName + "'" +
            " (function call \"" + node.toSourceString() + "\").");
  }

  private PyExpr visitCheckNotNull(FunctionNode node) {
    PyExpr childExpr = visit(node.getChild(0));
    return new PyFunctionExprBuilder("runtime.check_not_null").addArg(childExpr).asPyExpr();
  }

  private PyExpr visitForEachFunction(FunctionNode node, String suffix) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return localVarExprs.getVariableExpression(varName + suffix);
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper for {@code visitDataAccessNode()} to generate the code for key access given a
   * key literal, e.g. {@code .get('key')}.
   *
   * @param key The String literal value to be used as a key.
   */
  private static String genCodeForLiteralKeyAccess(String containerExpr, String key) {
    return genCodeForKeyAccess(containerExpr, "'" + key + "'");
  }

  /**
   * Private helper for {@code visitDataAccessNode()} to generate the code for key access given the
   * name of a variable to be used as a key, e.g. {@code .get(key)}.
   *
   * @param keyName The variable name to be used as a key.
   */
  private static String genCodeForKeyAccess(String containerExpr, String keyName) {
    return containerExpr + ".get(" + keyName + ")";
  }

  /**
   * Private helper for {@code visitDataAccessNode()} to generate the code for a field
   * name access, e.g. ".foo" or "['bar']". If the base type is an object type, then it delegates
   * the generation of the Python code to the type object.
   *
   * @param baseType The type of the object that contains the field.
   * @param containerExpr An expression that evaluates to the container of the named field.
   *     This expression may have any operator precedence that binds more tightly than
   *     exponentiation.
   * @param fieldName The field name.
   */
  private static String genCodeForFieldAccess(
      SoyType baseType, String containerExpr, String fieldName) {
    if (baseType != null && baseType.getKind() == SoyType.Kind.OBJECT) {
      SoyObjectType objType = (SoyObjectType) baseType;
      String accessExpr = objType.getFieldAccessExpr(
          containerExpr, fieldName, SoyBackendKind.PYTHON_SRC);
      if (accessExpr != null) {
        return accessExpr;
      }
    }
    return genCodeForLiteralKeyAccess(containerExpr, fieldName);
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
}
