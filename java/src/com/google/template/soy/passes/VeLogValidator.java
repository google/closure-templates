/*
 * Copyright 2018 Google Inc.
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
package com.google.template.soy.passes;

import com.google.common.base.Optional;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;

/** Validates VE log usages. */
final class VeLogValidator {

  private static final SoyErrorKind NO_CONFIG_FOR_ELEMENT =
      SoyErrorKind.of(
          "Could not find logging configuration for this element.{0}",
          StyleAllowance.NO_PUNCTUATION);

  private final ValidatedLoggingConfig loggingConfig;
  private final ErrorReporter errorReporter;

  VeLogValidator(ValidatedLoggingConfig loggingConfig, ErrorReporter errorReporter) {
    this.loggingConfig = loggingConfig;
    this.errorReporter = errorReporter;
  }

  /**
   * Gets the logging element associated with {@code name}. If it can't be found, returns {@code
   * absent} and logs an error at {@code loc}.
   */
  Optional<ValidatedLoggableElement> getLoggingElement(String name, SourceLocation loc) {
    ValidatedLoggableElement config = loggingConfig.getElement(name);

    if (config == null) {
      errorReporter.report(
          loc,
          NO_CONFIG_FOR_ELEMENT,
          SoyErrors.getDidYouMeanMessage(loggingConfig.allKnownIdentifiers(), name));
    }
    return Optional.fromNullable(config);
  }
}
