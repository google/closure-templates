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

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.GrammaticalGender;
import java.util.Objects;

/**
 * Represents a message with different versions based on the viewer's grammatical gender. For
 * example, a welcome message in Spanish might have have a "Bienvenido" masculine version and a
 * "Bienvenida" feminine version. All of the versions are stored in a
 * SoyMsgViewerGrammaticalGenderPart.
 */
public final class SoyMsgViewerGrammaticalGenderPart extends SoyMsgPart {

  /** The various cases for this gender statement. */
  private final ImmutableList<Case<GrammaticalGender>> cases;

  /**
   * @param cases The list of cases for this gender statement.
   */
  public SoyMsgViewerGrammaticalGenderPart(Iterable<Case<GrammaticalGender>> cases) {
    this.cases = ImmutableList.copyOf(cases);
  }

  /** Returns the cases. */
  public ImmutableList<Case<GrammaticalGender>> getCases() {
    return cases;
  }

  public ImmutableList<SoyMsgPart> getPartsForGender(GrammaticalGender viewerGrammaticalGender) {
    ImmutableList<SoyMsgPart> other = null;
    for (Case<GrammaticalGender> selectCase : cases) {
      GrammaticalGender spec = selectCase.spec();
      if (spec == GrammaticalGender.OTHER) {
        other = selectCase.parts();
      }
      if (viewerGrammaticalGender.equals(spec)) {
        return selectCase.parts();
      }
    }
    // Fall back to the OTHER case if no match is found.
    if (other != null) {
      return other;
    }
    // Fall back the first case if there is no OTHER case.
    // This is an error state - OTHER should always be present.
    return cases.get(0).parts();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SoyMsgViewerGrammaticalGenderPart)) {
      return false;
    }
    SoyMsgViewerGrammaticalGenderPart otherGender = (SoyMsgViewerGrammaticalGenderPart) other;
    return cases.equals(otherGender.cases);
  }

  @Override
  public int hashCode() {
    return Objects.hash(SoyMsgViewerGrammaticalGenderPart.class, cases);
  }

  @Override
  public String toString() {
    return "Gender{\n"
        + "  cases: "
        + cases.stream().map(Case::toString).collect(joining(",\n    ", "[\n    ", "],"))
        + "\n}";
  }
}
