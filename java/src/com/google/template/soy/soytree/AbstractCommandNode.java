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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.CommandNode;

/**
 * Abstract implementation of a CommandNode.
 *
 */
abstract class AbstractCommandNode extends AbstractSoyNode implements CommandNode {

  private static final String TAG_STRING = "{%s}";
  private static final String TAG_STRING_SELF_ENDING = "{%s /}";

  /** The name of the Soy command. */
  private final String commandName;

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param commandName The name of the Soy command.
   */
  public AbstractCommandNode(int id, SourceLocation sourceLocation, String commandName) {
    super(id, sourceLocation);
    this.commandName = checkNotNull(commandName);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected AbstractCommandNode(AbstractCommandNode orig, CopyState copyState) {
    super(orig, copyState);
    this.commandName = orig.commandName;
  }

  @Override
  public String getCommandName() {
    return commandName;
  }

  @Override
  public String getCommandText() {
    return "";
  }

  /**
   * @return A Soy tag string that could be the Soy tag for this node. Note that this may not
   *     necessarily be the actual original Soy tag, but a (sort of) canonical equivalent.
   */
  protected String getTagString() {
    return getTagString(false);
  }

  protected final String getTagString(boolean selfEnding) {
    String base = selfEnding ? TAG_STRING_SELF_ENDING : TAG_STRING;
    String commandName = getCommandName();
    String commandText = getCommandText();
    String tagText = String.format("%s %s", commandName, commandText);
    return String.format(base, tagText.trim());
  }

  @Override
  public String toSourceString() {
    return getTagString();
  }
}
