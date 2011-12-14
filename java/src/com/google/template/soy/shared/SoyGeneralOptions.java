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
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.template.soy.SoyUtils;
import com.google.template.soy.data.internalutils.DataUtils;
import com.google.template.soy.data.restricted.PrimitiveData;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;


/**
 * Compilation options applicable to the Soy frontend and/or to multiple Soy backends.
 *
 */
public class SoyGeneralOptions implements Cloneable {


  /**
   * Schemes for handling {@code css} commands.
   */
  public static enum CssHandlingScheme {
    LITERAL, REFERENCE, BACKEND_SPECIFIC;
  }


  /** Whether to allow external calls (calls to undefined templates). Null if not explicitly set. */
  private Boolean allowExternalCalls;

  /** Scheme for handling 'css' commands. */
  private CssHandlingScheme cssHandlingScheme;

  /** Map from compile-time global name to value. */
  private ImmutableMap<String, PrimitiveData> compileTimeGlobals;


  public SoyGeneralOptions() {
    allowExternalCalls = null;
    cssHandlingScheme = CssHandlingScheme.LITERAL;
    compileTimeGlobals = null;
  }


  /**
   * Sets whether to allow external calls (calls to undefined templates).
   * @param allowExternalCalls The value to set.
   */
  public void setAllowExternalCalls(boolean allowExternalCalls) {
    this.allowExternalCalls = allowExternalCalls;
  }


  /**
   * Returns whether to allow external calls (calls to undefined templates). If this option was
   * never explicitly set, then returns null.
   */
  public Boolean allowExternalCalls() {
    return allowExternalCalls;
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
   * @throws com.google.template.soy.base.SoySyntaxException If one of the values is not a valid
   *        Soy primitive type.
   */
  public void setCompileTimeGlobals(Map<String, ?> compileTimeGlobalsMap) {
    setCompileTimeGlobalsInternal(DataUtils.convertCompileTimeGlobalsMap(compileTimeGlobalsMap));
  }


  /**
   * Sets the map from compile-time global name to value using Soy primitive types.
   *
   * @param compileTimeGlobalsMap Map from compile-time global name to value.
   */
  private void setCompileTimeGlobalsInternal(
      ImmutableMap<String, PrimitiveData> compileTimeGlobalsMap) {
    Preconditions.checkState(compileTimeGlobals == null, "Compile-time globals already set.");
    compileTimeGlobals = compileTimeGlobalsMap;
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
    setCompileTimeGlobalsInternal(SoyUtils.parseCompileTimeGlobals(
        Files.newReaderSupplier(compileTimeGlobalsFile, Charsets.UTF_8)));
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
    setCompileTimeGlobalsInternal(SoyUtils.parseCompileTimeGlobals(
        Resources.newReaderSupplier(compileTimeGlobalsResource, Charsets.UTF_8)));
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
      throw new RuntimeException("Cloneable interface removed from SoyGeneralOptions.");
    }
  }

}
