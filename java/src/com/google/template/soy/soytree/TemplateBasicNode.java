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
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Node representing a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class TemplateBasicNode extends TemplateNode {

  public static final SoyErrorKind INVALID_USEVARIANTTYPE =
      SoyErrorKind.of("Invalid type name \"{0}\" for attribute \"usevarianttype\".");

  /** The "modifiable" attribute. */
  private final boolean modifiable;

  /** The "legacydeltemplatenamespace" attribute. */
  private final String legacyDeltemplateNamespace;

  /** The "usevarianttype" attribute, as a string. */
  private final String useVariantTypeString;

  private String variantString = null;

  /**
   * The parsed "usevarianttype" type. null is used to express that the type has not been resolved
   * yet, while NullType is used to express that there is no usevarianttype attribute at all.
   */
  private SoyType useVariantType = null;

  /**
   * Main constructor. This is package-private because TemplateBasicNode instances should be built
   * using TemplateBasicNodeBuilder.
   *
   * @param nodeBuilder builder containing template initialization params
   * @param soyFileHeaderInfo info from the containing Soy file's header declarations
   * @param visibility visibility of this template
   * @param params the params from template header or SoyDoc. Null if no decls and no SoyDoc.
   */
  TemplateBasicNode(
      TemplateBasicNodeBuilder nodeBuilder,
      SoyFileHeaderInfo soyFileHeaderInfo,
      Visibility visibility,
      boolean modifiable,
      String legacyDeltemplateNamespace,
      String useVariantTypeString,
      @Nullable ImmutableList<TemplateHeaderVarDefn> params) {
    super(nodeBuilder, "template", soyFileHeaderInfo, visibility, params);
    this.modifiable = modifiable;
    this.legacyDeltemplateNamespace = legacyDeltemplateNamespace;
    this.useVariantTypeString = useVariantTypeString;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private TemplateBasicNode(TemplateBasicNode orig, CopyState copyState) {
    super(orig, copyState);
    this.modifiable = orig.modifiable;
    this.legacyDeltemplateNamespace = orig.legacyDeltemplateNamespace;
    this.useVariantTypeString = orig.useVariantTypeString;
    this.useVariantType = orig.useVariantType;
  }

  @Override
  public String getTemplateNameForUserMsgs() {
    return getTemplateName();
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_BASIC_NODE;
  }

  @Override
  public TemplateBasicNode copy(CopyState copyState) {
    return new TemplateBasicNode(this, copyState);
  }

  public boolean isModifiable() {
    return modifiable;
  }

  private Optional<CommandTagAttribute> getCommandTagAttribute(String name) {
    return getAttributes().stream()
        .filter(a -> name.equals(a.getName().identifier()) && a.hasExprValue())
        .findFirst();
  }

  @Nullable
  public ExprRootNode getModifiesExpr() {
    return getCommandTagAttribute("modifies").map(a -> a.valueAsExprList().get(0)).orElse(null);
  }

  public String getLegacyDeltemplateNamespace() {
    return legacyDeltemplateNamespace;
  }

  @Nullable
  public ExprRootNode getVariantExpr() {
    return getCommandTagAttribute("variant").map(a -> a.valueAsExprList().get(0)).orElse(null);
  }

  public void resolveUseVariantType(SoyTypeRegistry registry, ErrorReporter errorReporter) {
    Preconditions.checkState(useVariantType == null);
    if (useVariantTypeString.isEmpty()) {
      useVariantType = NullType.getInstance();
      return;
    }
    SoyType resolvedType = registry.getType(useVariantTypeString);
    if (resolvedType == null) {
      errorReporter.report(getSourceLocation(), INVALID_USEVARIANTTYPE, useVariantTypeString);
      useVariantType = NullType.getInstance();
    } else {
      useVariantType = resolvedType;
    }
  }

  /**
   * Returns the type specified in the "usevarrianttype" attribute, or NullType if the attribute is
   * not set.
   */
  public SoyType getUseVariantType() {
    Preconditions.checkNotNull(
        useVariantType,
        "if usevarianttype is set, resolveUseVariantType() needs to be called to resolve the type"
            + " before getUseVariantType() is used");
    return useVariantType;
  }

  /** Returns the delegate template variant, as a string */
  public String getDelTemplateVariant() {
    if (variantString != null) {
      return variantString;
    }
    return resolveVariantExpression();
  }

  /**
   * Calculate the string version of the variant expression.
   *
   * <p>This is done lazily so that global references can be resolved. This is not ideal since
   * nothing guarantees that resolution happens before access.
   *
   * <p>TODO(b/233903316): Check the set of valid types.
   */
  private String resolveVariantExpression() {
    if (getVariantExpr() == null) {
      variantString = "";
      return variantString;
    }
    ExprNode exprNode = getVariantExpr().getRoot();
    if (exprNode instanceof GlobalNode) {
      GlobalNode globalNode = (GlobalNode) exprNode;
      // This global was not substituted.  This happens when TemplateRegistries are built for
      // message extraction and parseinfo generation.  To make this 'work' we just use the
      // Global name for the variant value.  This is fine and will help catch some errors.
      // Because these nodes won't be used for code generation this should be safe. For this
      // reason we also don't store the key, instead we just return it.
      return globalNode.getName();
    }
    variantString = TemplateNode.variantExprToString(getVariantExpr().getRoot());
    return variantString;
  }
}
