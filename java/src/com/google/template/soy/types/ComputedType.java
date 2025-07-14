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

abstract class ComputedType extends SoyType {

  @Override
  final boolean doIsAssignableFromNonUnionType(SoyType srcType, AssignabilityPolicy policy) {
    return getEffectiveType().isAssignableFromInternal(srcType, policy);
  }

  @Override
  public final boolean isOfKind(Kind kind) {
    return getEffectiveType().isOfKind(kind);
  }

  @Override
  public final boolean isEffectivelyEqual(SoyType type) {
    return this.getEffectiveType().isEffectivelyEqual(type);
  }

  public <T extends SoyType> T asType(Class<T> subType) {
    return subType.cast(getEffectiveType());
  }

  @Override
  public final Kind getKind() {
    return Kind.COMPUTED;
  }
}
