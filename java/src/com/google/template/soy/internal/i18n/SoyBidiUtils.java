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

import com.google.common.base.Preconditions;

import java.util.EnumMap;
import java.util.regex.Pattern;


/**
 * Bidi utilities for Soy code.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Aharon Lanin
 */
public class SoyBidiUtils {

  private SoyBidiUtils() {}


  /**
   * The code snippet that can be used to determine at template runtime whether the bidi global
   * direction is rtl.
   */
  private static final String GOOG_IS_RTL_CODE_SNIPPET = "goog.i18n.bidi.IS_RTL";


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
    boolean isRtl;
    try {
      isRtl = localeString != null
          && (BidiUtils.isRtlLanguage(localeString)
              || FAKE_RTL_LOCALES_PATTERN.matcher(localeString).matches());
    } catch (IllegalArgumentException localeException) {
      isRtl = false;
    }
    return isRtl ? -1 : 1;
  }


  /**
   * Decodes the bidi global directionality from an integer.
   * @param bidiGlobalDir 1: ltr, -1: rtl, 0: unspecified. Checks that no other value is used.
   * @return BidiGlobalDir object - or null if bidiGlobalDir is 0.
   */
  public static BidiGlobalDir decodeBidiGlobalDir(int bidiGlobalDir) {
    return decodeBidiGlobalDirFromOptions(bidiGlobalDir, false);
  }


  /**
   * Decodes the bidi global directionality from the usual command line options used to specify
   * it. Checks that at most one of the options was specified.
   * @param bidiGlobalDir 1: ltr, -1: rtl, 0: unspecified.
   * @param useGoogIsRtlForBidiGlobalDir Whether to determine the bidi global direction at template
   *     runtime by evaluating goog.i18n.bidi.IS_RTL.
   * @return BidiGlobalDir object - or null if neither option was specified.
   */
  public static BidiGlobalDir decodeBidiGlobalDirFromOptions(
      int bidiGlobalDir, boolean useGoogIsRtlForBidiGlobalDir) {
    if (bidiGlobalDir == 0) {
      if (!useGoogIsRtlForBidiGlobalDir) {
        return null;
      }
      return BidiGlobalDir.forIsRtlCodeSnippet(GOOG_IS_RTL_CODE_SNIPPET);
    }
    Preconditions.checkState(
        !useGoogIsRtlForBidiGlobalDir,
        "Must not specify both bidiGlobalDir and bidiGlobalDirIsRtlCodeSnippet.");
    Preconditions.checkArgument(
        bidiGlobalDir == 1 || bidiGlobalDir == -1,
        "If specified, bidiGlobalDir must be 1 for LTR or -1 for RTL.");
    return BidiGlobalDir.forStaticIsRtl(bidiGlobalDir < 0);
  }


  /**
   * A regular expression for matching language codes indicating the FakeBidi pseudo-locale.
   * The FakeBiDi pseudo-locale unfortunately currently does not have an accepted language code.
   * Some products use 'qbi' ('qXX' is a standard way of indicating a private-use language code,
   * and the 'bi' stands for bidi). Others prefer to tag on '-psrtl' (for pseudo-RTL) to the
   * original locale.
   */
  private static final Pattern FAKE_RTL_LOCALES_PATTERN =
      Pattern.compile("qbi|.*[-_]psrtl", Pattern.CASE_INSENSITIVE);


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
