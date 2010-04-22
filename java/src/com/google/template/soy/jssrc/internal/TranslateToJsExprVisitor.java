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

package com.google.template.soy.jssrc.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefIndexNode;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsCodeUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.internal.ImpureFunction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Visitor for translating a Soy expression (in the form of an {@code ExprNode}) into an
 * equivalent JS expression.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class TranslateToJsExprVisitor extends AbstractExprNodeVisitor<JsExpr> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface TranslateToJsExprVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement JS expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     */
    public TranslateToJsExprVisitor create(Deque<Map<String, JsExpr>> localVarTranslations);
  }


  /** Map of all SoyJsSrcFunctions (name to function). */
  private final Map<String, SoyJsSrcFunction> soyJsSrcFunctionsMap;

  /** The current stack of replacement JS expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, JsExpr>> localVarTranslations;

  /** Stack of partial results (during a pass). */
  private Deque<JsExpr> resultStack;


  /**
   * @param soyJsSrcFunctionsMap Map of all SoyJsSrcFunctions (name to function).
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   */
  @AssistedInject
  TranslateToJsExprVisitor(
      Map<String, SoyJsSrcFunction> soyJsSrcFunctionsMap,
      @Assisted Deque<Map<String, JsExpr>> localVarTranslations) {
    this.soyJsSrcFunctionsMap = soyJsSrcFunctionsMap;
    this.localVarTranslations = localVarTranslations;
  }


  @Override protected void setup() {
    resultStack = new ArrayDeque<JsExpr>();
  }


  @Override protected JsExpr getResult() {
    return resultStack.peek();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected void visitInternal(ExprRootNode<? extends ExprNode> node) {
    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives and data references (concrete classes).


  @Override protected void visitInternal(StringNode node) {

    // Note: StringNode.toSourceString() produces a Soy string, which is usually a valid JS string.
    // The rare exception is a string containing a Unicode Format character (Unicode category "Cf")
    // because of the JavaScript language quirk that requires all category "Cf" characters to be
    // escaped in JS strings. Therefore, we must call JsSrcUtils.escapeUnicodeFormatChars() on the
    // result.
    resultStack.push(new JsExpr(
        JsSrcUtils.escapeUnicodeFormatChars(node.toSourceString()),
        Integer.MAX_VALUE));
  }


  @Override protected void visitInternal(DataRefNode node) {

    StringBuilder exprTextSb = new StringBuilder();

    // ------ Translate the first part, which may be a variable or a data key ------
    String firstPart = ((DataRefKeyNode) node.getChild(0)).getKey();
    JsExpr translation = getLocalVarTranslation(firstPart);
    if (translation != null) {
      // Case 1: In-scope local var.
      exprTextSb.append(translation.getText());
    } else {
      // Case 2: Data reference.
      exprTextSb.append("opt_data");
      appendDataRefKey(exprTextSb, firstPart);
    }

    // ------ Translate the rest of the keys, if any ------
    int numKeys = node.numChildren();
    if (numKeys > 1) {
      for (int i = 1; i < numKeys; ++i) {
        ExprNode child = node.getChild(i);
        if (child instanceof DataRefKeyNode) {
          appendDataRefKey(exprTextSb, ((DataRefKeyNode) child).getKey());
        } else if (child instanceof DataRefIndexNode) {
          exprTextSb.append("[").append(((DataRefIndexNode) child).getIndex()).append("]");
        } else {
          visit(child);
          exprTextSb.append("[").append(resultStack.pop().getText()).append("]");
        }
      }
    }

    resultStack.push(new JsExpr(exprTextSb.toString(), Integer.MAX_VALUE));
  }


  @Override protected void visitInternal(GlobalNode node) {
    resultStack.push(new JsExpr(node.toSourceString(), Integer.MAX_VALUE));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators (concrete classes).


  @Override protected void visitInternal(NotOpNode node) {
    // Note: Since we're using Soy syntax for the 'not' operator, we'll end up generating code with
    // a space between the token '!' and the subexpression that it negates. This isn't the usual
    // style, but it should be fine (besides, it's more readable with the extra space).
    resultStack.push(genJsExprUsingSoySyntaxWithNewToken(node, "!"));
  }


  @Override protected void visitInternal(AndOpNode node) {
    resultStack.push(genJsExprUsingSoySyntaxWithNewToken(node, "&&"));
  }


  @Override protected void visitInternal(OrOpNode node) {
    resultStack.push(genJsExprUsingSoySyntaxWithNewToken(node, "||"));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.


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
    SoyJsSrcFunction fn = soyJsSrcFunctionsMap.get(fnName);
    if (fn != null) {
      if (! fn.getValidArgsSizes().contains(numArgs)) {
        throw new SoySyntaxException(
            "Function '" + fnName + "' called with the wrong number of arguments" +
            " (function call \"" + node.toSourceString() + "\").");
      }
      List<JsExpr> args = Lists.newArrayList();
      for (ExprNode child : node.getChildren()) {
        visit(child);
        args.add(resultStack.pop());
      }
      try {
        resultStack.push(fn.computeForJsSrc(args));
      } catch (Exception e) {
        throw new SoySyntaxException(
            "Error in function call \"" + node.toSourceString() + "\": " + e.getMessage(), e);
      }
      return;
    }

    // Function not found.
    throw new SoySyntaxException(
        "Failed to find SoyJsSrcFunction with name '" + fnName + "'" +
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
    resultStack.push(new JsExpr("opt_data != null", Operator.NOT_EQUAL.getPrecedence()));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(OperatorNode node) {
    resultStack.push(genJsExprUsingSoySyntax(node));
  }


  @Override protected void visitInternal(PrimitiveNode node) {

    // Note: ExprNode.toSourceString() technically returns a Soy expression. In the case of
    // primitives, the result is usually also the correct JS expression.
    // Note: The rare exception to the above note is a StringNode containing a Unicode Format
    // character (Unicode category "Cf") because of the JavaScript language quirk that requires all
    // category "Cf" characters to be escaped in JS strings. Therefore, we have a separate
    // implementation above for visitInternal(StringNode).
    resultStack.push(new JsExpr(node.toSourceString(), Integer.MAX_VALUE));
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Gets the translated expression for an in-scope local variable (or special "variable" derived
   * from a foreach-loop var), or null if not found.
   * @param ident The Soy local variable to translate.
   * @return The translated expression for the given variable, or null if not found.
   */
  private JsExpr getLocalVarTranslation(String ident) {

    for (Map<String, JsExpr> localVarTranslationsFrame : localVarTranslations) {
      JsExpr translation = localVarTranslationsFrame.get(ident);
      if (translation != null) {
        return translation;
      }
    }

    return null;
  }


  /**
   * Generates a JS expression for the given OperatorNode's subtree assuming that the JS expression
   * for the operator uses the same syntax format as the Soy operator.
   * @param opNode The OperatorNode whose subtree to generate a JS expression for.
   * @return The generated JS expression.
   */
  private JsExpr genJsExprUsingSoySyntax(OperatorNode opNode) {
    return genJsExprUsingSoySyntaxWithNewToken(opNode, null);
  }


  /**
   * Generates a JS expression for the given OperatorNode's subtree assuming that the JS expression
   * for the operator uses the same syntax format as the Soy operator, with the exception that the
   * JS operator uses a different token (e.g. "!" instead of "not").
   * @param opNode The OperatorNode whose subtree to generate a JS expression for.
   * @param newToken The equivalent JS operator's token.
   * @return The generated JS expression.
   */
  private JsExpr genJsExprUsingSoySyntaxWithNewToken(OperatorNode opNode, String newToken) {

    List<JsExpr> operandJsExprs = Lists.newArrayList();
    for (ExprNode child : opNode.getChildren()) {
      visit(child);
      operandJsExprs.add(resultStack.pop());
    }

    return SoyJsCodeUtils.genJsExprUsingSoySyntaxWithNewToken(
        opNode.getOperator(), operandJsExprs, newToken);
  }


  /**
   * Appends a key onto a data reference expression.
   * Handles JS reserved words.
   * @param sb StringBuilder to append to.
   * @param key Key to append.
   */
  private void appendDataRefKey(StringBuilder sb, String key) {
    if (JS_RESERVED_WORDS.contains(key)) {
      sb.append("['").append(key).append("']");
    } else {
      sb.append(".").append(key);
    }
  }


  /**
   * Set of words that JavaScript considers reserved words.  These words cannot
   * be used as identifiers.  This list is from the ECMA-262 v5, section 7.6.1:
   * http://www.ecma-international.org/publications/files/drafts/tc39-2009-050.pdf
   */
  private static final ImmutableSet<String> JS_RESERVED_WORDS = ImmutableSet.of(
      "break", "case", "catch", "class",
      "const", "continue", "debugger", "default", "delete", "do",
      "else", "enum", "export", "extends", "finally",
      "for", "function", "if", "implements", "import", "in",
      "instanceof", "interface", "let", "new",
      "package", "private", "protected", "public", "return", "static",
      "super", "switch", "this", "throw",
      "try", "typeof", "var", "void", "while", "with", "yield");
}
