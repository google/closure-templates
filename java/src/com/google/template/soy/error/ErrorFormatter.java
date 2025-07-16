/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.error;

import com.google.template.soy.base.SourceLocation;

/** Converts {@link SoyError} to {@link String}. */
public interface ErrorFormatter {

  /** Basic formatter. */
  ErrorFormatter SIMPLE =
      (report) -> {
        SourceLocation location = report.location();
        return String.format(
            "%s:%d:%d: %s: %s",
            location.getFilePath().realPath(),
            location.getBeginLine(),
            location.getBeginColumn(),
            (report.isWarning() ? "warning" : "error"),
            report.message());
      };

  /** Formatter that includes only the error message. */
  ErrorFormatter MESSAGE_ONLY = SoyError::message;

  String format(SoyError report);
}
