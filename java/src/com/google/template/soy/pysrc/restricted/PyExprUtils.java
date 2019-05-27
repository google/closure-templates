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

package com.google.template.soy.pysrc.restricted;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.exprtree.Operator.Operand;
import com.google.template.soy.exprtree.Operator.Spacer;
import com.google.template.soy.exprtree.Operator.SyntaxElement;
import com.google.template.soy.exprtree.Operator.Token;
import com.google.template.soy.internal.targetexpr.TargetExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Common utilities for dealing with Python expressions.
 *
 * <p>Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
public final class PyExprUtils {

  /** The variable name used to reference the current translator instance. */
  public static final String TRANSLATOR_NAME = "translator_impl";

  /** Expression constant for empty string. */
  private static final PyExpr EMPTY_STRING = new PyStringExpr("''");

  public static final int CALL_PRECEDENCE = 9;

  public static final int SUBSCRIPT_PRECEDENCE = 9;
  public static final int GETPROP_PRECEDENCE = 9;

  public static final int IN_PRECEDENCE = 5;

  /**
   * Map used to provide operator precedences in Python.
   *
   * @see <a href="https://docs.python.org/2/reference/expressions.html#operator-precedence">Python
   *     operator precedence.</a>
   */
  private static final ImmutableMap<Operator, Integer> PYTHON_PRECEDENCES =
      new ImmutableMap.Builder<Operator, Integer>()
          .put(Operator.NEGATIVE, 8)
          .put(Operator.TIMES, 7)
          .put(Operator.DIVIDE_BY, 7)
          .put(Operator.MOD, 7)
          .put(Operator.PLUS, 6)
          .put(Operator.MINUS, 6)
          .put(Operator.LESS_THAN, 5)
          .put(Operator.GREATER_THAN, 5)
          .put(Operator.LESS_THAN_OR_EQUAL, 5)
          .put(Operator.GREATER_THAN_OR_EQUAL, 5)
          .put(Operator.EQUAL, 5)
          .put(Operator.NOT_EQUAL, 5)
          .put(Operator.NOT, 4)
          .put(Operator.AND, 3)
          .put(Operator.OR, 2)
          .put(Operator.NULL_COALESCING, 1)
          .put(Operator.CONDITIONAL, 1)
          .build();

  private PyExprUtils() {}

  /**
   * Builds one Python expression that computes the concatenation of the given Python expressions.
   *
   * <p>Python doesn't allow arbitrary concatentation between types, so to ensure type safety and
   * consistent behavior, coerce all expressions to Strings before joining them. Python's array
   * joining mechanism is used in place of traditional concatenation to improve performance.
   *
   * @param pyExprs The Python expressions to concatenate.
   * @return One Python expression that computes the concatenation of the given Python expressions.
   */
  public static PyExpr concatPyExprs(List<? extends PyExpr> pyExprs) {

    if (pyExprs.isEmpty()) {
      return EMPTY_STRING;
    }

    if (pyExprs.size() == 1) {
      // If there's only one element, simply return the expression as a String.
      return pyExprs.get(0).toPyString();
    }

    StringBuilder resultSb = new StringBuilder();

    // Use Python's list joining mechanism to speed up concatenation.
    resultSb.append("[");

    boolean isFirst = true;
    for (PyExpr pyExpr : pyExprs) {

      if (isFirst) {
        isFirst = false;
      } else {
        resultSb.append(',');
      }

      resultSb.append(pyExpr.toPyString().getText());
    }

    resultSb.append("]");
    return new PyListExpr(resultSb.toString(), Integer.MAX_VALUE);
  }

  /** Generates a Python not null (None) check expression for the given {@link PyExpr}. */
  public static PyExpr genPyNotNullCheck(PyExpr pyExpr) {
    ImmutableList<PyExpr> exprs = ImmutableList.of(pyExpr, new PyExpr("None", Integer.MAX_VALUE));
    // Note: is/is not is Python's identity comparison. It's used for None checks for performance.
    String conditionalExpr = genExprWithNewToken(Operator.NOT_EQUAL, exprs, "is not");
    return new PyExpr(conditionalExpr, PyExprUtils.pyPrecedenceForOperator(Operator.NOT_EQUAL));
  }

  /** Generates a Python null (None) check expression for the given {@link PyExpr}. */
  public static PyExpr genPyNullCheck(PyExpr expr) {
    ImmutableList<PyExpr> exprs = ImmutableList.of(expr, new PyExpr("None", Integer.MAX_VALUE));
    // Note: is/is not is Python's identity comparison. It's used for None checks for performance.
    String conditionalExpr = genExprWithNewToken(Operator.EQUAL, exprs, "is");
    return new PyExpr(conditionalExpr, PyExprUtils.pyPrecedenceForOperator(Operator.EQUAL));
  }

  /**
   * Wraps an expression with parenthesis if it's not above the minimum safe precedence.
   *
   * <p>NOTE: For the sake of brevity, this implementation loses typing information in the
   * expressions.
   *
   * @param expr The expression to wrap.
   * @param minSafePrecedence The minimum safe precedence (not inclusive).
   * @return The PyExpr potentially wrapped in parenthesis.
   */
  public static PyExpr maybeProtect(PyExpr expr, int minSafePrecedence) {
    // all python operators are left associative, so if this has equivalent precedence we don't need
    // to wrap
    if (expr.getPrecedence() >= minSafePrecedence) {
      return expr;
    } else {
      return new PyExpr("(" + expr.getText() + ")", Integer.MAX_VALUE);
    }
  }

  /**
   * Wraps an expression with the proper SanitizedContent constructor.
   *
   * <p>NOTE: The pyExpr provided must be properly escaped for the given ContentKind. Please talk to
   * ISE (ise@) for any questions or concerns.
   *
   * @param contentKind The kind of sanitized content.
   * @param pyExpr The expression to wrap.
   * @deprecated this method is not safe to use without a security review. Do not use it.
   */
  @Deprecated
  public static PyExpr wrapAsSanitizedContent(ContentKind contentKind, PyExpr pyExpr) {
    if (contentKind == ContentKind.TEXT) {
      return pyExpr;
    }
    String sanitizer =
        NodeContentKinds.toPySanitizedContentOrdainer(
            SanitizedContentKind.valueOf(contentKind.name()));
    String approval =
        "sanitize.IActuallyUnderstandSoyTypeSafetyAndHaveSecurityApproval("
            + "'Internally created Sanitization.')";
    return new PyExpr(
        sanitizer + "(" + pyExpr.getText() + ", approval=" + approval + ")", Integer.MAX_VALUE);
  }

  /**
   * Provide the Python operator precedence for a given operator.
   *
   * @param op The operator.
   * @return THe python precedence as an integer.
   */
  public static int pyPrecedenceForOperator(Operator op) {
    return PYTHON_PRECEDENCES.get(op);
  }

  /**
   * Convert a java Iterable object to valid PyExpr as array.
   *
   * @param iterable Iterable of Objects to be converted to PyExpr, it must be Number, PyExpr or
   *     String.
   */
  public static PyExpr convertIterableToPyListExpr(Iterable<?> iterable) {
    return convertIterableToPyExpr(iterable, true);
  }

  /**
   * Convert a java Iterable object to valid PyExpr as tuple.
   *
   * @param iterable Iterable of Objects to be converted to PyExpr, it must be Number, PyExpr or
   *     String.
   */
  public static PyExpr convertIterableToPyTupleExpr(Iterable<?> iterable) {
    return convertIterableToPyExpr(iterable, false);
  }

  /**
   * Convert a java Map to valid PyExpr as dict.
   *
   * @param dict A Map to be converted to PyExpr as a dictionary, both key and value should be
   *     PyExpr.
   */
  public static PyExpr convertMapToOrderedDict(Map<PyExpr, PyExpr> dict) {
    List<String> values = new ArrayList<>();

    for (Map.Entry<PyExpr, PyExpr> entry : dict.entrySet()) {
      values.add("(" + entry.getKey().getText() + ", " + entry.getValue().getText() + ")");
    }

    Joiner joiner = Joiner.on(", ");
    return new PyExpr("collections.OrderedDict([" + joiner.join(values) + "])", Integer.MAX_VALUE);
  }

  /**
   * Convert a java Map to valid PyExpr as dict.
   *
   * @param dict A Map to be converted to PyExpr as a dictionary, both key and value should be
   *     PyExpr.
   */
  public static PyExpr convertMapToPyExpr(Map<PyExpr, PyExpr> dict) {
    List<String> values = new ArrayList<>();

    for (Map.Entry<PyExpr, PyExpr> entry : dict.entrySet()) {
      values.add(entry.getKey().getText() + ": " + entry.getValue().getText());
    }

    Joiner joiner = Joiner.on(", ");
    return new PyExpr("{" + joiner.join(values) + "}", Integer.MAX_VALUE);
  }

  private static PyExpr convertIterableToPyExpr(Iterable<?> iterable, boolean asArray) {
    List<String> values = new ArrayList<>();
    String leftDelimiter = "[";
    String rightDelimiter = "]";

    if (!asArray) {
      leftDelimiter = "(";
      rightDelimiter = ")";
    }

    for (Object elem : iterable) {
      if (!(elem instanceof Number || elem instanceof String || elem instanceof PyExpr)) {
        throw new UnsupportedOperationException("Only Number, String and PyExpr is allowed");
      } else if (elem instanceof Number) {
        values.add(String.valueOf(elem));
      } else if (elem instanceof PyExpr) {
        values.add(((PyExpr) elem).getText());
      } else if (elem instanceof String) {
        values.add("'" + elem + "'");
      }
    }

    String contents = Joiner.on(", ").join(values);

    // Tuples of one element require an extra comma otherwise the parens just set precedence.
    if (values.size() == 1 && !asArray) {
      contents += ",";
    }
    return new PyListExpr(leftDelimiter + contents + rightDelimiter, Integer.MAX_VALUE);
  }

  /**
   * Generates an expression for the given operator and operands assuming that the expression for
   * the operator uses the same syntax format as the Soy operator, with the exception that the of a
   * different token. Associativity, spacing, and precedence are maintained from the original
   * operator.
   *
   * <p>Examples:
   *
   * <pre>
   * NOT, ["$a"], "!" -> "! $a"
   * AND, ["$a", "$b"], "&&" -> "$a && $b"
   * NOT, ["$a * $b"], "!"; -> "! ($a * $b)"
   * </pre>
   *
   * @param op The operator.
   * @param operandExprs The operands.
   * @param newToken The language specific token equivalent to the operator's original token.
   * @return The generated expression with a new token.
   */
  public static String genExprWithNewToken(
      Operator op, List<? extends TargetExpr> operandExprs, String newToken) {

    int opPrec = op.getPrecedence();
    boolean isLeftAssociative = op.getAssociativity() == Associativity.LEFT;

    StringBuilder exprSb = new StringBuilder();

    // Iterate through the operator's syntax elements.
    List<SyntaxElement> syntax = op.getSyntax();
    for (int i = 0, n = syntax.size(); i < n; i++) {
      SyntaxElement syntaxEl = syntax.get(i);

      if (syntaxEl instanceof Operand) {
        // Retrieve the operand's subexpression.
        int operandIndex = ((Operand) syntaxEl).getIndex();
        TargetExpr operandExpr = operandExprs.get(operandIndex);
        // If left (right) associative, first (last) operand doesn't need protection if it's an
        // operator of equal precedence to this one.
        boolean needsProtection;
        if (i == (isLeftAssociative ? 0 : n - 1)) {
          needsProtection = operandExpr.getPrecedence() < opPrec;
        } else {
          needsProtection = operandExpr.getPrecedence() <= opPrec;
        }
        // Append the operand's subexpression to the expression we're building (if necessary,
        // protected using parentheses).
        String subexpr =
            needsProtection ? "(" + operandExpr.getText() + ")" : operandExpr.getText();
        exprSb.append(subexpr);

      } else if (syntaxEl instanceof Token) {
        // If a newToken is supplied, then use it, else use the token defined by Soy syntax.
        if (newToken != null) {
          exprSb.append(newToken);
        } else {
          exprSb.append(((Token) syntaxEl).getValue());
        }

      } else if (syntaxEl instanceof Spacer) {
        // Spacer is just one space.
        exprSb.append(' ');

      } else {
        throw new AssertionError();
      }
    }

    return exprSb.toString();
  }
}
