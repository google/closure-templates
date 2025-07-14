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
import java.util.Iterator;
import java.util.Set;

/** A Pick<> type. */
@AutoValue
public abstract class PickType extends ComputedType {

  public static PickType create(SoyType type, SoyType keys) {
    return new AutoValue_PickType(type, keys);
  }

  public abstract SoyType getType();

  public abstract SoyType getKeys();

  @Override
  public final String toString() {
    return "Pick<" + getType() + ", " + getKeys() + ">";
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    builder.getPickBuilder().setType(getType().toProto()).setKeys(getKeys().toProto());
  }

  @Override
  @Memoized
  public SoyType getEffectiveType() {
    SoyType baseType = getType().getEffectiveType();
    if (!(baseType instanceof RecordType)) {
      return NeverType.getInstance();
    }

    Set<String> memberNames = new HashSet<>();
    Iterator<? extends SoyType> i = SoyTypes.flattenUnion(getKeys()).iterator();
    while (i.hasNext()) {
      SoyType member = i.next();
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
                .filter(m -> memberNames.contains(m.name()))
                .collect(ImmutableList.toImmutableList()));
  }
}
