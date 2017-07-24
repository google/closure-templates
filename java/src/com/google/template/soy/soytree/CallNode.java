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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.List;
import javax.annotation.Nullable;

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

  /** True if this call is passing data="all". */
  private boolean isPassingAllData;

  /** The data= expression, or null if the call does not pass data or passes data="all". */
  @Nullable private ExprRootNode dataExpr;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;

  /**
   * Escaping directives names (including the vertical bar) to apply to the return value. With
   * strict autoescape, the result of each call site is escaped, which is potentially a no-op if the
   * template's return value is the correct SanitizedContent object.
   *
   * <p>Set by the contextual rewriter.
   */
  private ImmutableList<String> escapingDirectiveNames = ImmutableList.of();

  /** True if this node is within a HTML context. */
  private boolean isPcData = false;

  /** Protected constructor for use by subclasses. */
  protected CallNode(
      int id,
      SourceLocation location,
      String commandName,
      List<CommandTagAttribute> attributes,
      ErrorReporter reporter) {
    super(id, location, commandName);

    String phname = null;

    for (CommandTagAttribute attr : attributes) {
      String name = attr.getName().identifier();

      switch (name) {
        case "data":
          ExprNode dataExpr = attr.valueAsExpr(reporter);
          if ((dataExpr instanceof GlobalNode) && ((GlobalNode) dataExpr).getName().equals("all")) {
            this.isPassingAllData = true;
            this.dataExpr = null;
          } else {
            this.isPassingAllData = false;
            this.dataExpr = new ExprRootNode(dataExpr);
          }
          break;
        case "phname":
          phname = attr.getValue();
          break;
        default:
          // do nothing
      }
    }

    this.userSuppliedPlaceholderName = phname;
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

  /** Sets this CallNode to pass data="all". */
  // TODO(user): Remove once ChangeCallsToPassAllDataVisitor is gone.
  public void setDataAll() {
    this.isPassingAllData = true;
    this.dataExpr = null;
  }

  public boolean getIsPcData() {
    return isPcData;
  }

  public void setIsPcData(boolean isPcData) {
    this.isPcData = isPcData;
  }

  @Nullable
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

  @Override
  public Object genSamenessKey() {
    // CallNodes are never considered the same placeholder. We return the node instance as the info
    // for determining sameness. Since nodes have identity semantics this will only compare equal
    // to itself.
    return this;
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
  public abstract ImmutableList<TemplateParam> getParamsToRuntimeCheck(TemplateNode callee);

  /**
   * Returns the escaping directives, applied from left to right.
   *
   * <p>It is an error to call this before the contextual rewriter has been run.
   */
  public ImmutableList<String> getEscapingDirectiveNames() {
    return escapingDirectiveNames;
  }

  /** Sets the inferred escaping directives. */
  public void setEscapingDirectiveNames(ImmutableList<String> escapingDirectiveNames) {
    this.escapingDirectiveNames = escapingDirectiveNames;
  }
}
