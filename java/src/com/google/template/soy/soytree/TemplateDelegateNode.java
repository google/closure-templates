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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import javax.annotation.Nullable;

/**
 * Node representing a delegate template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class TemplateDelegateNode extends TemplateNode implements ExprHolderNode {

  /** Value class for a delegate template key (name and variant). */
  @AutoValue
  @VisibleForTesting
  public abstract static class DelTemplateKey {

    static DelTemplateKey create(String name, String variant) {
      return new AutoValue_TemplateDelegateNode_DelTemplateKey(name, variant);
    }

    abstract String name();

    abstract String variant();

    @Override
    public final String toString() {
      return name() + (variant().isEmpty() ? "" : ":" + variant());
    }
  }

  /** The delegate template name. */
  private final String delTemplateName;

  /** An expression that defines a delegate template variant. */
  @Nullable private final ExprRootNode delTemplateVariantExpr;

  /** The delegate template key (name and variant). */
  private DelTemplateKey delTemplateKey;

  /** The delegate priority. */
  private final Priority delPriority;

  /**
   * Main constructor. This is package-private because TemplateDelegateNode instances should be
   * built using TemplateDelegateNodeBuilder.
   *
   * @param nodeBuilder Builder containing template initialization params.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param delTemplateName The delegate template name.
   * @param delTemplateVariantExpr An expression that references a delegate template variant.
   * @param delPriority The delegate priority.
   */
  TemplateDelegateNode(
      TemplateDelegateNodeBuilder nodeBuilder,
      SoyFileHeaderInfo soyFileHeaderInfo,
      String delTemplateName,
      @Nullable ExprRootNode delTemplateVariantExpr,
      Priority delPriority,
      ImmutableList<TemplateParam> params) {

    super(
        nodeBuilder,
        "deltemplate",
        soyFileHeaderInfo,
        Visibility.PUBLIC /* deltemplate always has public visibility */,
        params);
    this.delTemplateName = checkNotNull(delTemplateName);
    this.delTemplateVariantExpr = delTemplateVariantExpr;
    this.delPriority = checkNotNull(delPriority);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private TemplateDelegateNode(TemplateDelegateNode orig, CopyState copyState) {
    super(orig, copyState);
    this.delTemplateName = orig.delTemplateName;
    this.delTemplateVariantExpr =
        orig.delTemplateVariantExpr == null ? null : orig.delTemplateVariantExpr.copy(copyState);
    this.delTemplateKey = orig.delTemplateKey;
    this.delPriority = orig.delPriority;
  }


  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_DELEGATE_NODE;
  }

  /** Returns the delegate template name. */
  public String getDelTemplateName() {
    return delTemplateName;
  }

  @Override
  public String getTemplateNameForUserMsgs() {
    return getDelTemplateKey().toString();
  }

  /** Returns the delegate template variant. */
  public String getDelTemplateVariant() {
    return getDelTemplateKey().variant();
  }

  /** Returns the delegate template key (name and variant). */
  @VisibleForTesting
  DelTemplateKey getDelTemplateKey() {
    if (delTemplateKey != null) {
      return delTemplateKey;
    }
    return resolveVariantExpression();
  }

  /** Returns the delegate priority. */
  public Priority getDelPriority() {
    return delPriority;
  }

  @Override
  public TemplateDelegateNode copy(CopyState copyState) {
    return new TemplateDelegateNode(this, copyState);
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> exprs = ImmutableList.builder();
    exprs.addAll(super.getExprList());
    if (delTemplateVariantExpr != null) {
      exprs.add(delTemplateVariantExpr);
    }
    return exprs.build();
  }

  /**
   * Calculate a DeltemplateKey for the variant.
   *
   * <p>This is done lazily so that global references can be resolved. This is not ideal since
   * nothing guarantees that resolution happens before access.
   *
   * <p>Note we don't do validation of the variant values since that is handled by the
   * TemplateDelegateNodeBuilder during construction
   */
  private DelTemplateKey resolveVariantExpression() {
    if (delTemplateVariantExpr == null) {
      delTemplateKey = DelTemplateKey.create(delTemplateName, "");
      return delTemplateKey;
    }
    ExprNode exprNode = delTemplateVariantExpr.getRoot();
    if (exprNode instanceof GlobalNode) {
      GlobalNode globalNode = (GlobalNode) exprNode;
      if (globalNode.isResolved()) {
        exprNode = globalNode.getValue();
      } else {
        // This global was not substituted.  This happens when TemplateRegistries are built for
        // message extraction and parseinfo generation.  To make this 'work' we just use the Global
        // name for the variant value.  This is fine and will help catch some errors.
        // Because these nodes won't be used for code generation this should be safe.
        // For this reason we also don't store the key, instead we just return it.
        return DelTemplateKey.create(delTemplateName, globalNode.getName());
      }
    }
    if (exprNode instanceof IntegerNode) {
      // Globals were already substituted: We may now create the definitive variant and key fields
      // on this node.
      long variantValue = ((IntegerNode) exprNode).getValue();
      delTemplateKey = DelTemplateKey.create(delTemplateName, String.valueOf(variantValue));
    } else if (exprNode instanceof StringNode) {
      // Globals were already substituted: We may now create the definitive variant and key fields
      // on this node.
      delTemplateKey = DelTemplateKey.create(delTemplateName, ((StringNode) exprNode).getValue());
    } else {
      // We must have already reported an error, just create an arbitrary variant expr.
      delTemplateKey = DelTemplateKey.create(delTemplateName, exprNode.toSourceString());
    }
    return delTemplateKey;
  }
}
