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


/**
 * Compilation options for the Java Src output target (backend).
 *
 * @author Kai Huang
 */
public class SoyJavaSrcOptions {


  /**
   * The two supported code styles.
   */
  public static enum CodeStyle {
    STRINGBUILDER, CONCAT;
  }


  /** The output variable code style to use. */
  private CodeStyle codeStyle;

  /** The bidi global directionality (ltr=1, rtl=-1). */
  private int bidiGlobalDir;


  public SoyJavaSrcOptions() {
    codeStyle = CodeStyle.STRINGBUILDER;
    bidiGlobalDir = 0;
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
   * Sets the bidi global directionality (1 for LTR, -1 for RTL, 0 to infer from message bundle).
   * @param bidiGlobalDir The value to set.
   */
  public void setBidiGlobalDir(int bidiGlobalDir) {
    Preconditions.checkArgument(
        bidiGlobalDir == 1 || bidiGlobalDir == -1 || bidiGlobalDir == 0,
        "Bidi global directionality must be 1 for LTR, -1 for RTL, or 0 to infer this from the" +
        " locale string in the message bundle.");
    this.bidiGlobalDir = bidiGlobalDir;
  }

  /**
   * Returns the bidi global directionality (ltr=1, rtl=-1). This is only applicable iff
   * shouldGenerateJavaMsgDefs is true.
   */
  public int getBidiGlobalDir() {
    return bidiGlobalDir;
  }

}
