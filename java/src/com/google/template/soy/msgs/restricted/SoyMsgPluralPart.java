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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.util.ULocale;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents a plural statement within a message.
 *
 */
public final class SoyMsgPluralPart extends SoyMsgPart {

  /** The plural variable name. */
  private final String pluralVarName;

  /** The offset. */
  private final int offset;

  /** The various cases for this plural statement. The default statement has a null key. */
  private final ImmutableList<Case<SoyMsgPluralCaseSpec>> cases;

  /**
   * @param pluralVarName The plural variable name.
   * @param offset The offset for this plural statement.
   * @param cases The list of cases for this plural statement.
   */
  public SoyMsgPluralPart(
      String pluralVarName, int offset, Iterable<Case<SoyMsgPluralCaseSpec>> cases) {

    this.pluralVarName = pluralVarName;
    this.offset = offset;
    this.cases = ImmutableList.copyOf(cases);
  }

  /** Returns the plural variable name. */
  public String getPluralVarName() {
    return pluralVarName;
  }

  /** Returns the offset. */
  public int getOffset() {
    return offset;
  }

  /** Returns the cases. */
  public ImmutableList<Case<SoyMsgPluralCaseSpec>> getCases() {
    return cases;
  }

  /**
   * Returns the list of parts to implement the case.
   *
   * @param pluralValue The current plural value
   * @param locale The locale for interpreting non-specific plural parts. Allowed to be null if it
   *     is known that there are no non-specific plural parts (This is commonly the case for default
   *     messages, since soy only allows direct specification of explicit or 'other').
   */
  public ImmutableList<SoyMsgPart> lookupCase(int pluralValue, @Nullable ULocale locale) {
    // TODO(lukes): clean up this method, the control flow is overly complex.  It could be cleaned
    // up by separating cases into 3 different lists.

    // Handle cases.
    ImmutableList<SoyMsgPart> caseParts = null;

    // Check whether the plural value matches any explicit numeric value.
    boolean hasNonExplicitCases = false;
    ImmutableList<SoyMsgPart> otherCaseParts = null;
    for (Case<SoyMsgPluralCaseSpec> case0 : getCases()) {

      SoyMsgPluralCaseSpec pluralCaseSpec = case0.spec();
      SoyMsgPluralCaseSpec.Type caseType = pluralCaseSpec.getType();
      if (caseType == SoyMsgPluralCaseSpec.Type.EXPLICIT) {
        if (pluralCaseSpec.getExplicitValue() == pluralValue) {
          caseParts = case0.parts();
          break;
        }

      } else if (caseType == SoyMsgPluralCaseSpec.Type.OTHER) {
        otherCaseParts = case0.parts();

      } else {
        hasNonExplicitCases = true;
      }
    }

    if (caseParts == null && hasNonExplicitCases) {
      // Didn't match any numeric value.  Check which plural rule it matches.
      String pluralKeyword = PluralRules.forLocale(locale).select(pluralValue - offset);
      SoyMsgPluralCaseSpec.Type correctCaseType = new SoyMsgPluralCaseSpec(pluralKeyword).getType();

      // Iterate the cases once again for non-numeric keywords.
      for (Case<SoyMsgPluralCaseSpec> case0 : getCases()) {

        if (case0.spec().getType() == correctCaseType) {
          caseParts = case0.parts();
          break;
        }
      }
    }

    if (caseParts == null) {
      // Fall back to the "other" case. This can happen either if there aren't any non-specific
      // cases, or there is not the non-specific case that we need.
      caseParts = otherCaseParts;
    }
    return checkNotNull(caseParts);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SoyMsgPluralPart)) {
      return false;
    }
    SoyMsgPluralPart otherPlural = (SoyMsgPluralPart) other;
    return offset == otherPlural.offset
        && pluralVarName.equals(otherPlural.pluralVarName)
        && cases.equals(otherPlural.cases);
  }

  @Override
  public int hashCode() {
    return Objects.hash(SoyMsgPluralPart.class, offset, pluralVarName, cases);
  }
}
