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
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.InjectedParam;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplatePropVar;
import javax.annotation.Nullable;

/**
 * Node representing a Soy element.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class TemplateElementNode extends TemplateNode implements ExprHolderNode {

  /** The prop variables from template header. */
  private final ImmutableList<TemplatePropVar> propVars;

  /**
   * Main constructor. This is package-private because TemplateElementNode instances should be built
   * using TemplateElementNodeBuilder.
   *
   * @param nodeBuilder builder containing template initialization params
   * @param soyFileHeaderInfo info from the containing Soy file's header declarations
   * @param params the params from template header or SoyDoc. Null if no decls and no SoyDoc.
   * @param propVars the prop variables from the template header
   */
  TemplateElementNode(
      TemplateElementNodeBuilder nodeBuilder,
      SoyFileHeaderInfo soyFileHeaderInfo,
      @Nullable ImmutableList<TemplateParam> params,
      ImmutableList<TemplatePropVar> propVars) {
    super(nodeBuilder, "element", soyFileHeaderInfo, Visibility.PUBLIC, params);
    this.propVars = propVars;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private TemplateElementNode(TemplateElementNode orig, CopyState copyState) {
    super(orig, copyState);
    this.propVars = orig.propVars;
  }

  /** Returns the prop variables from template header. */
  public ImmutableList<TemplatePropVar> getPropVars() {
    return propVars;
  }

  @Override
  public String getTemplateNameForUserMsgs() {
    return getTemplateName();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> builder = ImmutableList.builder();
    for (TemplatePropVar prop : getPropVars()) {
      builder.add(prop.initialValue());
    }
    return builder.build();
  }

  @Override
  protected ImmutableList<TemplateHeaderVarDefn> getHeaderParamsForSourceString() {
    // Header.
    // Gather up all the @params declared in the template header (not in the SoyDoc).
    return new ImmutableList.Builder<TemplateHeaderVarDefn>()
        .addAll(super.getHeaderParamsForSourceString())
        .addAll(propVars)
        .build();
  }

  @Override
  protected ImmutableMap<Class<?>, String> getDeclNameMap() {
    return ImmutableMap.of(
        HeaderParam.class, "@param",
        InjectedParam.class, "@inject",
        TemplatePropVar.class, "@prop");
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_ELEMENT_NODE;
  }

  @Override
  public TemplateElementNode copy(CopyState copyState) {
    return new TemplateElementNode(this, copyState);
  }
}
