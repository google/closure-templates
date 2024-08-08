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

import com.google.auto.value.AutoValue;
import com.google.template.soy.soytree.SoyTypeP;

/** An "indexed access type". Resolves to the type of a property of a record type. */
@AutoValue
public abstract class IndexedType extends SoyType {

  public static IndexedType create(SoyType type, String property) {
    return new AutoValue_IndexedType(type, property);
  }

  public abstract SoyType getType();

  public abstract String getProperty();

  @Override
  public Kind getKind() {
    return Kind.INDEXED;
  }

  @Override
  public final String toString() {
    return getType() + "[\"" + getProperty() + "\"]";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.getIndexedBuilder().setType(getType().toProto()).setProperty(getProperty());
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public SoyType getEffectiveType() {
    SoyType baseType = getType().getEffectiveType();
    if (baseType instanceof RecordType) {
      SoyType memberType = ((RecordType) baseType).getMemberType(getProperty());
      if (memberType != null) {
        return memberType;
      }
    }
    return NeverType.getInstance();
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy policy) {
    return getEffectiveType().isAssignableFromInternal(srcType, policy);
  }
}
