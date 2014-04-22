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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SoySyntaxException;


/**
 * Abstract node representing a 'case' or 'default' block in 'select', 'switch' or 'plural'
 * statements.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class CaseOrDefaultNode extends AbstractBlockCommandNode {


  /**
   * @param id The id for this node.
   * @param commandName The name of the Soy command.
   * @param commandText The command text, or empty string if none.
   */
  public CaseOrDefaultNode(int id, String commandName, String commandText)
      throws SoySyntaxException {
    super(id, commandName, commandText);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CaseOrDefaultNode(CaseOrDefaultNode orig) {
    super(orig);
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }

}
