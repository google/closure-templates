/*
 * Copyright 2023 Google Inc.
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
import com.google.template.soy.types.UndefinedType;

/** Node representing the "undefined" value. */
public final class UndefinedNode extends AbstractPrimitiveNode {

  public UndefinedNode(SourceLocation sourceLocation) {
    super(sourceLocation);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private UndefinedNode(UndefinedNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.UNDEFINED_NODE;
  }

  @Override
  public UndefinedType getAuthoredType() {
    return UndefinedType.getInstance();
  }

  @Override
  public String toSourceString() {
    return "undefined";
  }

  @Override
  public UndefinedNode copy(CopyState copyState) {
    return new UndefinedNode(this, copyState);
  }
}
