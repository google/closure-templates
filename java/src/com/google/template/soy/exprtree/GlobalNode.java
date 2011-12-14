/*
 * Copyright 2009 Google Inc.
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
 * Node representing a global.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class GlobalNode extends AbstractExprNode {


  /** The name of the global. */
  private final String name;


  /**
   * @param name The name of the global.
   */
  public GlobalNode(String name) {
    this.name = name;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected GlobalNode(GlobalNode orig) {
    super(orig);
    this.name = orig.name;
  }


  @Override public Kind getKind() {
    return Kind.GLOBAL_NODE;
  }


  /** Returns the name of the global. */
  public String getName() {
    return name;
  }


  @Override public String toSourceString() {
    return name;
  }


  @Override public GlobalNode clone() {
    return new GlobalNode(this);
  }

}
