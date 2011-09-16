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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialContentNode;
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
    MsgPlaceholderInitialContentNode {


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  protected static class CommandTextInfo {

    private final String commandText;
    private final boolean isPassingData;
    @Nullable private final String exprText;
    @Nullable private final String userSuppliedPlaceholderName;
    private final SyntaxVersion syntaxVersion;

    public CommandTextInfo(
        String commandText, boolean isPassingData, @Nullable String exprText,
        @Nullable String userSuppliedPlaceholderName, SyntaxVersion syntaxVersion) {
      Preconditions.checkArgument(isPassingData || exprText == null);
      this.commandText = commandText;
      this.isPassingData = isPassingData;
      this.exprText = exprText;
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
  @Nullable private final ExprRootNode<?> expr;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;


  /**
   * Protected constructor for use by subclasses.
   *
   * @param id The id for this node.
   * @param commandTextInfo All the info derived from the command text.
   */
  protected CallNode(int id, String commandName, CommandTextInfo commandTextInfo) {

    super(id, commandName, commandTextInfo.commandText);

    this.isPassingData = commandTextInfo.isPassingData;
    this.isPassingAllData = commandTextInfo.isPassingData && commandTextInfo.exprText == null;
    maybeSetSyntaxVersion(commandTextInfo.syntaxVersion);

    if (commandTextInfo.exprText != null) {
      try {
        this.expr = (new ExpressionParser(commandTextInfo.exprText)).parseExpression();
      } catch (TokenMgrError tme) {
        throw createExceptionForInvalidExpr(commandTextInfo.commandText, tme);
      } catch (ParseException pe) {
        throw createExceptionForInvalidExpr(commandTextInfo.commandText, pe);
      }
    } else {
      this.expr = null;
    }

    this.userSuppliedPlaceholderName = commandTextInfo.userSuppliedPlaceholderName;
  }


  /**
   * Private helper for constructor {@link #CallNode(int, String, CommandTextInfo)}.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidExpr(String commandText, Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid expression in 'call' command text \"" + commandText + "\".", cause);
  }


  /**
   * Private helper function for subclass constructors to parse the 'data' attribute.
   * @param dataAttr The 'data' attribute in a call.
   * @return A pair (isPassingData, exprText) where exprText may be null.
   */
  protected static final Pair<Boolean, String> parseDataAttributeHelper(String dataAttr) {

    boolean isPassingData;
    String exprText;
    if (dataAttr == null) {
      isPassingData = false;
      exprText = null;
    } else if (dataAttr.equals("all")) {
      isPassingData = true;
      exprText = null;
    } else {
      isPassingData = true;
      exprText = dataAttr;
    }
    return Pair.of(isPassingData, exprText);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallNode(CallNode orig) {
    super(orig);
    this.isPassingData = orig.isPassingData;
    this.isPassingAllData = orig.isPassingAllData;
    this.expr = (orig.expr != null) ? orig.expr.clone() : null;
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
  }


  /** Returns whether we're passing any part of the data (i.e. has 'data' attribute). */
  public boolean isPassingData() {
    return isPassingData;
  }


  /** Returns whether we're passing all of the data (i.e. data="all"). */
  public boolean isPassingAllData() {
    return isPassingAllData;
  
  }


  /** Returns the expression text for the data to pass, or null if not applicable. */
  public String getExprText() {
    return (expr != null) ? expr.toSourceString() : null;
  }


  /** Returns the expression for the data to pass, or null if not applicable. */
  public ExprRootNode<?> getExpr() {
    return expr;
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
    return (expr != null) ?
        ImmutableList.of(new ExprUnion(expr)) : Collections.<ExprUnion>emptyList();
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

}
