/*
 * Copyright 2008 Google Inc.
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

import com.google.common.base.CharMatcher;
import com.google.template.soy.soytree.SoyNode.SoyCommandNode;


/**
 * Abstract implementation of a SoyCommandNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class AbstractSoyCommandNode extends AbstractSoyNode implements SoyCommandNode {


  /** The name of the Soy command. */
  private final String commandName;

  /** The command text, or empty string if none. */
  private final String commandText;


  /**
   * @param id The id for this node.
   * @param commandName The name of the Soy command.
   * @param commandText The command text, or empty string if none.
   */
  public AbstractSoyCommandNode(String id, String commandName, String commandText) {
    super(id);
    this.commandName = commandName;
    this.commandText = commandText.trim();
  }


  @Override public String getCommandName() {
    return commandName;
  }

  @Override public String getCommandText() {
    return commandText;
  }


  @Override public String getTagString() {
    return buildTagStringHelper(false);
  }


  protected String buildTagStringHelper(boolean isSelfEnding) {

    String maybeSelfEndingStr = isSelfEnding ? " /" : "";

    if (commandText.length() == 0) {
      return "{" + commandName + maybeSelfEndingStr + "}";

    } else if (CharMatcher.anyOf("{}").matchesNoneOf(commandText)) {
      return "{" + commandName + " " + commandText + maybeSelfEndingStr + "}";

    } else {
      char lastChar = commandText.charAt(commandText.length()-1);
      if (lastChar == '{' || lastChar == '}') {
        if (isSelfEnding) {
          return "{{" + commandName + " " + commandText + " /}}";
        } else {
          return "{{" + commandName + " " + commandText + " }}";
        }
      } else {
        return "{{" + commandName + " " + commandText + maybeSelfEndingStr + "}}";
      }
    }
  }


  @Override public String toSourceString() {
    return getTagString();
  }

}
