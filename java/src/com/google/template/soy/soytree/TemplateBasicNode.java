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
import com.google.template.soy.basetree.SyntaxVersionBound;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.soytree.defn.TemplateParam;

import javax.annotation.Nullable;


/**
 * Node representing a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class TemplateBasicNode extends TemplateNode {


  /** Whether this template overrides another (always false for syntax version V2). */
  private final boolean isOverride;


  /**
   * Main constructor. This is package-private because TemplateBasicNode instances should be built
   * using TemplateBasicNodeBuilder.
   *
   * @param id The id for this node.
   * @param syntaxVersionBound The lowest known upper bound (exclusive!) for the syntax version of
   *     this node.
   * @param cmdText The command text.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param templateName This template's name.
   * @param partialTemplateName This template's partial name. Only applicable for V2; null for V1.
   * @param templateNameForUserMsgs A string suitable for display in user msgs as the template name.
   * @param isOverride Whether this template overrides another (always false for syntax version V2).
   * @param isPrivate Whether this template is private.
   * @param autoescapeMode The mode of autoescaping for this template.
   * @param contentKind Strict mode context. Nonnull iff autoescapeMode is strict.
   * @param requiredCssNamespaces CSS namespaces required to render the template.
   * @param soyDoc The full SoyDoc, including the start/end tokens, or null.
   * @param soyDocDesc The description portion of the SoyDoc (before declarations), or null.
   * @param params The params from template header or SoyDoc. Null if no decls and no SoyDoc.
   */
  TemplateBasicNode(
      int id, @Nullable SyntaxVersionBound syntaxVersionBound, String cmdText,
      SoyFileHeaderInfo soyFileHeaderInfo, String templateName,
      @Nullable String partialTemplateName, String templateNameForUserMsgs, boolean isOverride,
      boolean isPrivate, AutoescapeMode autoescapeMode, ContentKind contentKind,
      ImmutableList<String> requiredCssNamespaces, String soyDoc, String soyDocDesc,
      ImmutableList<TemplateParam> params) {

    super(
        id, syntaxVersionBound, "template", cmdText, soyFileHeaderInfo, templateName,
        partialTemplateName, templateNameForUserMsgs, isPrivate, autoescapeMode, contentKind,
        requiredCssNamespaces, soyDoc, soyDocDesc, params);
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
