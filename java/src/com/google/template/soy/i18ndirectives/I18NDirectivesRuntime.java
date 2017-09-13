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

import com.ibm.icu.text.CompactDecimalFormat;
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.ULocale;

/** Java implementations of the i18n directives. */
public final class I18NDirectivesRuntime {

  private I18NDirectivesRuntime() {}

  public static String formatNum(
      ULocale uLocale, double number, String formatType, String numbersKeyword) {
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
        {
          CompactDecimalFormat compactNumberFormat =
              CompactDecimalFormat.getInstance(uLocale, CompactStyle.SHORT);
          compactNumberFormat.setMaximumSignificantDigits(3);
          numberFormat = compactNumberFormat;
          break;
        }
      case "compact_long":
        {
          CompactDecimalFormat compactNumberFormat =
              CompactDecimalFormat.getInstance(uLocale, CompactStyle.LONG);
          compactNumberFormat.setMaximumSignificantDigits(3);
          numberFormat = compactNumberFormat;
          break;
        }
      default:
        throw new IllegalArgumentException(
            "First argument to formatNum must be "
                + "constant, and one of: 'decimal', 'currency', 'percent', 'scientific', "
                + "'compact_short', or 'compact_long'.");
    }

    return numberFormat.format(number);
  }
}
