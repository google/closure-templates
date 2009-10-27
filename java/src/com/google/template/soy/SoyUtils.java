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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.internalutils.DataUtils;
import com.google.template.soy.data.restricted.PrimitiveData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Map;


/**
 * Public utilities for Soy users.
 *
 * @author Kai Huang
 */
public class SoyUtils {


  private SoyUtils() {}


  /**
   * Generates the text for a compile-time globals file in the format expected by the Soy compiler
   * and appends the generated text to the given {@code Appendable}.
   *
   * <p> The generated lines will follow the iteration order of the provided map.
   *
   * <p> Important: When you write the output to a file, be sure to use UTF-8 encoding.
   *
   * @param compileTimeGlobalsMap Map from compile-time global name to value. The values can be
   *     any of the Soy primitive types: null, boolean, integer, float (Java double), or string.
   * @param output The object to append the generated text to.
   * @throws SoySyntaxException If one of the values is not a valid Soy primitive type.
   * @throws IOException If there is an error appending to the given {@code Appendable}.
   * @see #generateCompileTimeGlobalsFile(Map, File) 
   */
  public static void generateCompileTimeGlobalsFile(
      Map<String, ?> compileTimeGlobalsMap, Appendable output) throws IOException {

    Map<String, PrimitiveData> compileTimeGlobals =
        DataUtils.convertCompileTimeGlobalsMap(compileTimeGlobalsMap);

    for (Map.Entry<String, PrimitiveData> entry : compileTimeGlobals.entrySet()) {
      String valueSrcStr = DataUtils.convertPrimitiveDataToExpr(entry.getValue()).toSourceString();
      output.append(entry.getKey()).append(" = ").append(valueSrcStr).append("\n");
    }
  }


  /**
   * Generates a compile-time globals file in the format expected by the Soy compiler.
   *
   * <p> The generated lines will follow the iteration order of the provided map.
   *
   * @param compileTimeGlobalsMap Map from compile-time global name to value. The values can be
   *     any of the Soy primitive types: null, boolean, integer, float (Java double), or string.
   * @param file The file to write the generated text to.
   * @throws SoySyntaxException If one of the values is not a valid Soy primitive type.
   * @throws IOException If there is an error appending to the given {@code Appendable}.
   * @see #generateCompileTimeGlobalsFile(Map, Appendable)
   */
  public static void generateCompileTimeGlobalsFile(
      Map<String, ?> compileTimeGlobalsMap, File file) throws IOException {

    BufferedWriter writer = Files.newWriter(file, Charsets.UTF_8);
    generateCompileTimeGlobalsFile(compileTimeGlobalsMap, writer);
    writer.close();
  }

}
