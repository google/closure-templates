/*
 * Copyright 2012 Google Inc.
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
 * Node representing an access within a data reference.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Note: Even though only one subclass of this class needs to be a parent, we extend
 * {@code AbstractParentExprNode} anyway because the alternative would be to mix in
 * {@code MixinParentNode} directly into the subclass, which is more boilerplate.
 *
 * @author Kai Huang
 */
public abstract class DataRefAccessNode extends AbstractParentExprNode {


  /** Whether this access first checks that the left side is defined and nonnull. */
  private final boolean isNullSafe;


  /**
   * @param isNullSafe Whether this access first checks that the left side is defined and nonnull.
   */
  protected DataRefAccessNode(boolean isNullSafe) {
    this.isNullSafe = isNullSafe;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected DataRefAccessNode(DataRefAccessNode orig) {
    super(orig);
    this.isNullSafe = orig.isNullSafe;
  }


  /**
   * Returns whether this access first checks that the left side is defined and nonnull.
   */
  public boolean isNullSafe() {
    return isNullSafe;
  }

}
