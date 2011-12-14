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
 * Represents a select statement within a message.
 *
 */
public class SoyMsgSelectPart extends SoyMsgPart {


  /** The select variable name. */
  private final String selectVarName;

  /** The various cases for this select statement. The default statement has a null key. */
  private final List<Pair<String, List<SoyMsgPart>>> cases;

  /**
   * @param selectVarName The select variable name.
   * @param cases The list of cases for this select statement.
   */
  public SoyMsgSelectPart(String selectVarName, List<Pair<String, List<SoyMsgPart>>> cases) {
    this.selectVarName = selectVarName;
    this.cases = cases;
  }


  /** Returns the select variable name. */
  public String getSelectVarName() {
    return selectVarName;
  }

  /** Returns the cases. */
  public List<Pair<String, List<SoyMsgPart>>> getCases() {
    return cases;
  }

}
