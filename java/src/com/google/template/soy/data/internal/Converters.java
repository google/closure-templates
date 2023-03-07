/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.data.internal;

import com.google.common.base.Converter;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;

/** Converters between runtime and type system content kinds. */
public final class Converters {

  static final Converter<SanitizedContentKind, ContentKind> CONTENT_KIND_CONVERTER =
      new Converter<SanitizedContentKind, ContentKind>() {
        @Override
        protected ContentKind doForward(SanitizedContentKind sanitizedContentKind) {
          if (sanitizedContentKind == SanitizedContentKind.HTML_ELEMENT) {
            return ContentKind.HTML;
          }
          return ContentKind.valueOf(sanitizedContentKind.name());
        }

        @Override
        protected SanitizedContentKind doBackward(ContentKind contentKind) {
          return SanitizedContentKind.valueOf(contentKind.name());
        }
      };

  public static ContentKind toContentKind(SanitizedContentKind sanitizedContentKind) {
    return CONTENT_KIND_CONVERTER.convert(sanitizedContentKind);
  }

  public static SanitizedContentKind toSanitizedContentKind(ContentKind contentKind) {
    return CONTENT_KIND_CONVERTER.reverse().convert(contentKind);
  }

  private Converters() {}
}
