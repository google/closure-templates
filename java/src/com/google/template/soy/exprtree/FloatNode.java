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


/**
 * Node representing a float value.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class FloatNode extends AbstractPrimitiveNode {


  /** The float value */
  private final double value;


  /**
   * @param value The float value.
   */
  public FloatNode(double value) {
    this.value = value;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected FloatNode(FloatNode orig) {
    super(orig);
    this.value = orig.value;
  }


  @Override public Kind getKind() {
    return Kind.FLOAT_NODE;
  }


  /** Returns the float value. */
  public double getValue() {
    return value;
  }


  @Override public String toSourceString() {
    return Double.toString(value).replace('E', 'e');
  }


  @Override public FloatNode clone() {
    return new FloatNode(this);
  }

}
