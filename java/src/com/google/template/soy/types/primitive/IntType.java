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

package com.google.template.soy.types.primitive;

import com.google.template.soy.types.SoyType;

/**
 * Soy integer type.
 *
 */
public final class IntType extends PrimitiveType {

  private static final IntType INSTANCE = new IntType();

  // Not constructible - use getInstance().
  private IntType() {}

  @Override
  public Kind getKind() {
    return Kind.INT;
  }

  @Override
  public boolean isAssignableFrom(SoyType srcType) {
    Kind kind = srcType.getKind();
    // enums are implicitly assignable to ints since that is the runtime representation in all
    // backends
    return kind == Kind.INT || kind == Kind.PROTO_ENUM;
  }

  @Override
  public String toString() {
    return "int";
  }

  /** Return the single instance of this type. */
  public static IntType getInstance() {
    return INSTANCE;
  }
}
