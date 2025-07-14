/*
 * Copyright 2025 Google Inc.
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
import com.google.common.base.Preconditions;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.soytree.SoyTypeP;

/** A type that matches a specific literal value, like "xyz". */
@AutoValue
public abstract class LiteralType extends SoyType {

  public static LiteralType create(PrimitiveData literal) {
    Preconditions.checkArgument(literal instanceof StringData);
    return new AutoValue_LiteralType(literal);
  }

  public abstract PrimitiveData literal();

  @Override
  public Kind getKind() {
    return Kind.LITERAL;
  }

  @Override
  public final String toString() {
    PrimitiveData literal = literal();
    if (literal instanceof StringData) {
      return "'" + literal.stringValue() + "'";
    }
    throw new AssertionError();
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    PrimitiveData literal = literal();
    if (literal instanceof StringData) {
      builder.getLiteralBuilder().setStringValue(((StringData) literal).getValue());
      return;
    }
    throw new AssertionError();
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, AssignabilityPolicy policy) {
    if (srcType instanceof LiteralType) {
      return literal().equals(((LiteralType) srcType).literal());
    }
    return false;
  }

  public PrimitiveType getPrimitiveType() {
    PrimitiveData literal = literal();
    if (literal instanceof StringData) {
      return StringType.getInstance();
    }
    throw new AssertionError();
  }
}
