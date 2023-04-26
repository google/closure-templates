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
package com.google.template.soy.base.internal;

/** It's what you think it is. */
public enum QuoteStyle {
  SINGLE('\'', "\\'"),
  SINGLE_ESCAPED('\'', "&#39;"),
  DOUBLE('"', "\\\""),
  DOUBLE_ESCAPED('\"', "&quot;"),
  BACKTICK('`', "\\`");

  private final char quotChar;
  private final String escapeSeq;

  QuoteStyle(char quotChar, String escapeSeq) {
    this.quotChar = quotChar;
    this.escapeSeq = escapeSeq;
  }

  public char getQuoteChar() {
    return quotChar;
  }

  public String getEscapeSeq() {
    return escapeSeq;
  }

  /**
   * Returns a version of this style that will URL encode any encountered characters that would
   * close the string, e.g. ' -> &amp;#39;. This is necessary in HTML/JSX attributes and possibly
   * other contexts.
   */
  public QuoteStyle escaped() {
    switch (this) {
      case SINGLE:
        return SINGLE_ESCAPED;
      case DOUBLE:
        return DOUBLE_ESCAPED;
      default:
        return this;
    }
  }
}
