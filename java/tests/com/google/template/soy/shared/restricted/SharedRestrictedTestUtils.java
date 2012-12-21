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

package com.google.template.soy.shared.restricted;

import com.google.inject.Provider;

import com.google.template.soy.internal.i18n.BidiGlobalDir;


/**
 * Shared utilities for unit tests.
 *
 * <p> Important: This class may only be used when testing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class SharedRestrictedTestUtils {

  private static final BidiGlobalDir BIDI_GLOBAL_DIR_FOR_STATIC_LTR =
      BidiGlobalDir.forStaticIsRtl(false);

  private static final BidiGlobalDir BIDI_GLOBAL_DIR_FOR_STATIC_RTL =
      BidiGlobalDir.forStaticIsRtl(true);

  private static final BidiGlobalDir BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET =
      BidiGlobalDir.forIsRtlCodeSnippet("IS_RTL");


  private SharedRestrictedTestUtils() {}


  public static final Provider<BidiGlobalDir> BIDI_GLOBAL_DIR_FOR_STATIC_LTR_PROVIDER =
      new Provider<BidiGlobalDir>() {
        @Override public BidiGlobalDir get() {
          return BIDI_GLOBAL_DIR_FOR_STATIC_LTR;
        }
      };


  public static final Provider<BidiGlobalDir> BIDI_GLOBAL_DIR_FOR_STATIC_RTL_PROVIDER =
      new Provider<BidiGlobalDir>() {
        @Override public BidiGlobalDir get() {
          return BIDI_GLOBAL_DIR_FOR_STATIC_RTL;
        }
      };


  public static final Provider<BidiGlobalDir> BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET_PROVIDER =
      new Provider<BidiGlobalDir>() {
        @Override public BidiGlobalDir get() {
          return BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET;
        }
      };

}
