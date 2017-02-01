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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.SyntaxVersionUpperBound;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  protected static class CommandTextInfo {

    private final String commandText;
    private final DataAttribute dataAttribute;
    @Nullable private final String userSuppliedPlaceholderName;
    @Nullable protected final SyntaxVersionUpperBound syntaxVersionBound;

    public CommandTextInfo(
        String commandText,
        DataAttribute dataAttribute,
        @Nullable String userSuppliedPlaceholderName,
        @Nullable SyntaxVersionUpperBound syntaxVersionBound) {
      this.commandText = commandText;
      this.dataAttribute = dataAttribute;
      this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
      this.syntaxVersionBound = syntaxVersionBound;
    }
  }

  /** Fallback base placeholder name. */
  public static final String FALLBACK_BASE_PLACEHOLDER_NAME = "XXX";

  /** Parsed metadata from the 'data' attribute. */
  private final DataAttribute dataAttr;

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
    super(id, sourceLocation, commandName, commandTextInfo.commandText);
    this.dataAttr = commandTextInfo.dataAttribute;
    this.userSuppliedPlaceholderName = commandTextInfo.userSuppliedPlaceholderName;
    this.escapingDirectiveNames = escapingDirectiveNames;
    maybeSetSyntaxVersionUpperBound(commandTextInfo.syntaxVersionBound);
  }

  /** A Parsed {@code data} attribute. */
  @AutoValue
  public abstract static class DataAttribute {
    public static DataAttribute none() {
      return new AutoValue_CallNode_DataAttribute(false, null);
    }

    public static DataAttribute all() {
      return new AutoValue_CallNode_DataAttribute(true, null);
    }

    public static DataAttribute expr(ExprRootNode expr) {
      return new AutoValue_CallNode_DataAttribute(true, expr);
    }

    DataAttribute() {}

    public abstract boolean isPassingData();

    public final boolean isPassingAllData() {
      return isPassingData() && dataExpr() == null;
    }

    @Nullable
    public abstract ExprRootNode dataExpr();

    DataAttribute copy(CopyState copyState) {
      if (dataExpr() == null) {
        return this;
      }
      return new AutoValue_CallNode_DataAttribute(true, dataExpr().copy(copyState));
    }
  }

  /**
   * Private helper function for subclass constructors to parse the 'data' attribute.
   *
   * @param dataAttr The 'data' attribute in a call.
   * @param sourceLocation The 'data' attribute's source location.
   * @param errorReporter For reporting syntax errors.
   * @return A pair (isPassingData, dataExpr) where dataExpr may be null.
   */
  protected static DataAttribute parseDataAttributeHelper(
      String dataAttr, SourceLocation sourceLocation, SoyParsingContext context) {

    if (dataAttr == null) {
      return DataAttribute.none();
    } else if (dataAttr.equals("all")) {
      return DataAttribute.all();
    } else {
      return DataAttribute.expr(
          new ExprRootNode(
              new ExpressionParser(dataAttr, sourceLocation, context).parseExpression()));
    }
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected CallNode(CallNode orig, CopyState copyState) {
    super(orig, copyState);
    this.dataAttr = orig.dataAttr.copy(copyState);
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
    this.escapingDirectiveNames = orig.escapingDirectiveNames;
    this.isPcData = orig.getIsPcData();
  }

  /** The parsed 'data' attribute. */
  public DataAttribute dataAttribute() {
    return dataAttr;
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
  public String getTagString() {
    return buildTagStringHelper(numChildren() == 0);
  }

  @Override
  public String toSourceString() {
    return (numChildren() == 0) ? getTagString() : super.toSourceString();
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    return (dataAttr.dataExpr() != null)
        ? ImmutableList.of(new ExprUnion(dataAttr.dataExpr()))
        : Collections.<ExprUnion>emptyList();
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
    // for determining sameness. Sinces nodes have identity semantics this will only compare equal
    // to itself.
    return this;
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
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
