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

package com.google.template.soy.jssrc.restricted;

import com.google.common.base.Objects;


/**
 * Value class to represent a JS expression. Includes the text of the expression as well as the
 * precedence of the top-most operator.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * <p> Note that even though the precedence numbers we use are for Soy, the precedence ordering of
 * the Soy expression operators matches that of JS (as well as Java), so the precedence numbers are
 * correct when used for generating JS code as well.
 *
 */
public class JsExpr {


  /** The JS expression text. */
  private final String text;

  /** The precedence of the top-most operator, or Integer.MAX_VALUE. */
  private final int precedence;


  /**
   * @param text The JS expression text.
   * @param precedence The precedence of the top-most operator. Or Integer.MAX_VALUE.
   */
  public JsExpr(String text, int precedence) {
    this.text = text;
    this.precedence = precedence;
  }


  /** Returns the JS expression text. */
  public String getText() {
    return text;
  }

  /** Returns the precedence of the top-most operator, or Integer.MAX_VALUE. */
  public int getPrecedence() {
    return precedence;
  }


  @Override public String toString() {
    return Objects.toStringHelper(this).add("text", text).add("precedence", precedence).toString();
  }


  @Override public boolean equals(Object other) {
    if (other == null || this.getClass() != other.getClass()) {
      return false;
    }
    JsExpr otherCast = (JsExpr) other;
    if (this.text.equals(otherCast.text)) {
      if (this.precedence != otherCast.precedence) {
        throw new AssertionError();  // if text is equal, precedence should also be equal
      }
      return true;
    } else {
      return false;
    }
  }


  @Override public int hashCode() {
    return Objects.hashCode(text, precedence);
  }

}
