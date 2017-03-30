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

package com.google.template.soy.shared.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.Key;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;
import javax.annotation.Nullable;

/**
 * Shared utilities for working with the ApiCallScope.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class ApiCallScopeUtils {
  private static final Key<String> LOCALE_STRING_KEY = Key.get(String.class, LocaleString.class);
  private static final Key<BidiGlobalDir> GLOBAL_DIR_KEY = Key.get(BidiGlobalDir.class);

  private ApiCallScopeUtils() {}

  /**
   * Helper utility to seed params shared by multiple backends.
   *
   * @param inScope The scope object that manages the API call scope.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   */
  public static void seedSharedParams(
      GuiceSimpleScope.InScope inScope, @Nullable SoyMsgBundle msgBundle) {
    seedSharedParams(inScope, msgBundle, null);
  }

  /**
   * Helper utility to seed params shared by multiple backends.
   *
   * @param inScope The scope object that manages the API call scope.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   * @param bidiGlobalDir The bidi global directionality. If null, it is derived from the msgBundle
   *     locale, if any, otherwise ltr.
   */
  public static void seedSharedParams(
      GuiceSimpleScope.InScope inScope,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable BidiGlobalDir bidiGlobalDir) {

    String localeString = (msgBundle != null) ? msgBundle.getLocaleString() : null;
    if (bidiGlobalDir == null) {
      bidiGlobalDir = BidiGlobalDir.forStaticIsRtl(msgBundle == null ? false : msgBundle.isRtl());
    }

    seedSharedParams(inScope, bidiGlobalDir, localeString);
  }

  /**
   * Helper utility to seed params shared by multiple backends.
   *
   * @param inScope The scope object that manages the API call scope.
   * @param bidiGlobalDir The bidi global directionality.
   * @param localeString The current locale.
   */
  public static void seedSharedParams(
      GuiceSimpleScope.InScope inScope,
      BidiGlobalDir bidiGlobalDir,
      @Nullable String localeString) {
    inScope.seed(LOCALE_STRING_KEY, localeString);
    inScope.seed(GLOBAL_DIR_KEY, checkNotNull(bidiGlobalDir));
  }
}
