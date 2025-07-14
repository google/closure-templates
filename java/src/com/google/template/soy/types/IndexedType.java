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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.types.RecordType.Member;
import javax.annotation.Nullable;

/** An "indexed access type". Resolves to the type of a property of a record type. */
@AutoValue
public abstract class IndexedType extends ComputedType {

  public static IndexedType create(SoyType type, SoyType property) {
    return new AutoValue_IndexedType(type, property);
  }

  public static String jsSynthenticTypeDefName(String namedTypeName, String propertyName) {
    return namedTypeName + "__" + propertyName;
  }

  public abstract SoyType getType();

  public abstract SoyType getProperty();

  @Override
  public final String toString() {
    return getType() + "[" + getProperty() + "]";
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    builder.getIndexedBuilder().setType(getType().toProto()).setProperty(getProperty().toProto());
  }

  @Override
  @Memoized
  public SoyType getEffectiveType() {
    Member member = getMember();
    return member != null ? member.checkedType().getEffectiveType() : NeverType.getInstance();
  }

  /**
   * Returns whether the field represented by this type is optional in the record type where it is
   * defined.
   */
  public boolean isOriginallyOptional() {
    Member member = getMember();
    return member != null && member.optional();
  }

  @Nullable
  private Member getMember() {
    SoyType baseType = getType().getEffectiveType();
    if (baseType instanceof RecordType) {
      return ((RecordType) baseType).getMember(getPropertyName());
    }
    return null;
  }

  public String getPropertyName() {
    SoyType propType = getProperty().getEffectiveType();
    if (propType instanceof LiteralType) {
      PrimitiveData literal = ((LiteralType) propType).literal();
      if (literal instanceof StringData) {
        return literal.stringValue();
      }
    }
    return "-";
  }
}
