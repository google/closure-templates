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

/** A record type (eg, [a: someType, b: otherType]). */
@AutoValue
public abstract class RecordTypeNode extends TypeNode {

  public static RecordTypeNode create(
      SourceLocation sourceLocation, Iterable<Property> properties) {
    return new AutoValue_RecordTypeNode(sourceLocation, ImmutableList.copyOf(properties));
  }

  RecordTypeNode() {}

  /** A single property declaration in a record type. */
  @AutoValue
  public abstract static class Property {
    public static Property create(SourceLocation nameLocation, String name, TypeNode type) {
      return new AutoValue_RecordTypeNode_Property(nameLocation, name, type);
    }

    Property() {}

    public abstract SourceLocation nameLocation();

    public abstract String name();

    public abstract TypeNode type();

    @Override
    public final String toString() {
      return name() + ": " + type();
    }

    Property copy() {
      return create(nameLocation(), name(), type().copy());
    }
  }

  public abstract ImmutableList<Property> properties();

  @Override
  public final String toString() {
    if (properties().size() < 3) {
      return "[" + Joiner.on(", ").join(properties()) + "]";
    }
    return "[\n  " + Joiner.on(",\n  ").join(properties()) + "\n]";
  }

  @Override
  public <T> T accept(TypeNodeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public RecordTypeNode copy() {
    ImmutableList.Builder<Property> newProperties = ImmutableList.builder();
    for (Property property : properties()) {
      newProperties.add(property.copy());
    }
    RecordTypeNode copy = create(sourceLocation(), newProperties.build());
    copy.copyResolvedTypeFrom(this);
    return copy;
  }
}
