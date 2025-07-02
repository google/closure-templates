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
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.HashSet;
import java.util.Set;

/** An Omit<> type. */
@AutoValue
public abstract class OmitType extends ComputedType {

  public static OmitType create(SoyType type, SoyType keys) {
    return new AutoValue_OmitType(type, keys);
  }

  public abstract SoyType getType();

  public abstract SoyType getKeys();

  @Override
  public Kind getKind() {
    return Kind.OMIT;
  }

  @Override
  public final String toString() {
    return "Omit<" + getType() + ", " + getKeys() + ">";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.getOmitBuilder().setType(getType().toProto()).setKeys(getKeys().toProto());
  }

  @Override
  @Memoized
  public SoyType getEffectiveType() {
    SoyType baseType = getType().getEffectiveType();
    SoyType keysType = getKeys().getEffectiveType();
    if (!(baseType instanceof RecordType)) {
      return NeverType.getInstance();
    }

    Set<String> memberNames = new HashSet<>();
    for (SoyType member : SoyTypes.expandUnions(keysType)) {
      if (member instanceof LiteralType) {
        PrimitiveData literal = ((LiteralType) member).literal();
        if (literal instanceof StringData) {
          memberNames.add(literal.stringValue());
          continue;
        }
      }
      return NeverType.getInstance();
    }
    return RecordType.of(
        ((RecordType) baseType)
            .getMembers().stream()
                .filter(m -> !memberNames.contains(m.name()))
                .collect(ImmutableList.toImmutableList()));
  }
}
