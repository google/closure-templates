/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

/**
 * An error thrown by the JsLexer, used to communicate information about where the error ocurred.
 */
final class LexerError extends RuntimeException {

  private final String reason;
  private final int offset;

  LexerError(String reason, int offset) {
    this.reason = reason;
    this.offset = offset;
  }

  /** The error reason, for display in an error message. */
  String getReason() {
    return reason;
  }

  /** Character offset into the string being lexed. */
  int getOffset() {
    return offset;
  }
}
