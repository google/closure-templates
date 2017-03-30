/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.inject.Injector;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Utilities for unit tests in the Js Src backend.
 *
 */
final class JsSrcTestUtils {

  private JsSrcTestUtils() {}

  /**
   * Simulates the start of a new Soy API call by entering/re-entering the ApiCallScope and seeding
   * scoped values.
   *
   * @param injector The Guice injector responsible for injections during the API call.
   */
  @CheckReturnValue
  public static GuiceSimpleScope.InScope simulateNewApiCall(Injector injector) {
    return simulateNewApiCall(injector, new SoyJsSrcOptions());
  }

  /**
   * Simulates the start of a new Soy API call by entering/re-entering the ApiCallScope and seeding
   * scoped values.
   *
   * @param injector The Guice injector responsible for injections during the API call.
   * @param jsSrcOptions The options for generating JS source code.
   */
  @CheckReturnValue
  public static GuiceSimpleScope.InScope simulateNewApiCall(
      Injector injector, SoyJsSrcOptions jsSrcOptions) {
    return simulateNewApiCall(injector, jsSrcOptions, null, BidiGlobalDir.LTR);
  }

  /**
   * Simulates the start of a new Soy API call by entering/re-entering the ApiCallScope and seeding
   * scoped values.
   *
   * @param injector The Guice injector responsible for injections during the API call.
   * @param jsSrcOptions The options for generating JS source code.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param bidiGlobalDir The bidi global directionality
   */
  private static GuiceSimpleScope.InScope simulateNewApiCall(
      Injector injector,
      SoyJsSrcOptions jsSrcOptions,
      @Nullable SoyMsgBundle msgBundle,
      BidiGlobalDir bidiGlobalDir) {

    GuiceSimpleScope.InScope apiCallScope =
        SharedTestUtils.simulateNewApiCall(injector, msgBundle, bidiGlobalDir);
    apiCallScope.seed(SoyJsSrcOptions.class, jsSrcOptions);
    return apiCallScope;
  }
}
