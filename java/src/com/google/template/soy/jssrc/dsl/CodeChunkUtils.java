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

package com.google.template.soy.jssrc.dsl;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Utility methods for working with CodeChunks. */
public final class CodeChunkUtils {

  /**
   * See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Grammar_and_types#Variables
   *
   * <p>This incorrectly allows keywords, but that's not a big problem because doing so would cause
   * JSCompiler to crash. This also incorrectly disallows Unicode in identifiers, but that's not a
   * big problem because the JS backend generally names identifiers after Soy identifiers, which
   * don't allow Unicode either.
   */
  private static final Pattern ID = Pattern.compile("[A-Za-z_$][\\w$]*");

  private CodeChunkUtils() {}

  /** Validates that the given string is a valid javascript identifier. */
  static void checkId(String id) {
    if (!ID.matcher(id).matches()) {
      throw new IllegalArgumentException(String.format("not a valid js identifier: %s", id));
    }
  }

  /**
   * Outputs a stringified parameter list (e.g. `foo, bar, baz`) from JsDoc. Used e.g. in function
   * and method declarations.
   */
  static String generateParamList(JsDoc jsDoc, boolean addInlineTypeAnnotations) {
    ImmutableList<JsDoc.Param> params = jsDoc.params();
    List<String> functionParameters = new ArrayList<>();
    for (JsDoc.Param param : params) {
      if ("param".equals(param.annotationType())) {
        if (addInlineTypeAnnotations) {
          functionParameters.add(String.format("/* %s */ %s", param.type(), param.paramTypeName()));
        } else {
          functionParameters.add(param.paramTypeName());
        }
      }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < functionParameters.size(); i++) {
      sb.append(functionParameters.get(i));
      if (i + 1 < functionParameters.size()) {
        sb.append(", ");
      }
    }
    return sb.toString();
  }
}
