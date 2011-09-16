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
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;


/**
 * Node representing a delegate template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class TemplateDelegateNode extends TemplateNode {


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  private static class CommandTextInfo extends TemplateNode.CommandTextInfo {

    public final String delTemplateName;
    public final int delPriority;

    public CommandTextInfo(
        String commandText, String delTemplateName, int delPriority, String templateName,
        @Nullable String partialTemplateName, AutoescapeMode autoescapeMode
    ) {
      super(
          commandText, templateName, partialTemplateName, false /*deltemplate is never private*/,
          autoescapeMode, SyntaxVersion.V2);
      this.delTemplateName = delTemplateName;
      this.delPriority = delPriority;
    }
  }


  /** Pattern for the command text. */
  // 2 capturing groups: del template name, attributes.
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile("([.\\w]+) ( \\s .* | $ )", Pattern.COMMENTS | Pattern.DOTALL);

  /** Parser for the attributes in command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("deltemplate",
          new Attribute("autoescape", AutoescapeMode.getAttributeValuesAndNull(), null));


  /** The delegate template name. */
  private final String delTemplateName;

  /** The delegate priority. */
  private final int delPriority;


  /**
   * Creates a TemplateDelegateNode given the source command text.
   * @param id The id for this node.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param commandText The command text.
   * @param soyDoc The full SoyDoc, including the start/end tokens, or null if the template is not
   *     preceded by SoyDoc.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public TemplateDelegateNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, String commandText, @Nullable String soyDoc)
      throws SoySyntaxException {
    this(id, soyFileHeaderInfo, parseCommandTextHelper(id, soyFileHeaderInfo, commandText), soyDoc);
  }


  /**
   * Creates a TemplateDelegateNode given values for fields.
   * @param id The id for this node.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param delTemplateName The delegate name for this template.
   * @param delPriority The delegate priority.
   * @param autoescapeMode The kind of autoescaping, if any, done for this template.
   * @param soyDoc The full SoyDoc, including the start/end tokens, or null if the template is not
   *     preceded by SoyDoc.
   */
  public TemplateDelegateNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, String delTemplateName,
      int delPriority, AutoescapeMode autoescapeMode, @Nullable String soyDoc) {
    this(id, soyFileHeaderInfo,
        buildCommandTextInfoHelper(
            id, soyFileHeaderInfo, null, delTemplateName, delPriority, autoescapeMode),
        soyDoc);
  }

  /**
   * Private helper for constructor
   * {@link #TemplateDelegateNode(int, SoyFileHeaderInfo, String, String)}.
   */
  private static final CommandTextInfo parseCommandTextHelper(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, String commandText) {

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
    if (! matcher.matches()) {
      throw new SoySyntaxException("Invalid 'deltemplate' command text \"" + commandText + "\".");
    }

    String delTemplateName = matcher.group(1);
    if (! BaseUtils.isDottedIdentifier(delTemplateName)) {
      throw new SoySyntaxException("Invalid delegate template name \"" + delTemplateName + "\".");
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(matcher.group(2).trim());

    int delPriority = soyFileHeaderInfo.defaultDelPriority;
    if (delPriority < 0 || delPriority > TemplateNode.MAX_PRIORITY) {
      throw new SoySyntaxException(String.format(
          "Invalid delegate template priority %s (valid range is 0 to %s).",
          delPriority, TemplateNode.MAX_PRIORITY));
    }

    AutoescapeMode autoescapeMode;
    String autoescapeModeStr = attributes.get("autoescape");
    if (autoescapeModeStr != null) {
      autoescapeMode = AutoescapeMode.forAttributeValue(autoescapeModeStr);
    } else {
      autoescapeMode = soyFileHeaderInfo.defaultAutoescapeMode;  // Inherit from containing file.
    }

    return buildCommandTextInfoHelper(
        id, soyFileHeaderInfo, commandText, delTemplateName, delPriority, autoescapeMode);
  }


  /**
   * A helper for constructors.
   */
  private static CommandTextInfo buildCommandTextInfoHelper(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, @Nullable String commandText,
      String delTemplateName, int delPriority, AutoescapeMode autoescapeMode) {
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delTemplateName));
    Preconditions.checkArgument(0 <= delPriority && delPriority <= TemplateNode.MAX_PRIORITY);

    if (commandText == null) {
      commandText = delTemplateName + " autoescape=\"" + autoescapeMode.getAttributeValue() + "\"";
    }

    // Generate the actual internal-use template name.
    String generatedPartialTemplateName = ".__soy_deltemplate" + id;
    String generatedTemplateName = soyFileHeaderInfo.namespace + generatedPartialTemplateName;

    return new CommandTextInfo(
        commandText, delTemplateName, delPriority, generatedTemplateName,
        generatedPartialTemplateName, autoescapeMode);
  }


  /**
   * Private helper constructor used by constructor
   * {@link #TemplateDelegateNode(int, SoyFileHeaderInfo, String, String)}.
   */
  private TemplateDelegateNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, CommandTextInfo commandTextInfo,
      @Nullable String soyDoc) {
    super(id, soyFileHeaderInfo, "deltemplate", commandTextInfo, soyDoc);
    this.delTemplateName = commandTextInfo.delTemplateName;
    this.delPriority = commandTextInfo.delPriority;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected TemplateDelegateNode(TemplateDelegateNode orig) {
    super(orig);
    this.delTemplateName = orig.delTemplateName;
    this.delPriority = orig.delPriority;
  }


  @Override public Kind getKind() {
    return Kind.TEMPLATE_DELEGATE_NODE;
  }


  @Override public String getTemplateNameForUserMsgs() {
    return delTemplateName;
  }


  /** Returns the delegate template name. */
  public String getDelTemplateName() {
    return delTemplateName;
  }


  /** Returns the delegate priority. */
  public int getDelPriority() {
    return delPriority;
  }


  @Override public TemplateDelegateNode clone() {
    return new TemplateDelegateNode(this);
  }

}
