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
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import javax.annotation.Nullable;

/**
 * Node representing a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class TemplateBasicNode extends TemplateNode {

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
      @Nullable ImmutableList<TemplateHeaderVarDefn> params) {
    super(nodeBuilder, "template", soyFileHeaderInfo, visibility, params);
  }

  @Override
  public String getTemplateNameForUserMsgs() {
    return getTemplateName();
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private TemplateBasicNode(TemplateBasicNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_BASIC_NODE;
  }

  @Override
  public TemplateBasicNode copy(CopyState copyState) {
    return new TemplateBasicNode(this, copyState);
  }
}
