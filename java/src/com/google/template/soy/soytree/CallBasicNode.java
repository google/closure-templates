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
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.ExprRootNode;
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
 * @author Kai Huang
 */
public class CallBasicNode extends CallNode {


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  protected static class CommandTextInfo extends CallNode.CommandTextInfo {

    /** The callee name string as it appears in the source code. */
    private final String srcCalleeName;

    public CommandTextInfo(
        String commandText, String srcCalleeName, boolean isPassingData,
        @Nullable ExprRootNode<?> dataExpr, @Nullable String userSuppliedPlaceholderName,
        SyntaxVersion syntaxVersion) {
      super(commandText, isPassingData, dataExpr, userSuppliedPlaceholderName, syntaxVersion);

      Preconditions.checkArgument(
          BaseUtils.isIdentifierWithLeadingDot(srcCalleeName) ||
              BaseUtils.isDottedIdentifier(srcCalleeName));
      this.srcCalleeName = srcCalleeName;
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


  /** The callee name string as it appears in the source code. */
  private final String srcCalleeName;

  /** The full name of the template being called. Briefly null before being set. */
  private String calleeName;


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
    this(id, parseCommandTextHelper(commandTextWithoutPhnameAttr, userSuppliedPlaceholderName),
        ImmutableList.<String>of());
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
      throw SoySyntaxException.createWithoutMetaInfo(
          "The 'call' command text must contain the callee name (encountered command text \"" +
              commandTextWithoutPhnameAttr + "\").");
    }
    if (functionAttribute != null) {
      nameAttribute = functionAttribute;
      syntaxVersion = SyntaxVersion.V1;
    }

    String srcCalleeName;
    if (BaseUtils.isIdentifierWithLeadingDot(nameAttribute) ||
        BaseUtils.isDottedIdentifier(nameAttribute)) {
      srcCalleeName = nameAttribute;
    } else {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid callee name \"" + nameAttribute + "\" for 'call' command.");
    }

    Pair<Boolean, ExprRootNode<?>> dataAttrInfo =
        parseDataAttributeHelper(attributes.get("data"), commandText);

    return new CommandTextInfo(
        commandText, srcCalleeName, dataAttrInfo.first, dataAttrInfo.second,
        userSuppliedPlaceholderName, syntaxVersion);
  }


  /**
   * @param id The id for this node.
   * @param calleeName The full name of the template to call (including namespace).
   * @param srcCalleeName The callee name string as it appears in the source code.
   * @param useAttrStyleForCalleeName Whether to use name="..." when building command text.
   * @param isPassingData True if the call forwards the data from dataRefText to its target.
   * @param isPassingAllData True if the call forwards all data from the template that contains
   *     it to its target.
   * @param dataExpr The expression for the data to pass, or null if not applicable.
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   *     or not applicable.
   * @param syntaxVersion The syntax version for the CallBasicNode.
   * @param escapingDirectiveNames Call-site escaping directives used by strict autoescaping.
   */
  public CallBasicNode(
      int id, String calleeName, String srcCalleeName, boolean useAttrStyleForCalleeName,
      boolean isPassingData, boolean isPassingAllData, @Nullable ExprRootNode<?> dataExpr,
      @Nullable String userSuppliedPlaceholderName, SyntaxVersion syntaxVersion,
      ImmutableList<String> escapingDirectiveNames) {
    this(
        id,
        buildCommandTextInfoHelper(
            srcCalleeName, useAttrStyleForCalleeName, isPassingData, isPassingAllData, dataExpr,
            userSuppliedPlaceholderName, syntaxVersion),
        escapingDirectiveNames);

    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(calleeName));
    this.calleeName = calleeName;
  }


  /**
   * Private helper for constructor
   * {@link #CallBasicNode(
   *     int, String, String, boolean, boolean, boolean, ExprRootNode, String, SyntaxVersion)}.
   */
  private static final CommandTextInfo buildCommandTextInfoHelper(
      String srcCalleeName, boolean useAttrStyleForCalleeName, boolean isPassingData,
      boolean isPassingAllData, @Nullable ExprRootNode<?> dataExpr,
      @Nullable String userSuppliedPlaceholderName, SyntaxVersion syntaxVersion) {

    Preconditions.checkArgument(
        BaseUtils.isIdentifierWithLeadingDot(srcCalleeName) ||
            BaseUtils.isDottedIdentifier(srcCalleeName));
    if (isPassingAllData) {
      Preconditions.checkArgument(isPassingData);
    }
    if (dataExpr != null) {
      Preconditions.checkArgument(isPassingData && ! isPassingAllData);
    }

    String commandText = "";
    if (syntaxVersion == SyntaxVersion.V1) {
      commandText += "function=\"" + srcCalleeName + '"';
    } else {
      if (useAttrStyleForCalleeName) {
        commandText += "name=\"" + srcCalleeName + '"';
      } else {
        commandText += srcCalleeName;
      }
    }
    if (isPassingAllData) {
      commandText += " data=\"all\"";
    } else if (isPassingData) {
      assert dataExpr != null;  // suppress warnings
      commandText += " data=\"" + dataExpr.toSourceString() + '"';
    }
    if (userSuppliedPlaceholderName != null) {
      commandText += " phname=\"" + userSuppliedPlaceholderName + '"';
    }

    return new CommandTextInfo(
        commandText, srcCalleeName, isPassingData, dataExpr, userSuppliedPlaceholderName,
        syntaxVersion);
  }


  /**
   * Private helper constructor used by both of the constructors
   * {@link #CallBasicNode(int, String, String)} and
   * {@link #CallBasicNode(
   *     int, String, String, boolean, boolean, boolean, ExprRootNode, String, SyntaxVersion)}.
   *
   * @param id The id for this node.
   * @param commandTextInfo All the info derived from the command text.
   * @param escapingDirectiveNames Call-site escaping directives used by strict autoescaping.
   */
  private CallBasicNode(int id, CommandTextInfo commandTextInfo,
      ImmutableList<String> escapingDirectiveNames) {
    super(id, "call", commandTextInfo, escapingDirectiveNames);
    this.srcCalleeName = commandTextInfo.srcCalleeName;
    this.calleeName = null;  // to be set later by SetFullCalleeNamesVisitor
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallBasicNode(CallBasicNode orig) {
    super(orig);
    this.srcCalleeName = orig.srcCalleeName;
    this.calleeName = orig.calleeName;
  }


  @Override public Kind getKind() {
    return Kind.CALL_BASIC_NODE;
  }


  /** Returns the callee name string as it appears in the source code. */
  public String getSrcCalleeName() {
    return srcCalleeName;
  }


  /**
   * Sets the full name of the template being called (must not be a partial name).
   * @param calleeName The full name of the template being called.
   */
  public void setCalleeName(String calleeName) {
    Preconditions.checkState(this.calleeName == null);
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(calleeName));
    this.calleeName = calleeName;
  }


  /** Returns the full name of the template being called, or null if not yet set. */
  public String getCalleeName() {
    return calleeName;
  }


  @Override public CallBasicNode clone() {
    return new CallBasicNode(this);
  }

}
