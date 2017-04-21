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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Node representing a call.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class CallNode extends AbstractParentCommandNode<CallParamNode>
    implements StandaloneNode,
        SplitLevelTopNode<CallParamNode>,
        StatementNode,
        ExprHolderNode,
        MsgPlaceholderInitialNode {

  /** Fallback base placeholder name. */
  private static final String FALLBACK_BASE_PLACEHOLDER_NAME = "XXX";

  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  protected static class CommandTextInfo {

    private final String commandText;
    private final boolean isPassingAllData;
    @Nullable private final ExprRootNode dataExpr;
    @Nullable private final String userSuppliedPlaceholderName;

    public CommandTextInfo(
        String commandText,
        boolean isPassingAllData,
        @Nullable ExprRootNode dataExpr,
        @Nullable String userSuppliedPlaceholderName) {
      this.commandText = commandText;
      this.isPassingAllData = isPassingAllData;
      this.dataExpr = dataExpr;
      this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
    }
  }

  /** True if this call is passing data="all". */
  private final boolean isPassingAllData;

  /** The data= expression, or null if the call does not pass data or passes data="all". */
  @Nullable private final ExprRootNode dataExpr;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;

  /**
   * Escaping directives names (including the vertical bar) to apply to the return value. With
   * strict autoescape, the result of each call site is escaped, which is potentially a no-op if the
   * template's return value is the correct SanitizedContent object.
   */
  private ImmutableList<String> escapingDirectiveNames = ImmutableList.of();

  /** True if this node is within a HTML context. */
  private boolean isPcData = false;

  // TODO(user): Remove.
  private final String commandText;

  /**
   * Protected constructor for use by subclasses.
   *
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param commandTextInfo All the info derived from the command text.
   * @param escapingDirectiveNames Call-site escaping directives used by strict autoescaping. This
   *     is inferred by the autoescaper and not part of the syntax, and thus is not in the
   *     CommandTextInfo.
   */
  protected CallNode(
      int id,
      SourceLocation sourceLocation,
      String commandName,
      CommandTextInfo commandTextInfo,
      ImmutableList<String> escapingDirectiveNames) {
    super(id, sourceLocation, commandName);
    this.isPassingAllData = commandTextInfo.isPassingAllData;
    this.dataExpr = commandTextInfo.dataExpr;
    this.userSuppliedPlaceholderName = commandTextInfo.userSuppliedPlaceholderName;
    this.escapingDirectiveNames = escapingDirectiveNames;
    this.commandText = commandTextInfo.commandText;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected CallNode(CallNode orig, CopyState copyState) {
    super(orig, copyState);
    this.isPassingAllData = orig.isPassingAllData;
    this.dataExpr = (orig.dataExpr != null) ? orig.dataExpr.copy(copyState) : null;
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
    this.escapingDirectiveNames = orig.escapingDirectiveNames;
    this.isPcData = orig.getIsPcData();
    this.commandText = orig.commandText;
  }

  public boolean isPassingData() {
    return isPassingAllData || dataExpr != null;
  }

  public boolean isPassingAllData() {
    return isPassingAllData;
  }

  @Nullable
  public ExprRootNode getDataExpr() {
    return dataExpr;
  }

  public boolean getIsPcData() {
    return isPcData;
  }

  public void setIsPcData(boolean isPcData) {
    this.isPcData = isPcData;
  }

  @Override
  public String getUserSuppliedPhName() {
    return userSuppliedPlaceholderName;
  }

  @Override
  public String genBasePhName() {
    if (userSuppliedPlaceholderName != null) {
      return BaseUtils.convertToUpperUnderscore(userSuppliedPlaceholderName);
    }
    return FALLBACK_BASE_PLACEHOLDER_NAME;
  }

  @SuppressWarnings("UnnecessaryBoxing") // for IntelliJ
  @Override
  public Object genSamenessKey() {
    // CallNodes are never considered the same placeholder. We return the node instance as the info
    // for determining sameness. Since nodes have identity semantics this will only compare equal
    // to itself.
    return this;
  }

  @Override
  public String getCommandText() {
    return commandText;
  }

  @Override
  public String getTagString() {
    return getTagString(numChildren() == 0); // tag is self-ending if it has no children
  }

  @Override
  public String toSourceString() {
    return (numChildren() == 0) ? getTagString() : super.toSourceString();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return (dataExpr != null) ? ImmutableList.of(dataExpr) : ImmutableList.<ExprRootNode>of();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  /**
   * Returns the subset of {@link TemplateParam params} of the {@code callee} that require runtime
   * type checking when this node is being rendered.
   */
  public Collection<TemplateParam> getParamsToRuntimeCheck(TemplateNode callee) {
    return callee.getParams();
  }

  /** Sets the inferred escaping directives. */
  public void setEscapingDirectiveNames(ImmutableList<String> escapingDirectiveNames) {
    this.escapingDirectiveNames = escapingDirectiveNames;
  }

  /**
   * Returns the escaping directives, applied from left to right.
   *
   * <p>It is an error to call this before the contextual rewriter has been run.
   */
  public ImmutableList<String> getEscapingDirectiveNames() {
    return escapingDirectiveNames;
  }

  /** Base Builder for CallNode and CallDelegateNode. */
  public abstract static class Builder {
    public abstract SourceLocation getSourceLocation();

    public abstract Builder commandText(String commandText);

    public abstract Builder userSuppliedPlaceholderName(String commandText);

    public abstract CallNode build(SoyParsingContext context);
  }
}
