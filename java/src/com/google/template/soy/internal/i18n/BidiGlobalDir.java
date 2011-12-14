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

package com.google.template.soy.internal.i18n;

import com.google.common.base.Preconditions;
import javax.annotation.Nullable;


/**
 * Bidi global direction, which is either a "static" integer value (ltr=1, rtl=-1), or a
 * code snippet yielding such a value when evaluated at template runtime.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class BidiGlobalDir {

  /**
   * A source code snippet that evaluates at template runtime to the bidi global direction, i.e.
   * one of the integer values 1 (ltr), 0 (unknown), and -1 (rtl). When the bidi global direction is
   * static, the code snippet is still set: it is simply either "1" or "-1". (Zero is not allowed as
   * a static bidi global direction.) The code snippet should never be null or empty.
   */
  private String codeSnippet;

  /**
   * The "static" bidi global direction, as an integer: ltr=1, rtl=-1, unknown=0. It is zero if and
   * only if the bidi global direction is non-static: it has to be determined at template runtime by
   * evaluating the piece of code in codeSnippet.
   */
  private int staticValue;


  /**
   * Creates a "static" bidi global direction, i.e. one known at the time of the call.
   *
   * @param staticValue The "static" bidi global direction, as an integer: ltr=1, rtl=-1. Should
   *     never have any other value, including 0.
   */
  private BidiGlobalDir(int staticValue) {
    this.staticValue = staticValue;
    this.codeSnippet = Integer.toString(staticValue);
  }


  /**
   * Creates a bidi global direction that can only be determined at template runtime, by evaluating
   * a given source code snippet.
   *
   * @param codeSnippet A source code snippet that evaluates at template runtime to the bidi global
   * direction, i.e. one of the integer values 1 (ltr), 0 (unknown), and -1 (rtl). (Zero, however,
   * gives poor results and is highly discouraged.) The code snippet should never be null or empty.
   */
  private BidiGlobalDir(String codeSnippet) {
    this.codeSnippet = codeSnippet;
    // staticValue remains 0.
  }


  /**
   * Creates a "static" bidi global direction, i.e. one known at the time of the call, given a
   * boolean value where true indicates rtl.
   *
   * @param isRtl Whether the global direction value is rtl. Otherwise, it is ltr.
   */
  public static BidiGlobalDir forStaticIsRtl(boolean isRtl) {
    return new BidiGlobalDir(isRtl ? -1 : 1);
  }


  /**
   * Creates a "static" bidi global direction, i.e. one known at the time of the call, based on a
   * locale string. A null locale indicates ltr.
   *
   * @param localeString A BCP 47 locale string. If null, indicates ltr.
   */
  public static BidiGlobalDir forStaticLocale(@Nullable String localeString) {
    return new BidiGlobalDir(SoyBidiUtils.getBidiGlobalDir(localeString));
  }


  /**
   * Creates a bidi global direction that can only be determined at template runtime, by evaluating
   * a given source code snippet that yields a boolean value where true indicates rtl.
   *
   * @param isRtlCodeSnippet A code snippet that will evaluate at template runtime to a boolean
   *     value indicating whether the bidi global direction is rtl.
   */
  public static BidiGlobalDir forIsRtlCodeSnippet(String isRtlCodeSnippet) {
    Preconditions.checkArgument(isRtlCodeSnippet != null && isRtlCodeSnippet.length() > 0,
        "Bidi global direction source code snippet must be non-empty.");
    return new BidiGlobalDir(isRtlCodeSnippet + "?-1:1");
  }


  /**
   * Returns whether the bidi global direction is "static", i.e. is available now via
   * getStaticValue(), as opposed to having to be determined at template runtime by evaluating
   * the code returned by getCodeSnippet().
   */
  public boolean isStaticValue() {
    return staticValue != 0;
  }


  /**
   * The "static" bidi global direction, as an integer: ltr=1, rtl=-1.
   * If the bidi global direction is non-static, then calling this method will produce an exception.
   */
  public int getStaticValue() {
    if (staticValue == 0) {
      throw new RuntimeException("Cannot get static value for nonstatic BidiGlobalDir object.");
    }
    return staticValue;
  }


  /**
   * A source code snippet that evaluates at template runtime to the bidi global direction, i.e.
   * one of the integer values 1 (ltr), 0 (unknown), and -1 (rtl). When the bidi global direction is
   * static, returns a string representing an integer literal, e.g. "1". Thus, should never be null
   * or empty.
   */
  public String getCodeSnippet() {
    return codeSnippet;
  }
}
