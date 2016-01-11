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
import com.google.template.soy.base.internal.LegacyInternalSyntaxException;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyTypeRegistry;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private static final SoyErrorKind MISSING_TEMPLATE_NAME =
      SoyErrorKind.of("Missing template name.");
  private static final SoyErrorKind PRIVATE_AND_VISIBILITY =
      SoyErrorKind.of("Cannot specify both private=\"true\" and visibility=\"{0}\".");

  /** Pattern for a template name. */
  private static final Pattern TEMPLATE_NAME =
      Pattern.compile("^\\s*([.\\w]+)(?=\\s|$)", Pattern.DOTALL);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("template",
          new Attribute("private", Attribute.BOOLEAN_VALUES, "false"),
          new Attribute("autoescape", AutoescapeMode.getAttributeValues(), null),
          new Attribute("kind", NodeContentKinds.getAttributeValues(), null),
          new Attribute("requirecss", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("cssbase", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("visibility", Visibility.getAttributeValues(), null),
          new Attribute("deprecatedV1", Attribute.BOOLEAN_VALUES, "false"));

  /**
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param sourceLocation The template's source location.
   */
  public TemplateBasicNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo,
      SourceLocation sourceLocation,
      ErrorReporter errorReporter) {
    super(soyFileHeaderInfo, sourceLocation, errorReporter, null /* typeRegistry */);
  }

  /**
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param sourceLocation The template's source location.
   * @param typeRegistry Type registry used for parsing type expressions.
   */
  public TemplateBasicNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo,
      SourceLocation sourceLocation,
      ErrorReporter errorReporter,
      SoyTypeRegistry typeRegistry) {
    super(soyFileHeaderInfo, sourceLocation, errorReporter, typeRegistry);
  }

  @Override public TemplateBasicNodeBuilder setId(int id) {
    return (TemplateBasicNodeBuilder) super.setId(id);
  }

  @Override public TemplateBasicNodeBuilder setCmdText(String cmdText) {

    Preconditions.checkState(this.cmdText == null);
    this.cmdText = cmdText;

    String commandTextForParsing = cmdText;

    String nameAttr;
    Matcher ntnMatcher = TEMPLATE_NAME.matcher(commandTextForParsing);
    if (ntnMatcher.find()) {
      nameAttr = ntnMatcher.group(1);
      commandTextForParsing = commandTextForParsing.substring(ntnMatcher.end()).trim();
    } else {
      errorReporter.report(sourceLocation, MISSING_TEMPLATE_NAME);
      nameAttr = null;
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(
        commandTextForParsing, errorReporter, sourceLocation);

    // See go/soy-visibility for why this is considered "legacy private".
    if (attributes.get("private").equals("true")) {
      visibility = Visibility.LEGACY_PRIVATE;
    }

    String visibilityName = attributes.get("visibility");
    if (visibilityName != null) {
      // It is an error to specify both "private" and "visibility" attrs.
      if (visibility != null) {
        errorReporter.report(sourceLocation, PRIVATE_AND_VISIBILITY, visibilityName);
      }
      visibility = Visibility.forAttributeValue(visibilityName);
    }

    // If the visibility hasn't been set, through either the old "private" attr
    // or the new "visibility" attr, default to public.
    if (visibility == null) {
      visibility = Visibility.PUBLIC;
    }

    setAutoescapeCmdText(attributes);
    setRequireCssCmdText(attributes);
    setCssBaseCmdText(attributes);
    setV1Marker(attributes);

    if (nameAttr != null) {
      if (BaseUtils.isIdentifierWithLeadingDot(nameAttr)) {
        if (soyFileHeaderInfo.namespace == null) {
          // In this case, throwing a SoySyntaxException and halting compilation of the current file
          // is preferable to reporting a SoyErrorKind and continuing. To continue, we could make up
          // an "error" template name/namespace, but that would cause spurious duplicate template
          // errors (for every namespace-relative name in a non-namespaced file).
          // It's also dangerous, since "error" is a common real-world template/namespace name.
          throw LegacyInternalSyntaxException.createWithMetaInfo(
              "Template has namespace-relative name, but file has no namespace declaration.",
              sourceLocation);
        }
        setTemplateNames(soyFileHeaderInfo.namespace + nameAttr, nameAttr);
      } else {
        if (!isMarkedV1 && BaseUtils.isDottedIdentifier(nameAttr)) {
          // only allow fully qualified template names if it is also marked as v1
          errorReporter.report(sourceLocation, FULLY_QUALIFIED_NAME);
        }
        setTemplateNames(nameAttr, null);
      }
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
      String templateName, @Nullable String partialTemplateName,
      Visibility visibility, AutoescapeMode autoescapeMode,
      ContentKind contentKind, ImmutableList<String> requiredCssNamespaces) {

    Preconditions.checkState(this.cmdText == null);
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(templateName));
    Preconditions.checkArgument(
        partialTemplateName == null || BaseUtils.isIdentifierWithLeadingDot(partialTemplateName));
    Preconditions.checkArgument((contentKind != null) == (autoescapeMode == AutoescapeMode.STRICT));

    setTemplateNames(templateName, partialTemplateName);
    this.templateNameForUserMsgs = templateName;
    this.visibility = visibility;
    setAutoescapeInfo(autoescapeMode, contentKind);
    setRequiredCssNamespaces(requiredCssNamespaces);

    StringBuilder cmdTextBuilder = new StringBuilder();
    cmdTextBuilder.append((partialTemplateName != null) ? partialTemplateName : templateName);
    cmdTextBuilder.append(" autoescape=\"").append(autoescapeMode.getAttributeValue()).append('"');
    if (contentKind != null) {
      cmdTextBuilder.append(" kind=\"")
          .append(NodeContentKinds.toAttributeValue(contentKind))
          .append('"');
    }
    if (visibility == Visibility.LEGACY_PRIVATE) {
      // TODO(brndn): generate code for other visibility levels. b/15190131
      cmdTextBuilder.append(" private=\"true\"");
    }
    if (!requiredCssNamespaces.isEmpty()) {
      cmdTextBuilder.append(" requirecss=\"")
          .append(Joiner.on(", ").join(requiredCssNamespaces))
          .append("\"");
    }
    this.cmdText = cmdTextBuilder.toString();

    return this;
  }

  @Override public TemplateBasicNodeBuilder setSoyDoc(String soyDoc) {
    return (TemplateBasicNodeBuilder) super.setSoyDoc(soyDoc);
  }

  @Override public TemplateBasicNodeBuilder setHeaderDecls(DeclInfo... declInfos) {
    return (TemplateBasicNodeBuilder) super.setHeaderDecls(declInfos);
  }

  @Override public TemplateBasicNodeBuilder addParams(Iterable<? extends TemplateParam> allParams) {
    return (TemplateBasicNodeBuilder) super.addParams(allParams);
  }

  @Override public TemplateBasicNode build() {
    Preconditions.checkState(id != null && cmdText != null);
    return new TemplateBasicNode(this, soyFileHeaderInfo, visibility, params);
  }
}
