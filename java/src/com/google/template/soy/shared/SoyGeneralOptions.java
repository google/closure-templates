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

package com.google.template.soy.shared;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.Resources;
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
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Compilation options applicable to the Soy frontend and/or to multiple Soy backends.
 *
 * @author Kai Huang
 */
public class SoyGeneralOptions implements Cloneable {


  /**
   * Levels of safe-print-tags inference.
   */
  public static enum SafePrintTagsInferenceLevel {
    NONE, SIMPLE, ADVANCED;
  }


  /**
   * Schemes for handling {@code css} commands.
   */
  public static enum CssHandlingScheme {
    LITERAL, REFERENCE, BACKEND_SPECIFIC;
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


  /** The level of safe-print-tags inference. */
  private SafePrintTagsInferenceLevel safePrintTagsInferenceLevel;

  /** Scheme for handling 'css' commands. */
  private CssHandlingScheme cssHandlingScheme;

  /** Map from compile-time global name to value. */
  private ImmutableMap<String, PrimitiveData> compileTimeGlobals;


  public SoyGeneralOptions() {
    safePrintTagsInferenceLevel = SafePrintTagsInferenceLevel.NONE;
    cssHandlingScheme = CssHandlingScheme.LITERAL;
    compileTimeGlobals = null;
  }


  /**
   * Sets the level of safe-print-tags inference. For all inferred safe print tags, the compiler
   * automatically adds the '|noAutoescape' directive.
   * <pre>
   *     NONE: No inference. '@safe' declarations are ignored.
   *     SIMPLE: Infers safe print tags based on '@safe' declarations in the current template's
   *         SoyDoc (same behavior for public and private templates).
   *     ADVANCED: Infers safe print tags based on '@safe' declarations in SoyDoc of public
   *         templates. For private templates, infers safe data from the data being passed in all
   *         the calls to the private template from the rest of the Soy code (in the same compiled
   *         bundle).
   * </pre>
   * Also, for inference levels SIMPLE and ADVANCED, the compiler checks calls between templates.
   * Specifically, if the callee template's SoyDoc specifies '@safe' declarations, then the data
   * being passed in the call must be known to be safe for all of those declared safe data paths.
   * Otherwise, a data-safety-mismatch error is reported.
   *
   * @param inferenceLevel The level of safe-print-tags inference to set (0, 1, or 2).
   */
  public void setSafePrintTagsInferenceLevel(SafePrintTagsInferenceLevel inferenceLevel) {
    this.safePrintTagsInferenceLevel = inferenceLevel;
  }


  /**
   * Returns the level of safe-print-tags inference.
   */
  public SafePrintTagsInferenceLevel getSafePrintTagsInferenceLevel() {
    return safePrintTagsInferenceLevel;
  }


  /**
   * Sets the scheme for handling {@code css} commands.
   *
   * @param cssHandlingScheme The css-handling scheme to set.
   */
  public void setCssHandlingScheme(CssHandlingScheme cssHandlingScheme) {
    this.cssHandlingScheme = cssHandlingScheme;
  }


  /**
   * Returns the scheme for handling {@code css} commands.
   */
  public CssHandlingScheme getCssHandlingScheme() {
    return cssHandlingScheme;
  }


  /**
   * Sets the map from compile-time global name to value.
   *
   * <p> The values can be any of the Soy primitive types: null, boolean, integer, float (Java
   * double), or string.
   *
   * @param compileTimeGlobalsMap Map from compile-time global name to value. The values can be
   *     any of the Soy primitive types: null, boolean, integer, float (Java double), or string.
   * @throws SoySyntaxException If one of the values is not a valid Soy primitive type.
   */
  public void setCompileTimeGlobals(Map<String, ?> compileTimeGlobalsMap) {

    Preconditions.checkState(compileTimeGlobals == null, "Compile-time globals already set.");
    compileTimeGlobals = DataUtils.convertCompileTimeGlobalsMap(compileTimeGlobalsMap);
  }


  /**
   * Sets the file containing compile-time globals.
   *
   * <p> Each line of the file should have the format
   * <pre>
   *     &lt;global_name&gt; = &lt;primitive_data&gt;
   * </pre>
   * where primitive_data is a valid Soy expression literal for a primitive type (null, boolean,
   * integer, float, or string). Empty lines and lines beginning with "//" are ignored. The file
   * should be encoded in UTF-8.
   *
   * <p> If you need to generate a file in this format from Java, consider using the utility
   * {@code SoyUtils.generateCompileTimeGlobalsFile()}.
   *
   * @param compileTimeGlobalsFile The file containing compile-time globals.
   * @throws IOException If there is an error reading the compile-time globals file.
   */
  public void setCompileTimeGlobals(File compileTimeGlobalsFile) throws IOException {
    setCompileTimeGlobalsHelper(Files.toString(compileTimeGlobalsFile, Charsets.UTF_8));
  }


  /**
   * Sets the resource file containing compile-time globals.
   *
   * <p> Each line of the file should have the format
   * <pre>
   *     &lt;global_name&gt; = &lt;primitive_data&gt;
   * </pre>
   * where primitive_data is a valid Soy expression literal for a primitive type (null, boolean,
   * integer, float, or string). Empty lines and lines beginning with "//" are ignored. The file
   * should be encoded in UTF-8.
   *
   * <p> If you need to generate a file in this format from Java, consider using the utility
   * {@code SoyUtils.generateCompileTimeGlobalsFile()}.
   *
   * @param compileTimeGlobalsResource The resource file containing compile-time globals.
   * @throws IOException If there is an error reading the compile-time globals file.
   */
  public void setCompileTimeGlobals(URL compileTimeGlobalsResource) throws IOException {
    setCompileTimeGlobalsHelper(Resources.toString(compileTimeGlobalsResource, Charsets.UTF_8));
  }


  /**
   * Private helper for setCompileTimeGlobals(File) and setCompileTimeGlobals(URL).
   *
   * @param compileTimeGlobalsFileContent The content of the file containing compile-time globals.
   */
  private void setCompileTimeGlobalsHelper(String compileTimeGlobalsFileContent) {

    Preconditions.checkState(compileTimeGlobals == null, "Compile-time globals already set.");

    ImmutableMap.Builder<String, PrimitiveData> compileTimeGlobalsBuilder = ImmutableMap.builder();
    List<Pair<CompileTimeGlobalsFileError, String>> errors = Lists.newArrayListWithCapacity(0);

    BufferedReader reader = new BufferedReader(new StringReader(compileTimeGlobalsFileContent));
    try {
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
    } catch (IOException e) {
      throw new AssertionError("Should not have error reading a string.");
    }

    compileTimeGlobals = compileTimeGlobalsBuilder.build();

    if (errors.size() > 0) {
      StringBuilder errorMsgSb =
          new StringBuilder("Compile-time globals file contains the following errors:\n");
      for (Pair<CompileTimeGlobalsFileError, String> error : errors) {
        errorMsgSb.append("[").append(String.format("%-19s", error.first.toString()))
            .append("] ").append(error.second).append("\n");
      }
      throw new SoySyntaxException(errorMsgSb.toString());
    }
  }


  /**
   * Returns the map from compile-time global name to value.
   */
  public ImmutableMap<String, PrimitiveData> getCompileTimeGlobals() {
    return compileTimeGlobals;
  }


  @Override public SoyGeneralOptions clone() {
    try {
      return (SoyGeneralOptions) super.clone();
    } catch (CloneNotSupportedException cnse) {
      throw new RuntimeException("Cloneable interface removed from SoyGeneralOptions");
    }
  }

}
