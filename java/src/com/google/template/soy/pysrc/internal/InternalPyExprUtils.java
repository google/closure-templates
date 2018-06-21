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

package com.google.template.soy.pysrc.internal;

import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.pysrc.restricted.PyExpr;

/** Simple utilities for constructing {@link PyExprs}. */
final class InternalPyExprUtils {

  /**
   * Wraps an expression with the proper SanitizedContent constructor.
   *
   * @param contentKind The kind of sanitized content.
   * @param pyExpr The expression to wrap.
   */
  static PyExpr wrapAsSanitizedContent(SanitizedContentKind contentKind, PyExpr pyExpr) {
    String sanitizer = NodeContentKinds.toPySanitizedContentOrdainer(contentKind);
    String approval =
        "sanitize.IActuallyUnderstandSoyTypeSafetyAndHaveSecurityApproval("
            + "'Internally created Sanitization.')";
    return new PyExpr(
        sanitizer + "(" + pyExpr.getText() + ", approval=" + approval + ")", Integer.MAX_VALUE);
  }

  private InternalPyExprUtils() {}
}
