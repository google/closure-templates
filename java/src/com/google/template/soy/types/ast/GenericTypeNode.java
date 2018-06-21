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
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;

/** A typename with type arguments (eg, list<someType>). */
@AutoValue
public abstract class GenericTypeNode extends TypeNode {
  GenericTypeNode() {}

  public static GenericTypeNode create(
      SourceLocation sourceLocation, String name, Iterable<? extends TypeNode> arguments) {
    return new AutoValue_GenericTypeNode(sourceLocation, name, ImmutableList.copyOf(arguments));
  }

  public abstract String name();

  /** All the type parameters, possibly empty. */
  public abstract ImmutableList<TypeNode> arguments();

  @Override
  public String toString() {
    return name() + "<" + Joiner.on(", ").join(arguments()) + ">";
  }

  @Override
  public <T> T accept(TypeNodeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
