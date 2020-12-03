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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Translator of Soy V1 expressions to their equivalent JS expressions. Needed in order to provide
 * the semi backwards compatibility with Soy V1.
 *
 * <p> Adapted from Soy V1 code.
 *
 */
final class V1JsExprTranslator {

  private static final SoyErrorKind UNSUPPORTED_FUNCTION =
      SoyErrorKind.of(
          "''v1Expression'' no longer supports the ''index'', ''isFirst'' or ''isLast'' functions. "
              + "Migrate to a v2 expression to access this functionality.");

  private static final SoyErrorKind UNSUPPORTED_OPERATOR =
      SoyErrorKind.of(
          "''v1Expression'' no longer supports the ''and'', ''or'' or ''not'' operators. "
              + "Move the operator outside of the ''v1Expression'' or migrate to a v2 expression "
              + "to access this functionality.");

  /** Regex for a template variable or data reference. */
  // 1 capturing group: first part (including '$')
  // Example:  $boo.foo.goo  ==>  group(1) == "$boo"
  private static final String VAR_RE = "(\\$[a-zA-Z_][a-zA-Z0-9_]*)";

  /** Regex pattern for a template variable or data reference. */
  private static final Pattern VAR = Pattern.compile(VAR_RE);

  /** Regex for a special function ({@code isFirst()}, {@code isLast()}, or {@code index()}). */
  // 2 capturing groups: function name, variable name (excluding '$').
  private static final String SOY_FUNCTION_RE =
      "(isFirst|isLast|index)\\(\\s*\\$([a-zA-Z0-9_]+)\\s*\\)";

  /** Regex pattern for operators to translate: 'not', 'and', 'or'. */
  private static final Pattern BOOL_OP_RE = Pattern.compile("\\b(not|and|or)\\b");

  /** Regex pattern for a data reference or a Soy function. */
  private static final Pattern VAR_OR_BOOL_OP_OR_SOY_FUNCTION =
      Pattern.compile(VAR_RE + "|" + BOOL_OP_RE + "|" + SOY_FUNCTION_RE);

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
   * @param sourceLocation Source location of the expression text.
   * @param variableMappings -
   * @param errorReporter For reporting syntax errors.
   * @return The resulting expression code after the necessary substitutions.
   */
  static JsExpr translateToJsExpr(
      String soyExpr,
      SourceLocation sourceLocation,
      SoyToJsVariableMappings variableMappings,
      ErrorReporter errorReporter) {

    StringBuffer jsExprTextSb = new StringBuffer();

    Matcher matcher = VAR_OR_BOOL_OP_OR_SOY_FUNCTION.matcher(soyExpr);
    while (matcher.find()) {
      String group = matcher.group();
      Matcher var = VAR.matcher(group);
      if (var.matches()) {
        matcher.appendReplacement(
            jsExprTextSb, Matcher.quoteReplacement(translateVar(variableMappings, var)));
      } else if (BOOL_OP_RE.matcher(group).matches()) {
        errorReporter.report(matcherLocation(matcher, sourceLocation), UNSUPPORTED_OPERATOR);
      } else {
        errorReporter.report(matcherLocation(matcher, sourceLocation), UNSUPPORTED_FUNCTION);
      }
    }
    matcher.appendTail(jsExprTextSb);

    String jsExprText = jsExprTextSb.toString();

    // Note: There is a JavaScript language quirk that requires all Unicode Foramt characters
    // (Unicode category "Cf") to be escaped in JS strings. Therefore, we call
    // JsSrcUtils.escapeUnicodeFormatChars() on the expression text in case it contains JS strings.
    jsExprText = JsSrcUtils.escapeUnicodeFormatChars(jsExprText);
    // Use a high precedence to ensure that everything with the v1Expression is grouped together
    return new JsExpr('(' + jsExprText + ')', /* precedence= */ Integer.MAX_VALUE);
  }

  private static SourceLocation matcherLocation(Matcher matcher, SourceLocation textLocation) {
    // We add 1 to the indexes to account for the quote character at the beginning of the string
    // literal
    return textLocation
        .getBeginLocation()
        .offsetEndCol(matcher.end() + 1)
        .offsetStartCol(matcher.start() + 1);
  }

  /**
   * Helper function to translate a variable or data reference.
   *
   * <p>Examples:
   *
   * <pre>
   * $boo    -->  opt_data.boo      (var ref)
   * </pre>
   *
   * @param variableMappings The current replacement JS expressions for the local variables (and
   *     foreach-loop special functions) current in scope.
   * @param matcher Matcher formed from {@link V1JsExprTranslator#VAR_OR_REF}.
   * @return Generated translation for the variable or data reference.
   */
  private static String translateVar(SoyToJsVariableMappings variableMappings, Matcher matcher) {
    Preconditions.checkArgument(matcher.matches());
    String firstPart = matcher.group(1);

    StringBuilder exprTextSb = new StringBuilder();

    // ------ Translate the first key, which may be a variable or a data key ------
    String translation = getLocalVarTranslation(firstPart, variableMappings);
    if (translation != null) {
      // Case 1: In-scope local var.
      exprTextSb.append(translation);
    } else {
      // Case 2: Data reference.
      exprTextSb.append("opt_data.").append(firstPart.substring(1));
    }

    return exprTextSb.toString();
  }

  /**
   * Gets the translated expression for an in-scope local variable (or special "variable" derived
   * from a foreach-loop var), or null if not found.
   *
   * @param ident The Soy local variable to translate.
   * @param mappings The replacement JS expressions for the local variables (and foreach-loop
   *     special functions) current in scope.
   * @return The translated string for the given variable, or null if not found.
   */
  @Nullable
  private static String getLocalVarTranslation(String ident, SoyToJsVariableMappings mappings) {
    Expression translation = mappings.maybeGet(ident);
    if (translation == null) {
      return null;
    }
    JsExpr asExpr = translation.assertExpr();
    return asExpr.getPrecedence() != Integer.MAX_VALUE
        ? "(" + asExpr.getText() + ")"
        : asExpr.getText();
  }
}
