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
 * Node representing an access using literal identifier key.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class DataRefAccessKeyNode extends DataRefAccessNode {


  /** The key. */
  private final String key;


  /**
   * @param isNullSafe Whether this access first checks that the left side is defined and nonnull.
   * @param key The key.
   */
  public DataRefAccessKeyNode(boolean isNullSafe, String key) {
    super(isNullSafe);
    this.key = key;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected DataRefAccessKeyNode(DataRefAccessKeyNode orig) {
    super(orig);
    this.key = orig.key;
  }


  @Override public Kind getKind() {
    return Kind.DATA_REF_ACCESS_KEY_NODE;
  }


  /** Returns the key. */
  public String getKey() {
    return key;
  }


  @Override public String toSourceString() {
    return (isNullSafe() ? "?" : "") + "." + key;
  }


  @Override public DataRefAccessKeyNode clone() {
    return new DataRefAccessKeyNode(this);
  }

}
