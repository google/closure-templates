/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.msgs.restricted;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.util.Iterator;
import java.util.Objects;

/** Represents a select statement within a message. */
@Immutable
public final class SoyMsgSelectPartForRendering extends SoyMsgRawParts {

  /** The select variable name. */
  private final PlaceholderName selectVarName;

  private final SoyMsgRawParts defaultParts;
  private final ImmutableMap<String, SoyMsgRawParts> caseParts;

  public SoyMsgSelectPartForRendering(
      PlaceholderName name, ImmutableList<SoyMsgRawParts.RawCase<String>> cases) {
    this.selectVarName = name;
    this.defaultParts =
        cases.stream()
            .filter(c -> c.spec() == null)
            .map(SoyMsgRawParts.RawCase::parts)
            .collect(toOptional())
            .orElse(SoyMsgRawParts.EMPTY);
    this.caseParts =
        cases.stream()
            // Filter out cases that are the same as the default case, since we can just fall back
            // to it.
            .filter(c -> c.spec() != null && !c.parts().equals(defaultParts))
            .collect(toImmutableMap(SoyMsgRawParts.RawCase::spec, SoyMsgRawParts.RawCase::parts));
  }

  SoyMsgSelectPartForRendering(SoyMsgSelectPart selectPart) {
    this(
        PlaceholderName.create(selectPart.getSelectVarName()),
        selectPart.getCases().stream()
            .map(SoyMsgRawParts.RawCase::create)
            .collect(toImmutableList()));
  }

  /** Returns the select variable name. */
  public PlaceholderName getSelectVarName() {
    return selectVarName;
  }

  public ImmutableList<SoyMsgRawParts.RawCase<String>> getCases() {
    var builder =
        ImmutableList.<SoyMsgRawParts.RawCase<String>>builderWithExpectedSize(1 + caseParts.size())
            .add(SoyMsgRawParts.RawCase.create(null, defaultParts));
    caseParts.forEach((k, v) -> builder.add(SoyMsgRawParts.RawCase.create(k, v)));
    return builder.build();
  }

  public SoyMsgRawParts lookupCase(String selectValue) {
    return caseParts.getOrDefault(selectValue, defaultParts);
  }

  @Override
  public ImmutableList<SoyMsgPart> toSoyMsgParts() {
    return ImmutableList.of(toSelectPart());
  }

  SoyMsgSelectPart toSelectPart() {
    ImmutableList.Builder<SoyMsgPart.Case<String>> cases = ImmutableList.builder();
    caseParts.forEach((k, v) -> cases.add(SoyMsgPart.Case.create(k, v.toSoyMsgParts())));
    if (!defaultParts.equals(SoyMsgRawParts.EMPTY)) {
      cases.add(SoyMsgPart.Case.create(null, defaultParts.toSoyMsgParts()));
    }
    return new SoyMsgSelectPart(selectVarName.name(), cases.build());
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SoyMsgSelectPartForRendering)) {
      return false;
    }
    SoyMsgSelectPartForRendering otherSelect = (SoyMsgSelectPartForRendering) other;
    return selectVarName.equals(otherSelect.selectVarName)
        && defaultParts.equals(otherSelect.defaultParts)
        && caseParts.equals(otherSelect.caseParts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(SoyMsgSelectPartForRendering.class, selectVarName, defaultParts, caseParts);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("SelectForRendering")
        .addValue(selectVarName)
        .add("defaultParts", defaultParts)
        .add("caseParts", caseParts)
        .toString();
  }

  @Override
  public int numParts() {
    return 0;
  }

  @Override
  public Iterator<Object> iterator() {
    return ImmutableSet.of().iterator();
  }

  @Override
  public boolean isPlrselMsg() {
    return true;
  }

  @Override
  public Object getPart(int i) {
    throw new IllegalArgumentException();
  }
}
