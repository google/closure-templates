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
import com.google.common.collect.Lists;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionBound;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.defn.TemplateParam;

import java.util.Collection;
import java.util.List;
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
        @Nullable SyntaxVersionBound syntaxVersionBound) {
      super(commandText, isPassingData, dataExpr, userSuppliedPlaceholderName, syntaxVersionBound);

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
   * The list of params that need to be type checked when this node is run.  All the params that
   * could be statically verified will be checked up front (by the
   * {@code CheckCallingParamTypesVisitor}), this list contains the params that could not be
   * statically checked.
   *
   * <p>NOTE:This list will be a subset of the params of the callee, not a subset of the params
   * passed from this caller.
   */
  private ImmutableList<TemplateParam> paramsToRuntimeTypeCheck;

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
  private static CommandTextInfo parseCommandTextHelper(
      String cmdTextWithoutPhnameAttr, @Nullable String userSuppliedPlaceholderName) {

    String cmdText =
        cmdTextWithoutPhnameAttr +
        ((userSuppliedPlaceholderName != null) ?
            " phname=\"" + userSuppliedPlaceholderName + "\"" : "");

    String cmdTextForParsing = cmdTextWithoutPhnameAttr;

    SyntaxVersionBound syntaxVersionBound = null;
    List<String> srcCalleeNames = Lists.newArrayList();

    Matcher ncnMatcher = NONATTRIBUTE_CALLEE_NAME.matcher(cmdTextForParsing);
    if (ncnMatcher.find()) {
      srcCalleeNames.add(ncnMatcher.group());
      cmdTextForParsing = cmdTextForParsing.substring(ncnMatcher.end()).trim();
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(cmdTextForParsing);

    String nameAttr = attributes.get("name");
    if (nameAttr != null) {
      srcCalleeNames.add(nameAttr);
      // Explicit attribute 'name' is only allowed in syntax versions 2.1 and below.
      SyntaxVersionBound newSyntaxVersionBound = new SyntaxVersionBound(
          SyntaxVersion.V2_2,
          String.format(
              "Callee name should be written directly instead of within attribute 'name' (i.e." +
                  " use {call %s} instead of {call name=\"%s\"}.",
              nameAttr, nameAttr));
      syntaxVersionBound =
          SyntaxVersionBound.selectLower(syntaxVersionBound, newSyntaxVersionBound);
    }
    String functionAttr = attributes.get("function");
    if (functionAttr != null) {
      srcCalleeNames.add(functionAttr);
      SyntaxVersionBound newSyntaxVersionBound = new SyntaxVersionBound(
          SyntaxVersion.V2_0, "The 'function' attribute in a 'call' tag is a Soy V1 artifact.");
      syntaxVersionBound =
          SyntaxVersionBound.selectLower(syntaxVersionBound, newSyntaxVersionBound);
    }

    String srcCalleeName;
    if (srcCalleeNames.size() == 0) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid 'call' command missing callee name: {call " + cmdText + "}.");
    } else if (srcCalleeNames.size() == 1) {
      srcCalleeName = srcCalleeNames.get(0);
      if (! (BaseUtils.isIdentifierWithLeadingDot(srcCalleeName) ||
             BaseUtils.isDottedIdentifier(srcCalleeName))) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid callee name \"" + srcCalleeName + "\" for 'call' command.");
      }
    } else {
      throw SoySyntaxException.createWithoutMetaInfo(String.format(
          "Invalid 'call' command with callee name declared multiple times (%s, %s)",
          srcCalleeNames.get(0), srcCalleeNames.get(1)));
    }

    Pair<Boolean, ExprRootNode<?>> dataAttrInfo =
        parseDataAttributeHelper(attributes.get("data"), cmdText);

    return new CommandTextInfo(
        cmdText, srcCalleeName, dataAttrInfo.first, dataAttrInfo.second,
        userSuppliedPlaceholderName, syntaxVersionBound);
  }


  /**
   * @param id The id for this node.
   * @param calleeName The full name of the template to call (including namespace).
   * @param srcCalleeName The callee name string as it appears in the source code.
   * @param useAttrStyleForCalleeName Whether to use name="..." when building command text.
   * @param useV1FunctionAttrForCalleeName Whether to use function="..." when building command text.
   * @param isPassingData True if the call forwards the data from dataRefText to its target.
   * @param isPassingAllData True if the call forwards all data from the template that contains
   *     it to its target.
   * @param dataExpr The expression for the data to pass, or null if not applicable.
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   *     or not applicable.
   * @param syntaxVersionBound The lowest known upper bound (exclusive!) for the syntax version of
   *     this node.
   * @param escapingDirectiveNames Call-site escaping directives used by strict autoescaping.
   */
  public CallBasicNode(
      int id, String calleeName, String srcCalleeName, boolean useAttrStyleForCalleeName,
      boolean useV1FunctionAttrForCalleeName, boolean isPassingData, boolean isPassingAllData,
      @Nullable ExprRootNode<?> dataExpr, @Nullable String userSuppliedPlaceholderName,
      @Nullable SyntaxVersionBound syntaxVersionBound,
      ImmutableList<String> escapingDirectiveNames) {
    this(
        id,
        buildCommandTextInfoHelper(
            srcCalleeName, useAttrStyleForCalleeName, useV1FunctionAttrForCalleeName, isPassingData,
            isPassingAllData, dataExpr, userSuppliedPlaceholderName, syntaxVersionBound),
        escapingDirectiveNames);

    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(calleeName));
    this.calleeName = calleeName;
  }


  /**
   * Private helper for constructor
   * {@link #CallBasicNode(
   *     int, String, String, boolean, boolean, boolean, boolean, ExprRootNode, String,
   *     SyntaxVersionBound, ImmutableList)}.
   */
  private static CommandTextInfo buildCommandTextInfoHelper(
      String srcCalleeName, boolean useAttrStyleForCalleeName,
      boolean useV1FunctionAttrForCalleeName, boolean isPassingData, boolean isPassingAllData,
      @Nullable ExprRootNode<?> dataExpr, @Nullable String userSuppliedPlaceholderName,
      @Nullable SyntaxVersionBound syntaxVersionBound) {

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
    if (useV1FunctionAttrForCalleeName) {
      Preconditions.checkArgument(
          syntaxVersionBound != null && syntaxVersionBound.syntaxVersion == SyntaxVersion.V2_0);
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
        syntaxVersionBound);
  }


  /**
   * Private helper constructor used by both of the constructors
   * {@link #CallBasicNode(int, String, String)} and
   * {@link #CallBasicNode(
   *     int, String, String, boolean, boolean, boolean, boolean, ExprRootNode, String,
   *     SyntaxVersionBound, ImmutableList)}.
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
    this.paramsToRuntimeTypeCheck = orig.paramsToRuntimeTypeCheck;
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

  /**
   * Sets the names of the parameters that require runtime type checking against the callees formal
   * types.
   */
  public void setParamsToRuntimeCheck(Collection<TemplateParam> paramNames) {
    this.paramsToRuntimeTypeCheck = ImmutableList.copyOf(paramNames);
  }

  @Override public Collection<TemplateParam> getParamsToRuntimeCheck(TemplateNode callee) {
    return paramsToRuntimeTypeCheck == null ? callee.getParams() : paramsToRuntimeTypeCheck;
  }

  /** Returns the full name of the template being called, or null if not yet set. */
  public String getCalleeName() {
    return calleeName;
  }


  @Override public CallBasicNode clone() {
    return new CallBasicNode(this);
  }

}
