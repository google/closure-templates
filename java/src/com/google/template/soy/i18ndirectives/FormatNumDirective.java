/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.i18ndirectives;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.targetexpr.TargetExpr;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * A directive that formats an input number based on Locale of the current SoyMsgBundle. It may take
 * two optional arguments. The first is a lower-case string describing the type of format to apply,
 * which can be one of 'decimal', 'currency', 'percent', 'scientific', 'compact_short', or
 * 'compact_long'. If this argument is not provided, the default 'decimal' will be used. The second
 * argument is the "numbers" keyword passed to the ICU4J's locale. For instance, it can be "native"
 * so that we show native characters in languages like arabic (this argument is ignored for
 * templates running in JavaScript).
 *
 * <p>Usage examples: {@code {$value|formatNum} {$value|formatNum:'decimal'}
 * {$value|formatNum:'decimal','native'} }
 *
 */
class FormatNumDirective
    implements SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective,
        SoyJbcSrcPrintDirective {

  // Map of format arguments to the Closure Format enum.
  private static final ImmutableMap<String, String> JS_ARGS_TO_ENUM =
      ImmutableMap.<String, String>builder()
          .put("'decimal'", "goog.i18n.NumberFormat.Format.DECIMAL")
          .put("'currency'", "goog.i18n.NumberFormat.Format.CURRENCY")
          .put("'percent'", "goog.i18n.NumberFormat.Format.PERCENT")
          .put("'scientific'", "goog.i18n.NumberFormat.Format.SCIENTIFIC")
          .put("'compact_short'", "goog.i18n.NumberFormat.Format.COMPACT_SHORT")
          .put("'compact_long'", "goog.i18n.NumberFormat.Format.COMPACT_LONG")
          .build();

  // This directive can be called with no arguments, with one argument setting the format type,
  // or with two arguments setting the format type and the 'numbers' keyword for the ICU4J
  // formatter.
  private static final ImmutableSet<Integer> VALID_ARGS_SIZES = ImmutableSet.of(0, 1, 2);

  private static final ImmutableSet<String> REQUIRED_JS_LIBS =
      ImmutableSet.of("goog.i18n.NumberFormat");

  private static final String DEFAULT_FORMAT = "decimal";

  /**
   * Provide the current Locale string.
   *
   * <p>Note that this Locale value is only used in the Java environment. Closure does not provide a
   * clear mechanism to override the NumberFormat defined when the NumberFormat module loads. This
   * is probably not a significant loss of functionality, since the primary reason to inject the
   * LocaleString is because the Java VM's default Locale may not be the same as the desired Locale
   * for the page, while in the JavaScript environment, the value of goog.LOCALE should reliably
   * indicate which Locale Soy should use. Similarly, the Python backend relies on implementation
   * specific runtime locale support.
   */
  private final Provider<String> localeStringProvider;

  @Inject
  FormatNumDirective(@LocaleString Provider<String> localeStringProvider) {
    this.localeStringProvider = localeStringProvider;
  }

  @Override
  public String getName() {
    return "|formatNum";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return VALID_ARGS_SIZES;
  }

  @Override
  public boolean shouldCancelAutoescape() {
    return false;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    ULocale uLocale = I18nUtils.parseULocale(localeStringProvider.get());
    String numbersKeyword = "local";
    if (args.size() > 1) {
      // A keyword for ULocale was passed (like 'native', for instance, to use native characters).
      numbersKeyword = args.get(1).stringValue();
    }
    String formatType = args.isEmpty() ? DEFAULT_FORMAT : args.get(0).stringValue();
    double number = value.numberValue();
    return StringData.forValue(
        I18NDirectivesRuntime.formatNum(uLocale, number, formatType, numbersKeyword));
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String numberFormatType = parseFormat(args);

    StringBuilder expr = new StringBuilder();
    expr.append("(new goog.i18n.NumberFormat(" + JS_ARGS_TO_ENUM.get(numberFormatType) + "))");
    if ("'compact_short'".equals(numberFormatType) || "'compact_long'".equals(numberFormatType)) {
      expr.append(".setSignificantDigits(3)");
    }
    expr.append(".format(" + value.getText() + ")");

    return new JsExpr(expr.toString(), Integer.MAX_VALUE);
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    String numberFormatType = parseFormat(args);

    PyFunctionExprBuilder builder =
        new PyFunctionExprBuilder(PyExprUtils.TRANSLATOR_NAME + ".format_num")
            .addArg(value)
            .addArg(new PyExpr(numberFormatType, Integer.MAX_VALUE));

    return builder.asPyStringExpr();
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return REQUIRED_JS_LIBS;
  }

  private static final class JbcSrcMethods {
    static final MethodRef FORMAT_NUM =
        MethodRef.create(
                I18NDirectivesRuntime.class,
                "formatNum",
                ULocale.class,
                double.class,
                String.class,
                String.class)
            .asNonNullable();
  }

  @Override
  public SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
    return SoyExpression.forString(
        JbcSrcMethods.FORMAT_NUM.invoke(
            context.getULocale(),
            value.coerceToDouble(),
            !args.isEmpty()
                ? args.get(0).unboxAs(String.class)
                : BytecodeUtils.constant(DEFAULT_FORMAT),
            args.size() > 1 ? args.get(1).unboxAs(String.class) : BytecodeUtils.constant("local")));
  }

  /**
   * Validates that the provided format matches a supported format, and returns the value, if not,
   * this throws an exception.
   *
   * @param args The list of provided arguments.
   * @return String The number format type.
   */
  private static String parseFormat(List<? extends TargetExpr> args) {
    String numberFormatType = !args.isEmpty() ? args.get(0).getText() : "'" + DEFAULT_FORMAT + "'";

    if (!JS_ARGS_TO_ENUM.containsKey(numberFormatType)) {
      String validKeys = Joiner.on("', '").join(JS_ARGS_TO_ENUM.keySet());
      throw new IllegalArgumentException(
          "First argument to formatNum must be " + "constant, and one of: '" + validKeys + "'.");
    }

    return numberFormatType;
  }
}
