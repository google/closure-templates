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
package com.google.template.soy.coredirectives;

import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.shared.restricted.EscapingConventions;

/** Runtime functions for implementing the directives in this package. */
public final class CoreDirectivesRuntime {
  public static SanitizedContent escapeHtml(SoyValue value) {
    if (value == null) {
      // jbcsrc uses null as null.
      value = NullData.INSTANCE;
    }
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      if (sanitizedContent.getContentKind() == SanitizedContent.ContentKind.HTML) {
        return (SanitizedContent) value;
      }
      valueDir = sanitizedContent.getContentDirection();
    }
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        EscapingConventions.EscapeHtml.INSTANCE.escape(value.coerceToString()),
        ContentKind.HTML,
        valueDir);
  }
}
