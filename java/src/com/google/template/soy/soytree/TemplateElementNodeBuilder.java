/*
 * Copyright 2013 Google Inc.
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplatePropVar;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Builder for TemplateElementNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class TemplateElementNodeBuilder extends TemplateNodeBuilder {

  protected static final ImmutableSet<String> BANNED_ATTRIBUTE_NAMES =
      ImmutableSet.of("autoescape", "kind", "stricthtml", "visibility");

  private static final SoyErrorKind BANNED_ATTRIBUTE_NAMES_ERROR =
      SoyErrorKind.of("Attribute ''{0}'' is not allowed on Soy elements.");

  private List<CommandTagAttribute> attrs = ImmutableList.of();

  /** @param soyFileHeaderInfo Info from the containing Soy file's header declarations. */
  public TemplateElementNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo, ErrorReporter errorReporter) {
    super(soyFileHeaderInfo, errorReporter);
    setAutoescapeInfo(AutoescapeMode.STRICT, SanitizedContentKind.HTML, this.sourceLocation);
  }

  @Override
  public TemplateElementNodeBuilder setId(int id) {
    return (TemplateElementNodeBuilder) super.setId(id);
  }

  @Override
  public TemplateElementNodeBuilder setSourceLocation(SourceLocation location) {
    return (TemplateElementNodeBuilder) super.setSourceLocation(location);
  }

  @Override
  public TemplateNodeBuilder setCommandValues(
      Identifier templateName, List<CommandTagAttribute> attrs) {
    this.attrs = attrs;
    this.cmdText = templateName.identifier() + " " + Joiner.on(' ').join(attrs);
    setCommonCommandValues(attrs);

    setTemplateNames(
        soyFileHeaderInfo.namespace + templateName.identifier(),
        templateName.identifier());
    return this;
  }

  /**
   * Alternative to {@code setCmdText()} that sets command text info directly as opposed to having
   * it parsed from the command text string. The cmdText field will be set to a canonical string
   * generated from the given info.
   *
   * @param templateName This template's name.
   * @param partialTemplateName This template's partial name. Only applicable for V2; null for V1.
   * @param requiredCssNamespaces CSS namespaces required to render the template.
   * @return This builder.
   */
  public TemplateElementNodeBuilder setCmdTextInfo(
      String templateName,
      @Nullable String partialTemplateName,
      ImmutableList<String> requiredCssNamespaces) {

    Preconditions.checkState(this.sourceLocation != null);
    Preconditions.checkState(this.cmdText == null);
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(templateName));
    Preconditions.checkArgument(
        partialTemplateName == null || BaseUtils.isIdentifierWithLeadingDot(partialTemplateName));

    setTemplateNames(templateName, partialTemplateName);
    setRequiredCssNamespaces(requiredCssNamespaces);

    StringBuilder cmdTextBuilder = new StringBuilder();
    cmdTextBuilder.append((partialTemplateName != null) ? partialTemplateName : templateName);
    if (!requiredCssNamespaces.isEmpty()) {
      cmdTextBuilder
          .append(" requirecss=\"")
          .append(Joiner.on(", ").join(requiredCssNamespaces))
          .append("\"");
    }
    this.cmdText = cmdTextBuilder.toString();

    return this;
  }

  @Override
  public TemplateNodeBuilder setPropVars(ImmutableList<TemplatePropVar> newPropVars) {
    this.propVars = newPropVars;
    checkDuplicateHeaderVars(params, propVars, errorReporter);
    return this;
  }

  @Override
  public TemplateElementNodeBuilder setSoyDoc(String soyDoc, SourceLocation soyDocLocation) {
    return (TemplateElementNodeBuilder) super.setSoyDoc(soyDoc, soyDocLocation);
  }

  @Override
  public TemplateElementNodeBuilder addParams(Iterable<? extends TemplateParam> allParams) {
    super.addParams(allParams);
    checkDuplicateHeaderVars(params, propVars, errorReporter);
    return this;
  }

  @Override
  public TemplateElementNode build() {
    Preconditions.checkState(id != null && cmdText != null);
    for (CommandTagAttribute attr : attrs) {
      if (BANNED_ATTRIBUTE_NAMES.contains(attr.getName().identifier())) {
        this.errorReporter.report(
            this.sourceLocation, BANNED_ATTRIBUTE_NAMES_ERROR, attr.getName().identifier());
      }
    }
    return new TemplateElementNode(this, soyFileHeaderInfo, params, propVars);
  }
}
