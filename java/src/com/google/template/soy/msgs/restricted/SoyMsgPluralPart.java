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

import com.google.template.soy.internal.base.Pair;

import java.util.List;

/**
 * Represents a plural statement within a message.
 *
 * @author Mohamed Eldawy
 */
public class SoyMsgPluralPart extends SoyMsgPart {


  /** The plural variable name. */
  private final String pluralVarName;

  /** The offset. */
  private final int offset;

  /** The various cases for this plural statement. The default statement has a null key. */
  private final List<Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>>> cases;


  /**
   * @param pluralVarName The plural variable name.
   * @param offset The offset for this plural statement.
   * @param cases The list of cases for this plural statement.
   */
  public SoyMsgPluralPart(String pluralVarName, int offset,
      List<Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>>> cases) {

    this.pluralVarName = pluralVarName;
    this.offset = offset;
    this.cases = cases;
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
  public List<Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>>> getCases() {
    return cases;
  }

}
