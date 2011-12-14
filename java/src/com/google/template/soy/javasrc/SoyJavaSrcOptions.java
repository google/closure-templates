/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.javasrc;

import com.google.common.base.Preconditions;
import com.google.template.soy.shared.SoyCssRenamingMap;


/**
 * Compilation options for the Java Src output target (backend).
 *
 */
public class SoyJavaSrcOptions implements Cloneable {


  /**
   * The two supported code styles.
   */
  public static enum CodeStyle {
    /** Template methods take a {@code StringBuilder} to which the template output is appended. */
    STRINGBUILDER,
    /** Template methods return a {@code String} containing the template output. */
    CONCAT;
  }


  /** A CSS renamer hint that prevents compile-time renaming.  This is the default. */
  private static final SoyCssRenamingMap NO_COMPILE_TIME_CSS_RENAMING = new SoyCssRenamingMap() {

    @Override
    public String get(String key) {
      return null;  // Indicates that no compile time renaming can be done.
    }

  };


  /** The output variable code style to use. */
  private CodeStyle codeStyle;

  /**
   * The bidi global directionality as a static value, 1: ltr, -1: rtl, 0: unspecified. If 0, the
   * bidi global directionality will be inferred from the message bundle locale. This is the
   * recommended mode of operation.
   */
  private int bidiGlobalDir;

  /**
   * A CSS renaming map that may specify, at compile time how some CSS selectors should be renamed,
   * but return {@code null} to indicate that others cannot be renamed at compile time.
   */
  private SoyCssRenamingMap cssRenamingHints;


  public SoyJavaSrcOptions() {
    codeStyle = CodeStyle.STRINGBUILDER;
    bidiGlobalDir = 0;
    cssRenamingHints = NO_COMPILE_TIME_CSS_RENAMING;
  }


  /**
   * Sets the output variable code style to use.
   * @param codeStyle The code style to set.
   */
  public void setCodeStyle(CodeStyle codeStyle) {
    this.codeStyle = codeStyle;
  }


  /** Returns the currently set code style. */
  public CodeStyle getCodeStyle() {
    return codeStyle;
  }


  /**
   * Sets the bidi global directionality to a static value, 1: ltr, -1: rtl, 0: unspecified. If 0,
   * the bidi global directionality will be inferred from the message bundle locale. This is the
   * recommended mode of operation. Thus, THERE IS USUALLY NO NEED TO USE THIS METHOD!
   *
   * @param bidiGlobalDir 1: ltr, -1: rtl, 0: unspecified. Checks that no other value is used.
   */
  public void setBidiGlobalDir(int bidiGlobalDir) {
    Preconditions.checkArgument(
        bidiGlobalDir >= -1 && bidiGlobalDir <= 1,
        "bidiGlobalDir must be 1 for LTR, or -1 for RTL (or 0 to leave unspecified).");
    this.bidiGlobalDir = bidiGlobalDir;
  }


  /**
   * Returns the static bidi global directionality, 1: ltr, -1: rtl, 0: unspecified.
   */
  public int getBidiGlobalDir() {
    return bidiGlobalDir;
  }


  /**
   * Sets the CSS renaming map that specifies at compile time how CSS selectors should be renamed.
   *
   * <p>
   * By default, no renaming is done.  If you know that no renaming is needed, ever, use the
   * {@link SoyCssRenamingMap#IDENTITY identity renamer}.
   *
   * @param cssRenamingHints Returns {@code null} to indicate that a given CSS selector cannot be
   *     renamed at compile time.
   */
  public void setCssRenamingHints(SoyCssRenamingMap cssRenamingHints) {
    this.cssRenamingHints = cssRenamingHints;
  }


  /**
   * Returns the CSS renaming map that specifies at compile time how to rename CSS selectors.
   */
  public SoyCssRenamingMap getCssRenamingHints() {
    return cssRenamingHints;
  }


  @Override public SoyJavaSrcOptions clone() {
    try {
      return (SoyJavaSrcOptions) super.clone();
    } catch (CloneNotSupportedException cnse) {
      throw new RuntimeException("Cloneable interface removed from SoyJavaSrcOptions.");
    }
  }

}
