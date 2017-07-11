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
 * The "unknown" type is used to indicate that the type was unspecified or could not be inferred.
 * Variables with unknown type are considered to be dynamically-typed, and all operations are
 * allowed (but may fail at runtime).
 */
public final class UnknownType extends PrimitiveType {

  private static final UnknownType INSTANCE = new UnknownType();

  // Not constructible - use getInstance().
  private UnknownType() {}

  @Override
  public Kind getKind() {
    return Kind.UNKNOWN;
  }

  @Override
  public boolean isAssignableFrom(SoyType srcType) {
    return true;
  }

  @Override
  public String toString() {
    return "?";
  }

  /** Return the single instance of this type. */
  public static UnknownType getInstance() {
    return INSTANCE;
  }
}
