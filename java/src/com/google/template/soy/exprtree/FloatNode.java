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
import com.google.template.soy.base.internal.NumericCoercions;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.FloatType;

/** Node representing a float value. */
public final class FloatNode extends NumberNode {

  /** The float value */
  private final double value;

  /**
   * @param value The float value.
   * @param sourceLocation The node's source location.
   */
  public FloatNode(double value, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.value = value;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private FloatNode(FloatNode orig, CopyState copyState) {
    super(orig, copyState);
    this.value = orig.value;
  }

  @Override
  public Kind getKind() {
    return Kind.FLOAT_NODE;
  }

  @Override
  public FloatType getAuthoredType() {
    return FloatType.getInstance();
  }

  /** Returns the float value. */
  public double getValue() {
    return value;
  }

  @Override
  public String toSourceString() {
    return Double.toString(value).replace('E', 'e');
  }

  @Override
  public FloatNode copy(CopyState copyState) {
    return new FloatNode(this, copyState);
  }

  @Override
  public double doubleValue() {
    return value;
  }

  @Override
  public long longValue() {
    return NumericCoercions.safeLong(value);
  }

  @Override
  public int intValue() {
    return NumericCoercions.safeInt(value);
  }
}
