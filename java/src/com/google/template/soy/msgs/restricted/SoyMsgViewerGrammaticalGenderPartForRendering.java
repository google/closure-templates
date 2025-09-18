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

package com.google.template.soy.msgs.restricted;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.msgs.GrammaticalGender;
import java.util.Iterator;
import java.util.Objects;

/** Represents a gender statement within a message. */
@Immutable
public final class SoyMsgViewerGrammaticalGenderPartForRendering extends SoyMsgRawParts {

  private final ImmutableMap<GrammaticalGender, SoyMsgRawParts> partsByGender;
  private final SoyMsgRawParts defaultParts;

  public SoyMsgViewerGrammaticalGenderPartForRendering(
      ImmutableList<SoyMsgRawParts.RawCase<GrammaticalGender>> cases) {
    checkArgument(!cases.isEmpty(), "At least one case is required.");
    partsByGender =
        cases.stream()
            .filter(c -> c.spec() != null)
            .collect(
                toImmutableMap(
                    SoyMsgRawParts.RawCase::spec,
                    SoyMsgRawParts.RawCase::parts,
                    // Resolve collisions by picking the first one.
                    (l, r) -> l));
    if (partsByGender.containsKey(GrammaticalGender.OTHER)) {
      defaultParts = partsByGender.get(GrammaticalGender.OTHER);
    } else {
      // If there is no OTHER case, then we just use the first case as the default.
      // This is an error state - OTHER should always be present.
      defaultParts = cases.get(0).parts();
    }
  }

  SoyMsgViewerGrammaticalGenderPartForRendering(SoyMsgViewerGrammaticalGenderPart genderPart) {
    this(
        genderPart.getCases().stream()
            .map(SoyMsgRawParts.RawCase::create)
            .collect(toImmutableList()));
  }

  public ImmutableList<SoyMsgRawParts.RawCase<GrammaticalGender>> getCases() {
    var builder =
        ImmutableList.<SoyMsgRawParts.RawCase<GrammaticalGender>>builderWithExpectedSize(
            partsByGender.size());
    partsByGender.forEach((k, v) -> builder.add(SoyMsgRawParts.RawCase.create(k, v)));
    return builder.build();
  }

  public SoyMsgRawParts getSoyMsgRawPartsForGender(GrammaticalGender viewerGrammaticalGender) {
    return partsByGender.getOrDefault(viewerGrammaticalGender, defaultParts);
  }

  public ImmutableList<SoyMsgPart> getPartsForGender(GrammaticalGender viewerGrammaticalGender) {
    return getSoyMsgRawPartsForGender(viewerGrammaticalGender).toSoyMsgParts();
  }

  @Override
  public ImmutableList<SoyMsgPart> toSoyMsgParts() {
    return ImmutableList.of(toGenderPart());
  }

  SoyMsgViewerGrammaticalGenderPart toGenderPart() {
    ImmutableList.Builder<SoyMsgPart.Case<GrammaticalGender>> cases = ImmutableList.builder();
    partsByGender.forEach((k, v) -> cases.add(SoyMsgPart.Case.create(k, v.toSoyMsgParts())));
    return new SoyMsgViewerGrammaticalGenderPart(cases.build());
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SoyMsgViewerGrammaticalGenderPartForRendering)) {
      return false;
    }
    SoyMsgViewerGrammaticalGenderPartForRendering otherGender =
        (SoyMsgViewerGrammaticalGenderPartForRendering) other;
    return defaultParts.equals(otherGender.defaultParts)
        && partsByGender.equals(otherGender.partsByGender);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        SoyMsgViewerGrammaticalGenderPartForRendering.class, defaultParts, partsByGender);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("GenderForRendering")
        .add("defaultParts", defaultParts)
        .add("partsByGender", partsByGender)
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
