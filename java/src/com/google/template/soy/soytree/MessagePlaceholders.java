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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;

/** Constants and utilities for message placeholders */
public final class MessagePlaceholders {
  public static final String PHNAME_ATTR = "phname";
  public static final String PHEX_ATTR = "phex";
  private static final SoyErrorKind INVALID_PHNAME_ATTRIBUTE =
      SoyErrorKind.of("''phname'' is not a valid identifier.");
  private static final SoyErrorKind INVALID_PHNAME_EXAMPLE =
      SoyErrorKind.of("Placeholder examples must be non-empty.");

  static String validatePlaceholderName(
      String placeholderName, SourceLocation location, ErrorReporter reporter) {
    if (BaseUtils.isIdentifier(placeholderName)) {
      return placeholderName;
    }
    reporter.report(location, INVALID_PHNAME_ATTRIBUTE);
    return null;
  }

  static String validatePlaceholderExample(
      String example, SourceLocation location, ErrorReporter reporter) {
    if (!example.isEmpty()) {
      return example;
    }
    reporter.report(location, INVALID_PHNAME_EXAMPLE);
    return null;
  }
}
