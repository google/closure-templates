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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;

import java.util.Deque;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Translator of Soy V1 expressions to their equivalent JS expressions. Needed in order to provide
 * the semi backwards compatibility with Soy V1.
 *
 * <p> Adapted from Soy V1 code.
 *
 * @author Kai Huang
 */
class V1JsExprTranslator {


  /** Regex for a template variable or data reference. */
  // 2 capturing groups: first part (excluding '$'), rest
  // Example:  $boo.foo.goo  ==>  group(1) == "boo",  group(2) == ".foo.goo"
  public static final String VAR_OR_REF_RE = "\\$([a-zA-Z0-9_]+)((?:\\.[a-zA-Z0-9_]+)*)";

  /** Regex pattern for a template variable or data reference. */
  public static final Pattern VAR_OR_REF = Pattern.compile(VAR_OR_REF_RE);

  /** Regex for a special function ({@code isFirst()}, {@code isLast()}, or {@code index()}). */
  // 2 capturing groups: function name, variable name (excluding '$').
  private static final String SOY_FUNCTION_RE =
      "(isFirst|isLast|index)\\(\\s*\\$([a-zA-Z0-9_]+)\\s*\\)";

  /** Regex pattern for a Soy function. */
  // 2 capturing groups: function name, variable name.
  private static final Pattern SOY_FUNCTION = Pattern.compile(SOY_FUNCTION_RE);

  /** Regex pattern for operators to translate: 'not', 'and', 'or'. */
  private static final Pattern BOOL_OP_RE = Pattern.compile("\\b(not|and|or)\\b");

  /** Regex pattern for a data reference or a Soy function. */
  private static final Pattern VAR_OR_REF_OR_BOOL_OP_OR_SOY_FUNCTION =
      Pattern.compile(VAR_OR_REF_RE + "|" + BOOL_OP_RE + "|" + SOY_FUNCTION_RE);

  /** Regex pattern for a number. */
  private static final Pattern NUMBER = Pattern.compile("[0-9]+");

  /** Regex pattern for chars that appear in operator tokens (some appear in multiple tokens). */
  private static final Pattern OP_TOKEN_CHAR = Pattern.compile("[-?|&=!<>+*/%]");


  /**
   * Helper function to generate code for a JS expression found in a Soy tag.
   * Replaces all variables, data references, and special function calls in
   * the given expression text with the appropriate generated code. E.g.
   * <pre>
   *   $boo.foo + ($goo.moo).doIt()
   * </pre>
   * might become
   * <pre>
   *   opt_data.boo.foo + (gooData0.moo).doIt()
   * </pre>
   *
   * @param soyExpr The expression text to generate code for.
   * @param localVarTranslations -
   * @return The resulting expression code after the necessary substitutions.
   * @throws SoySyntaxException If the given expression has an error.
   */
  public static JsExpr translateToJsExpr(String soyExpr,
                                         Deque<Map<String, JsExpr>> localVarTranslations)
      throws SoySyntaxException {

    soyExpr = CharMatcher.WHITESPACE.collapseFrom(soyExpr, ' ');

    StringBuffer jsExprTextSb = new StringBuffer();

    Matcher matcher = VAR_OR_REF_OR_BOOL_OP_OR_SOY_FUNCTION.matcher(soyExpr);
    while (matcher.find()) {
      String group = matcher.group();
      if (VAR_OR_REF.matcher(group).matches()) {
        matcher.appendReplacement(
            jsExprTextSb,
            Matcher.quoteReplacement(translateVarOrRef(group, localVarTranslations)));
      } else if (BOOL_OP_RE.matcher(group).matches()) {
        matcher.appendReplacement(
            jsExprTextSb,
            Matcher.quoteReplacement(translateBoolOp(group)));
      } else {
        matcher.appendReplacement(
            jsExprTextSb,
            Matcher.quoteReplacement(translateFunction(group, localVarTranslations)));
      }
    }
    matcher.appendTail(jsExprTextSb);

    String jsExprText = jsExprTextSb.toString();

    // Note: There is a JavaScript language quirk that requires all Unicode Foramt characters
    // (Unicode category "Cf") to be escaped in JS strings. Therefore, we call
    // JsSrcUtils.escapeUnicodeFormatChars() on the expression text in case it contains JS strings.
    jsExprText = JsSrcUtils.escapeUnicodeFormatChars(jsExprText);
    int jsExprPrec = guessJsExprPrecedence(jsExprText);
    return new JsExpr(jsExprText, jsExprPrec);
  }


  /**
   * Helper function to translate a variable or data reference.
   *
   * Examples:
   * <pre>
   * $boo.foo    -->  opt_data.boo.foo      (data ref)
   * $boo.3.foo  -->  opt_data.boo[3].foo   (data ref)
   * $boo        -->  booData2              (data ref with foreach var)
   * $boo.foo    -->  booData2.Foo          (data ref with foreach var)
   * $i          -->  i3                    (for var)
   * </pre>
   *
   * @param varOrRefText The variable or data reference to translage.
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return Generated translation for the variable or data reference.
   * @throws SoySyntaxException If the {@code varOrRefText} is malformed.
   */
  private static String translateVarOrRef(String varOrRefText,
                                          Deque<Map<String, JsExpr>> localVarTranslations)
      throws SoySyntaxException {

    Matcher matcher = VAR_OR_REF.matcher(varOrRefText);
    if (!matcher.matches()) {
      throw new SoySyntaxException(
          "Variable or data reference \"" + varOrRefText + "\" is malformed.");
    }

    String firstPart = matcher.group(1);
    String rest = matcher.group(2);

    StringBuilder exprTextSb = new StringBuilder();

    // ------ Translate the first key, which may be a variable or a data key ------
    String translation = getLocalVarTranslation(firstPart, localVarTranslations);
    if (translation != null) {
      // Case 1: In-scope local var.
      exprTextSb.append(translation);
    } else {
      // Case 2: Data reference.
      exprTextSb.append("opt_data.").append(firstPart);
    }

    // ------ Translate the rest of the keys, if any ------
    if (rest != null && rest.length() > 0) {
      for (String part : Splitter.on('.').split(rest.substring(1))) {
        if (NUMBER.matcher(part).matches()) {
          exprTextSb.append("[").append(part).append("]");
        } else {
          exprTextSb.append(".").append(part);
        }
      }
    }

    return exprTextSb.toString();
  }


  /**
   * Helper function to translate a boolean operator from Soy to JS.
   * @param boolOp The Soy boolean operator.
   * @return The translated string.
   */
  private static String translateBoolOp(String boolOp) {

    if (boolOp.equals("not")) {
      return "!";
    } else if (boolOp.equals("and")) {
      return "&&";
    } else if (boolOp.equals("or")) {
      return "||";
    } else {
      throw new AssertionError();
    }
  }


  /**
   * Private helper for {@code genExpressionCode} to generate code for a
   * special function call.
   *
   * @param functionText The text of the special function call.
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The translated string
   * @throws SoySyntaxException If a syntax error is detected.
   */
  private static String translateFunction(String functionText,
                                          Deque<Map<String, JsExpr>> localVarTranslations)
      throws SoySyntaxException {

    Matcher matcher = SOY_FUNCTION.matcher(functionText);
    if (!matcher.matches()) {
      throw new SoySyntaxException("Soy function call \"" + functionText + "\" is malformed.");
    }

    String funcName = matcher.group(1);
    String varName = matcher.group(2);
    return getLocalVarTranslation(varName + "__" + funcName, localVarTranslations);
  }


  // We guess the precedence of the expression by searching for characters that appear in
  // operator tokens. This is of course far from accurate, but it's a reasonable effort.
  private static int guessJsExprPrecedence(String jsExprText) {

    // We guess the precedence of the expression by searching for characters that appear in
    // operator tokens. This is of course far from accurate, but it's a reasonable effort.

    int prec = Integer.MAX_VALUE;  // to be adjusted below

    Matcher matcher = OP_TOKEN_CHAR.matcher(jsExprText);
    while (matcher.find()) {
      switch(matcher.group().charAt(0)) {
        case '?':
          prec = Math.min(prec, Operator.CONDITIONAL.getPrecedence());
          break;
        case '|':
          prec = Math.min(prec, Operator.OR.getPrecedence());
          break;
        case '&':
          prec = Math.min(prec, Operator.AND.getPrecedence());
          break;
        case '=':
          // Could be any of "==", "!=", "<=", ">=". Instead of wasting time checking, we simply
          // set the precedence to the lowest possible value.
          prec = Math.min(prec, Operator.EQUAL.getPrecedence());
          break;
        case '!':
          if (jsExprText.contains("!=")) {
            prec = Math.min(prec, Operator.NOT_EQUAL.getPrecedence());
          } else {  // must be "!"
            prec = Math.min(prec, Operator.NOT.getPrecedence());
          }
          break;
        case '<':
        case '>':
          prec = Math.min(prec, Operator.LESS_THAN.getPrecedence());
          break;
        case '+':
          prec = Math.min(prec, Operator.PLUS.getPrecedence());
          break;
        case '-':
          if (matcher.start() == 0) {
            // Matched at beginning of expression, so it must be the unary "-"
            prec = Math.min(prec, Operator.NEGATIVE.getPrecedence());
          } else {
            // Could be binary or unary "-". Since we're not sure, set the precedence to the lowest
            // possible value.
            prec = Math.min(prec, Operator.MINUS.getPrecedence());
          }
          break;
        case '*':
        case '/':
        case '%':
          prec = Math.min(prec, Operator.TIMES.getPrecedence());
          break;
        default:
          throw new AssertionError();
      }
    }

    return prec;
  }


  /**
   * Gets the translated expression for an in-scope local variable (or special "variable" derived
   * from a foreach-loop var), or null if not found.
   * @param ident The Soy local variable to translate.
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The translated string for the given variable, or null if not found.
   */
  private static String getLocalVarTranslation(String ident,
                                               Deque<Map<String, JsExpr>> localVarTranslations) {

    for (Map<String, JsExpr> localVarTranslationsFrame : localVarTranslations) {
      JsExpr translation = localVarTranslationsFrame.get(ident);
      if (translation != null) {
        if (translation.getPrecedence() != Integer.MAX_VALUE) {
          return "(" + translation.getText() + ")";
        } else {
          return translation.getText();
        }
      }
    }

    return null;
  }

}
