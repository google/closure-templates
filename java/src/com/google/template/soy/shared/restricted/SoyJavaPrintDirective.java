/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.shared.restricted;

import com.google.template.soy.data.SoyValue;
import java.util.List;

/**
 * Interface for a Soy print directive implemented for Java runtime rendering. Directives
 * implementing this interface will be used during Tofu rendering, and may also be used during
 * optimization passes if the directive is also marked with annotation
 * {@code @SoyPurePrintDirective}.
 *
 * <p>Important: This may only be used in implementing print directive plugins.
 *
 * @deprecated Use Soy functions instead
 */
@Deprecated
public interface SoyJavaPrintDirective extends SoyPrintDirective {

  /**
   * Applies this directive on the given value.
   *
   * @param value The input to the directive. This is not necessarily a string. If a directive only
   *     applies to string inputs, then it should first call {@code coerceToString()} on this input
   *     value.
   * @param args The directive's arguments, if any (often none).
   * @return The resulting value. Must be either {@code StringData} or {@code SanitizedContent}.
   */
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args);
}
