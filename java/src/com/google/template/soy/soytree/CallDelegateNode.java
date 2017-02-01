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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Node representing a call to a delegate template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallDelegateNode extends CallNode {

  private static final SoyErrorKind MISSING_CALLEE_NAME =
      SoyErrorKind.of(
          "The ''delcall'' command text must contain the callee name "
              + "(encountered command text \"{0}\").");
  public static final SoyErrorKind INVALID_DELEGATE_NAME =
      SoyErrorKind.of("Invalid delegate name \"{0}\" for ''delcall'' command.");
  private static final SoyErrorKind INVALID_VARIANT_EXPRESSION =
      SoyErrorKind.of(
          "Invalid variant expression \"{0}\" in ''delcall''"
              + " (variant expression must evaluate to an identifier).");

  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  private static class CommandTextInfo extends CallNode.CommandTextInfo {

    public final String delCalleeName;
    @Nullable public final ExprRootNode delCalleeVariantExpr;
    public final Boolean allowsEmptyDefault;

    public CommandTextInfo(
        String commandText,
        String delCalleeName,
        @Nullable ExprRootNode delCalleeVariantExpr,
        Boolean allowsEmptyDefault,
        DataAttribute dataAttr,
        @Nullable String userSuppliedPlaceholderName) {
      super(commandText, dataAttr, userSuppliedPlaceholderName, null);
      this.delCalleeName = delCalleeName;
      this.delCalleeVariantExpr = delCalleeVariantExpr;
      this.allowsEmptyDefault = allowsEmptyDefault;
    }
  }

  /** Pattern for a callee name. */
  private static final Pattern NONATTRIBUTE_CALLEE_NAME =
      Pattern.compile("^\\s* ([.\\w]+) (?= \\s | $)", Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser(
          "delcall",
          new Attribute("variant", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("data", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("allowemptydefault", Attribute.BOOLEAN_VALUES, null));

  /** The name of the delegate template being called. */
  private final String delCalleeName;

  /** The variant expression for the delegate being called, or null. */
  @Nullable private final ExprRootNode delCalleeVariantExpr;

  /**
   * User-specified value of whether this delegate call defaults to empty string if there's no
   * active implementation, or null if the attribute is not specified.
   */
  private Boolean allowsEmptyDefault;

  /**
   * The list of params that need to be type checked when this node is run on a per delegate basis.
   * All the params that could be statically verified will be checked up front (by the {@code
   * CheckCallingParamTypesVisitor}), this list contains the params that could not be statically
   * checked.
   *
   * <p>NOTE:This list will be a subset of the params of the callee, not a subset of the params
   * passed from this caller.
   */
  private ImmutableMap<TemplateDelegateNode, ImmutableList<TemplateParam>>
      paramsToRuntimeCheckByDelegate;

  public static final class Builder extends CallNode.Builder {

    private static CallDelegateNode error() {
      return new Builder(-1, SourceLocation.UNKNOWN)
          .commandText("error.error")
          .build(SoyParsingContext.exploding()); // guaranteed to be valid
    }

    private final int id;
    private final SourceLocation sourceLocation;

    private boolean allowEmptyDefault;
    private DataAttribute dataAttribute = DataAttribute.none();
    private ImmutableList<String> escapingDirectiveNames = ImmutableList.of();

    @Nullable private String commandText;
    @Nullable private String delCalleeName;
    @Nullable private ExprRootNode delCalleeVariantExpr;
    @Nullable private String userSuppliedPlaceholderName;

    public Builder(int id, SourceLocation sourceLocation) {
      this.id = id;
      this.sourceLocation = sourceLocation;
    }

    public Builder allowEmptyDefault(boolean allowEmptyDefault) {
      this.allowEmptyDefault = allowEmptyDefault;
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

    public Builder delCalleeName(String delCalleeName) {
      this.delCalleeName = delCalleeName;
      return this;
    }

    public Builder delCalleeVariantExpr(ExprRootNode delCalleeVariantExpr) {
      this.delCalleeVariantExpr = delCalleeVariantExpr;
      return this;
    }

    public Builder escapingDirectiveNames(ImmutableList<String> escapingDirectiveNames) {
      this.escapingDirectiveNames = escapingDirectiveNames;
      return this;
    }

    public Builder dataAttribute(DataAttribute dataAttribute) {
      this.dataAttribute = dataAttribute;
      return this;
    }

    @Override
    public Builder userSuppliedPlaceholderName(String userSuppliedPlaceholderName) {
      this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
      return this;
    }

    @Override
    public CallDelegateNode build(SoyParsingContext context) {
      Checkpoint checkpoint = context.errorReporter().checkpoint();
      CommandTextInfo commandTextInfo =
          commandText != null ? parseCommandText(context) : buildCommandText();
      if (context.errorReporter().errorsSince(checkpoint)) {
        return error();
      }
      CallDelegateNode callDelegateNode =
          new CallDelegateNode(id, sourceLocation, commandTextInfo, escapingDirectiveNames);
      return callDelegateNode;
    }

    private CommandTextInfo parseCommandText(SoyParsingContext context) {
      String commandTextWithoutPhnameAttr = this.commandText;

      String commandText =
          commandTextWithoutPhnameAttr
              + ((userSuppliedPlaceholderName != null)
                  ? " phname=\"" + userSuppliedPlaceholderName + "\""
                  : "");

      // Handle callee name not listed as an attribute.
      Matcher ncnMatcher = NONATTRIBUTE_CALLEE_NAME.matcher(commandTextWithoutPhnameAttr);
      String delCalleeName;
      if (ncnMatcher.find()) {
        delCalleeName = ncnMatcher.group(1);
        if (!BaseUtils.isDottedIdentifier(delCalleeName)) {
          context.report(sourceLocation, INVALID_DELEGATE_NAME, delCalleeName);
        }
        commandTextWithoutPhnameAttr =
            commandTextWithoutPhnameAttr.substring(ncnMatcher.end()).trim();
      } else {
        delCalleeName = null;
        context.report(sourceLocation, MISSING_CALLEE_NAME, commandText);
      }

      Map<String, String> attributes =
          ATTRIBUTES_PARSER.parse(commandTextWithoutPhnameAttr, context, sourceLocation);

      String variantExprText = attributes.get("variant");
      ExprRootNode delCalleeVariantExpr;
      if (variantExprText == null) {
        delCalleeVariantExpr = null;
      } else {
        ExprNode expr =
            new ExpressionParser(variantExprText, sourceLocation, context).parseExpression();
        // If the variant is a fixed string, do a sanity check.
        if (expr instanceof StringNode) {
          String fixedVariantStr = ((StringNode) expr).getValue();
          if (!BaseUtils.isIdentifier(fixedVariantStr)) {
            context.report(sourceLocation, INVALID_VARIANT_EXPRESSION, variantExprText);
          }
        }
        delCalleeVariantExpr = new ExprRootNode(expr);
      }

      DataAttribute dataAttrInfo =
          parseDataAttributeHelper(attributes.get("data"), sourceLocation, context);

      String allowemptydefaultAttr = attributes.get("allowemptydefault");
      Boolean allowsEmptyDefault =
          (allowemptydefaultAttr == null) ? null : allowemptydefaultAttr.equals("true");

      return new CommandTextInfo(
          commandText,
          delCalleeName,
          delCalleeVariantExpr,
          allowsEmptyDefault,
          dataAttrInfo,
          userSuppliedPlaceholderName);
    }

    private CommandTextInfo buildCommandText() {

      Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delCalleeName));
      String commandText = "";
      commandText += delCalleeName;
      if (dataAttribute.isPassingAllData()) {
        commandText += " data=\"all\"";
      } else if (dataAttribute.isPassingData()) {
        assert dataAttribute.dataExpr() != null; // suppress warnings
        commandText += " data=\"" + dataAttribute.dataExpr().toSourceString() + '"';
      }
      if (userSuppliedPlaceholderName != null) {
        commandText += " phname=\"" + userSuppliedPlaceholderName + '"';
      }

      return new CommandTextInfo(
          commandText,
          delCalleeName,
          delCalleeVariantExpr,
          allowEmptyDefault,
          dataAttribute,
          userSuppliedPlaceholderName);
    }
  }

  private CallDelegateNode(
      int id,
      SourceLocation sourceLocation,
      CommandTextInfo commandTextInfo,
      ImmutableList<String> escapingDirectiveNames) {
    super(id, sourceLocation, "delcall", commandTextInfo, escapingDirectiveNames);
    this.delCalleeName = commandTextInfo.delCalleeName;
    this.delCalleeVariantExpr = commandTextInfo.delCalleeVariantExpr;
    this.allowsEmptyDefault = commandTextInfo.allowsEmptyDefault;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  @SuppressWarnings("ConstantConditions") // for IntelliJ
  private CallDelegateNode(CallDelegateNode orig, CopyState copyState) {
    super(orig, copyState);
    this.delCalleeName = orig.delCalleeName;
    this.delCalleeVariantExpr =
        (orig.delCalleeVariantExpr != null) ? orig.delCalleeVariantExpr.copy(copyState) : null;
    this.allowsEmptyDefault = orig.allowsEmptyDefault;
    this.paramsToRuntimeCheckByDelegate = orig.paramsToRuntimeCheckByDelegate;
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_DELEGATE_NODE;
  }

  /** Returns the name of the delegate template being called. */
  public String getDelCalleeName() {
    return delCalleeName;
  }

  /** Returns the variant expression for the delegate being called, or null if it's a string. */
  @Nullable
  public ExprRootNode getDelCalleeVariantExpr() {
    return delCalleeVariantExpr;
  }

  /**
   * Sets the template params that require runtime type checking for each possible delegate target.
   */
  public void setParamsToRuntimeCheck(
      ImmutableMap<TemplateDelegateNode, ImmutableList<TemplateParam>> paramsToRuntimeCheck) {
    this.paramsToRuntimeCheckByDelegate = Preconditions.checkNotNull(paramsToRuntimeCheck);
  }

  @Override
  public Collection<TemplateParam> getParamsToRuntimeCheck(TemplateNode callee) {
    if (paramsToRuntimeCheckByDelegate == null) {
      return callee.getParams();
    }
    ImmutableList<TemplateParam> params = paramsToRuntimeCheckByDelegate.get(callee);
    if (params == null) {
      // The callee was not known when we performed static type checking.  Check all params.
      return callee.getParams();
    }
    return params;
  }

  /** Returns whether this delegate call defaults to empty string if there's no active impl. */
  public boolean allowsEmptyDefault() {
    // Default to 'false' if not specified.
    if (allowsEmptyDefault == null) {
      return false;
    }
    return allowsEmptyDefault;
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    List<ExprUnion> allExprUnions = Lists.newArrayListWithCapacity(2);
    if (delCalleeVariantExpr != null) {
      allExprUnions.add(new ExprUnion(delCalleeVariantExpr));
    }
    allExprUnions.addAll(super.getAllExprUnions());
    return Collections.unmodifiableList(allExprUnions);
  }

  @Override
  public CallDelegateNode copy(CopyState copyState) {
    return new CallDelegateNode(this, copyState);
  }
}
