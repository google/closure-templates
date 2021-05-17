/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.internalutils.InternalValueUtils;
import com.google.template.soy.data.restricted.PrimitiveData;
import java.io.IOException;
import java.util.Map;

/** Public utilities for Soy users. */
public final class SoyUtils {

  private SoyUtils() {}

  /**
   * Generates the text for a Soy file containing a list of exported constants and appends the
   * generated text to the given {@code Appendable}.
   *
   * <p>The generated lines will follow the iteration order of the provided map. Map keys will be
   * converted to valid Soy single identifiers, replacing '.' with '_' if necessary.
   *
   * <p>Important: When you write the output to a file, be sure to use UTF-8 encoding.
   *
   * @param namespace The namespace of the generated Soy file.
   * @param constantNameToJavaValue Map from compile-time global name to value. The values can be
   *     any of the Soy primitive types: null, boolean, integer, float (Java double), or string.
   * @param output The object to append the generated text to.
   * @throws IllegalArgumentException If one of the values is not a valid Soy primitive type.
   * @throws IOException If there is an error appending to the given {@code Appendable}.
   */
  public static void generateConstantsFile(
      String namespace, Map<String, ?> constantNameToJavaValue, Appendable output)
      throws IOException {

    Map<String, PrimitiveData> constantNameToSoyValue =
        InternalValueUtils.convertConstantsMap(constantNameToJavaValue);

    output.append("{namespace ").append(namespace).append("}\n\n");
    for (Map.Entry<String, PrimitiveData> entry : constantNameToSoyValue.entrySet()) {
      String valueSrcStr =
          InternalValueUtils.convertPrimitiveDataToExpr(entry.getValue(), SourceLocation.UNKNOWN)
              .toSourceString();
      output
          .append("{export const ")
          .append(entry.getKey().replace('.', '_'))
          .append(" = ")
          .append(valueSrcStr)
          .append(" /}\n");
    }
  }
}
