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

/** An Extract<> type. */
@AutoValue
public abstract class ExtractType extends ComputedType {

  public static ExtractType create(SoyType union, SoyType extracted) {
    return new AutoValue_ExtractType(union, extracted);
  }

  public abstract SoyType getType();

  public abstract SoyType getExtracted();

  @Override
  public final String toString() {
    return "Extract<" + getType() + ", " + getExtracted() + ">";
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    getEffectiveType().doToProto(builder);
  }

  @Override
  @Memoized
  public SoyType getEffectiveType() {
    // In TypeScript Extract<any, string> == any and Extract<unknown, string> == never.
    // And remember these names are inverted. Soy {?, any} is TypeScript {any, unknown}.
    if (getType().isOfKind(Kind.UNKNOWN)) {
      return UnknownType.getInstance();
    }
    return UnionType.of(
        Sets.intersection(
            SoyTypes.flattenUnionToSet(getType()), SoyTypes.flattenUnionToSet(getExtracted())));
  }
}
