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
import static com.google.common.collect.Maps.toImmutableEnumMap;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.util.ULocale;
import java.util.Iterator;
import java.util.Objects;
import javax.annotation.Nullable;

/** Represents a plural statement within a message in a form optimized for rendering. */
@Immutable
public final class SoyMsgPluralPartForRendering extends SoyMsgRawParts {

  /** The plural variable name. */
  private final PlaceholderName pluralVarName;

  /** The offset. */
  private final int offset;

  private final ImmutableMap<Long, SoyMsgRawParts> explicitCases;
  private final ImmutableMap<SoyMsgPluralCaseSpec.Type, SoyMsgRawParts> nonExplicitCases;
  private final SoyMsgRawParts otherCases;

  public SoyMsgPluralPartForRendering(
      PlaceholderName pluralVarName,
      int offset,
      ImmutableList<SoyMsgRawParts.RawCase<SoyMsgPluralCaseSpec>> cases) {
    this.pluralVarName = pluralVarName;
    this.offset = offset;
    this.explicitCases =
        cases.stream()
            .filter(c -> c.spec().getType() == SoyMsgPluralCaseSpec.Type.EXPLICIT)
            .collect(
                toImmutableMap(c -> c.spec().getExplicitValue(), SoyMsgRawParts.RawCase::parts));
    ;
    this.nonExplicitCases =
        cases.stream()
            .filter(
                c ->
                    c.spec().getType() != SoyMsgPluralCaseSpec.Type.EXPLICIT
                        && c.spec().getType() != SoyMsgPluralCaseSpec.Type.OTHER)
            .collect(toImmutableEnumMap(c -> c.spec().getType(), SoyMsgRawParts.RawCase::parts));
    this.otherCases =
        cases.stream()
            .filter(c -> c.spec().getType() == SoyMsgPluralCaseSpec.Type.OTHER)
            .map(SoyMsgRawParts.RawCase::parts)
            .collect(toOptional())
            .orElse(SoyMsgRawParts.EMPTY);
  }

  SoyMsgPluralPartForRendering(SoyMsgPluralPart pluralPart) {
    this(
        PlaceholderName.create(pluralPart.getPluralVarName()),
        pluralPart.getOffset(),
        pluralPart.getCases().stream()
            .map(SoyMsgRawParts.RawCase::create)
            .collect(toImmutableList()));
  }

  @Override
  public ImmutableList<SoyMsgPart> toSoyMsgParts() {
    return ImmutableList.of(toPluralPart());
  }

  SoyMsgPluralPart toPluralPart() {
    ImmutableList.Builder<SoyMsgPart.Case<SoyMsgPluralCaseSpec>> cases = ImmutableList.builder();
    explicitCases.forEach(
        (k, v) ->
            cases.add(SoyMsgPart.Case.create(new SoyMsgPluralCaseSpec(k), v.toSoyMsgParts())));
    nonExplicitCases.forEach(
        (k, v) ->
            cases.add(SoyMsgPart.Case.create(SoyMsgPluralCaseSpec.forType(k), v.toSoyMsgParts())));
    if (otherCases != null) {
      cases.add(
          SoyMsgPart.Case.create(
              SoyMsgPluralCaseSpec.forType(SoyMsgPluralCaseSpec.Type.OTHER),
              otherCases.toSoyMsgParts()));
    }
    return new SoyMsgPluralPart(pluralVarName.name(), offset, cases.build());
  }

  /** Returns the plural variable name. */
  public PlaceholderName getPluralVarName() {
    return pluralVarName;
  }

  /** Returns the offset. */
  public int getOffset() {
    return offset;
  }

  public ImmutableList<SoyMsgRawParts.RawCase<SoyMsgPluralCaseSpec>> getCases() {
    var builder =
        ImmutableList.<SoyMsgRawParts.RawCase<SoyMsgPluralCaseSpec>>builderWithExpectedSize(
            1 + explicitCases.size() + nonExplicitCases.size());

    explicitCases.forEach(
        (k, v) -> builder.add(SoyMsgRawParts.RawCase.create(new SoyMsgPluralCaseSpec(k), v)));
    nonExplicitCases.forEach(
        (k, v) -> builder.add(SoyMsgRawParts.RawCase.create(SoyMsgPluralCaseSpec.forType(k), v)));
    return builder
        .add(
            SoyMsgRawParts.RawCase.create(
                SoyMsgPluralCaseSpec.forType(SoyMsgPluralCaseSpec.Type.OTHER), otherCases))
        .build();
  }

  /**
   * Returns the list of parts to implement the case.
   *
   * @param pluralValue The current plural value
   * @param locale The locale for interpreting non-specific plural parts. Allowed to be null if it
   *     is known that there are no non-specific plural parts (This is commonly the case for default
   *     messages, since soy only allows direct specification of explicit or 'other').
   */
  public SoyMsgRawParts lookupCase(double pluralValue, @Nullable ULocale locale) {
    long longValue = (long) pluralValue;
    if (pluralValue == longValue) {
      // Handle exact cases
      var cases = explicitCases.get(longValue);
      if (cases != null) {
        return cases;
      }
    }

    if (!nonExplicitCases.isEmpty()) {
      // Didn't match any numeric value.  Check which plural rule it matches.
      String pluralKeyword = PluralRules.forLocale(locale).select(pluralValue - offset);
      SoyMsgPluralCaseSpec.Type correctCaseType =
          SoyMsgPluralCaseSpec.forType(pluralKeyword).getType();
      var cases = nonExplicitCases.get(correctCaseType);
      if (cases != null) {
        return cases;
      }
    }

    // Fall back to the "other" case. This can happen either if there aren't any non-specific
    // cases, or there is not the non-specific case that we need.
    return otherCases;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SoyMsgPluralPartForRendering)) {
      return false;
    }
    SoyMsgPluralPartForRendering otherPlural = (SoyMsgPluralPartForRendering) other;
    return pluralVarName.equals(otherPlural.pluralVarName)
        && otherCases.equals(otherPlural.otherCases)
        && explicitCases.equals(otherPlural.explicitCases)
        && nonExplicitCases.equals(otherPlural.nonExplicitCases);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        SoyMsgPluralPartForRendering.class,
        pluralVarName,
        offset,
        explicitCases,
        nonExplicitCases,
        otherCases);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("PluralForRendering")
        .omitNullValues()
        .addValue(pluralVarName)
        .add("explicitCases", explicitCases)
        .add("nonExplicitCases", nonExplicitCases)
        .add("otherCases", otherCases)
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
