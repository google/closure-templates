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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
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
   * Value class for a delegate template key (name and variant).
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static final class DelTemplateKey {

    public final String name;
    public final String variant;

    public DelTemplateKey(String name, String variant) {
      this.name = name;
      this.variant = variant;
    }

    @Override public boolean equals(Object other) {
      if (! (other instanceof DelTemplateKey)) {
        return false;
      }
      DelTemplateKey otherKey = (DelTemplateKey) other;
      return this.name.equals(otherKey.name) && this.variant.equals(otherKey.variant);
    }

    @Override public int hashCode() {
      return Objects.hashCode(name, variant);
    }

    @Override public String toString() {
      return name + ((variant.length() == 0) ? "" : ":" + variant);
    }
  }


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  private static class CommandTextInfo extends TemplateNode.CommandTextInfo {

    public final String delTemplateName;
    public final String delTemplateVariant;
    public final int delPriority;

    public CommandTextInfo(
        String commandText, String delTemplateName, String delTemplateVariant, int delPriority,
        String templateName, @Nullable String partialTemplateName, AutoescapeMode autoescapeMode,
        ContentKind contentKind) {
      super(
          commandText, templateName, partialTemplateName, false /*deltemplate is never private*/,
          autoescapeMode, contentKind, SyntaxVersion.V2);
      this.delTemplateName = delTemplateName;
      this.delTemplateVariant = delTemplateVariant;
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
          new Attribute("variant", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("autoescape", AutoescapeMode.getAttributeValues(), null),
          new Attribute("kind", NodeContentKinds.getAttributeValues(), null));


  /** The delegate template name. */
  private final String delTemplateName;

  /** The delegate template variant. */
  private final String delTemplateVariant;

  /** The delegate template key (name and variant). */
  private final DelTemplateKey delTemplateKey;

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
   * @param delTemplateVariant The delegate variant for this template.
   * @param delPriority The delegate priority.
   * @param autoescapeMode The kind of autoescaping, if any, done for this template.
   * @param soyDoc The full SoyDoc, including the start/end tokens, or null if the template is not
   *     preceded by SoyDoc.
   */
  public TemplateDelegateNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, String delTemplateName,
      String delTemplateVariant, int delPriority, AutoescapeMode autoescapeMode,
      ContentKind contentKind, @Nullable String soyDoc) {
    this(id, soyFileHeaderInfo,
        buildCommandTextInfoHelper(
            id, soyFileHeaderInfo, null, delTemplateName, delTemplateVariant, delPriority,
            autoescapeMode, contentKind),
        soyDoc);
    Preconditions.checkState(
        (this.getContentKind() != null) == (this.getAutoescapeMode() == AutoescapeMode.STRICT));
  }


  /**
   * Private helper for constructor
   * {@link #TemplateDelegateNode(int, SoyFileHeaderInfo, String, String)}.
   */
  private static final CommandTextInfo parseCommandTextHelper(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, String commandText) {

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
    if (! matcher.matches()) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid 'deltemplate' command text \"" + commandText + "\".");
    }

    String delTemplateName = matcher.group(1);
    if (! BaseUtils.isDottedIdentifier(delTemplateName)) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid delegate template name \"" + delTemplateName + "\".");
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(matcher.group(2).trim());

    int delPriority = soyFileHeaderInfo.defaultDelPriority;
    if (delPriority < 0 || delPriority > TemplateNode.MAX_PRIORITY) {
      throw SoySyntaxException.createWithoutMetaInfo(String.format(
          "Invalid delegate template priority %s (valid range is 0 to %s).",
          delPriority, TemplateNode.MAX_PRIORITY));
    }

    String variantExprText = attributes.get("variant");
    String delTemplateVariant;
    if (variantExprText == null) {
      delTemplateVariant = "";
    } else {
      ExprRootNode<?> variantExpr = ExprParseUtils.parseExprElseThrowSoySyntaxException(
          variantExprText,
          String.format("Invalid variant expression \"%s\" in 'deltemplate'.", variantExprText));
      // For now, the variant expression must be a fixed string. In the future, we have the ability
      // to allow expressions containing compile-time globals, if necessary.
      if (! (variantExpr.getChild(0) instanceof StringNode)) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid variant expression \"" + variantExprText + "\" in 'deltemplate'" +
                " (must be a string literal that contains an identifier).");
      }
      delTemplateVariant = ((StringNode) variantExpr.getChild(0)).getValue();
      if (delTemplateVariant.length() > 0 && ! BaseUtils.isIdentifier(delTemplateVariant)) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid variant \"" + delTemplateVariant + "\" in 'deltemplate'" +
                " (must be an identifier).");
      }
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

    return buildCommandTextInfoHelper(
        id, soyFileHeaderInfo, commandText, delTemplateName, delTemplateVariant, delPriority,
        autoescapeMode, contentKind);
  }


  /**
   * A helper for constructors.
   */
  private static CommandTextInfo buildCommandTextInfoHelper(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, @Nullable String commandText,
      String delTemplateName, String delTemplateVariant, int delPriority,
      AutoescapeMode autoescapeMode, ContentKind contentKind) {
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delTemplateName));
    Preconditions.checkArgument(
        delTemplateVariant.length() == 0 || BaseUtils.isIdentifier(delTemplateVariant));
    Preconditions.checkArgument(0 <= delPriority && delPriority <= TemplateNode.MAX_PRIORITY);

    if (commandText == null) {
      commandText = delTemplateName +
          ((delTemplateVariant.length() == 0) ? "" : " variant=\"" + delTemplateVariant + "\"") +
          " autoescape=\"" + autoescapeMode.getAttributeValue() + "\"";
      if (contentKind != null) {
        commandText += " kind=\"" + NodeContentKinds.toAttributeValue(contentKind) + '"';
      }
    }

    // Compute a SHA-1 hash value for the delPackageName plus delTemplateKey, and take the first 32
    // bits worth as a hex string. This will be included in the generated internal-use template name
    // to prevent collisions in the case where not all Soy files are compiled at once (not really
    // the intended usage of the Soy compiler, but some projects use it this way). Note that the
    // node id is also included in the generated name, which is already sufficient for guaranteeing
    // unique names in the case where all Soy files are compiled together at once.
    String delPackageAndDelTemplateStr =
        (soyFileHeaderInfo.delPackageName == null ? "" : soyFileHeaderInfo.delPackageName) +
            "~" + delTemplateName + "~" + delTemplateVariant;
    String collisionPreventionStr =
        BaseUtils.computePartialSha1AsHexString(delPackageAndDelTemplateStr, 32);

    // Generate the actual internal-use template name.
    String generatedPartialTemplateName = ".__deltemplate_s" + id + "_" + collisionPreventionStr;
    String generatedTemplateName = soyFileHeaderInfo.namespace + generatedPartialTemplateName;

    return new CommandTextInfo(
        commandText, delTemplateName, delTemplateVariant, delPriority, generatedTemplateName,
        generatedPartialTemplateName, autoescapeMode, contentKind);
  }


  /**
   * Private helper constructor used by constructor
   * {@link #TemplateDelegateNode(int, SoyFileHeaderInfo, String, String)}.
   */
  private TemplateDelegateNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, CommandTextInfo commandTextInfo,
      @Nullable String soyDoc) {
    super(id, soyFileHeaderInfo, "deltemplate", commandTextInfo, soyDoc);
    if (soyDoc == null) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Encountered delegate template " + commandTextInfo.delTemplateName + " without SoyDoc.");
    }
    this.delTemplateName = commandTextInfo.delTemplateName;
    this.delTemplateVariant = commandTextInfo.delTemplateVariant;
    this.delTemplateKey = new DelTemplateKey(delTemplateName, delTemplateVariant);
    this.delPriority = commandTextInfo.delPriority;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected TemplateDelegateNode(TemplateDelegateNode orig) {
    super(orig);
    this.delTemplateName = orig.delTemplateName;
    this.delTemplateVariant = orig.delTemplateVariant;
    this.delTemplateKey = orig.delTemplateKey;
    this.delPriority = orig.delPriority;
  }


  @Override public Kind getKind() {
    return Kind.TEMPLATE_DELEGATE_NODE;
  }


  @Override public String getTemplateNameForUserMsgs() {
    return delTemplateKey.toString();
  }


  /** Returns the delegate template name. */
  public String getDelTemplateName() {
    return delTemplateName;
  }


  /** Returns the delegate template variant. */
  public String getDelTemplateVariant() {
    return delTemplateVariant;
  }


  /** Returns the delegate template key (name and variant). */
  public DelTemplateKey getDelTemplateKey() {
    return delTemplateKey;
  }


  /** Returns the delegate priority. */
  public int getDelPriority() {
    return delPriority;
  }


  @Override public TemplateDelegateNode clone() {
    return new TemplateDelegateNode(this);
  }

}
