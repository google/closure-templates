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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;


/**
 * Node representing a basic template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class TemplateBasicNode extends TemplateNode {


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  private static class CommandTextInfo extends TemplateNode.CommandTextInfo {

    public final boolean isOverride;

    public CommandTextInfo(
        String commandText, String templateName, @Nullable String partialTemplateName,
        boolean isOverride, boolean isPrivate, AutoescapeMode autoescapeMode,
        ContentKind contentKind, SyntaxVersion syntaxVersion) {
      super(commandText, templateName, partialTemplateName, isPrivate, autoescapeMode,
          contentKind, syntaxVersion);
      this.isOverride = isOverride;
    }
  }


  /** Pattern for a template name not listed as an attribute name="...". */
  private static final Pattern NONATTRIBUTE_TEMPLATE_NAME =
      Pattern.compile("^ (?! name=\") [.\\w]+ (?= \\s | $)", Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("template",
          new Attribute("name", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("private", Attribute.BOOLEAN_VALUES, "false"),
          new Attribute("override", Attribute.BOOLEAN_VALUES, null),  // V1
          new Attribute("autoescape", AutoescapeMode.getAttributeValues(), null),
          new Attribute("kind", NodeContentKinds.getAttributeValues(), null));


  /** Whether this template overrides another (always false for syntax version V2). */
  private final boolean isOverride;


  /**
   * Creates a TemplateNode given the source command text.
   * @param id The id for this node.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param commandText The command text.
   * @param soyDoc The full SoyDoc, including the start/end tokens, or null if the template is not
   *     preceded by SoyDoc.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public TemplateBasicNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, String commandText, @Nullable String soyDoc)
      throws SoySyntaxException {
    this(id, soyFileHeaderInfo, parseCommandTextHelper(soyFileHeaderInfo, commandText), soyDoc);
  }


  /**
   * Private helper for constructor
   * {@link #TemplateBasicNode(int, SoyFileHeaderInfo, String, String)}.
   */
  private static final CommandTextInfo parseCommandTextHelper(
      SoyFileHeaderInfo soyFileHeaderInfo, String commandText) {

    SyntaxVersion syntaxVersion = SyntaxVersion.V2;

    String commandTextForParsing = commandText;

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
            "Invalid 'template' command missing template name: {template " + commandText + "}.");
      }
    } else {
      if (attributes.get("name") != null) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid 'template' command with template name declared multiple times (" +
            nameAttr + ", " + attributes.get("name") + ").");
      }
    }
    String templateName;
    String partialTemplateName;
    if (BaseUtils.isIdentifierWithLeadingDot(nameAttr)) {
      if (soyFileHeaderInfo.namespace == null) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Missing namespace in Soy file containing 'template' with namespace-relative name" +
                " ({template " + commandText + "}).");
      }
      partialTemplateName = nameAttr;
      templateName = soyFileHeaderInfo.namespace + partialTemplateName;
    } else if (BaseUtils.isDottedIdentifier(nameAttr)) {
      syntaxVersion = SyntaxVersion.V1;
      templateName = nameAttr;
      partialTemplateName = null;
    } else {
      throw SoySyntaxException.createWithoutMetaInfo("Invalid template name \"" + nameAttr + "\".");
    }

    boolean isPrivate = attributes.get("private").equals("true");

    boolean isOverride;
    String overrideAttr = attributes.get("override");
    if (overrideAttr == null) {
      isOverride = false;
    } else {
      syntaxVersion = SyntaxVersion.V1;
      isOverride = overrideAttr.equals("true");
    }

    AutoescapeMode autoescapeMode;
    String autoescapeModeStr = attributes.get("autoescape");
    if (autoescapeModeStr != null) {
      autoescapeMode = AutoescapeMode.forAttributeValue(autoescapeModeStr);
    } else {
      autoescapeMode = soyFileHeaderInfo.defaultAutoescapeMode;  // Inherit from containing file.
    }

    ContentKind contentKind = (attributes.get("kind") != null) ?
        NodeContentKinds.forAttributeValue(attributes.get("kind")) : null;

    return new CommandTextInfo(
        commandText, templateName, partialTemplateName, isOverride, isPrivate, autoescapeMode,
        contentKind, syntaxVersion);
  }


  /**
   * Creates a TemplateNode given values for fields.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param templateName The full template name.
   * @param partialTemplateName The template name without any namespace, or null for templates in
   *     V1 syntax.
   * @param useAttrStyleForName true to use an attribute to specify the name.
   *     This is purely cosmetic.
   * @param isOverride True iff the template overrides another (false for V2).
   * @param isPrivate True iff the template can only be used by other templates from the same file
   * @param autoescapeMode Specifies how <code>{print}</code> commands are escaped.
   */
  public TemplateBasicNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, String templateName,
      @Nullable String partialTemplateName, boolean useAttrStyleForName, boolean isOverride,
      boolean isPrivate, AutoescapeMode autoescapeMode, ContentKind contentKind,
      @Nullable String soyDoc, SyntaxVersion syntaxVersion) {
    this(
        id, soyFileHeaderInfo,
        buildCommandTextInfoHelper(
            templateName, partialTemplateName, useAttrStyleForName, isOverride, isPrivate,
            autoescapeMode, contentKind, syntaxVersion),
        soyDoc);
  }


  /**
   * Private helper for constructor
   * {@link #TemplateBasicNode(
   *     int, SoyFileHeaderInfo, String, String, boolean, boolean, boolean, AutoescapeMode,
   *     ContentKind, String, SyntaxVersion)}.
   */
  private static final CommandTextInfo buildCommandTextInfoHelper(
      String templateName, @Nullable String partialTemplateName, boolean useAttrStyleForName,
      boolean isOverride, boolean isPrivate, AutoescapeMode autoescapeMode, ContentKind contentKind,
      SyntaxVersion syntaxVersion) {

    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(templateName));
    Preconditions.checkArgument(
        partialTemplateName == null || BaseUtils.isIdentifierWithLeadingDot(partialTemplateName));
    Preconditions.checkArgument((contentKind != null) == (autoescapeMode == AutoescapeMode.STRICT));

    StringBuilder commandText = new StringBuilder();
    String templateNameInCommandText =
        (partialTemplateName != null) ? partialTemplateName : templateName;
    if (useAttrStyleForName) {
      commandText.append("name=\"").append(templateNameInCommandText).append('"');
    } else {
      commandText.append(templateNameInCommandText);
    }
    commandText.append(" autoescape=\"").append(autoescapeMode.getAttributeValue()).append('"');
    if (contentKind != null) {
      commandText.append(" kind=\"" + NodeContentKinds.toAttributeValue(contentKind) + '"');
    }
    if (isOverride) {
      commandText.append(" override=\"true\"");
    }
    if (isPrivate) {
      commandText.append(" private=\"true\"");
    }

    return new CommandTextInfo(
        commandText.toString(), templateName, partialTemplateName, isOverride, isPrivate,
        autoescapeMode, contentKind, syntaxVersion);
  }


  /**
   * Private helper constructor used by both of the constructors
   * {@link #TemplateBasicNode(int, SoyFileHeaderInfo, String, String)} and
   * {@link #TemplateBasicNode(
   *     int, SoyFileHeaderInfo, String, String, boolean, boolean, boolean, AutoescapeMode,
   *     ContentKind, String, SyntaxVersion)}.
   */
  private TemplateBasicNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, CommandTextInfo commandTextInfo,
      @Nullable String soyDoc) {
    super(id, soyFileHeaderInfo, "template", commandTextInfo, soyDoc);
    this.isOverride = commandTextInfo.isOverride;
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


  @Override public String getTemplateNameForUserMsgs() {
    return getTemplateName();
  }


  /** Returns whether this template overrides another (always false for syntax version V2). */
  public boolean isOverride() {
    return isOverride;
  }


  @Override public TemplateBasicNode clone() {
    return new TemplateBasicNode(this);
  }

}
