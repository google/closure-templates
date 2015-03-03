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
import com.google.template.soy.soytree.defn.TemplateParam;


/**
 * Node representing a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TemplateBasicNode extends TemplateNode {

  /** Whether this template overrides another (always false for syntax version V2). */
  private final boolean isOverride;

  /**
   * Main constructor. This is package-private because TemplateBasicNode instances should be built
   * using TemplateBasicNodeBuilder.
   *
   * @param nodeBuilder Builder containing template initialization params.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param isOverride Whether this template overrides another (always false for syntax version V2).
   * @param visibility Visibility of this template.
   * @param params The params from template header or SoyDoc. Null if no decls and no SoyDoc.
   */
  TemplateBasicNode(
      TemplateBasicNodeBuilder nodeBuilder,
      SoyFileHeaderInfo soyFileHeaderInfo,
      boolean isOverride,
      Visibility visibility,
      ImmutableList<TemplateParam> params) {
    super(nodeBuilder, "template", soyFileHeaderInfo, visibility, params);
    this.isOverride = isOverride;
  }

  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected TemplateBasicNode(TemplateBasicNode orig) {
    super(orig);
    this.isOverride = orig.isOverride;
  }

  @Override public Kind getKind() {
    return Kind.TEMPLATE_BASIC_NODE;
  }

  /** Returns whether this template overrides another (always false for syntax version V2). */
  public boolean isOverride() {
    return isOverride;
  }

  @Override public TemplateBasicNode clone() {
    return new TemplateBasicNode(this);
  }
}
