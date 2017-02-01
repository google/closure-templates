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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.SyntaxVersionUpperBound;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Node representing a call to a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallBasicNode extends CallNode {

  public static final SoyErrorKind MISSING_CALLEE_NAME =
      SoyErrorKind.of("Invalid ''call'' command missing callee name: '{'call {0}'}'.");
  public static final SoyErrorKind BAD_CALLEE_NAME =
      SoyErrorKind.of("Invalid callee name \"{0}\" for ''call'' command.");

  /** Helper class used by constructors. Encapsulates all the info derived from the command text. */
  @Immutable
  private static final class CommandTextInfo extends CallNode.CommandTextInfo {

    /** The callee name string as it appears in the source code. */
    private final String srcCalleeName;

    CommandTextInfo(
        String commandText,
        String srcCalleeName,
        DataAttribute dataAttr,
        @Nullable String userSuppliedPlaceholderName,
        @Nullable SyntaxVersionUpperBound syntaxVersionBound) {
      super(commandText, dataAttr, userSuppliedPlaceholderName, syntaxVersionBound);
      this.srcCalleeName = srcCalleeName;
    }
  }

  /** Pattern for a callee name not listed as an attribute function="...". */
  private static final Pattern NONATTRIBUTE_CALLEE_NAME =
      Pattern.compile("^\\s* ([.\\w]+) (?= \\s | $)", Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser(
          "call", new Attribute("data", Attribute.ALLOW_ALL_VALUES, null));

  /** The callee name string as it appears in the source code. */
  private final String sourceCalleeName;

  /** The full name of the template being called. Briefly null before being set. */
  private String calleeName;

  /**
   * The list of params that need to be type checked when this node is run. All the params that
   * could be statically verified will be checked up front (by the {@code
   * CheckCallingParamTypesVisitor}), this list contains the params that could not be statically
   * checked.
   *
   * <p>NOTE:This list will be a subset of the params of the callee, not a subset of the params
   * passed from this caller.
   */
  private ImmutableList<TemplateParam> paramsToRuntimeTypeCheck;

  /**
   * Private constructor. {@link Builder} is the public API.
   *
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param commandTextInfo All the info derived from the command text.
   * @param escapingDirectiveNames Call-site escaping directives used by strict autoescaping.
   */
  private CallBasicNode(
      int id,
      SourceLocation sourceLocation,
      CommandTextInfo commandTextInfo,
      ImmutableList<String> escapingDirectiveNames,
      @Nullable String calleeName) {
    super(id, sourceLocation, "call", commandTextInfo, escapingDirectiveNames);
    this.sourceCalleeName = commandTextInfo.srcCalleeName;
    this.calleeName = calleeName;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private CallBasicNode(CallBasicNode orig, CopyState copyState) {
    super(orig, copyState);
    this.sourceCalleeName = orig.sourceCalleeName;
    this.calleeName = orig.calleeName;
    this.paramsToRuntimeTypeCheck = orig.paramsToRuntimeTypeCheck;
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_BASIC_NODE;
  }

  /** Returns the callee name string as it appears in the source code. */
  public String getSrcCalleeName() {
    return sourceCalleeName;
  }

  /**
   * Sets the full name of the template being called (must not be a partial name).
   *
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

  @Override
  public Collection<TemplateParam> getParamsToRuntimeCheck(TemplateNode callee) {
    return paramsToRuntimeTypeCheck == null ? callee.getParams() : paramsToRuntimeTypeCheck;
  }

  /** Returns the full name of the template being called, or null if not yet set. */
  public String getCalleeName() {
    return calleeName;
  }

  @Override
  public CallBasicNode copy(CopyState copyState) {
    return new CallBasicNode(this, copyState);
  }

  public static final class Builder extends CallNode.Builder {

    private static CallBasicNode error() {
      return new Builder(-1, SourceLocation.UNKNOWN)
          .commandText(".error")
          .build(SoyParsingContext.exploding()); // guaranteed to be valid
    }

    private final int id;
    private final SourceLocation sourceLocation;

    private ImmutableList<String> escapingDirectiveNames = ImmutableList.of();
    private DataAttribute dataAttr = DataAttribute.none();

    @Nullable private String commandText;
    @Nullable private String userSuppliedPlaceholderName;
    @Nullable private String calleeName;
    @Nullable private String sourceCalleeName;
    @Nullable private SyntaxVersionUpperBound syntaxVersionBound;

    public Builder(int id, SourceLocation sourceLocation) {
      this.id = id;
      this.sourceLocation = sourceLocation;
    }

    public Builder calleeName(String calleeName) {
      this.calleeName = calleeName;
      return this;
    }

    @Override
    public SourceLocation getSourceLocation() {
      return sourceLocation;
    }

    @Override
    public Builder commandText(String commandText) {
      this.commandText = commandText;
      return this;
    }

    public Builder escapingDirectiveNames(ImmutableList<String> escapingDirectiveNames) {
      this.escapingDirectiveNames = escapingDirectiveNames;
      return this;
    }

    public Builder dataAttribute(DataAttribute dataAttr) {
      this.dataAttr = dataAttr;
      return this;
    }

    public Builder sourceCalleeName(String sourceCalleeName) {
      this.sourceCalleeName = sourceCalleeName;
      return this;
    }

    public Builder syntaxVersionBound(SyntaxVersionUpperBound syntaxVersionBound) {
      this.syntaxVersionBound = syntaxVersionBound;
      return this;
    }

    @Override
    public Builder userSuppliedPlaceholderName(String userSuppliedPlaceholderName) {
      this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
      return this;
    }

    @Override
    public CallBasicNode build(SoyParsingContext context) {
      Checkpoint c = context.errorReporter().checkpoint();
      CommandTextInfo commandTextInfo =
          commandText != null ? parseCommandText(context) : buildCommandText();
      if (context.errorReporter().errorsSince(c)) {
        return error();
      }
      CallBasicNode callBasicNode =
          new CallBasicNode(
              id, sourceLocation, commandTextInfo, escapingDirectiveNames, calleeName);
      return callBasicNode;
    }

    // TODO(user): eliminate side-channel parsing. This should be a part of the grammar.
    private CommandTextInfo parseCommandText(SoyParsingContext context) {
      String cmdText =
          commandText
              + ((userSuppliedPlaceholderName != null)
                  ? " phname=\"" + userSuppliedPlaceholderName + "\""
                  : "");

      String cmdTextForParsing = commandText;

      SyntaxVersionUpperBound syntaxVersionBound = null;

      Matcher ncnMatcher = NONATTRIBUTE_CALLEE_NAME.matcher(cmdTextForParsing);
      if (ncnMatcher.find()) {
        sourceCalleeName = ncnMatcher.group(1);
        cmdTextForParsing = cmdTextForParsing.substring(ncnMatcher.end()).trim();
        if (!(BaseUtils.isIdentifierWithLeadingDot(sourceCalleeName)
            || BaseUtils.isDottedIdentifier(sourceCalleeName))) {
          context.report(sourceLocation, BAD_CALLEE_NAME, sourceCalleeName);
        }
      } else {
        context.report(sourceLocation, MISSING_CALLEE_NAME, commandText);
      }

      Map<String, String> attributes =
          ATTRIBUTES_PARSER.parse(cmdTextForParsing, context, sourceLocation);

      DataAttribute dataAttrInfo =
          parseDataAttributeHelper(attributes.get("data"), sourceLocation, context);

      return new CommandTextInfo(
          cmdText, sourceCalleeName, dataAttrInfo, userSuppliedPlaceholderName, syntaxVersionBound);
    }

    // TODO(user): eliminate side-channel parsing. This should be a part of the grammar.
    private CommandTextInfo buildCommandText() {
      String commandText = sourceCalleeName;
      if (dataAttr.isPassingAllData()) {
        commandText += " data=\"all\"";
      } else if (dataAttr.isPassingData()) {
        assert dataAttr.dataExpr() != null; // suppress warnings
        commandText += " data=\"" + dataAttr.dataExpr().toSourceString() + '"';
      }
      if (userSuppliedPlaceholderName != null) {
        commandText += " phname=\"" + userSuppliedPlaceholderName + '"';
      }

      return new CommandTextInfo(
          commandText, sourceCalleeName, dataAttr, userSuppliedPlaceholderName, syntaxVersionBound);
    }
  }
}
