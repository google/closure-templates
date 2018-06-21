/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.internal.targetexpr;

import com.google.errorprone.annotations.Immutable;
import java.util.Objects;

/**
 * Value class to represent an expression in the target source (JS, Python, etc.). Includes the text
 * of the expression as well as the precedence of the top-most operator.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>Note that even though the precedence numbers we use are for Soy (see {@link
 * com.google.template.soy.exprtree.Operator#getPrecedence}), the precedence ordering of the Soy
 * expression operators matches that of JS, Python, and Java, so the precedence numbers are correct
 * when used for generating the target code as well.
 *
 */
@Immutable
public class TargetExpr {

  /** The expression text in the target language. */
  private final String text;

  /** The precedence of the top-most operator, or Integer.MAX_VALUE. */
  private final int precedence;

  /**
   * @param text The expression text in the target language.
   * @param precedence The precedence of the top-most operator. Or Integer.MAX_VALUE.
   */
  public TargetExpr(String text, int precedence) {
    this.text = text;
    this.precedence = precedence;
  }

  /** Returns the expression text. */
  public String getText() {
    return text;
  }

  /** Returns the precedence of the top-most operator, or Integer.MAX_VALUE. */
  public int getPrecedence() {
    return precedence;
  }

  @Override
  public String toString() {
    return String.format("%s{text=%s, precedence=%d}", this.getClass().getName(), text, precedence);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || this.getClass() != other.getClass()) {
      return false;
    }
    TargetExpr otherCast = (TargetExpr) other;
    if (this.text.equals(otherCast.text)) {
      if (this.precedence != otherCast.precedence) {
        throw new AssertionError(); // if text is equal, precedence should also be equal
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(text, precedence);
  }
}
