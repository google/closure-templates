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

package com.google.template.soy.shared;

import com.google.inject.util.Providers;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import javax.inject.Provider;

/**
 * Shared utilities for unit tests.
 *
 * <p>Important: This class may only be used when testing plugins (e.g. functions, directives).
 *
 */
public class SharedRestrictedTestUtils {

  private static final BidiGlobalDir BIDI_GLOBAL_DIR_FOR_JS_ISRTL_CODE_SNIPPET =
      BidiGlobalDir.forIsRtlCodeSnippet("IS_RTL", null, SoyBackendKind.JS_SRC);

  private static final BidiGlobalDir BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET =
      BidiGlobalDir.forIsRtlCodeSnippet("IS_RTL", null, SoyBackendKind.PYTHON_SRC);

  private SharedRestrictedTestUtils() {}

  public static final Provider<BidiGlobalDir> BIDI_GLOBAL_DIR_FOR_JS_ISRTL_CODE_SNIPPET_PROVIDER =
      Providers.of(BIDI_GLOBAL_DIR_FOR_JS_ISRTL_CODE_SNIPPET);

  public static final Provider<BidiGlobalDir> BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET_PROVIDER =
      Providers.of(BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET);
}
