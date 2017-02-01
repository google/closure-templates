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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.LegacyInternalSyntaxException;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Builder for TemplateBasicNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TemplateBasicNodeBuilder extends TemplateNodeBuilder {
  private static final SoyErrorKind FULLY_QUALIFIED_NAME =
      SoyErrorKind.of(
          "Soy V2 template names must be relative to the file namespace, i.e. a dot "
              + "followed by an identifier.  Templates with fully qualified names are only allowed "
              + "in legacy templates marked with the deprecatedV1=\"true\" attribute.");
  private static final SoyErrorKind PRIVATE_AND_VISIBILITY =
      SoyErrorKind.of("Cannot specify both private=\"true\" and visibility=\"{0}\".");

  /** @param soyFileHeaderInfo Info from the containing Soy file's header declarations. */
  public TemplateBasicNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo, ErrorReporter errorReporter) {
    super(soyFileHeaderInfo, errorReporter);
  }

  @Override
  public TemplateBasicNodeBuilder setId(int id) {
    return (TemplateBasicNodeBuilder) super.setId(id);
  }

  @Override
  public TemplateBasicNodeBuilder setSourceLocation(SourceLocation location) {
    return (TemplateBasicNodeBuilder) super.setSourceLocation(location);
  }

  @Override
  public TemplateNodeBuilder setCommandValues(
      Identifier templateName, List<CommandTagAttribute> attrs) {
    this.cmdText = templateName.identifier() + " " + Joiner.on(' ').join(attrs);
    AutoescapeMode autoescapeMode = soyFileHeaderInfo.defaultAutoescapeMode;
    ContentKind kind = null;
    SourceLocation kindLocation = null;
    for (CommandTagAttribute attribute : attrs) {
      Identifier name = attribute.getName();
      switch (name.identifier()) {
        case "private":
          if (attribute.valueAsBoolean(errorReporter, false)) {
            if (visibility != null) {
              errorReporter.report(
                  attribute.getName().location(),
                  PRIVATE_AND_VISIBILITY,
                  visibility.getAttributeValue());
            }
            // See go/soy-visibility for why this is considered "legacy private".
            visibility = Visibility.LEGACY_PRIVATE;
          }
          break;

        case "visibility":
          if (visibility != null) {
            errorReporter.report(
                attribute.getName().location(), PRIVATE_AND_VISIBILITY, attribute.getValue());
          }
          visibility = attribute.valueAsVisibility(errorReporter);
          break;
        case "autoescape":
          autoescapeMode = attribute.valueAsAutoescapeMode(errorReporter);
          break;
        case "kind":
          kind = attribute.valueAsContentKind(errorReporter);
          kindLocation = attribute.getValueLocation();
          break;
        case "requirecss":
          setRequiredCssNamespaces(attribute.valueAsRequireCss(errorReporter));
          break;
        case "cssbase":
          setCssBaseNamespace(attribute.valueAsCssBase(errorReporter));
          break;
        case "deprecatedV1":
          markDeprecatedV1(attribute.valueAsBoolean(errorReporter, false));
          break;
        case "stricthtml":
          strictHtmlMode = attribute.valueAsStrictHtmlMode(errorReporter);
          break;
        default:
          errorReporter.report(
              name.location(),
              CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY,
              name.identifier(),
              ImmutableList.of(
                  "private",
                  "visibility",
                  "autoescape",
                  "kind",
                  "requirecss",
                  "cssbase",
                  "deprecatedV1",
                  "stricthtml"));
      }
    }

    if (visibility == null) {
      visibility = Visibility.PUBLIC;
    }
    setAutoescapeInfo(autoescapeMode, kind, kindLocation);

    if (templateName.isPartialIdentifier()) {
      if (soyFileHeaderInfo.namespace == null) {
        // In this case, throwing a SoySyntaxException and halting compilation of the current file
        // is preferable to reporting a SoyErrorKind and continuing. To continue, we could make up
        // an "error" template name/namespace, but that would cause spurious duplicate template
        // errors (for every namespace-relative name in a non-namespaced file).
        // It's also dangerous, since "error" is a common real-world template/namespace name.
        throw LegacyInternalSyntaxException.createWithMetaInfo(
            "Template has namespace-relative name, but file has no namespace declaration.",
            templateName.location());
      }
      setTemplateNames(
          soyFileHeaderInfo.namespace + templateName.identifier(),
          templateName.location(),
          templateName.identifier());
    } else {
      if (!isMarkedV1 && !templateName.isPartialIdentifier()) {
        // only allow fully qualified template names if it is also marked as v1
        errorReporter.report(templateName.location(), FULLY_QUALIFIED_NAME);
      }
      setTemplateNames(templateName.identifier(), templateName.location(), null);
    }
    this.templateNameForUserMsgs = getTemplateName();
    return this;
  }

  /**
   * Alternative to {@code setCmdText()} that sets command text info directly as opposed to having
   * it parsed from the command text string. The cmdText field will be set to a canonical string
   * generated from the given info.
   *
   * @param templateName This template's name.
   * @param partialTemplateName This template's partial name. Only applicable for V2; null for V1.
   * @param visibility Visibility of this template.
   * @param autoescapeMode The mode of autoescaping for this template.
   * @param contentKind Strict mode context. Nonnull iff autoescapeMode is strict.
   * @param requiredCssNamespaces CSS namespaces required to render the template.
   * @return This builder.
   */
  public TemplateBasicNodeBuilder setCmdTextInfo(
      String templateName,
      @Nullable String partialTemplateName,
      Visibility visibility,
      AutoescapeMode autoescapeMode,
      ContentKind contentKind,
      ImmutableList<String> requiredCssNamespaces) {

    Preconditions.checkState(this.sourceLocation != null);
    Preconditions.checkState(this.cmdText == null);
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(templateName));
    Preconditions.checkArgument(
        partialTemplateName == null || BaseUtils.isIdentifierWithLeadingDot(partialTemplateName));
    Preconditions.checkArgument((contentKind != null) == (autoescapeMode == AutoescapeMode.STRICT));

    setTemplateNames(templateName, sourceLocation, partialTemplateName);
    this.templateNameForUserMsgs = templateName;
    this.visibility = visibility;
    setAutoescapeInfo(autoescapeMode, contentKind, sourceLocation);
    setRequiredCssNamespaces(requiredCssNamespaces);

    StringBuilder cmdTextBuilder = new StringBuilder();
    cmdTextBuilder.append((partialTemplateName != null) ? partialTemplateName : templateName);
    cmdTextBuilder.append(" autoescape=\"").append(autoescapeMode.getAttributeValue()).append('"');
    if (contentKind != null) {
      cmdTextBuilder
          .append(" kind=\"")
          .append(NodeContentKinds.toAttributeValue(contentKind))
          .append('"');
    }
    if (visibility == Visibility.LEGACY_PRIVATE) {
      // TODO(brndn): generate code for other visibility levels. b/15190131
      cmdTextBuilder.append(" private=\"true\"");
    }
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
  public TemplateBasicNodeBuilder setSoyDoc(String soyDoc, SourceLocation soyDocLocation) {
    return (TemplateBasicNodeBuilder) super.setSoyDoc(soyDoc, soyDocLocation);
  }

  @Override
  public TemplateBasicNodeBuilder addParams(Iterable<? extends TemplateParam> allParams) {
    return (TemplateBasicNodeBuilder) super.addParams(allParams);
  }

  @Override
  public TemplateBasicNode build() {
    Preconditions.checkState(id != null && cmdText != null);
    return new TemplateBasicNode(this, soyFileHeaderInfo, visibility, params);
  }
}
