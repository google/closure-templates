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
import com.google.common.collect.Iterables;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
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
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visitor for translating a Soy expression (in the form of an {@link ExprNode}) into an equivalent
 * Python expression.
 *
 */
public final class TranslateToPyExprVisitor extends AbstractReturningExprNodeVisitor<PyExpr> {

  private static final SoyErrorKind PROTO_ACCESS_NOT_SUPPORTED =
      SoyErrorKind.of("Proto accessors are not supported in pysrc.");
  private static final SoyErrorKind PROTO_INIT_NOT_SUPPORTED =
      SoyErrorKind.of("Proto init is not supported in pysrc.");
  private static final SoyErrorKind SOY_PY_SRC_FUNCTION_NOT_FOUND =
      SoyErrorKind.of("Failed to find SoyPySrcFunction ''{0}''.");

  /**
   * Errors in this visitor generate Python source that immediately explodes. Users of Soy are
   * expected to check the error reporter before using the gencode; if they don't, this should
   * apprise them. TODO(brndn): consider changing the visitor to return {@code Optional<PyExpr>} and
   * returning {@link Optional#absent()} on error.
   */
  private static final PyExpr ERROR =
      new PyExpr("raise Exception('Soy compilation failed')", Integer.MAX_VALUE);

  private final LocalVariableStack localVarExprs;

  private final ErrorReporter errorReporter;

  TranslateToPyExprVisitor(LocalVariableStack localVarExprs, ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.localVarExprs = localVarExprs;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.

  @Override
  protected PyExpr visitExprRootNode(ExprRootNode node) {
    return visit(node.getRoot());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.

  @Override
  protected PyExpr visitPrimitiveNode(PrimitiveNode node) {
    // Note: ExprNode.toSourceString() technically returns a Soy expression. In the case of
    // primitives, the result is usually also the correct Python expression.
    return new PyExpr(node.toSourceString(), Integer.MAX_VALUE);
  }

  @Override
  protected PyExpr visitStringNode(StringNode node) {
    return new PyStringExpr(node.toSourceString());
  }

  @Override
  protected PyExpr visitNullNode(NullNode node) {
    // Nulls are represented as 'None' in Python.
    return new PyExpr("None", Integer.MAX_VALUE);
  }

  @Override
  protected PyExpr visitBooleanNode(BooleanNode node) {
    // Specifically set booleans to 'True' and 'False' given python's strict naming for booleans.
    return new PyExpr(node.getValue() ? "True" : "False", Integer.MAX_VALUE);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.

  @Override
  protected PyExpr visitListLiteralNode(ListLiteralNode node) {
    return PyExprUtils.convertIterableToPyListExpr(
        Iterables.transform(
            node.getChildren(),
            new Function<ExprNode, PyExpr>() {
              @Override
              public PyExpr apply(ExprNode node) {
                return visit(node);
              }
            }));
  }

  @Override
  protected PyExpr visitMapLiteralNode(MapLiteralNode node) {
    Preconditions.checkArgument(node.numChildren() % 2 == 0);
    Map<PyExpr, PyExpr> dict = new LinkedHashMap<>();

    for (int i = 0, n = node.numChildren(); i < n; i += 2) {
      ExprNode keyNode = node.getChild(i);
      ExprNode valueNode = node.getChild(i + 1);
      dict.put(visit(keyNode), visit(valueNode));
    }

    return PyExprUtils.convertMapToOrderedDict(dict);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override
  protected PyExpr visitVarRefNode(VarRefNode node) {
    return visitNullSafeNode(node);
  }

  @Override
  protected PyExpr visitDataAccessNode(DataAccessNode node) {
    return visitNullSafeNode(node);
  }

  private PyExpr visitNullSafeNode(ExprNode node) {
    StringBuilder nullSafetyPrefix = new StringBuilder();
    String refText = visitNullSafeNodeRecurse(node, nullSafetyPrefix);

    if (nullSafetyPrefix.length() == 0) {
      return new PyExpr(refText, Integer.MAX_VALUE);
    } else {
      return new PyExpr(
          nullSafetyPrefix + refText, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL));
    }
  }

  private String visitNullSafeNodeRecurse(ExprNode node, StringBuilder nullSafetyPrefix) {
    switch (node.getKind()) {
      case VAR_REF_NODE:
        {
          VarRefNode varRef = (VarRefNode) node;
          if (varRef.isInjected()) {
            // Case 1: Injected data reference.
            return genCodeForLiteralKeyAccess("ijData", varRef.getName());
          } else {
            PyExpr translation = localVarExprs.getVariableExpression(varRef.getName());
            if (translation != null) {
              // Case 2: In-scope local var.
              return translation.getText();
            } else {
              // Case 3: Data reference.
              return genCodeForLiteralKeyAccess("data", varRef.getName());
            }
          }
        }

      case FIELD_ACCESS_NODE:
      case ITEM_ACCESS_NODE:
        {
          DataAccessNode dataAccess = (DataAccessNode) node;
          // First recursively visit base expression.
          String refText =
              visitNullSafeNodeRecurse(dataAccess.getBaseExprChild(), nullSafetyPrefix);

          // Generate null safety check for base expression.
          if (dataAccess.isNullSafe()) {
            nullSafetyPrefix.append("None if ").append(refText).append(" is None else ");
          }

          // Generate access to field
          if (node.getKind() == ExprNode.Kind.FIELD_ACCESS_NODE) {
            FieldAccessNode fieldAccess = (FieldAccessNode) node;
            return genCodeForFieldAccess(
                fieldAccess,
                fieldAccess.getBaseExprChild().getType(),
                refText,
                fieldAccess.getFieldName());
          } else {
            ItemAccessNode itemAccess = (ItemAccessNode) node;
            Kind baseKind = itemAccess.getBaseExprChild().getType().getKind();
            PyExpr keyPyExpr = visit(itemAccess.getKeyExprChild());
            if (baseKind == Kind.MAP || baseKind == Kind.RECORD) {
              return genCodeForKeyAccess(refText, keyPyExpr.getText());
            } else {
              return new PyFunctionExprBuilder("runtime.key_safe_data_access")
                  .addArg(new PyExpr(refText, Integer.MAX_VALUE))
                  .addArg(keyPyExpr)
                  .build();
            }
          }
        }

      default:
        {
          PyExpr value = visit(node);
          return PyExprUtils.maybeProtect(value, Integer.MAX_VALUE).getText();
        }
    }
  }

  @Override
  protected PyExpr visitGlobalNode(GlobalNode node) {
    return visit(node.getValue());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override
  protected PyExpr visitOperatorNode(OperatorNode node) {
    return genPyExprUsingSoySyntax(node);
  }

  @Override
  protected PyExpr visitNullCoalescingOpNode(NullCoalescingOpNode node) {
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
    return genTernaryConditional(conditionalExpr, trueExpr, falseExpr);
  }

  @Override
  protected PyExpr visitEqualOpNode(EqualOpNode node) {
    // Python has stricter type casting rules during equality comparison. To get around this we
    // use our custom utility to emulate the behavior of Soy/JS.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr(
        "runtime.type_safe_eq("
            + operandPyExprs.get(0).getText()
            + ", "
            + operandPyExprs.get(1).getText()
            + ")",
        Integer.MAX_VALUE);
  }

  @Override
  protected PyExpr visitNotEqualOpNode(NotEqualOpNode node) {
    // Invert type_safe_eq.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr(
        "not runtime.type_safe_eq("
            + operandPyExprs.get(0).getText()
            + ", "
            + operandPyExprs.get(1).getText()
            + ")",
        PyExprUtils.pyPrecedenceForOperator(Operator.NOT));
  }

  @Override
  protected PyExpr visitPlusOpNode(PlusOpNode node) {
    // Python has stricter type casting between strings and other primitives than Soy, so addition
    // must be sent through the type_safe_add utility to emulate that behavior.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr(
        "runtime.type_safe_add("
            + operandPyExprs.get(0).getText()
            + ", "
            + operandPyExprs.get(1).getText()
            + ")",
        Integer.MAX_VALUE);
  }

  @Override
  protected PyExpr visitConditionalOpNode(ConditionalOpNode node) {
    // Retrieve the operands.
    Operator op = Operator.CONDITIONAL;
    List<SyntaxElement> syntax = op.getSyntax();
    List<PyExpr> operandExprs = visitChildren(node);

    Operand conditionalOperand = ((Operand) syntax.get(0));
    PyExpr conditionalExpr = operandExprs.get(conditionalOperand.getIndex());
    Operand trueOperand = ((Operand) syntax.get(4));
    PyExpr trueExpr = operandExprs.get(trueOperand.getIndex());
    Operand falseOperand = ((Operand) syntax.get(8));
    PyExpr falseExpr = operandExprs.get(falseOperand.getIndex());

    return genTernaryConditional(conditionalExpr, trueExpr, falseExpr);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The source of available functions is a look-up map provided by Guice in {@link
   * SharedModule#provideSoyFunctionsMap}.
   *
   * @see BuiltinFunction
   * @see SoyPySrcFunction
   */
  @Override
  protected PyExpr visitFunctionNode(FunctionNode node) {
    SoyFunction soyFunction = node.getSoyFunction();
    if (soyFunction instanceof BuiltinFunction) {
      return visitNonPluginFunction(node, (BuiltinFunction) soyFunction);
    } else if (soyFunction instanceof SoyPySrcFunction) {
      List<PyExpr> args = visitChildren(node);
      return ((SoyPySrcFunction) soyFunction).computeForPySrc(args);
    } else {
      errorReporter.report(
          node.getSourceLocation(), SOY_PY_SRC_FUNCTION_NOT_FOUND, node.getFunctionName());
      return ERROR;
    }
  }

  private PyExpr visitNonPluginFunction(FunctionNode node, BuiltinFunction nonpluginFn) {
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
        return visitCheckNotNullFunction(node);
      case CSS:
        return visitCssFunction(node);
      case XID:
        return visitXidFunction(node);
      case V1_EXPRESSION:
        throw new UnsupportedOperationException(
            "the v1Expression function can't be used in templates compiled to Python");
      default:
        throw new AssertionError();
    }
  }

  private PyExpr visitForEachFunction(FunctionNode node, String suffix) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return localVarExprs.getVariableExpression(varName + suffix);
  }

  private PyExpr visitCheckNotNullFunction(FunctionNode node) {
    PyExpr childExpr = visit(node.getChild(0));
    return new PyFunctionExprBuilder("runtime.check_not_null").addArg(childExpr).asPyExpr();
  }

  private PyExpr visitCssFunction(FunctionNode node) {
    return new PyFunctionExprBuilder("runtime.get_css_name")
        .addArgs(visitChildren(node))
        .asPyExpr();
  }

  private PyExpr visitXidFunction(FunctionNode node) {
    return new PyFunctionExprBuilder("runtime.get_xid_name")
        .addArg(visit(node.getChild(0)))
        .asPyExpr();
  }

  /**
   * Generates the code for key access given a key literal, e.g. {@code .get('key')}.
   *
   * @param key the String literal value to be used as a key
   */
  private static String genCodeForLiteralKeyAccess(String containerExpr, String key) {
    return genCodeForKeyAccess(containerExpr, "'" + key + "'");
  }

  /**
   * Generates the code for key access given the name of a variable to be used as a key, e.g. {@code
   * .get(key)}.
   *
   * @param keyName the variable name to be used as a key
   */
  private static String genCodeForKeyAccess(String containerExpr, String keyName) {
    return containerExpr + ".get(" + keyName + ")";
  }

  /**
   * Generates the code for a field name access, e.g. ".foo" or "['bar']".
   *
   * @param node the field access source node
   * @param baseType the type of the object that contains the field
   * @param containerExpr an expression that evaluates to the container of the named field. This
   *     expression may have any operator precedence that binds more tightly than exponentiation.
   * @param fieldName the field name
   */
  private String genCodeForFieldAccess(
      ExprNode node, SoyType baseType, String containerExpr, String fieldName) {
    if (baseType != null && baseType.getKind() == SoyType.Kind.PROTO) {
      errorReporter.report(node.getSourceLocation(), PROTO_ACCESS_NOT_SUPPORTED);
      return ".ERROR";
    }
    return genCodeForLiteralKeyAccess(containerExpr, fieldName);
  }

  /**
   * Generates a Python expression for the given OperatorNode's subtree assuming that the Python
   * expression for the operator uses the same syntax format as the Soy operator.
   *
   * @param opNode the OperatorNode whose subtree to generate a Python expression for
   * @return the generated Python expression
   */
  private PyExpr genPyExprUsingSoySyntax(OperatorNode opNode) {
    List<PyExpr> operandPyExprs = visitChildren(opNode);
    String newExpr = PyExprUtils.genExprWithNewToken(opNode.getOperator(), operandPyExprs, null);

    return new PyExpr(newExpr, PyExprUtils.pyPrecedenceForOperator(opNode.getOperator()));
  }

  /**
   * Generates a ternary conditional Python expression given the conditional and true/false
   * expressions.
   *
   * @param conditionalExpr the conditional expression
   * @param trueExpr the expression to execute if the conditional executes to true
   * @param falseExpr the expression to execute if the conditional executes to false
   * @return a ternary conditional expression
   */
  private PyExpr genTernaryConditional(PyExpr conditionalExpr, PyExpr trueExpr, PyExpr falseExpr) {
    // Python's ternary operator switches the order from <conditional> ? <true> : <false> to
    // <true> if <conditional> else <false>.
    int conditionalPrecedence = PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL);
    StringBuilder exprSb =
        new StringBuilder()
            .append(PyExprUtils.maybeProtect(trueExpr, conditionalPrecedence).getText())
            .append(" if ")
            .append(PyExprUtils.maybeProtect(conditionalExpr, conditionalPrecedence).getText())
            .append(" else ")
            .append(PyExprUtils.maybeProtect(falseExpr, conditionalPrecedence).getText());

    return new PyExpr(exprSb.toString(), conditionalPrecedence);
  }

  @Override
  protected PyExpr visitProtoInitNode(ProtoInitNode node) {
    errorReporter.report(node.getSourceLocation(), PROTO_INIT_NOT_SUPPORTED);
    return ERROR;
  }
}
