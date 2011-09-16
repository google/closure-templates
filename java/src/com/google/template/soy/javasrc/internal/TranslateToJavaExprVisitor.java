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
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNewListData;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNewMapData;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNewStringData;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genNumberValue;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genUnaryOp;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysAtLeastOneFloat;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysAtLeastOneString;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysFloat;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysInteger;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysTwoFloatsOrOneFloatOneInteger;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.isAlwaysTwoIntegers;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataRefIndexNode;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
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
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.shared.internal.ImpureFunction;

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
public class TranslateToJavaExprVisitor extends AbstractReturningExprNodeVisitor<JavaExpr> {


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


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected JavaExpr visitExprRootNode(ExprRootNode<?> node) {
    return visit(node.getChild(0));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.


  @Override protected JavaExpr visitNullNode(NullNode node) {
    return new JavaExpr(
        "com.google.template.soy.data.restricted.NullData.INSTANCE",
        NullData.class, Integer.MAX_VALUE);
  }


  @Override protected JavaExpr visitBooleanNode(BooleanNode node) {
    // Soy boolean literals have same form as Java 'boolean' literals.
    return convertBooleanResult(genNewBooleanData(node.toSourceString()));
  }


  @Override protected JavaExpr visitIntegerNode(IntegerNode node) {
    // Soy integer literals have same form as Java 'int' literals.
    return convertIntegerResult(genNewIntegerData(node.toSourceString()));
  }


  @Override protected JavaExpr visitFloatNode(FloatNode node) {
    // Soy float literals have same form as Java 'double' literals.
    return convertFloatResult(genNewFloatData(node.toSourceString()));
  }


  @Override protected JavaExpr visitStringNode(StringNode node) {
    return convertStringResult(genNewStringData(
        '"' + CharEscapers.javaStringEscaper().escape(node.getValue()) + '"'));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.


  @Override protected JavaExpr visitListLiteralNode(ListLiteralNode node) {
    return convertListResult(genNewListData(buildCommaSepChildrenListHelper(node)));
  }


  @Override protected JavaExpr visitMapLiteralNode(MapLiteralNode node) {
    return convertMapResult(genNewMapData(buildCommaSepChildrenListHelper(node)));
  }


  /**
   * Private helper for visitListLiteralNode() and visitMapLiteralNode() to build a
   * comma-separated list of children expression texts.
   * @param node The parent node whose children should be visited and then the resulting expression
   *     texts joined into a comma-separated list.
   * @return A comma-separated list of children expression texts.
   */
  private String buildCommaSepChildrenListHelper(ParentExprNode node) {

    StringBuilder resultSb = new StringBuilder();
    boolean isFirst = true;
    for (ExprNode child : node.getChildren()) {
      if (isFirst) {
        isFirst = false;
      } else {
        resultSb.append(", ");
      }
      resultSb.append(visit(child).getText());
    }
    return resultSb.toString();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.


  @Override protected JavaExpr visitDataRefNode(DataRefNode node) {

    if (node.isIjDataRef()) {
      // Case 1: $ij data reference.
      return convertUnknownResult(genFunctionCall(
          "this.$$getIjData", buildKeyStringExprText(node, 0)));

    } else {
      JavaExpr translation = getLocalVarTranslation(node.getFirstKey());
      if (translation != null) {
        // Case 2: In-scope local var.
        if (node.numChildren() == 1) {
          return translation;
        } else {
          return convertUnknownResult(genFunctionCall(
              UTILS_LIB + ".$$getData",
              genMaybeCast(translation, CollectionData.class), buildKeyStringExprText(node, 1)));
        }

      } else {
        // Case 3: Data reference.
        return convertUnknownResult(genFunctionCall(
            UTILS_LIB + ".$$getData", "data", buildKeyStringExprText(node, 0)));
      }
    }
  }


  /**
   * Private helper for visitDataRefNode(DataRefNode).
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
        JavaExpr childJavaExpr = visit(child);
        keyStrParts.add("\"" + currStringLiteralPart.toString() + "\"");
        keyStrParts.add(genMaybeProtect(childJavaExpr, Integer.MAX_VALUE) + ".toString()");
        currStringLiteralPart = new StringBuilder();
      }
    }

    if (currStringLiteralPart.length() > 0) {
      keyStrParts.add("\"" + currStringLiteralPart.toString() + "\"");
    }

    return Joiner.on(" + ").join(keyStrParts);
  }


  @Override protected JavaExpr visitGlobalNode(GlobalNode node) {
    throw new UnsupportedOperationException();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.


  @Override protected JavaExpr visitNegativeOpNode(NegativeOpNode node) {

    JavaExpr operand = visit(node.getChild(0));

    String integerComputation = genNewIntegerData(genUnaryOp("-", genIntegerValue(operand)));
    String floatComputation = genNewFloatData(genUnaryOp("-", genFloatValue(operand)));

    if (isAlwaysInteger(operand)) {
      return convertIntegerResult(integerComputation);
    } else if (isAlwaysFloat(operand)) {
      return convertFloatResult(floatComputation);
    } else {
      return convertNumberResult(genFunctionCall(
          UTILS_LIB + ".$$negative", genMaybeCast(operand, NumberData.class)));
    }
  }


  @Override protected JavaExpr visitNotOpNode(NotOpNode node) {

    JavaExpr operand = visit(node.getChild(0));
    return convertBooleanResult(genNewBooleanData(genUnaryOp("!", genCoerceBoolean(operand))));
  }


  @Override protected JavaExpr visitTimesOpNode(TimesOpNode node) {
    return visitNumberToNumberBinaryOpHelper(node, "*", "$$times");
  }


  @Override protected JavaExpr visitDivideByOpNode(DivideByOpNode node) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));

    // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
    return convertFloatResult(genNewFloatData(genBinaryOp(
        "/", genNumberValue(operand0), genNumberValue(operand1))));
  }


  @Override protected JavaExpr visitModOpNode(ModOpNode node) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));

    return convertIntegerResult(genNewIntegerData(genBinaryOp(
        "%", genIntegerValue(operand0), genIntegerValue(operand1))));
  }


  @Override protected JavaExpr visitPlusOpNode(PlusOpNode node) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));

    String stringComputation = genNewStringData(genBinaryOp(
        "+", genCoerceString(operand0), genCoerceString(operand1)));
    String integerComputation = genNewIntegerData(genBinaryOp(
        "+", genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewFloatData(genBinaryOp(
        "+", genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      return convertIntegerResult(integerComputation);
    } else if (isAlwaysAtLeastOneString(operand0, operand1)) {
      return convertStringResult(stringComputation);
    } else if (isAlwaysTwoFloatsOrOneFloatOneInteger(operand0, operand1)) {
      return convertFloatResult(floatComputation);
    } else {
      return convertUnknownResult(genFunctionCall(
          UTILS_LIB + ".$$plus", operand0.getText(), operand1.getText()));
    }
  }


  @Override protected JavaExpr visitMinusOpNode(MinusOpNode node) {
    return visitNumberToNumberBinaryOpHelper(node, "-", "$$minus");
  }


  @Override protected JavaExpr visitLessThanOpNode(LessThanOpNode node) {
    return visitNumberToBooleanBinaryOpHelper(node, "<", "$$lessThan");
  }


  @Override protected JavaExpr visitGreaterThanOpNode(GreaterThanOpNode node) {
    return visitNumberToBooleanBinaryOpHelper(node, ">", "$$greaterThan");
  }


  @Override protected JavaExpr visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
    return visitNumberToBooleanBinaryOpHelper(node, "<=", "$$lessThanOrEqual");
  }


  @Override protected JavaExpr visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
    return visitNumberToBooleanBinaryOpHelper(node, ">=", "$$greaterThanOrEqual");
  }


  @Override protected JavaExpr visitEqualOpNode(EqualOpNode node) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));

    return convertBooleanResult(genNewBooleanData(
        genMaybeProtect(operand0, Integer.MAX_VALUE) + ".equals(" + operand1.getText() + ")"));
  }


  @Override protected JavaExpr visitNotEqualOpNode(NotEqualOpNode node) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));

    return convertBooleanResult(genNewBooleanData(
        "! " + genMaybeProtect(operand0, Integer.MAX_VALUE) + ".equals(" +
        operand1.getText() + ")"));
  }


  @Override protected JavaExpr visitAndOpNode(AndOpNode node) {
    return visitBooleanToBooleanBinaryOpHelper(node, "&&");
  }


  @Override protected JavaExpr visitOrOpNode(OrOpNode node) {
    return visitBooleanToBooleanBinaryOpHelper(node, "||");
  }


  @Override protected JavaExpr visitConditionalOpNode(ConditionalOpNode node) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));
    JavaExpr operand2 = visit(node.getChild(2));

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

    return new JavaExpr(
        genCoerceBoolean(operand0) + " ? " +
        genMaybeProtect(operand1, Operator.CONDITIONAL.getPrecedence() + 1) + " : " +
        genMaybeProtect(operand2, Operator.CONDITIONAL.getPrecedence() + 1),
        resultType, Operator.CONDITIONAL.getPrecedence());
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for functions.


  @Override protected JavaExpr visitFunctionNode(FunctionNode node) {

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
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case HAS_DATA:
          return visitHasDataFunction();
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
      List<JavaExpr> args = visitChildren(node);
      try {
        return fn.computeForJavaSrc(args);
      } catch (Exception e) {
        throw new SoySyntaxException(
            "Error in function call \"" + node.toSourceString() + "\": " + e.getMessage(), e);
      }
    }

    throw new SoySyntaxException(
        "Failed to find SoyJavaSrcFunction with name '" + fnName + "'" +
        " (function call \"" + node.toSourceString() + "\").");
  }


  private JavaExpr visitIsFirstFunction(FunctionNode node) {
    String varName = ((DataRefNode) node.getChild(0)).getFirstKey();
    return getLocalVarTranslation(varName + "__isFirst");
  }


  private JavaExpr visitIsLastFunction(FunctionNode node) {
    String varName = ((DataRefNode) node.getChild(0)).getFirstKey();
    return getLocalVarTranslation(varName + "__isLast");
  }


  private JavaExpr visitIndexFunction(FunctionNode node) {
    String varName = ((DataRefNode) node.getChild(0)).getFirstKey();
    return getLocalVarTranslation(varName + "__index");
  }


  private JavaExpr visitHasDataFunction() {
    return convertBooleanResult(genNewBooleanData("data != null"));
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  private JavaExpr convertBooleanResult(String exprText) {
    return new JavaExpr(exprText, BooleanData.class, Integer.MAX_VALUE);
  }


  private JavaExpr convertIntegerResult(String exprText) {
    return new JavaExpr(exprText, IntegerData.class, Integer.MAX_VALUE);
  }


  private JavaExpr convertFloatResult(String exprText) {
    return new JavaExpr(exprText, FloatData.class, Integer.MAX_VALUE);
  }


  private JavaExpr convertNumberResult(String exprText) {
    return new JavaExpr(exprText, NumberData.class, Integer.MAX_VALUE);
  }


  private JavaExpr convertStringResult(String exprText) {
    return new JavaExpr(exprText, StringData.class, Integer.MAX_VALUE);
  }


  private JavaExpr convertListResult(String exprText) {
    return new JavaExpr(exprText, SoyListData.class, Integer.MAX_VALUE);
  }


  private JavaExpr convertMapResult(String exprText) {
    return new JavaExpr(exprText, SoyMapData.class, Integer.MAX_VALUE);
  }


  private JavaExpr convertUnknownResult(String exprText) {
    return new JavaExpr(exprText, SoyData.class, Integer.MAX_VALUE);
  }


  private JavaExpr visitBooleanToBooleanBinaryOpHelper(OperatorNode node, String javaOpToken) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));

    return convertBooleanResult(genNewBooleanData(genBinaryOp(
        javaOpToken, genCoerceBoolean(operand0), genCoerceBoolean(operand1))));
  }


  private JavaExpr visitNumberToNumberBinaryOpHelper(
      OperatorNode node, String javaOpToken, String utilsLibFnName) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));

    String integerComputation = genNewIntegerData(genBinaryOp(
        javaOpToken, genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewFloatData(genBinaryOp(
        javaOpToken, genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      return convertIntegerResult(integerComputation);
    } else if (isAlwaysAtLeastOneFloat(operand0, operand1)) {
      return convertFloatResult(floatComputation);
    } else {
      return convertNumberResult(genFunctionCall(
          UTILS_LIB + "." + utilsLibFnName,
          genMaybeCast(operand0, NumberData.class), genMaybeCast(operand1, NumberData.class)));
    }
  }


  private JavaExpr visitNumberToBooleanBinaryOpHelper(
      OperatorNode node, String javaOpToken, String utilsLibFnName) {

    JavaExpr operand0 = visit(node.getChild(0));
    JavaExpr operand1 = visit(node.getChild(1));

    String integerComputation = genNewBooleanData(genBinaryOp(
        javaOpToken, genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewBooleanData(genBinaryOp(
        javaOpToken, genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      return convertBooleanResult(integerComputation);
    } else if (isAlwaysAtLeastOneFloat(operand0, operand1)) {
      return convertBooleanResult(floatComputation);
    } else {
      return convertBooleanResult(genFunctionCall(
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
