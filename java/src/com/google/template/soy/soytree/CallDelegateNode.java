/*
 * Copyright 2011 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;


/**
 * Node representing a call to a delegate template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author kai@google.com (Kai Huang)
 */
public class CallDelegateNode extends CallNode {


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  private static class CommandTextInfo extends CallNode.CommandTextInfo {

    public final String delCalleeName;

    public CommandTextInfo(
        String commandText, String delCalleeName, boolean isPassingData,
        @Nullable String exprText, @Nullable String userSuppliedPlaceholderName) {
      super(commandText, isPassingData, exprText, userSuppliedPlaceholderName, SyntaxVersion.V2);

      Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delCalleeName));
      this.delCalleeName = delCalleeName;
    }
  }


  /** Pattern for a callee name not listed as an attribute name="...". */
  private static final Pattern NONATTRIBUTE_CALLEE_NAME =
      Pattern.compile("^ (?! name=\") [.\\w]+ (?= \\s | $)", Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("call",
          new Attribute("name", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("data", Attribute.ALLOW_ALL_VALUES, null));


  /** The name of the delegate being called. */
  private final String delCalleeName;


  /**
   * @param id The id for this node.
   * @param commandTextWithoutPhnameAttr The command text with 'phname' attribute remove (if any).
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   *     or not applicable.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public CallDelegateNode(
      int id, String commandTextWithoutPhnameAttr, @Nullable String userSuppliedPlaceholderName)
      throws SoySyntaxException {
    this(id, parseCommandTextHelper(commandTextWithoutPhnameAttr, userSuppliedPlaceholderName));
  }


  /**
   * Private helper for constructor {@link #CallDelegateNode(int, String, String)}.
   */
  private static final CommandTextInfo parseCommandTextHelper(
      String commandTextWithoutPhnameAttr, @Nullable String userSuppliedPlaceholderName) {

    String commandText =
        commandTextWithoutPhnameAttr +
        ((userSuppliedPlaceholderName != null) ?
            " phname=\"" + userSuppliedPlaceholderName + "\"" : "");

    // Handle callee name not listed as an attribute.
    Matcher ncnMatcher = NONATTRIBUTE_CALLEE_NAME.matcher(commandTextWithoutPhnameAttr);
    if (ncnMatcher.find()) {
      commandTextWithoutPhnameAttr = ncnMatcher.replaceFirst("name=\"" + ncnMatcher.group() + "\"");
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandTextWithoutPhnameAttr);

    String delCalleeName = attributes.get("name");
    if (delCalleeName == null) {
      throw new SoySyntaxException("The 'delcall' command text must contain the callee name.");
    }
    if (! BaseUtils.isDottedIdentifier(delCalleeName)) {
      throw new SoySyntaxException(
          "Invalid delegate name \"" + delCalleeName + "\" for 'delcall' command.");
    }

    Pair<Boolean, String> dataAttrInfo = parseDataAttributeHelper(attributes.get("data"));

    return new CommandTextInfo(
        commandText, delCalleeName, dataAttrInfo.first, dataAttrInfo.second,
        userSuppliedPlaceholderName);
  }


  /**
   * @param id The id for this node.
   * @param delCalleeName The name of the delegate template to call.
   * @param useAttrStyleForCalleeName Whether to use name="..." when building command text.
   * @param isPassingData True if the call forwards the data from dataRefText to its target.
   * @param isPassingAllData True if the call forwards all data from the template that contains
*     it to its target.
   * @param exprText The expression for the data to pass, or null if not applicable.
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   *     or not applicable.
   */
  public CallDelegateNode(
      int id, String delCalleeName, boolean useAttrStyleForCalleeName, boolean isPassingData,
      boolean isPassingAllData, @Nullable String exprText,
      @Nullable String userSuppliedPlaceholderName) {
    this(
        id,
        buildCommandTextInfoHelper(
            delCalleeName, useAttrStyleForCalleeName, isPassingData, isPassingAllData, exprText,
            userSuppliedPlaceholderName));
  }


  /**
   * Private helper for constructor
   * {@link #CallDelegateNode(int, String,boolean,boolean,boolean, String, String)}.
   */
  private static final CommandTextInfo buildCommandTextInfoHelper(
      String delCalleeName, boolean useAttrStyleForCalleeName, boolean isPassingData,
      boolean isPassingAllData, @Nullable String exprText,
      @Nullable String userSuppliedPlaceholderName) {

    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delCalleeName));
    if (isPassingAllData) {
      Preconditions.checkArgument(isPassingData);
    }
    if (exprText != null) {
      Preconditions.checkArgument(isPassingData && ! isPassingAllData);
      Preconditions.checkArgument(! exprText.contains("\""));
    }

    StringBuilder commandText = new StringBuilder();
    if (useAttrStyleForCalleeName) {
      commandText.append("name=\"").append(delCalleeName).append('"');
    } else {
      commandText.append(delCalleeName);
    }
    if (isPassingAllData) {
      commandText.append(" data=\"all\"");
    } else if (isPassingData) {
      commandText.append(" data=\"").append(exprText).append('"');
    }

    if (userSuppliedPlaceholderName != null) {
      commandText.append(" phname=\"").append(userSuppliedPlaceholderName).append('"');
    }

    return new CommandTextInfo(
        commandText.toString(), delCalleeName, isPassingData, exprText,
        userSuppliedPlaceholderName);
  }


  /**
   * Private helper constructor used by both of the constructors
   * {@link #CallDelegateNode(int, String, String)} and
   * {@link #CallDelegateNode(int, String,boolean,boolean,boolean, String, String)}.
   * @param id The id for this node.
   * @param commandTextInfo All the info derived from the command text.
   */
  private CallDelegateNode(int id, CommandTextInfo commandTextInfo) {
    super(id, "delcall", commandTextInfo);
    this.delCalleeName = commandTextInfo.delCalleeName;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallDelegateNode(CallDelegateNode orig) {
    super(orig);
    this.delCalleeName = orig.delCalleeName;
  }


  @Override public Kind getKind() {
    return Kind.CALL_DELEGATE_NODE;
  }


  /** Returns the name of the delegate template being called. */
  public String getDelCalleeName() {
    return delCalleeName;
  }


  @Override public CallDelegateNode clone() {
    return new CallDelegateNode(this);
  }

}
