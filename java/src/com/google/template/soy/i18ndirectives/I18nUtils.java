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

import com.google.common.base.Ascii;
import com.ibm.icu.util.ULocale;
import java.util.Locale;

/**
 * Helper functions for Internationalization in Soy.
 *
 */
class I18nUtils {

  // Private constructor to prevent instantiation.
  private I18nUtils() {}

  /**
   * Given a string representing a Locale, returns the Locale object for that string.
   *
   * @return A Locale object built from the string provided
   */
  public static Locale parseLocale(String localeString) {
    if (localeString == null) {
      return Locale.US;
    }
    String[] groups = localeString.split("[-_]");
    switch (groups.length) {
      case 1:
        return new Locale(groups[0]);
      case 2:
        return new Locale(groups[0], Ascii.toUpperCase(groups[1]));
      case 3:
        return new Locale(groups[0], Ascii.toUpperCase(groups[1]), groups[2]);
      default:
        throw new IllegalArgumentException("Malformed localeString: " + localeString);
    }
  }

  /**
   * Given a string representing a Locale, returns the ICU4J ULocale object for that string.
   *
   * @return A ULocale object built from the string provided
   */
  public static ULocale parseULocale(String localeString) {
    return ULocale.forLocale(parseLocale(localeString));
  }
}
