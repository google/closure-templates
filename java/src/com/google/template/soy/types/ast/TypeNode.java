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


import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SetOnce;
import com.google.template.soy.basetree.Copyable;
import com.google.template.soy.types.SoyType;
import java.util.stream.Stream;

/** The base class for an immutable node in the type AST. */
public abstract class TypeNode implements Copyable<TypeNode> {

  private SetOnce<SoyType> resolvedType = new SetOnce<>();

  TypeNode() {}

  public abstract SourceLocation sourceLocation();

  public void setResolvedType(SoyType type) {
    resolvedType.set(type);
  }

  public void setResolvedType(SoyType type, boolean reset) {
    if (reset) {
      resolvedType = new SetOnce<>();
    }
    resolvedType.set(type);
  }

  public SoyType getResolvedType() {
    return resolvedType.get();
  }

  public boolean isTypeResolved() {
    return resolvedType.isPresent();
  }

  /** Returns round-trippable (through the parser) source code for this node. */
  @Override
  public abstract String toString();

  void copyInternal(TypeNode old) {
    this.resolvedType = old.resolvedType.copy();
  }

  public Stream<TypeNode> asStreamExpandingUnion() {
    return Stream.of(this);
  }

  public boolean childNeedsGrouping(TypeNode child) {
    return false;
  }
}
