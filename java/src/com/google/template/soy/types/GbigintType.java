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

import com.google.template.soy.soytree.SoyTypeP;

/** Soy gbigint type. */
public final class GbigintType extends SoyType {
  private static final GbigintType INSTANCE = new GbigintType();

  /** Return the single instance of this type. */
  public static GbigintType getInstance() {
    return INSTANCE;
  }

  // Not constructible - use getInstance().
  private GbigintType() {}

  @Override
  public String toString() {
    return "gbigint";
  }

  @Override
  public Kind getKind() {
    return Kind.GBIGINT;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    Kind kind = srcType.getKind();
    return kind == Kind.GBIGINT;
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setPrimitive(SoyTypeP.PrimitiveTypeP.GBIGINT);
  }
}
