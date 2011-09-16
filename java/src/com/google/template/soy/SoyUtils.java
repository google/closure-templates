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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.internalutils.DataUtils;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.internal.base.Pair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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


  /**
   * Error types for bad lines in the compile-time globals file.
   */
  private static enum CompileTimeGlobalsFileError {

    INVALID_FORMAT("Invalid line format"),
    INVALID_VALUE("Invalid value"),
    NON_PRIMITIVE_VALUE("Non-primitive value");

    private final String errorString;

    private CompileTimeGlobalsFileError(String errorString) {
      this.errorString = errorString;
    }

    @Override public String toString() {
      return errorString;
    }
  }


  /** Pattern for one line in the compile-time globals file. */
  // Note: group 1 = key, group 2 = value.
  private static final Pattern COMPILE_TIME_GLOBAL_LINE =
      Pattern.compile("([a-zA-Z_][a-zA-Z_0-9.]*) \\s* = \\s* (.+)", Pattern.COMMENTS);


  /**
   * Parses a globals file in the format created by {@link #generateCompileTimeGlobalsFile} into a
   * map from global name to primitive value.
   * @param inputSupplier A supplier that returns a reader for the globals file.
   * @return The parsed globals map.
   * @throws IOException If an error occurs while reading the globals file.
   * @throws SoySyntaxException If the globals file is not in the correct format.
   */
  public static ImmutableMap<String, PrimitiveData> parseCompileTimeGlobals(
      InputSupplier<? extends Reader> inputSupplier) throws IOException, SoySyntaxException {
    ImmutableMap.Builder<String, PrimitiveData> compileTimeGlobalsBuilder = ImmutableMap.builder();
    List<Pair<CompileTimeGlobalsFileError, String>> errors = Lists.newArrayListWithCapacity(0);

    BufferedReader reader = new BufferedReader(inputSupplier.getInput());
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {

      if (line.startsWith("//") || line.trim().length() == 0) {
        continue;
      }

      Matcher matcher = COMPILE_TIME_GLOBAL_LINE.matcher(line);
      if (!matcher.matches()) {
        errors.add(Pair.of(CompileTimeGlobalsFileError.INVALID_FORMAT, line));
        continue;
      }
      String name = matcher.group(1);
      String valueText = matcher.group(2).trim();

      PrimitiveData value;
      try {
        ExprNode valueExpr = (new ExpressionParser(valueText)).parseExpression().getChild(0);
        if (!(valueExpr instanceof PrimitiveNode)) {
          if (valueExpr instanceof GlobalNode || valueExpr instanceof DataRefNode) {
            errors.add(Pair.of(CompileTimeGlobalsFileError.INVALID_VALUE, line));
          } else {
            errors.add(Pair.of(CompileTimeGlobalsFileError.NON_PRIMITIVE_VALUE, line));
          }
          continue;
        }
        value = DataUtils.convertPrimitiveExprToData((PrimitiveNode) valueExpr);
      } catch (TokenMgrError tme) {
        errors.add(Pair.of(CompileTimeGlobalsFileError.INVALID_VALUE, line));
        continue;
      } catch (ParseException pe) {
        errors.add(Pair.of(CompileTimeGlobalsFileError.INVALID_VALUE, line));
        continue;
      }

      compileTimeGlobalsBuilder.put(name, value);
    }

    if (errors.size() > 0) {
      StringBuilder errorMsgSb =
          new StringBuilder("Compile-time globals file contains the following errors:\n");
      for (Pair<CompileTimeGlobalsFileError, String> error : errors) {
        errorMsgSb.append("[").append(String.format("%-19s", error.first.toString()))
            .append("] ").append(error.second).append("\n");
      }
      throw new SoySyntaxException(errorMsgSb.toString());
    }

    return compileTimeGlobalsBuilder.build();
  }
}
