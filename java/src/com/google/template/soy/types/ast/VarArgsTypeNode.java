/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.types.ast;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;

/** A representation of a varargs type, e.g. "string..." or "float..". */
@AutoValue
public abstract class VarArgsTypeNode extends TypeNode {
  VarArgsTypeNode() {}

  public static VarArgsTypeNode create(SourceLocation sourceLocation, TypeNode baseType) {
    return new AutoValue_VarArgsTypeNode(sourceLocation, baseType);
  }

  public abstract TypeNode baseType();

  @Override
  public final String toString() {
    return baseType().toString() + "...";
  }

  @Override
  public VarArgsTypeNode copy(CopyState copyState) {
    VarArgsTypeNode copy = create(sourceLocation(), baseType().copy(copyState));
    copy.copyInternal(this);
    return copy;
  }
}
