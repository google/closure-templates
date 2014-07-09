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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionBound;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.types.SoyTypeRegistry;

import java.util.List;
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

  /** Pattern for a template name not listed as an attribute name="...". */
  private static final Pattern NONATTRIBUTE_TEMPLATE_NAME =
      Pattern.compile("^ (?! name=\") [.\\w]+ (?= \\s | $)", Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("template",
          new Attribute("name", Attribute.ALLOW_ALL_VALUES, null),  // V2.1-
          new Attribute("private", Attribute.BOOLEAN_VALUES, "false"),
          new Attribute("override", Attribute.BOOLEAN_VALUES, null),  // V1.0
          new Attribute("autoescape", AutoescapeMode.getAttributeValues(), null),
          new Attribute("kind", NodeContentKinds.getAttributeValues(), null),
          new Attribute("requirecss", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("cssbase", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("visibility", Visibility.getAttributeValues(), null));


  /** Whether this template overrides another (always false for syntax version V2). */
  private Boolean isOverride;

  /**
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   */
  public TemplateBasicNodeBuilder(SoyFileHeaderInfo soyFileHeaderInfo) {
    super(soyFileHeaderInfo, null);
  }

  /**
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param typeRegistry Type registry used for parsing type expressions.
   */
  public TemplateBasicNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo, SoyTypeRegistry typeRegistry) {
    super(soyFileHeaderInfo, typeRegistry);
  }

  @Override public TemplateBasicNodeBuilder setId(int id) {
    return (TemplateBasicNodeBuilder) super.setId(id);
  }

  @Override public TemplateBasicNodeBuilder setCmdText(String cmdText) {

    Preconditions.checkState(this.cmdText == null);
    this.cmdText = cmdText;

    String commandTextForParsing = cmdText;

    // Handle template name not listed as an attribute name="...".
    String nameAttr = null;
    Matcher ntnMatcher = NONATTRIBUTE_TEMPLATE_NAME.matcher(commandTextForParsing);
    if (ntnMatcher.find()) {
      nameAttr = ntnMatcher.group();
      commandTextForParsing = commandTextForParsing.substring(ntnMatcher.end()).trim();
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandTextForParsing);

    if (nameAttr == null) {
      nameAttr = attributes.get("name");
      if (nameAttr == null) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid 'template' command missing template name: {template " + cmdText + "}.");
      }
      // Explicit attribute 'name' is only allowed in syntax versions 2.1 and below.
      SyntaxVersionBound newSyntaxVersionBound = new SyntaxVersionBound(
          SyntaxVersion.V2_2,
          String.format(
              "Template name should be written directly instead of within attribute 'name' (i.e." +
                  " use {template %s} instead of {template name=\"%s\"}.",
              nameAttr, nameAttr));
      this.syntaxVersionBound =
          SyntaxVersionBound.selectLower(this.syntaxVersionBound, newSyntaxVersionBound);
    } else {
      if (attributes.get("name") != null) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid 'template' command with template name declared multiple times (" +
            nameAttr + ", " + attributes.get("name") + ").");
      }
    }
    if (BaseUtils.isIdentifierWithLeadingDot(nameAttr)) {
      if (soyFileHeaderInfo.namespace == null) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Missing namespace in Soy file containing 'template' with namespace-relative name" +
                " ({template " + cmdText + "}).");
      }
      setTemplateNames(soyFileHeaderInfo.namespace + nameAttr, nameAttr);
    } else if (BaseUtils.isDottedIdentifier(nameAttr)) {
      SyntaxVersionBound newSyntaxVersionBound = new SyntaxVersionBound(
          SyntaxVersion.V2_0,
          "Soy V2 template names must be relative to the namespace, i.e. a dot followed by an" +
              " identifier.");
      this.syntaxVersionBound =
          SyntaxVersionBound.selectLower(this.syntaxVersionBound, newSyntaxVersionBound);
      setTemplateNames(nameAttr, null);
    } else {
      throw SoySyntaxException.createWithoutMetaInfo("Invalid template name \"" + nameAttr + "\".");
    }

    this.templateNameForUserMsgs = getTemplateName();

    String overrideAttr = attributes.get("override");
    if (overrideAttr == null) {
      this.isOverride = false;
    } else {
      SyntaxVersionBound newSyntaxVersionBound = new SyntaxVersionBound(
          SyntaxVersion.V2_0, "The 'override' attribute in a 'template' tag is a Soy V1 artifact.");
      this.syntaxVersionBound =
          SyntaxVersionBound.selectLower(this.syntaxVersionBound, newSyntaxVersionBound);
      this.isOverride = overrideAttr.equals("true");
    }

    // See go/soy-visibility for why this is considered "legacy private".
    if (attributes.get("private").equals("true")) {
      visibility = Visibility.LEGACY_PRIVATE;
    }

    String visibilityName = attributes.get("visibility");
    if (visibilityName != null) {
      // It is an error to specify both "private" and "visibility" attrs.
      if (visibility != null) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Template cannot specify both private=\"true\""
            + "and visibility=\"" + visibilityName + "\".");
      }
      visibility = Visibility.forAttributeValue(visibilityName);
      if (visibility == null) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid visibility type \"" + visibilityName + "\".");
      }
    }

    // If the visibility hasn't been set, through either the old "private" attr
    // or the new "visibility" attr, default to public.
    if (visibility == null) {
      visibility = Visibility.PUBLIC;
    }

    setAutoescapeCmdText(attributes);
    setRequireCssCmdText(attributes);
    setCssBaseCmdText(attributes);
    return this;
  }

  /**
   * Alternative to {@code setCmdText()} that sets command text info directly as opposed to having
   * it parsed from the command text string. The cmdText field will be set to a canonical string
   * generated from the given info.
   *
   * @param templateName This template's name.
   * @param partialTemplateName This template's partial name. Only applicable for V2; null for V1.
   * @param useAttrStyleForName Whether to use an attribute to specify the name. This is purely
   *     cosmetic for the generated cmdText string.
   * @param isOverride Whether this template overrides another.
   * @param visibility Visibility of this template.
   * @param autoescapeMode The mode of autoescaping for this template.
   * @param contentKind Strict mode context. Nonnull iff autoescapeMode is strict.
   * @param requiredCssNamespaces CSS namespaces required to render the template.
   * @return This builder.
   */
  public TemplateBasicNodeBuilder setCmdTextInfo(
      String templateName, @Nullable String partialTemplateName, boolean useAttrStyleForName,
      boolean isOverride, Visibility visibility, AutoescapeMode autoescapeMode,
      ContentKind contentKind, ImmutableList<String> requiredCssNamespaces) {

    Preconditions.checkState(this.cmdText == null);
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(templateName));
    Preconditions.checkArgument(
        partialTemplateName == null || BaseUtils.isIdentifierWithLeadingDot(partialTemplateName));
    Preconditions.checkArgument((contentKind != null) == (autoescapeMode == AutoescapeMode.STRICT));

    setTemplateNames(templateName, partialTemplateName);
    this.templateNameForUserMsgs = templateName;
    this.isOverride = isOverride;
    this.visibility = visibility;
    setAutoescapeInfo(autoescapeMode, contentKind);
    setRequiredCssNamespaces(requiredCssNamespaces);

    StringBuilder cmdTextBuilder = new StringBuilder();
    String templateNameInCommandText =
        (partialTemplateName != null) ? partialTemplateName : templateName;
    if (useAttrStyleForName) {
      cmdTextBuilder.append("name=\"").append(templateNameInCommandText).append('"');
    } else {
      cmdTextBuilder.append(templateNameInCommandText);
    }
    cmdTextBuilder.append(" autoescape=\"").append(autoescapeMode.getAttributeValue()).append('"');
    if (contentKind != null) {
      cmdTextBuilder.append(" kind=\"" + NodeContentKinds.toAttributeValue(contentKind) + '"');
    }
    if (isOverride) {
      cmdTextBuilder.append(" override=\"true\"");
    }
    if (visibility == Visibility.LEGACY_PRIVATE) {
      // TODO(brndn): generate code for other visibility levels. b/15190131
      cmdTextBuilder.append(" private=\"true\"");
    }
    if (!requiredCssNamespaces.isEmpty()) {
      cmdTextBuilder.append(" requirecss=\"" + Joiner.on(", ").join(requiredCssNamespaces) + "\"");
    }
    this.cmdText = cmdTextBuilder.toString();

    return this;
  }

  @Override public TemplateBasicNodeBuilder setSoyDoc(String soyDoc) {
    return (TemplateBasicNodeBuilder) super.setSoyDoc(soyDoc);
  }

  @Override public TemplateBasicNodeBuilder setHeaderDecls(List<DeclInfo> declInfos) {
    return (TemplateBasicNodeBuilder) super.setHeaderDecls(declInfos);
  }

  @Override public TemplateBasicNode build() {
    Preconditions.checkState(id != null && isSoyDocSet && cmdText != null);
    return new TemplateBasicNode(this, soyFileHeaderInfo, isOverride, visibility, params);
  }
}
