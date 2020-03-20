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

package com.google.template.soy.data;

import com.google.auto.value.AutoOneOf;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleSheet;

/**
 * Wrapper type used in the generated {@link SoyTemplate} API for Soy parameters of type {@code
 * css}.
 */
@AutoOneOf(CssParam.Type.class)
public abstract class CssParam {
  enum Type {
    SAFE_STYLE,
    SAFE_STYLE_SHEET
  }

  public static CssParam of(SafeStyle safeStyle) {
    return AutoOneOf_CssParam.safeStyle(safeStyle);
  }

  public static CssParam of(SafeStyleSheet safeStyleSheet) {
    return AutoOneOf_CssParam.safeStyleSheet(safeStyleSheet);
  }

  abstract Type type();

  abstract SafeStyle safeStyle();

  abstract SafeStyleSheet safeStyleSheet();
}
