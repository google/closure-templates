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
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;

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
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class CallDelegateNode extends CallNode {


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  private static class CommandTextInfo extends CallNode.CommandTextInfo {

    public final String delCalleeName;
    @Nullable public final ExprRootNode<?> delCalleeVariantExpr;
    public final boolean allowsEmptyDefault;

    public CommandTextInfo(
        String commandText, String delCalleeName, @Nullable ExprRootNode<?> delCalleeVariantExpr,
        boolean allowsEmptyDefault, boolean isPassingData, @Nullable ExprRootNode<?> dataExpr,
        @Nullable String userSuppliedPlaceholderName) {
      super(commandText, isPassingData, dataExpr, userSuppliedPlaceholderName, SyntaxVersion.V2);

      Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delCalleeName));
      this.delCalleeName = delCalleeName;
      this.delCalleeVariantExpr = delCalleeVariantExpr;
      this.allowsEmptyDefault = allowsEmptyDefault;
    }
  }


  /** Pattern for a callee name not listed as an attribute name="...". */
  private static final Pattern NONATTRIBUTE_CALLEE_NAME =
      Pattern.compile("^ (?! name=\") [.\\w]+ (?= \\s | $)", Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("delcall",
          new Attribute("name", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("variant", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("data", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("allowemptydefault", Attribute.BOOLEAN_VALUES, "true"));


  /** The name of the delegate template being called. */
  private final String delCalleeName;

  /** The variant expression for the delegate being called, or null. */
  @Nullable private final ExprRootNode<?> delCalleeVariantExpr;

  /** Whether this delegate call defaults to empty string if there's no active implementation. */
  private final boolean allowsEmptyDefault;


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
    this(id, parseCommandTextHelper(commandTextWithoutPhnameAttr, userSuppliedPlaceholderName),
        ImmutableList.<String>of());
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
      throw SoySyntaxException.createWithoutMetaInfo(
          "The 'delcall' command text must contain the callee name (encountered command text \"" +
              commandTextWithoutPhnameAttr + "\").");
    }
    if (! BaseUtils.isDottedIdentifier(delCalleeName)) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid delegate name \"" + delCalleeName + "\" for 'delcall' command.");
    }

    String variantExprText = attributes.get("variant");
    ExprRootNode<?> delCalleeVariantExpr;
    if (variantExprText == null) {
      delCalleeVariantExpr = null;
    } else {
      delCalleeVariantExpr = ExprParseUtils.parseExprElseThrowSoySyntaxException(
          variantExprText,
          String.format("Invalid variant expression \"%s\" in 'delcall'.", variantExprText));
      // If the variant is a fixed string, do a sanity check.
      if (delCalleeVariantExpr.getChild(0) instanceof StringNode) {
        String fixedVariantStr = ((StringNode) delCalleeVariantExpr.getChild(0)).getValue();
        if (! BaseUtils.isIdentifier(fixedVariantStr)) {
          throw SoySyntaxException.createWithoutMetaInfo(
              "Invalid variant expression \"" + variantExprText + "\" in 'delcall'" +
                  " (variant expression must evaluate to an identifier).");
        }
      }
    }

    Pair<Boolean, ExprRootNode<?>> dataAttrInfo =
        parseDataAttributeHelper(attributes.get("data"), commandText);

    boolean allowsEmptyDefault = attributes.get("allowemptydefault").equals("true");

    return new CommandTextInfo(
        commandText, delCalleeName, delCalleeVariantExpr, allowsEmptyDefault, dataAttrInfo.first,
        dataAttrInfo.second, userSuppliedPlaceholderName);
  }


  /**
   * @param id The id for this node.
   * @param delCalleeName The name of the delegate template being called.
   * @param delCalleeVariantExpr The variant expression for the delegate being called, or null.
   * @param useAttrStyleForCalleeName Whether to use name="..." when building command text.
   * @param allowsEmptyDefault Whether this delegate call defaults to empty string if there's no
   *     active implementation.
   * @param isPassingData True if the call forwards the data from dataRefText to its target.
   * @param isPassingAllData True if the call forwards all data from the template that contains
   *     it to its target.
   * @param dataExpr The expression for the data to pass, or null if not applicable.
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   * @param escapingDirectiveNames Call-site escaping directives used by strict autoescaping.
   */
  public CallDelegateNode(
      int id, String delCalleeName, @Nullable ExprRootNode<?> delCalleeVariantExpr,
      boolean useAttrStyleForCalleeName, boolean allowsEmptyDefault, boolean isPassingData,
      boolean isPassingAllData, @Nullable ExprRootNode<?> dataExpr,
      @Nullable String userSuppliedPlaceholderName, ImmutableList<String> escapingDirectiveNames) {
    this(
        id,
        buildCommandTextInfoHelper(
            delCalleeName, delCalleeVariantExpr, useAttrStyleForCalleeName, allowsEmptyDefault,
            isPassingData, isPassingAllData, dataExpr, userSuppliedPlaceholderName),
        escapingDirectiveNames);
  }


  /**
   * Private helper for constructor
   * {@link #CallDelegateNode(
   *     int, String, ExprRootNode, boolean, boolean, boolean, boolean, ExprRootNode, String)}.
   */
  private static final CommandTextInfo buildCommandTextInfoHelper(
      String delCalleeName, @Nullable ExprRootNode<?> delCalleeVariantExpr,
      boolean useAttrStyleForCalleeName, boolean allowsEmptyDefault, boolean isPassingData,
      boolean isPassingAllData, @Nullable ExprRootNode<?> dataExpr,
      @Nullable String userSuppliedPlaceholderName) {

    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delCalleeName));
    if (isPassingAllData) {
      Preconditions.checkArgument(isPassingData);
    }
    if (dataExpr != null) {
      Preconditions.checkArgument(isPassingData && ! isPassingAllData);
    }

    String commandText = "";
    if (useAttrStyleForCalleeName) {
      commandText += "name=\"" + delCalleeName + '"';
    } else {
      commandText += delCalleeName;
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
        commandText, delCalleeName, delCalleeVariantExpr, allowsEmptyDefault, isPassingData,
        dataExpr, userSuppliedPlaceholderName);
  }


  /**
   * Private helper constructor used by both of the constructors
   * {@link #CallDelegateNode(int, String, String)} and
   * {@link #CallDelegateNode(
   *     int, String, ExprRootNode, boolean, boolean, boolean, boolean, ExprRootNode, String)}.
   * @param id The id for this node.
   * @param commandTextInfo All the info derived from the command text.
   * @param escapingDirectiveNames Call-site escaping directives used by strict autoescaping.
   */
  private CallDelegateNode(int id, CommandTextInfo commandTextInfo,
      ImmutableList<String> escapingDirectiveNames) {
    super(id, "delcall", commandTextInfo, escapingDirectiveNames);
    this.delCalleeName = commandTextInfo.delCalleeName;
    this.delCalleeVariantExpr = commandTextInfo.delCalleeVariantExpr;
    this.allowsEmptyDefault = commandTextInfo.allowsEmptyDefault;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallDelegateNode(CallDelegateNode orig) {
    super(orig);
    this.delCalleeName = orig.delCalleeName;
    this.delCalleeVariantExpr = orig.delCalleeVariantExpr;
    this.allowsEmptyDefault = orig.allowsEmptyDefault;
  }


  @Override public Kind getKind() {
    return Kind.CALL_DELEGATE_NODE;
  }


  /** Returns the name of the delegate template being called. */
  public String getDelCalleeName() {
    return delCalleeName;
  }


  /** Returns the variant expression for the delegate being called, or null if it's a string. */
  @Nullable public ExprRootNode<?> getDelCalleeVariantExpr() {
    return delCalleeVariantExpr;
  }


  /** Returns whether this delegate call defaults to empty string if there's no active impl. */
  public boolean allowsEmptyDefault() {
    return allowsEmptyDefault;
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    List<ExprUnion> allExprUnions = Lists.newArrayListWithCapacity(2);
    if (delCalleeVariantExpr != null) {
      allExprUnions.add(new ExprUnion(delCalleeVariantExpr));
    }
    allExprUnions.addAll(super.getAllExprUnions());
    return Collections.unmodifiableList(allExprUnions);
  }


  @Override public CallDelegateNode clone() {
    return new CallDelegateNode(this);
  }

}
