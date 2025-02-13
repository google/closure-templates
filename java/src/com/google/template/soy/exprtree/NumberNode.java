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

package com.google.template.soy.exprtree;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.NumberType;

/** Node representing a float value. */
public final class NumberNode extends AbstractPrimitiveNode {

  // JavaScript Number.MAX_SAFE_INTEGER:
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
  private static final long JS_MAX_SAFE_INTEGER = (1L << 53) - 1;
  private static final long JS_MIN_SAFE_INTEGER = -1 * JS_MAX_SAFE_INTEGER;

  /** The float value */
  private final double value;

  /**
   * @param value The float value.
   * @param sourceLocation The node's source location.
   */
  public NumberNode(double value, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.value = value;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private NumberNode(NumberNode orig, CopyState copyState) {
    super(orig, copyState);
    this.value = orig.value;
  }

  /** Returns true if {@code value} is within JavaScript safe range. */
  public static boolean isInRange(long value) {
    return JS_MIN_SAFE_INTEGER <= value && value <= JS_MAX_SAFE_INTEGER;
  }

  @Override
  public Kind getKind() {
    return Kind.NUMBER_NODE;
  }

  @Override
  public NumberType getAuthoredType() {
    return NumberType.getInstance();
  }

  /** Returns the float value. */
  public double getValue() {
    return value;
  }

  public boolean isInteger() {
    return value % 1 == 0 && isInRange((long) value);
  }

  @Override
  public String toSourceString() {
    return BaseUtils.formatDouble(value);
  }

  @Override
  public NumberNode copy(CopyState copyState) {
    return new NumberNode(this, copyState);
  }
}
