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


package com.google.template.soy.tofu.internal;

import com.google.inject.Injector;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SharedTestUtils;

import javax.annotation.Nullable;

/**
 * Utilities for unit tests in the Java Object (tofu) backend.
 *
 */
public class TofuTestUtils {

  private TofuTestUtils() {}


  /**
   * Simulates the start of a new Soy API call by entering/re-entering the ApiCallScope and seeding
   * scoped values.
   *
   * @param injector The Guice injector responsible for injections during the API call.
   */
  public static void simulateNewApiCall(Injector injector) {
    simulateNewApiCall(injector, null, 0);
  }


  /**
   * Simulates the start of a new Soy API call by entering/re-entering the ApiCallScope and seeding
   * scoped values.
   *
   * @param injector The Guice injector responsible for injections during the API call.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @param bidiGlobalDir The bidi global directionality (ltr=1, rtl=-1), or 0 to use a value
   *     derived from the message bundle.
   */
  public static void simulateNewApiCall(
      Injector injector, @Nullable SoyMsgBundle msgBundle, int bidiGlobalDir) {
    SharedTestUtils.simulateNewApiCall(injector, msgBundle, bidiGlobalDir);
  }


}
