/*
 * Copyright 2025 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.NumericCoercions;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.SoyType;

/** Node representing a float value. */
public final class NumberNode extends AbstractPrimitiveNode {

  /** The float value */
  private final double value;

  private final boolean isInt;

  /**
   * @param value The float value.
   * @param sourceLocation The node's source location.
   */
  public NumberNode(double value, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.value = value;
    this.isInt = false;
  }

  public NumberNode(long value, SourceLocation sourceLocation) {
    super(sourceLocation);
    Preconditions.checkArgument(NumericCoercions.isInRange(value));
    this.value = (double) value;
    this.isInt = true;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private NumberNode(NumberNode orig, CopyState copyState) {
    super(orig, copyState);
    this.value = orig.value;
    this.isInt = orig.isInt;
  }

  @Override
  public Kind getKind() {
    return Kind.NUMBER_NODE;
  }

  @Override
  public SoyType getAuthoredType() {
    return isInteger() ? IntType.getInstance() : FloatType.getInstance();
  }

  /** Returns the float value. */
  public double getValue() {
    return value;
  }

  public boolean isInteger() {
    return isInt;
  }

  @Override
  public String toSourceString() {
    if (isInteger()) {
      return Long.toString((long) value);
    } else {
      return Double.toString(value).replace("E", "e");
    }
  }

  @Override
  public NumberNode copy(CopyState copyState) {
    return new NumberNode(this, copyState);
  }

  public double doubleValue() {
    return value;
  }

  /** Asserts in safe JS range, truncates non-integer. */
  public long longValue() {
    return NumericCoercions.safeLong(value);
  }

  /** Asserts in range, truncates non-integer. */
  public int intValue() {
    return NumericCoercions.safeInt(value);
  }
}
