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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;


/**
 * Node representing a call.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class CallNode extends AbstractParentCommandNode<CallParamNode>
    implements StandaloneNode, SplitLevelTopNode<CallParamNode>, StatementNode, ExprHolderNode,
    MsgPlaceholderInitialNode {


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  protected static class CommandTextInfo {

    private final String commandText;
    private final boolean isPassingData;
    @Nullable private final ExprRootNode<?> dataExpr;
    @Nullable private final String userSuppliedPlaceholderName;
    protected final SyntaxVersion syntaxVersion;

    public CommandTextInfo(
        String commandText, boolean isPassingData, @Nullable ExprRootNode<?> dataExpr,
        @Nullable String userSuppliedPlaceholderName, SyntaxVersion syntaxVersion) {
      Preconditions.checkArgument(isPassingData || dataExpr == null);
      this.commandText = commandText;
      this.isPassingData = isPassingData;
      this.dataExpr = dataExpr;
      this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
      this.syntaxVersion = syntaxVersion;
    }
  }


  /** Fallback base placeholder name. */
  public static final String FALLBACK_BASE_PLACEHOLDER_NAME = "XXX";


  /** Whether we're passing any part of the data (i.e. has 'data' attribute). */
  private final boolean isPassingData;

  /** Whether we're passing all of the data (i.e. data="all"). */
  private final boolean isPassingAllData;

  /** The expression for the data to pass, or null if not applicable. */
  @Nullable private final ExprRootNode<?> dataExpr;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;

  /**
   * Escaping directives names (including the vertical bar) to apply to the return value. With
   * strict autoescape, the result of each call site is escaped, which is potentially a no-op if
   * the template's return value is the correct SanitizedContent object.
   */
  private ImmutableList<String> escapingDirectiveNames = ImmutableList.of();


  /**
   * Protected constructor for use by subclasses.
   *
   * @param id The id for this node.
   * @param commandTextInfo All the info derived from the command text.
   * @param escapingDirectiveNames Call-site escaping directives used by strict autoescaping.
   *     This is inferred by the autoescaper and not part of the syntax, and thus is not in the
   *     CommandTextInfo.
   */
  protected CallNode(int id, String commandName, CommandTextInfo commandTextInfo,
      ImmutableList<String> escapingDirectiveNames) {

    super(id, commandName, commandTextInfo.commandText);

    this.isPassingData = commandTextInfo.isPassingData;
    this.isPassingAllData = commandTextInfo.isPassingData && commandTextInfo.dataExpr == null;
    this.dataExpr = commandTextInfo.dataExpr;
    this.userSuppliedPlaceholderName = commandTextInfo.userSuppliedPlaceholderName;
    this.escapingDirectiveNames = escapingDirectiveNames;
    maybeSetSyntaxVersion(commandTextInfo.syntaxVersion);
  }


  /**
   * Private helper function for subclass constructors to parse the 'data' attribute.
   *
   * @param dataAttr The 'data' attribute in a call.
   * @param commandTextForErrorMsgs The call command text for use in error messages.
   * @return A pair (isPassingData, dataExpr) where dataExpr may be null.
   */
  protected static final Pair<Boolean, ExprRootNode<?>> parseDataAttributeHelper(
      String dataAttr, String commandTextForErrorMsgs) {

    boolean isPassingData;
    ExprRootNode<?> dataExpr;
    if (dataAttr == null) {
      isPassingData = false;
      dataExpr = null;
    } else if (dataAttr.equals("all")) {
      isPassingData = true;
      dataExpr = null;
    } else {
      isPassingData = true;
      dataExpr = ExprParseUtils.parseExprElseThrowSoySyntaxException(
          dataAttr,
          "Invalid expression in call command text \"" + commandTextForErrorMsgs + "\".");
    }

    return Pair.<Boolean, ExprRootNode<?>>of(isPassingData, dataExpr);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallNode(CallNode orig) {
    super(orig);
    this.isPassingData = orig.isPassingData;
    this.isPassingAllData = orig.isPassingAllData;
    this.dataExpr = (orig.dataExpr != null) ? orig.dataExpr.clone() : null;
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
    this.escapingDirectiveNames = orig.escapingDirectiveNames;
  }


  /** Returns whether we're passing any part of the data (i.e. has 'data' attribute). */
  public boolean isPassingData() {
    return isPassingData;
  }


  /** Returns whether we're passing all of the data (i.e. data="all"). */
  public boolean isPassingAllData() {
    return isPassingAllData;
  
  }


  /** Returns the expression for the data to pass, or null if not applicable. */
  @Nullable public ExprRootNode<?> getDataExpr() {
    return dataExpr;
  }


  @Override public String getUserSuppliedPlaceholderName() {
    return userSuppliedPlaceholderName;
  }


  @Override public String getTagString() {
    return buildTagStringHelper(numChildren() == 0);
  }


  @Override public String toSourceString() {
    return (numChildren() == 0) ? getTagString() : super.toSourceString();
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return (dataExpr != null) ?
        ImmutableList.of(new ExprUnion(dataExpr)) : Collections.<ExprUnion>emptyList();
  }


  @Override public String genBasePlaceholderName() {

    if (userSuppliedPlaceholderName != null) {
      return BaseUtils.convertToUpperUnderscore(userSuppliedPlaceholderName);
    }

    return FALLBACK_BASE_PLACEHOLDER_NAME;
  }


  @Override public Object genSamenessKey() {
    // CallNodes are never considered the same placeholder. We return the node id as the info for
    // determining sameness. The node id should be unique among all nodes in the tree.
    return Integer.valueOf(getId());
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  /**
   * Sets the inferred escaping directives.
   */
  public void setEscapingDirectiveNames(ImmutableList<String> escapingDirectiveNames) {
    this.escapingDirectiveNames = escapingDirectiveNames;
  }


  /**
   * Returns the escaping directives, applied from left to right.
   *
   * It is an error to call this before the contextual rewriter has been run.
   */
  public ImmutableList<String> getEscapingDirectiveNames() {
    return escapingDirectiveNames;
  }
}
