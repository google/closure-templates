/*
 * Copyright 2020 Google Inc.
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

import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.types.SoyType.Kind;
import java.util.Objects;

/** Placeholder type for named templates before their signatures have been resolved. */
public final class NamedTemplateType extends SoyType {

  private final String name;

  public NamedTemplateType(String name) {
    this.name = name;
  }

  @Override
  public Kind getKind() {
    return Kind.NAMED_TEMPLATE;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    // Nothing is assignable to this placeholder type.
    return false;
  }

  @Override
  public String toString() {
    return name;
  }

  public String getTemplateName() {
    return name;
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    throw new UnsupportedOperationException(
        "NamedTemplateType should have been resolved before being written to proto.");
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && other.getClass() == this.getClass()
        && ((NamedTemplateType) other).name.equals(this.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getClass(), name);
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
