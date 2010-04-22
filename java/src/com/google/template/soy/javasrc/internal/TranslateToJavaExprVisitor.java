/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.javasrc.internal;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataRefIndexNode;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.internal.base.CharEscapers;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.UTILS_LIB;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genBinaryOp;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genCoerceBoolean;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genCoerceString;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genFloatValue;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genFunctionCall;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genIntegerValue;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genMaybeCast;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genMaybeProtect;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNewBooleanData;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNewFloatData;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNewIntegerData;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNewStringData;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNumberValue;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genUnaryOp;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysAtLeastOneFloat;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysAtLeastOneString;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysFloat;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysInteger;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysTwoFloatsOrOneFloatOneInteger;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysTwoIntegers;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.shared.internal.ImpureFunction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Visitor for translating a Soy expression (in the form of an {@code ExprNode}) into an
 * equivalent Java expression.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class TranslateToJavaExprVisitor extends AbstractExprNodeVisitor<JavaExpr> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface TranslateToJavaExprVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement Java expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     */
    public TranslateToJavaExprVisitor create(Deque<Map<String, JavaExpr>> localVarTranslations);
  }


  /** Map of all SoyJavaSrcFunctions (name to function). */
  private final Map<String, SoyJavaSrcFunction> soyJavaSrcFunctionsMap;

  /** The current stack of replacement Java expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, JavaExpr>> localVarTranslations;

  /** Stack of partial results (during a pass). */
  private Deque<JavaExpr> resultStack;


  /**
   * @param soyJavaSrcFunctionsMap Map of all SoyJavaSrcFunctions (name to function).
   * @param localVarTranslations The current stack of replacement Java expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   */
  @AssistedInject
  TranslateToJavaExprVisitor(
      Map<String, SoyJavaSrcFunction> soyJavaSrcFunctionsMap,
      @Assisted Deque<Map<String, JavaExpr>> localVarTranslations) {
    this.soyJavaSrcFunctionsMap = soyJavaSrcFunctionsMap;
    this.localVarTranslations = localVarTranslations;
  }


  @Override protected void setup() {
    resultStack = new ArrayDeque<JavaExpr>();
  }


  @Override protected JavaExpr getResult() {
    return resultStack.peek();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected void visitInternal(ExprRootNode<? extends ExprNode> node) {
    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives and data references (concrete classes).


  @Override protected void visitInternal(NullNode node) {
    resultStack.push(new JavaExpr(
        "com.google.template.soy.data.restricted.NullData.INSTANCE",
        NullData.class, Integer.MAX_VALUE));
  }


  @Override protected void visitInternal(BooleanNode node) {
    // Soy boolean literals have same form as Java 'boolean' literals.
    pushBooleanResult(genNewBooleanData(node.toSourceString()));
  }


  @Override protected void visitInternal(IntegerNode node) {
    // Soy integer literals have same form as Java 'int' literals.
    pushIntegerResult(genNewIntegerData(node.toSourceString()));
  }


  @Override protected void visitInternal(FloatNode node) {
    // Soy float literals have same form as Java 'double' literals.
    pushFloatResult(genNewFloatData(node.toSourceString()));
  }


  @Override protected void visitInternal(StringNode node) {
    pushStringResult(genNewStringData(
        '"' + CharEscapers.javaStringEscaper().escape(node.getValue()) + '"'));
  }


  @Override protected void visitInternal(DataRefNode node) {

    String firstPart = ((DataRefKeyNode) node.getChild(0)).getKey();

    JavaExpr translation = getLocalVarTranslation(firstPart);
    if (translation != null) {
      // Case 1: In-scope local var.
      if (node.numChildren() == 1) {
        resultStack.push(translation);
      } else {
        pushUnknownResult(genFunctionCall(
            UTILS_LIB + ".$$getData",
            genMaybeCast(translation, CollectionData.class), buildKeyStringExprText(node, 1)));
      }

    } else {
      // Case 2: Data reference.
      pushUnknownResult(genFunctionCall(
          UTILS_LIB + ".$$getData", "data", buildKeyStringExprText(node, 0)));
    }
  }


  /**
   * Private helper for visitInternal(DataRefNode).
   * @param node -
   * @param startIndex -
   */
  private String buildKeyStringExprText(DataRefNode node, int startIndex) {

    List<String> keyStrParts = Lists.newArrayList();
    StringBuilder currStringLiteralPart = new StringBuilder();

    for (int i = startIndex; i < node.numChildren(); i++) {
      ExprNode child = node.getChild(i);

      if (i != startIndex) {
        currStringLiteralPart.append(".");
      }

      if (child instanceof DataRefKeyNode) {
        currStringLiteralPart.append(
            CharEscapers.javaStringEscaper().escape(((DataRefKeyNode) child).getKey()));
      } else if (child instanceof DataRefIndexNode) {
        currStringLiteralPart.append(Integer.toString(((DataRefIndexNode) child).getIndex()));
      } else {
        visit(child);
        keyStrParts.add("\"" + currStringLiteralPart.toString() + "\"");
        keyStrParts.add(genMaybeProtect(resultStack.pop(), Integer.MAX_VALUE) + ".toString()");
        currStringLiteralPart = new StringBuilder();
      }
    }

    if (currStringLiteralPart.length() > 0) {
      keyStrParts.add("\"" + currStringLiteralPart.toString() + "\"");
    }

    return Joiner.on(" + ").join(keyStrParts);
  }


  @Override protected void visitInternal(GlobalNode node) {
    throw new UnsupportedOperationException();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators (concrete classes).


  @Override protected void visitInternal(NegativeOpNode node) {

    visitChildren(node);
    JavaExpr operand = resultStack.pop();

    String integerComputation = genNewIntegerData(genUnaryOp("-", genIntegerValue(operand)));
    String floatComputation = genNewFloatData(genUnaryOp("-", genFloatValue(operand)));

    if (isAlwaysInteger(operand)) {
      pushIntegerResult(integerComputation);
    } else if (isAlwaysFloat(operand)) {
      pushFloatResult(floatComputation);
    } else {
      pushNumberResult(genFunctionCall(UTILS_LIB + ".$$negative", operand.getText()));
    }
  }


  @Override protected void visitInternal(NotOpNode node) {

    visitChildren(node);
    JavaExpr operand = resultStack.pop();
    pushBooleanResult(genNewBooleanData(genUnaryOp("!", genCoerceBoolean(operand))));
  }


  @Override protected void visitInternal(TimesOpNode node) {
    visitNumberToNumberBinaryOpHelper(node, "*", "$$times");
  }


  @Override protected void visitInternal(DivideByOpNode node) {

    visitChildren(node);
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
    pushFloatResult(genNewFloatData(genBinaryOp(
        "/", genNumberValue(operand0), genNumberValue(operand1))));
  }


  @Override protected void visitInternal(ModOpNode node) {

    visitChildren(node);
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    pushIntegerResult(genNewIntegerData(genBinaryOp(
        "%", genIntegerValue(operand0), genIntegerValue(operand1))));
  }


  @Override protected void visitInternal(PlusOpNode node) {

    visitChildren(node);
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    String stringComputation = genNewStringData(genBinaryOp(
        "+", genCoerceString(operand0), genCoerceString(operand1)));
    String integerComputation = genNewIntegerData(genBinaryOp(
        "+", genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewFloatData(genBinaryOp(
        "+", genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      pushIntegerResult(integerComputation);
    } else if (isAlwaysAtLeastOneString(operand0, operand1)) {
      pushStringResult(stringComputation);
    } else if (isAlwaysTwoFloatsOrOneFloatOneInteger(operand0, operand1)) {
      pushFloatResult(floatComputation);
    } else {
      pushNumberResult(genFunctionCall(
          UTILS_LIB + ".$$plus", operand0.getText(), operand1.getText()));
    }
  }


  @Override protected void visitInternal(MinusOpNode node) {
    visitNumberToNumberBinaryOpHelper(node, "-", "$$minus");
  }


  @Override protected void visitInternal(LessThanOpNode node) {
    visitNumberToBooleanBinaryOpHelper(node, "<", "$$lessThan");
  }


  @Override protected void visitInternal(GreaterThanOpNode node) {
    visitNumberToBooleanBinaryOpHelper(node, ">", "$$greaterThan");
  }


  @Override protected void visitInternal(LessThanOrEqualOpNode node) {
    visitNumberToBooleanBinaryOpHelper(node, "<=", "$$lessThanOrEqual");
  }


  @Override protected void visitInternal(GreaterThanOrEqualOpNode node) {
    visitNumberToBooleanBinaryOpHelper(node, ">=", "$$greaterThanOrEqual");
  }


  @Override protected void visitInternal(EqualOpNode node) {

    visitChildren(node);
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    pushBooleanResult(genNewBooleanData(
        genMaybeProtect(operand0, Integer.MAX_VALUE) + ".equals(" + operand1.getText() + ")"));
  }


  @Override protected void visitInternal(NotEqualOpNode node) {

    visitChildren(node);
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    pushBooleanResult(genNewBooleanData(
        "! " + genMaybeProtect(operand0, Integer.MAX_VALUE) + ".equals(" +
        operand1.getText() + ")"));
  }


  @Override protected void visitInternal(AndOpNode node) {
    visitBooleanToBooleanBinaryOpHelper(node, "&&");
  }


  @Override protected void visitInternal(OrOpNode node) {
    visitBooleanToBooleanBinaryOpHelper(node, "||");
  }


  @Override protected void visitInternal(ConditionalOpNode node) {

    visitChildren(node);
    JavaExpr operand2 = resultStack.pop();
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    Class<?> type1 = operand1.getType();
    Class<?> type2 = operand2.getType();
    // Set result type to nearest common ancestor of type1 and type2.
    Class<?> resultType = null;
    for (Class<?> type = type1; type != null; type = type.getSuperclass()) {
      if (type.isAssignableFrom(type2)) {
        resultType = type;
        break;
      }
    }
    if (resultType == null) {
      throw new AssertionError();
    }

    resultStack.push(new JavaExpr(
        genCoerceBoolean(operand0) + " ? " +
        genMaybeProtect(operand1, Operator.CONDITIONAL.getPrecedence() + 1) + " : " +
        genMaybeProtect(operand2, Operator.CONDITIONAL.getPrecedence() + 1),
        resultType, Operator.CONDITIONAL.getPrecedence()));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for functions.


  @Override protected void visitInternal(FunctionNode node) {

    String fnName = node.getFunctionName();
    int numArgs = node.numChildren();

    // Handle impure functions.
    ImpureFunction impureFn = ImpureFunction.forFunctionName(fnName);
    if (impureFn != null) {
      if (numArgs != impureFn.getNumArgs()) {
        throw new SoySyntaxException(
            "Function '" + fnName + "' called with the wrong number of arguments" +
            " (function call \"" + node.toSourceString() + "\").");
      }
      switch (impureFn) {
        case IS_FIRST:
          visitIsFirstFunction(node);
          return;
        case IS_LAST:
          visitIsLastFunction(node);
          return;
        case INDEX:
          visitIndexFunction(node);
          return;
        case HAS_DATA:
          visitHasDataFunction();
          return;
        default:
          throw new AssertionError();
      }
    }

    // Handle pure functions.
    SoyJavaSrcFunction fn = soyJavaSrcFunctionsMap.get(fnName);
    if (fn != null) {
      if (! fn.getValidArgsSizes().contains(numArgs)) {
        throw new SoySyntaxException(
            "Function '" + fnName + "' called with the wrong number of arguments" +
            " (function call \"" + node.toSourceString() + "\").");
      }
      List<JavaExpr> args = Lists.newArrayList();
      for (ExprNode child : node.getChildren()) {
        visit(child);
        args.add(resultStack.pop());
      }
      try {
        resultStack.push(fn.computeForJavaSrc(args));
      } catch (Exception e) {
        throw new SoySyntaxException(
            "Error in function call \"" + node.toSourceString() + "\": " + e.getMessage(), e);
      }
      return;
    }

    throw new SoySyntaxException(
        "Failed to find SoyJavaSrcFunction with name '" + fnName + "'" +
        " (function call \"" + node.toSourceString() + "\").");
  }


  private void visitIsFirstFunction(FunctionNode node) {
    String varName = ((DataRefKeyNode) ((DataRefNode) node.getChild(0)).getChild(0)).getKey();
    resultStack.push(getLocalVarTranslation(varName + "__isFirst"));
  }


  private void visitIsLastFunction(FunctionNode node) {
    String varName = ((DataRefKeyNode) ((DataRefNode) node.getChild(0)).getChild(0)).getKey();
    resultStack.push(getLocalVarTranslation(varName + "__isLast"));
  }


  private void visitIndexFunction(FunctionNode node) {
    String varName = ((DataRefKeyNode) ((DataRefNode) node.getChild(0)).getChild(0)).getKey();
    resultStack.push(getLocalVarTranslation(varName + "__index"));
  }


  private void visitHasDataFunction() {
    pushBooleanResult(genNewBooleanData("data != null"));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper to push a BooleanData expression onto the result stack.
   * @param exprText The expression text that computes a BooleanData.
   */
  private void pushBooleanResult(String exprText) {
    resultStack.push(new JavaExpr(exprText, BooleanData.class, Integer.MAX_VALUE));
  }


  /**
   * Private helper to push an IntegerData expression onto the result stack.
   * @param exprText The expression text that computes an IntegerData.
   */
  private void pushIntegerResult(String exprText) {
    resultStack.push(new JavaExpr(exprText, IntegerData.class, Integer.MAX_VALUE));
  }


  /**
   * Private helper to push a FloatData expression onto the result stack.
   * @param exprText The expression text that computes a DoubleData.
   */
  private void pushFloatResult(String exprText) {
    resultStack.push(new JavaExpr(exprText, FloatData.class, Integer.MAX_VALUE));
  }


  private void pushNumberResult(String exprText) {
    resultStack.push(new JavaExpr(exprText, NumberData.class, Integer.MAX_VALUE));
  }


  /**
   * Private helper to push a StringData expression onto the result stack.
   * @param exprText The expression text that computes a StringData.
   */
  private void pushStringResult(String exprText) {
    resultStack.push(new JavaExpr(exprText, StringData.class, Integer.MAX_VALUE));
  }


  private void pushUnknownResult(String exprText) {
    resultStack.push(new JavaExpr(exprText, SoyData.class, Integer.MAX_VALUE));
  }


  private void visitBooleanToBooleanBinaryOpHelper(OperatorNode node, String javaOpToken) {

    visitChildren(node);
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    pushBooleanResult(genNewBooleanData(genBinaryOp(
        javaOpToken, genCoerceBoolean(operand0), genCoerceBoolean(operand1))));
  }


  private void visitNumberToNumberBinaryOpHelper(
      OperatorNode node, String javaOpToken, String utilsLibFnName) {

    visitChildren(node);
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    String integerComputation = genNewIntegerData(genBinaryOp(
        javaOpToken, genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewFloatData(genBinaryOp(
        javaOpToken, genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      pushIntegerResult(integerComputation);
    } else if (isAlwaysAtLeastOneFloat(operand0, operand1)) {
      pushFloatResult(floatComputation);
    } else {
      pushNumberResult(genFunctionCall(
          UTILS_LIB + "." + utilsLibFnName,
          genMaybeCast(operand0, NumberData.class), genMaybeCast(operand1, NumberData.class)));
    }
  }


  private void visitNumberToBooleanBinaryOpHelper(
      OperatorNode node, String javaOpToken, String utilsLibFnName) {

    visitChildren(node);
    JavaExpr operand1 = resultStack.pop();
    JavaExpr operand0 = resultStack.pop();

    String integerComputation = genNewBooleanData(genBinaryOp(
        javaOpToken, genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewBooleanData(genBinaryOp(
        javaOpToken, genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      pushBooleanResult(integerComputation);
    } else if (isAlwaysAtLeastOneFloat(operand0, operand1)) {
      pushBooleanResult(floatComputation);
    } else {
      pushBooleanResult(genFunctionCall(
          UTILS_LIB + "." + utilsLibFnName,
          genMaybeCast(operand0, NumberData.class), genMaybeCast(operand1, NumberData.class)));
    }
  }


  /**
   * Gets the translated expression for an in-scope local variable (or special "variable" derived
   * from a foreach-loop var), or null if not found.
   * @param ident The Soy local variable to translate.
   * @return The translated expression for the given variable, or null if not found.
   */
  private JavaExpr getLocalVarTranslation(String ident) {

    for (Map<String, JavaExpr> localVarTranslationsFrame : localVarTranslations) {
      JavaExpr translation = localVarTranslationsFrame.get(ident);
      if (translation != null) {
        return translation;
      }
    }

    return null;
  }

}
