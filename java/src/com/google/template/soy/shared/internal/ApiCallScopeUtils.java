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

import com.google.inject.Key;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;

import javax.annotation.Nullable;


/**
 * Shared utilities for working with the ApiCallScope.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class ApiCallScopeUtils {

  private ApiCallScopeUtils() {}


  /**
   * Helper utility to seed params shared by multiple backends.
   *
   * @param apiCallScope The scope object that manages the API call scope.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   * @param bidiGlobalDir The bidi global directionality (ltr=1, rtl=-1, or 0 to use a value derived
   *     from the msgBundle locale, if any, otherwise ltr).
   */
  public static void seedSharedParams(
      GuiceSimpleScope apiCallScope, @Nullable SoyMsgBundle msgBundle, int bidiGlobalDir) {
    seedSharedParams(apiCallScope, msgBundle,
                     bidiGlobalDir == 0 ? null : BidiGlobalDir.forStaticIsRtl(bidiGlobalDir < 0));
  }


  /**
   * Helper utility to seed params shared by multiple backends.
   *
   * @param apiCallScope The scope object that manages the API call scope.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   * @param bidiGlobalDir The bidi global directionality. If null, it is derived from the msgBundle
   *     locale, if any, otherwise ltr.
   */
  public static void seedSharedParams(
      GuiceSimpleScope apiCallScope, @Nullable SoyMsgBundle msgBundle,
      @Nullable BidiGlobalDir bidiGlobalDir) {

    String localeString = (msgBundle != null) ? msgBundle.getLocaleString() : null;
    if (bidiGlobalDir == null) {
        bidiGlobalDir = BidiGlobalDir.forStaticLocale(localeString);
    }

    apiCallScope.seed(SoyMsgBundle.class, msgBundle);
    apiCallScope.seed(Key.get(String.class, LocaleString.class), localeString);
    apiCallScope.seed(BidiGlobalDir.class, bidiGlobalDir);
  }

}
