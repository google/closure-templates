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
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import javax.annotation.Nullable;

/**
 * Node representing a Soy element.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class TemplateElementNode extends TemplateNode implements ExprHolderNode {

  /** The "allowbrokenelementcollisions" attribute */
  private final boolean allowBrokenElementCollisions;

  /**
   * Main constructor. This is package-private because TemplateElementNode instances should be built
   * using TemplateElementNodeBuilder.
   *
   * @param nodeBuilder builder containing template initialization params
   * @param soyFileHeaderInfo info from the containing Soy file's header declarations
   * @param params the params from template header or SoyDoc. Null if no decls and no SoyDoc.
   */
  TemplateElementNode(
      TemplateElementNodeBuilder nodeBuilder,
      SoyFileHeaderInfo soyFileHeaderInfo,
      @Nullable ImmutableList<TemplateHeaderVarDefn> params) {
    super(nodeBuilder, "element", soyFileHeaderInfo, Visibility.PUBLIC, params);
    // TODO(b/282947052): Add documentation explaining what this attribute is/how to get rid of it.
    this.allowBrokenElementCollisions = nodeBuilder.shouldAllowBrokenElementCollisions();
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private TemplateElementNode(TemplateElementNode orig, CopyState copyState) {
    super(orig, copyState);
    this.allowBrokenElementCollisions = orig.shouldAllowBrokenElementCollisions();
  }

  /** Returns the state variables from template header. */
  public ImmutableList<TemplateStateVar> getStateVars() {
    ImmutableList.Builder<TemplateStateVar> builder = ImmutableList.builder();
    for (TemplateHeaderVarDefn header : this.getHeaderParams()) {
      if (header instanceof TemplateStateVar) {
        builder.add((TemplateStateVar) header);
      }
    }
    return builder.build();
  }

  public boolean shouldAllowBrokenElementCollisions() {
    return allowBrokenElementCollisions;
  }

  @Override
  public String getTemplateNameForUserMsgs() {
    return getTemplateName();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> builder = ImmutableList.builder();
    builder.addAll(super.getExprList());
    for (TemplateStateVar state : getStateVars()) {
      if (state.defaultValue() != null) {
        builder.add(state.defaultValue());
      }
    }
    return builder.build();
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
