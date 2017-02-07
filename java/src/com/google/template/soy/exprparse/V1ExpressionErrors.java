/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.exprparse;

import com.google.template.soy.error.SoyErrorKind;

/** Permanent errors for Soy v1 expressions. */
public final class V1ExpressionErrors {
  public static final SoyErrorKind LEGACY_AND_ERROR =
      SoyErrorKind.of("Found use of ''&&'' instead of the ''and'' operator");
  public static final SoyErrorKind LEGACY_OR_ERROR =
      SoyErrorKind.of("Found use of ''||'' instead of the ''or'' operator");
  public static final SoyErrorKind LEGACY_NOT_ERROR =
      SoyErrorKind.of("Found use of ''!'' instead of the ''not'' operator");
  public static final SoyErrorKind LEGACY_DOUBLE_QUOTED_STRING =
      SoyErrorKind.of("Found use of double quotes, Soy strings use single quotes");

  private V1ExpressionErrors() {}
}
