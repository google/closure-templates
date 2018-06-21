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
import com.google.template.soy.base.SoyBackendKind;
import java.util.regex.Pattern;

/**
 * Bidi utilities for Soy code.
 *
 * <p>This is separate from {@link BidiUtils} for ease of unforking the latter, if we ever decide to
 * do so.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SoyBidiUtils {

  private SoyBidiUtils() {}

  /** The name used as an alias for importing a module containing the bidiIsRtlFn. */
  public static final String IS_RTL_MODULE_ALIAS = "external_bidi";

  /**
   * The code snippet that can be used to determine at template runtime whether the bidi global
   * direction is rtl.
   */
  private static final String GOOG_IS_RTL_CODE_SNIPPET = "soy.$$IS_LOCALE_RTL";
  private static final String GOOG_IS_RTL_CODE_SNIPPET_NAMESPACE = "soy";

  /**
   * Gets the bidi global directionality from a language/locale string (ltr=1, rtl=-1).
   *
   * @param localeString The language/locale string for which to get the bidi global directionality.
   * @return 1 if the language/locale is left-to-right or unknown, and -1 if it's right-to-left.
   */
  static BidiGlobalDir getBidiGlobalDir(String localeString) {
    boolean isRtl;
    try {
      isRtl =
          localeString != null
              && (BidiUtils.isRtlLanguage(localeString)
                  || FAKE_RTL_LOCALES_PATTERN.matcher(localeString).matches());
    } catch (IllegalArgumentException localeException) {
      isRtl = false;
    }
    return BidiGlobalDir.forStaticIsRtl(isRtl);
  }

  /**
   * Decodes the bidi global directionality from the usual command line options used to specify it.
   * Checks that at most one of the options was specified.
   *
   * @param bidiGlobalDir 1: ltr, -1: rtl, 0: unspecified.
   * @param useGoogIsRtlForBidiGlobalDir Whether to determine the bidi global direction at template
   *     runtime by evaluating goog.i18n.bidi.IS_RTL.
   * @return BidiGlobalDir object - or null if neither option was specified.
   */
  public static BidiGlobalDir decodeBidiGlobalDirFromJsOptions(
      int bidiGlobalDir, boolean useGoogIsRtlForBidiGlobalDir) {
    if (bidiGlobalDir == 0) {
      if (!useGoogIsRtlForBidiGlobalDir) {
        return null;
      }
      return BidiGlobalDir.forIsRtlCodeSnippet(
          GOOG_IS_RTL_CODE_SNIPPET, GOOG_IS_RTL_CODE_SNIPPET_NAMESPACE, SoyBackendKind.JS_SRC);
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
   * Decodes bidi global directionality from the Python bidiIsRtlFn command line option.
   *
   * @param bidiIsRtlFn The string containing the full module path and function name.
   * @return BidiGlobalDir object - or null if the option was not specified.
   */
  public static BidiGlobalDir decodeBidiGlobalDirFromPyOptions(String bidiIsRtlFn) {
    if (bidiIsRtlFn == null || bidiIsRtlFn.isEmpty()) {
      return null;
    }
    int dotIndex = bidiIsRtlFn.lastIndexOf('.');
    Preconditions.checkArgument(
        dotIndex > 0 && dotIndex < bidiIsRtlFn.length() - 1,
        "If specified a bidiIsRtlFn must include the module path to allow for proper importing.");
    // When importing the module, we'll using the constant name to avoid potential conflicts.
    String fnName = bidiIsRtlFn.substring(dotIndex + 1) + "()";
    return BidiGlobalDir.forIsRtlCodeSnippet(
        IS_RTL_MODULE_ALIAS + '.' + fnName, null, SoyBackendKind.PYTHON_SRC);
  }

  /**
   * A regular expression for matching language codes indicating the FakeBidi pseudo-locale. The
   * FakeBiDi pseudo-locale unfortunately currently does not have an accepted language code. Some
   * products use 'qbi' ('qXX' is a standard way of indicating a private-use language code, and the
   * 'bi' stands for bidi). Others prefer to tag on '-psrtl' (for pseudo-RTL) to the original
   * locale.
   */
  private static final Pattern FAKE_RTL_LOCALES_PATTERN =
      Pattern.compile("qbi|.*[-_]psrtl", Pattern.CASE_INSENSITIVE);
}
