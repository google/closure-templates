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
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Node representing a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class TemplateBasicNode extends TemplateNode {

  public static final SoyErrorKind INVALID_USEVARIANTTYPE =
      SoyErrorKind.of(
          "Invalid type name \"{0}\" for attribute \"usevarianttype\". Must be \"number\", "
              + "\"string\", or a proto enum.");

  /** The "modifiable" attribute. */
  private final boolean modifiable;

  /** The "legacydeltemplatenamespace" attribute. */
  private final CommandTagAttribute legacyDeltemplateNamespaceAttr;

  /** The "usevarianttype" attribute, as a string. */
  private final CommandTagAttribute useVariantTypeAttr;

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
      @Nullable CommandTagAttribute legacyDeltemplateNamespaceAttr,
      @Nullable CommandTagAttribute useVariantTypeAttr,
      @Nullable ImmutableList<TemplateHeaderVarDefn> params) {
    super(nodeBuilder, "template", soyFileHeaderInfo, visibility, params);
    this.modifiable = modifiable;
    this.legacyDeltemplateNamespaceAttr = legacyDeltemplateNamespaceAttr;
    this.useVariantTypeAttr = useVariantTypeAttr;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private TemplateBasicNode(TemplateBasicNode orig, CopyState copyState) {
    super(orig, copyState);
    this.modifiable = orig.modifiable;
    this.legacyDeltemplateNamespaceAttr =
        copyState.copyNullable(orig.legacyDeltemplateNamespaceAttr);
    this.useVariantTypeAttr = copyState.copyNullable(orig.useVariantTypeAttr);
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
    return getAttributes().stream().filter(a -> name.equals(a.getName().identifier())).findFirst();
  }

  /** Returns the CommandTagAttribute, if it's an expression. */
  private Optional<CommandTagAttribute> getCommandTagAttributeExpr(String name) {
    return getCommandTagAttribute(name).map(a -> a.hasExprValue() ? a : null);
  }

  @Nullable
  public ExprRootNode getModifiesExpr() {
    return getCommandTagAttributeExpr("modifies").map(a -> a.valueAsExprList().get(0)).orElse(null);
  }

  public String getLegacyDeltemplateNamespace() {
    return legacyDeltemplateNamespaceAttr != null ? legacyDeltemplateNamespaceAttr.getValue() : "";
  }

  @Nullable
  public ExprRootNode getVariantExpr() {
    return getCommandTagAttributeExpr("variant").map(a -> a.valueAsExprList().get(0)).orElse(null);
  }

  private static boolean isValidVariantType(SoyType type) {
    return type.equals(SoyTypes.NUMBER_TYPE)
        || type.equals(StringType.getInstance())
        || type.getKind().equals(SoyType.Kind.PROTO_ENUM);
  }

  public void resolveUseVariantType(SoyTypeRegistry registry, ErrorReporter errorReporter) {
    Preconditions.checkState(useVariantType == null);
    if (useVariantTypeAttr == null) {
      useVariantType = NullType.getInstance();
      return;
    }
    SoyType resolvedType = registry.getType(useVariantTypeAttr.getValue());
    if (resolvedType == null || !isValidVariantType(resolvedType)) {
      errorReporter.report(
          getCommandTagAttribute("usevarianttype")
              .map(CommandTagAttribute::getSourceLocation)
              .orElse(getSourceLocation()),
          INVALID_USEVARIANTTYPE,
          useVariantTypeAttr.getValue());
      useVariantType = NullType.getInstance();
    } else {
      useVariantType = resolvedType;
    }
  }

  @Nullable
  public CommandTagAttribute getUseVariantTypeAttribute() {
    return useVariantTypeAttr;
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
    if (getVariantExpr() == null) {
      variantString = "";
      return variantString;
    }
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
    variantString = TemplateNode.variantExprToString(getVariantExpr().getRoot());
    return variantString;
  }

  /**
   * The name to use for the @mods JS annotation. For modifying templates, return the namespace of
   * the template being modified, as long as it doesn't have legacydeltemplatenamespace. Otherwise,
   * return null (ie, don't emit @mods).
   */
  @Nullable
  public String moddedSoyNamespace() {
    if (getModifiesExpr() != null
        && getModName() != null
        && getModifiesExpr().getRoot() instanceof TemplateLiteralNode) {
      TemplateLiteralNode templateLiteralNode = (TemplateLiteralNode) getModifiesExpr().getRoot();
      SoyType nodeType = templateLiteralNode.getType();
      if ((nodeType instanceof TemplateType)
          && ((TemplateType) nodeType).getLegacyDeltemplateNamespace().isEmpty()) {
        return templateLiteralNode
            .getResolvedName()
            .substring(0, templateLiteralNode.getResolvedName().lastIndexOf("."));
      }
    }
    return null;
  }

  /**
   * The name to use for the legacy @hassoydeltemplate annotation. For modifiable templates with,
   * legacydeltemplatenamespace, return its own legacy namespace. For modifying templates, return
   * the legacy namespace of the template being modified. Otherwise, return null (ie, don't emit
   * hassoydeltemplate).
   */
  @Nullable
  // TODO(b/233903480): Remove these once all deltemplates and delcalls are removed.
  public String deltemplateAnnotationName() {
    if (isModifiable()) {
      return !getLegacyDeltemplateNamespace().isEmpty() ? getLegacyDeltemplateNamespace() : null;
    }
    if (getModifiesExpr() != null && getModifiesExpr().getRoot() instanceof TemplateLiteralNode) {
      TemplateLiteralNode templateLiteralNode = (TemplateLiteralNode) getModifiesExpr().getRoot();
      SoyType nodeType = templateLiteralNode.getType();
      if (nodeType instanceof TemplateType) {
        String legacyNamespace = ((TemplateType) nodeType).getLegacyDeltemplateNamespace();
        return !legacyNamespace.isEmpty() ? legacyNamespace : null;
      }
    }
    return null;
  }
}
