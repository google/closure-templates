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

import static com.google.template.soy.shared.restricted.SoyJavaRuntimeFunctionUtils.toSoyData;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuPrintDirective;

import com.ibm.icu.text.NumberFormat;

import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 * A directive that formats an input number based on Locale of the current SoyMsgBundle.
 * It takes a single argument : an optional lower-case string describing the type of format to
 * apply, which can be one of 'decimal', 'currency', 'percent', or 'scientific'. If the argument is
 * not provided, the default, 'decimal' will be used.
 *
 * @author Jeff Craig
 */
class FormatNumDirective extends SoyAbstractTofuPrintDirective implements SoyJsSrcPrintDirective {


  /**
   * Provide the current Locale string.
   *
   * Note that this Locale value is only used in the Java environment. Closure does not provide a
   * clear mechanism to override the NumberFormat defined when the NumberFormat module loads. This
   * is probably not a significant loss of functionality, since the primary reason to inject the
   * LocaleString is because the Java VM's default Locale may not be the same as the desired Locale
   * for the page, while in the JavaScript environment, the value of goog.LOCALE should reliably
   * indicate which Locale Soy should use.
   */
  private Provider<String> localeStringProvider;

  @Inject
  FormatNumDirective(@LocaleString Provider<String> localeStringProvider) {
    this.localeStringProvider = localeStringProvider;
  }


  @Override public String getName() {
    return "|formatNum";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }


  @Override public boolean shouldCancelAutoescape() {
    return false;
  }

  @Override public SoyData apply(SoyData value, List<SoyData> args) {
    Locale locale = I18nUtils.parseLocale(localeStringProvider.get());
    NumberFormat instance;
    String formatType = args.isEmpty() ? "decimal" : args.get(0).stringValue();

    if (formatType == "decimal") {
      instance = NumberFormat.getInstance(locale);
    } else if (formatType == "percent") {
      instance = NumberFormat.getPercentInstance(locale);
    } else if (formatType == "currency") {
      instance = NumberFormat.getCurrencyInstance(locale);
    } else if (formatType == "scientific") {
      instance = NumberFormat.getScientificInstance(locale);
    } else {
      throw new IllegalArgumentException(
          String.format("Unrecognized Number Format Type: {0}", formatType));
    }

    return toSoyData(instance.format(((NumberData) value).toFloat()));
  }


  @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String numberFormatNameJsExprText;

    if (args.isEmpty()) {
      numberFormatNameJsExprText = "'DECIMAL'";
    } else {
      numberFormatNameJsExprText = "(" + args.get(0).getText() + ").toUpperCase()";
    }
    String numberFormatDecl = "goog.i18n.NumberFormat.Format[" + numberFormatNameJsExprText + "]";

    return new JsExpr(
        "(new goog.i18n.NumberFormat(" + numberFormatDecl + ")).format(" + value.getText() + ")",
        Integer.MAX_VALUE);
  }
}
