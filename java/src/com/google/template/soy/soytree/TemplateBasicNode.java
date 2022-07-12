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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Node representing a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class TemplateBasicNode extends TemplateNode {

  /** The "modifiable" attribute. */
  private final boolean modifiable;

  /** The "legacydeltemplatenamespace" attribute. */
  private final String legacyDeltemplateNamespace;

  /** The "usevarianttype" attribute. */
  private final String useVariantType;

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
      String useVariantType,
      @Nullable ImmutableList<TemplateHeaderVarDefn> params) {
    super(nodeBuilder, "template", soyFileHeaderInfo, visibility, params);
    this.modifiable = modifiable;
    this.legacyDeltemplateNamespace = legacyDeltemplateNamespace;
    this.useVariantType = useVariantType;
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

  public boolean getModifiable() {
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

  public String getUseVariantType() {
    return useVariantType;
  }
}
