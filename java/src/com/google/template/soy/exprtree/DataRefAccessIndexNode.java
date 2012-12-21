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
 * Node representing an access using literal list index.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class DataRefAccessIndexNode extends DataRefAccessNode {


  /** The index. */
  private final int index;


  /**
   * @param isNullSafe Whether this access first checks that the left side is defined and nonnull.
   * @param index The index.
   */
  public DataRefAccessIndexNode(boolean isNullSafe, int index) {
    super(isNullSafe);
    this.index = index;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected DataRefAccessIndexNode(DataRefAccessIndexNode orig) {
    super(orig);
    this.index = orig.index;
  }


  @Override public Kind getKind() {
    return Kind.DATA_REF_ACCESS_INDEX_NODE;
  }


  /** Returns the index. */
  public int getIndex() {
    return index;
  }


  @Override public String toSourceString() {
    return (isNullSafe() ? "?" : "") + "." + index;
  }


  @Override public DataRefAccessIndexNode clone() {
    return new DataRefAccessIndexNode(this);
  }

}
