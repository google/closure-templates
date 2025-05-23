/*
 * Copyright 2013 Google Inc.
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

/** Soy integer type. */
public final class NumberType extends PrimitiveType {

  private static final NumberType INSTANCE = new NumberType();

  // Not constructible - use getInstance().
  private NumberType() {}

  @Override
  public Kind getKind() {
    return Kind.NUMBER;
  }

  @Override
  public String toString() {
    return "number";
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, AssignabilityPolicy policy) {
    Kind kind = srcType.getKind();
    return kind == Kind.NUMBER
        || kind == Kind.FLOAT
        || (policy.isNumericCoercionsAllowed() && (kind == Kind.INT || kind == Kind.PROTO_ENUM));
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setPrimitive(SoyTypeP.PrimitiveTypeP.NUMBER);
  }

  /** Return the single instance of this type. */
  public static NumberType getInstance() {
    return INSTANCE;
  }
}
