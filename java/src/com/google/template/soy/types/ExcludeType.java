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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Sets;
import com.google.template.soy.soytree.SoyTypeP;

/** An Exclude<> type. */
@AutoValue
public abstract class ExcludeType extends ComputedType {

  public static ExcludeType create(SoyType union, SoyType excluded) {
    return new AutoValue_ExcludeType(union, excluded);
  }

  public abstract SoyType getType();

  public abstract SoyType getExcluded();

  @Override
  public final String toString() {
    return "Exclude<" + getType() + ", " + getExcluded() + ">";
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    builder
        .getExcludeBuilder()
        .setType(getType().toProto())
        .setExcludedTypes(getExcluded().toProto());
  }

  @Override
  @Memoized
  public SoyType getEffectiveType() {
    return UnionType.of(
        Sets.difference(
            SoyTypes.flattenUnionToSet(getType()), SoyTypes.flattenUnionToSet(getExcluded())));
  }
}
