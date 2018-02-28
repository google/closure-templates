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
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.IntType;

/**
 * Node representing a Soy integer value. Note that Soy supports up to JavaScript
 * +-Number.MAX_SAFE_INTEGER at the least; Java and Python backends support full 64 bit longs.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class IntegerNode extends AbstractPrimitiveNode {

  // JavaScript Number.MAX_SAFE_INTEGER:
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
  private static final long JS_MAX_SAFE_INTEGER = (1L << 53) - 1;
  private static final long JS_MIN_SAFE_INTEGER = -1 * JS_MAX_SAFE_INTEGER;

  /** Returns true if {@code value} is within JavaScript safe range. */
  public static boolean isInRange(long value) {
    return JS_MIN_SAFE_INTEGER <= value && value <= JS_MAX_SAFE_INTEGER;
  }

  /** The Soy integer value. */
  private final long value;

  /**
   * @param value The Soy integer value.
   * @param sourceLocation The node's source location.
   */
  public IntegerNode(long value, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.value = value;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private IntegerNode(IntegerNode orig, CopyState copyState) {
    super(orig, copyState);
    this.value = orig.value;
  }

  @Override
  public Kind getKind() {
    return Kind.INTEGER_NODE;
  }

  @Override
  public IntType getType() {
    return IntType.getInstance();
  }

  /** Returns the Soy integer value. */
  public long getValue() {
    return value;
  }

  /** Returns true if the value stored by the node is a 32-bit integer. */
  public boolean isInt() {
    return Integer.MIN_VALUE <= value && value <= Integer.MAX_VALUE;
  }

  @Override
  public String toSourceString() {
    return Long.toString(value);
  }

  @Override
  public IntegerNode copy(CopyState copyState) {
    return new IntegerNode(this, copyState);
  }
}
