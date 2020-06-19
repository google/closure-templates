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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.List;
import java.util.function.Predicate;
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
        HtmlContext.HtmlContextHolder,
        ExprHolderNode,
        MsgPlaceholderInitialNode,
        CommandTagAttributesHolder {

  /** Fallback base placeholder name. */
  private static final String FALLBACK_BASE_PLACEHOLDER_NAME = "XXX";

  /** True if this call is passing data="all". */
  private boolean isPassingAllData;

  /** Used for formatting */
  private final boolean selfClosing;

  /** Used for formatting */
  private final List<CommandTagAttribute> attributes;

  private final SourceLocation openTagLocation;

  /** The data= expression, or null if the call does not pass data or passes data="all". */
  @Nullable private ExprRootNode dataExpr;

  /** The key= expression, or null if the call does not pass key. */
  @Nullable private ExprRootNode keyExpr;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;

  /** The user-supplied placeholder example, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderExample;

  /** The HTML context that the call is in, such as in HTML or Attributes. */
  @Nullable private HtmlContext htmlContext;

  /**
   * The call key, which is the encompassing template name along with position in template. This is
   * used to help with dom alignment in Incremental DOM backend.
   */
  @Nullable private String callKey;

  /**
   * Escaping directives to apply to the return value. With strict autoescaping, the result of each
   * call site is escaped, which is potentially a no-op if the template's return value is the
   * correct SanitizedContent object.
   *
   * <p>Set by the contextual rewriter.
   */
  private ImmutableList<SoyPrintDirective> escapingDirectives = ImmutableList.of();

  /** True if this node is within a HTML context. */
  private boolean isPcData = false;

  /** Protected constructor for use by subclasses. */
  protected CallNode(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      String commandName,
      List<CommandTagAttribute> attributes,
      boolean selfClosing,
      ErrorReporter reporter) {
    super(id, location, commandName);

    String phname = null;
    String phex = null;

    for (CommandTagAttribute attr : attributes) {
      String name = attr.getName().identifier();

      switch (name) {
        case "data":
          ExprRootNode dataExpr = attr.valueAsExpr(reporter);
          if ((dataExpr.getRoot() instanceof GlobalNode)
              && ((GlobalNode) dataExpr.getRoot()).getName().equals("all")) {
            this.isPassingAllData = true;
            this.dataExpr = null;
          } else {
            this.isPassingAllData = false;
            this.dataExpr = dataExpr;
          }
          break;
        case "key":
          this.keyExpr = attr.valueAsExpr(reporter);
          break;
        case MessagePlaceholders.PHNAME_ATTR:
          phname =
              MessagePlaceholders.validatePlaceholderName(
                  attr.getValue(), attr.getValueLocation(), reporter);
          break;
        case MessagePlaceholders.PHEX_ATTR:
          phex =
              MessagePlaceholders.validatePlaceholderExample(
                  attr.getValue(), attr.getValueLocation(), reporter);
          break;
        default:
          // do nothing, validated by subclasses
      }
    }

    this.attributes = attributes;
    this.selfClosing = selfClosing;
    this.userSuppliedPlaceholderName = phname;
    this.userSuppliedPlaceholderExample = phex;
    this.openTagLocation = openTagLocation;
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
    this.keyExpr = (orig.keyExpr != null) ? orig.keyExpr.copy(copyState) : null;
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
    this.userSuppliedPlaceholderExample = orig.userSuppliedPlaceholderExample;
    this.escapingDirectives = orig.escapingDirectives;
    this.callKey = orig.callKey;
    this.isPcData = orig.getIsPcData();
    this.htmlContext = orig.htmlContext;
    this.openTagLocation = orig.openTagLocation;
    this.selfClosing = orig.selfClosing;
    this.attributes =
        orig.attributes.stream().map(c -> c.copy(copyState)).collect(toImmutableList());
    // we may have handed out a copy to ourselves via genSamenessKey()
    copyState.updateRefs(orig, this);
  }

  /**
   * Gets the HTML source context immediately prior to the node (typically tag, attribute value,
   * HTML PCDATA, or plain text) which this node emits in. This affects how the node is escaped (for
   * traditional backends) or how it's passed to incremental DOM APIs.
   */
  @Override
  public HtmlContext getHtmlContext() {
    return checkNotNull(
        htmlContext, "Cannot access HtmlContext before HtmlContextVisitor or InferenceEngine.");
  }

  public void setHtmlContext(HtmlContext value) {
    this.htmlContext = value;
  }

  public boolean isPassingData() {
    return isPassingAllData || dataExpr != null;
  }

  public void setTemplateCallKey(String key) {
    this.callKey = key;
  }

  public String getTemplateCallKey() {
    return callKey;
  }

  public boolean isPassingAllData() {
    return isPassingAllData;
  }

  public boolean isSelfClosing() {
    return this.selfClosing;
  }

  @Override
  public List<CommandTagAttribute> getAttributes() {
    return attributes;
  }

  @Nullable
  public ExprRootNode getDataExpr() {
    return dataExpr;
  }

  @Nullable
  public ExprRootNode getKeyExpr() {
    return keyExpr;
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

  @Nullable
  @Override
  public String getUserSuppliedPhExample() {
    return userSuppliedPlaceholderExample;
  }

  @Override
  public String genBasePhName() {
    if (userSuppliedPlaceholderName != null) {
      return BaseUtils.convertToUpperUnderscore(userSuppliedPlaceholderName);
    }
    return FALLBACK_BASE_PLACEHOLDER_NAME;
  }

  @Override
  public SamenessKey genSamenessKey() {
    // CallNodes are never considered the same placeholder. We return the node instance as the info
    // for determining sameness. Since nodes have identity semantics this will only compare equal
    // to itself.
    return new IdentitySamenessKey(this);
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
    if (dataExpr == null && keyExpr == null) {
      return ImmutableList.of();
    } else if (dataExpr != null && keyExpr != null) {
      return ImmutableList.of(dataExpr, keyExpr);
    }
    return (dataExpr != null) ? ImmutableList.of(dataExpr) : ImmutableList.of(keyExpr);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public SourceLocation getOpenTagLocation() {
    return openTagLocation;
  }

  /** Returns the location of the callee name in the source code. */
  public abstract SourceLocation getSourceCalleeLocation();

  /**
   * Returns the subset of {@link TemplateParam params} of the {@code callee} that require runtime
   * type checking when this node is being rendered.
   */
  public abstract Predicate<String> getParamsToRuntimeCheck(String calleeTemplateName);

  /**
   * Returns the escaping directives, applied from left to right.
   *
   * <p>It is an error to call this before the contextual rewriter has been run.
   */
  public ImmutableList<SoyPrintDirective> getEscapingDirectives() {
    return escapingDirectives;
  }

  /** Sets the inferred escaping directives. */
  public void setEscapingDirectives(ImmutableList<SoyPrintDirective> escapingDirectives) {
    this.escapingDirectives = escapingDirectives;
  }
}
