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

import com.google.common.collect.ImmutableList;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents a select statement within a message.
 *
 */
public final class SoyMsgSelectPart extends SoyMsgPart {

  /** The select variable name. */
  private final String selectVarName;

  /** The various cases for this select statement. The default statement has a null key. */
  private final ImmutableList<Case<String>> cases;

  /**
   * @param selectVarName The select variable name.
   * @param cases The list of cases for this select statement.
   */
  public SoyMsgSelectPart(String selectVarName, Iterable<Case<String>> cases) {
    this.selectVarName = selectVarName;
    this.cases = ImmutableList.copyOf(cases);
  }

  /** Returns the select variable name. */
  public String getSelectVarName() {
    return selectVarName;
  }

  /** Returns the cases. */
  public ImmutableList<Case<String>> getCases() {
    return cases;
  }

  @Nullable
  public ImmutableList<SoyMsgPart> lookupCase(String selectValue) {
    // TODO(lukes): consider indexing the case in some way to speed lookups
    ImmutableList<SoyMsgPart> caseParts = null;
    ImmutableList<SoyMsgPart> defaultParts = null;
    for (Case<String> case0 : getCases()) {
      if (case0.spec() == null) {
        defaultParts = case0.parts();
      } else if (case0.spec().equals(selectValue)) {
        caseParts = case0.parts();
        break;
      }
    }

    if (caseParts == null) {
      return defaultParts;
    }
    return caseParts;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SoyMsgSelectPart)) {
      return false;
    }
    SoyMsgSelectPart otherSelect = (SoyMsgSelectPart) other;
    return selectVarName.equals(otherSelect.selectVarName) && cases.equals(otherSelect.cases);
  }

  @Override
  public int hashCode() {
    return Objects.hash(SoyMsgSelectPart.class, selectVarName, cases);
  }
}
