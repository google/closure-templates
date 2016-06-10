/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.incrementaldomsrc;

import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.jssrc.internal.JsSrcUtils;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.primitive.SanitizedType;

/** Contains helper method to get the right JsType for a given soy type. */
final class IncrementalDomSrcUtils {

  private IncrementalDomSrcUtils() {
  }

  /**
   * Given a Soy type, return the corresponding jscompiler type name. Only
   * handles types which have names and have a declared constructor - not
   * arbitrary type expressions.
   */
  static String getJsTypeName(SoyType type) {
    if (type instanceof SanitizedType) {
      return NodeContentKinds.toIDOMSanitizedContentCtorName(
          ((SanitizedType) type).getContentKind());
    } else {
      return JsSrcUtils.getJsTypeName(type);
    }
  }

}
