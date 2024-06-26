/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.types;

import com.google.common.base.Preconditions;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Objects;

/** Represents an iterable object that can be used in {for} and list comprehension. */
public final class IterableType extends AbstractIterableType {

  public static final IterableType EMPTY_ITERABLE = new IterableType(null);

  protected IterableType(SoyType elementType) {
    super(elementType);
  }

  public static IterableType of(SoyType elementType) {
    Preconditions.checkNotNull(elementType);
    return new IterableType(elementType);
  }

  @Override
  public Kind getKind() {
    return Kind.ITERABLE;
  }

  @Override
  public String toString() {
    return "iterable<" + elementType + ">";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setIterableElement(elementType.toProto());
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && this.getClass() == other.getClass()
        && Objects.equals(((IterableType) other).elementType, elementType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getClass(), elementType);
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
