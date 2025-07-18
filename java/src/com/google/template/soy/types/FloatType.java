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

/**
 * Soy floating-point type.
 */
public final class FloatType extends PrimitiveType {

  private static final FloatType INSTANCE = new FloatType();

  // Not constructible - use getInstance().
  private FloatType() {}

  @Override
  public Kind getKind() {
    return Kind.FLOAT;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    Kind kind = srcType.getKind();
    return kind == Kind.FLOAT || kind == Kind.NUMBER;
  }

  @Override
  public String toString() {
    return "float";
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    builder.setPrimitive(SoyTypeP.PrimitiveTypeP.FLOAT);
  }

  /** Return the single instance of this type. */
  public static FloatType getInstance() {
    return INSTANCE;
  }
}
