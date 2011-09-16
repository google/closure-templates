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
 * Node representing a call to a basic template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author kai@google.com (Kai Huang)
 */
public class CallBasicNode extends CallNode {


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  protected static class CommandTextInfo extends CallNode.CommandTextInfo {

    @Nullable private final String calleeName;
    @Nullable private final String partialCalleeName;

    public CommandTextInfo(
        String commandText, @Nullable String calleeName, @Nullable String partialCalleeName,
        boolean isPassingData, @Nullable String exprText,
        @Nullable String userSuppliedPlaceholderName, SyntaxVersion syntaxVersion) {
      super(commandText, isPassingData, exprText, userSuppliedPlaceholderName, syntaxVersion);

      Preconditions.checkArgument(calleeName == null || BaseUtils.isDottedIdentifier(calleeName));
      Preconditions.checkArgument(
          partialCalleeName == null || BaseUtils.isIdentifierWithLeadingDot(partialCalleeName));
      Preconditions.checkArgument(calleeName != null || partialCalleeName != null);
      this.calleeName = calleeName;
      this.partialCalleeName = partialCalleeName;
    }
  }


  /** Pattern for a callee name not listed as an attribute name="...". */
  private static final Pattern NONATTRIBUTE_CALLEE_NAME =
      Pattern.compile("^ (?! name=\" | function=\") [.\\w]+ (?= \\s | $)", Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("call",
          new Attribute("name", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("function", Attribute.ALLOW_ALL_VALUES, null),  // V1
          new Attribute("data", Attribute.ALLOW_ALL_VALUES, null));


  /** The full name of the template being called. (May be briefly null before being set.) */
  @Nullable private String calleeName;

  /** The partial name of the template being called, or null if the call is in V1 syntax. */
  @Nullable private final String partialCalleeName;


  /**
   * @param id The id for this node.
   * @param commandTextWithoutPhnameAttr The command text with 'phname' attribute remove (if any).
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   *     or not applicable.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public CallBasicNode(
      int id, String commandTextWithoutPhnameAttr, @Nullable String userSuppliedPlaceholderName)
      throws SoySyntaxException {
    this(id, parseCommandTextHelper(commandTextWithoutPhnameAttr, userSuppliedPlaceholderName));
  }


  /**
   * Private helper for constructor {@link #CallBasicNode(int, String, String)}.
   */
  private static final CommandTextInfo parseCommandTextHelper(
      String commandTextWithoutPhnameAttr, @Nullable String userSuppliedPlaceholderName) {

    String commandText =
        commandTextWithoutPhnameAttr +
        ((userSuppliedPlaceholderName != null) ?
            " phname=\"" + userSuppliedPlaceholderName + "\"" : "");

    SyntaxVersion syntaxVersion = SyntaxVersion.V2;

    // Handle callee name not listed as an attribute.
    Matcher ncnMatcher = NONATTRIBUTE_CALLEE_NAME.matcher(commandTextWithoutPhnameAttr);
    if (ncnMatcher.find()) {
      commandTextWithoutPhnameAttr = ncnMatcher.replaceFirst("name=\"" + ncnMatcher.group() + "\"");
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandTextWithoutPhnameAttr);

    String nameAttribute = attributes.get("name");
    String functionAttribute = attributes.get("function");
    if ((nameAttribute == null) == (functionAttribute == null)) {
      throw new SoySyntaxException("The 'call' command text must contain the callee name.");
    }
    if (functionAttribute != null) {
      nameAttribute = functionAttribute;
      syntaxVersion = SyntaxVersion.V1;
    }

    String calleeName;
    String partialCalleeName;
    if (BaseUtils.isIdentifierWithLeadingDot(nameAttribute)) {
      partialCalleeName = nameAttribute;
      calleeName = null;
    } else if (BaseUtils.isDottedIdentifier(nameAttribute)) {
      calleeName = nameAttribute;
      partialCalleeName = null;
    } else {
      throw new SoySyntaxException(
          "Invalid callee name \"" + nameAttribute + "\" for 'call' command.");
    }

    Pair<Boolean, String> dataAttrInfo = parseDataAttributeHelper(attributes.get("data"));

    return new CommandTextInfo(
        commandText, calleeName, partialCalleeName, dataAttrInfo.first, dataAttrInfo.second,
        userSuppliedPlaceholderName, syntaxVersion);
  }


  /**
   * @param id The id for this node.
   * @param calleeName The full name of the template to call (including namespace).
   * @param partialCalleeName The callee name without any namespace, or null for calls in
*     V1 syntax.
   * @param useAttrStyleForCalleeName Whether to use name="..." when building command text.
   * @param isPassingData True if the call forwards the data from dataRefText to its target.
   * @param isPassingAllData True if the call forwards all data from the template that contains
*     it to its target.
   * @param exprText The expression for the data to pass, or null if not applicable.
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   *     or not applicable.
   * @param syntaxVersion The syntax version for the CallBasicNode.
   */
  public CallBasicNode(
      int id, String calleeName, @Nullable String partialCalleeName,
      boolean useAttrStyleForCalleeName, boolean isPassingData, boolean isPassingAllData,
      @Nullable String exprText, @Nullable String userSuppliedPlaceholderName,
      SyntaxVersion syntaxVersion) {
    this(
        id,
        buildCommandTextInfoHelper(
            calleeName, partialCalleeName, useAttrStyleForCalleeName, isPassingData,
            isPassingAllData, exprText, userSuppliedPlaceholderName, syntaxVersion));
  }


  /**
   * Private helper for constructor
   * {@link #CallBasicNode(
   *     int, String, String, boolean, boolean, boolean, String, String, SyntaxVersion)}.
   */
  private static final CommandTextInfo buildCommandTextInfoHelper(
      String calleeName, @Nullable String partialCalleeName, boolean useAttrStyleForCalleeName,
      boolean isPassingData, boolean isPassingAllData, @Nullable String exprText,
      @Nullable String userSuppliedPlaceholderName, SyntaxVersion syntaxVersion) {

    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(calleeName));
    Preconditions.checkArgument(
        partialCalleeName == null || BaseUtils.isIdentifierWithLeadingDot(partialCalleeName));
    if (isPassingAllData) {
      Preconditions.checkArgument(isPassingData);
    }
    if (exprText != null) {
      Preconditions.checkArgument(isPassingData && ! isPassingAllData);
      Preconditions.checkArgument(! exprText.contains("\""));
    }

    StringBuilder commandText = new StringBuilder();
    if (syntaxVersion == SyntaxVersion.V1) {
      commandText.append("function=\"").append(calleeName).append('"');
    } else {
      String calleeNameInCommandText = (partialCalleeName != null) ? partialCalleeName : calleeName;
      if (useAttrStyleForCalleeName) {
        commandText.append("name=\"").append(calleeNameInCommandText).append('"');
      } else {
        commandText.append(calleeNameInCommandText);
      }
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
        commandText.toString(), calleeName, partialCalleeName, isPassingData, exprText,
        userSuppliedPlaceholderName, syntaxVersion);
  }


  /**
   * Private helper constructor used by both of the constructors
   * {@link #CallBasicNode(int, String, String)} and
   * {@link #CallBasicNode(
   *     int, String, String, boolean, boolean, boolean, String, String, SyntaxVersion)}.
   *
   * @param id The id for this node.
   * @param commandTextInfo All the info derived from the command text.
   */
  private CallBasicNode(int id, CommandTextInfo commandTextInfo) {
    super(id, "call", commandTextInfo);
    this.calleeName = commandTextInfo.calleeName;
    this.partialCalleeName = commandTextInfo.partialCalleeName;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallBasicNode(CallBasicNode orig) {
    super(orig);
    this.calleeName = orig.calleeName;
    this.partialCalleeName = orig.partialCalleeName;
  }


  @Override public Kind getKind() {
    return Kind.CALL_BASIC_NODE;
  }


  /**
   * Sets the full name of the template being called (must not be a partial name).
   * @param calleeName The full name of the template being called.
   */
  public void setCalleeName(String calleeName) {
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(calleeName));
    this.calleeName = calleeName;
  }


  /** Returns the full name of the template being called. */
  public String getCalleeName() {
    return calleeName;
  }


  /** Returns the partial name of the template being called, or null if call is in V1 syntax. */
  public String getPartialCalleeName() {
    return partialCalleeName;
  }


  @Override public CallBasicNode clone() {
    return new CallBasicNode(this);
  }

}
