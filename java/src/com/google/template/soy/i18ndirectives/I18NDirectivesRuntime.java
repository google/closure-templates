/*
 * Copyright 2017 Google Inc.
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

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.NumberData;
import com.ibm.icu.text.CompactDecimalFormat;
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.ULocale;
import javax.annotation.Nullable;

/** Java implementations of the i18n directives. */
public final class I18NDirectivesRuntime {

  private I18NDirectivesRuntime() {}

  public static String formatNum(
      ULocale uLocale,
      @Nullable SoyValue number,
      String formatType,
      String numbersKeyword,
      @Nullable NumberData minFractionDigits,
      @Nullable NumberData maxFractionDigits) {
    if (number == null) {
      return "";
    } else if (number instanceof NumberData) {
      return formatInternal(
          uLocale,
          ((NumberData) number).toFloat(),
          formatType,
          numbersKeyword,
          minFractionDigits != null ? (int) minFractionDigits.numberValue() : null,
          maxFractionDigits != null ? (int) maxFractionDigits.numberValue() : null);
    } else {
      return "NaN";
    }
  }

  /**
   * Formats a number using ICU4J. Note: If min or max fraction digits is null, the param will be
   * ignored.
   */
  private static String formatInternal(
      ULocale uLocale,
      double number,
      String formatType,
      String numbersKeyword,
      @Nullable Integer minFractionDigits,
      @Nullable Integer maxFractionDigits) {
    uLocale = uLocale.setKeywordValue("numbers", numbersKeyword);
    NumberFormat numberFormat;
    switch (formatType) {
      case "decimal":
        numberFormat = NumberFormat.getInstance(uLocale);
        break;
      case "percent":
        numberFormat = NumberFormat.getPercentInstance(uLocale);
        break;
      case "currency":
        numberFormat = NumberFormat.getCurrencyInstance(uLocale);
        break;
      case "scientific":
        numberFormat = NumberFormat.getScientificInstance(uLocale);
        break;
      case "compact_short":
        numberFormat = CompactDecimalFormat.getInstance(uLocale, CompactStyle.SHORT);
        break;
      case "compact_long":
        numberFormat = CompactDecimalFormat.getInstance(uLocale, CompactStyle.LONG);
        break;
      default:
        throw new IllegalArgumentException(
            "First argument to formatNum must be "
                + "constant, and one of: 'decimal', 'currency', 'percent', 'scientific', "
                + "'compact_short', or 'compact_long'.");
    }

    if (minFractionDigits != null || maxFractionDigits != null) {
      if (maxFractionDigits == null) {
        maxFractionDigits = minFractionDigits;
      }
      if (minFractionDigits != null) {
        numberFormat.setMinimumFractionDigits(minFractionDigits);
      }
      numberFormat.setMaximumFractionDigits(maxFractionDigits);
    } else if (numberFormat instanceof CompactDecimalFormat) {
      ((CompactDecimalFormat) numberFormat).setMaximumSignificantDigits(3);
    }

    return numberFormat.format(number);
  }
}
