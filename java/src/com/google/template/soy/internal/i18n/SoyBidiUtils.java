/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.internal.i18n;

import java.util.EnumMap;


/**
 * Bidi utilities for Soy code.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public class SoyBidiUtils {

  private SoyBidiUtils() {}


  /** BiDi formatter cache, so we don't have to keep creating new ones. */
  private static EnumMap<BidiUtils.Dir, BidiFormatter> bidiFormatterCache =
      new EnumMap<BidiUtils.Dir, BidiFormatter>(BidiUtils.Dir.class);


  /**
   * Gets the bidi global directionality from a language/locale string (ltr=1, rtl=-1).
   * @param localeString The language/locale string for which to get the bidi global
   *     directionality.
   * @return 1 if the language/locale is left-to-right or unknown, and -1 if it's right-to-left.
   */
  public static int getBidiGlobalDir(String localeString) {
    // The FakeBiDi pseudo-locale does not have an accepted language code; 'qbi' has been
    // tentatively adopted by some products. ('qXX' is a standard way of indicating a private-use
    // language code, and the 'bi' stands for BiDi.)

    boolean isRtl;
    try {
      isRtl =
          localeString != null &&
          (localeString.equals("qbi") || BidiUtils.isRtlLanguage(localeString));
    } catch (IllegalArgumentException localeException) {
      isRtl = false;
    }
    return isRtl ? -1 : 1;
  }


  /**
   * Get a bidi formatter - preferably a cached one.
   * @param dir The directionality as an integer (ltr=1, rtl=-1).
   * @return The BidiFormatter.
   */
  public static BidiFormatter getBidiFormatter(int dir) {
    BidiUtils.Dir actualDir = BidiUtils.Dir.valueOf(dir);
    BidiFormatter bidiFormatter = bidiFormatterCache.get(actualDir);
    if (bidiFormatter == null) {
      bidiFormatter = BidiFormatter.getInstance(actualDir);
      bidiFormatterCache.put(actualDir, bidiFormatter);
    }
    return bidiFormatter;
  }

}
