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
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.TemplateDelegateNode.DelTemplateKey;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.types.SoyTypeRegistry;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Builder for TemplateDelegateNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TemplateDelegateNodeBuilder extends TemplateNodeBuilder {

  /** Pattern for the command text. */
  // 2 capturing groups: del template name, attributes.
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile("([.\\w]+) ( \\s .* | $ )", Pattern.COMMENTS | Pattern.DOTALL);

  /** Parser for the attributes in command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("deltemplate",
          new Attribute("variant", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("autoescape", AutoescapeMode.getAttributeValues(), null),
          new Attribute("kind", NodeContentKinds.getAttributeValues(), null),
          new Attribute("requirecss", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("cssbase", Attribute.ALLOW_ALL_VALUES, null));

  /** The delegate template name. */
  private String delTemplateName;

  /** Value of a delegate template variant. */
  private String delTemplateVariant = null;

  /** Expression that will evaluate to the value of a delegate template variant. */
  private ExprRootNode<?> delTemplateVariantExpr = null;

  /** The delegate template key (name and variant). */
  private DelTemplateKey delTemplateKey;

  /** The delegate priority. */
  private int delPriority;

  /**
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   */
  public TemplateDelegateNodeBuilder(SoyFileHeaderInfo soyFileHeaderInfo) {
    super(soyFileHeaderInfo, null);
  }

  /**
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   */
  public TemplateDelegateNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo, SoyTypeRegistry typeRegistry) {
    super(soyFileHeaderInfo, typeRegistry);
  }

  @Override public TemplateDelegateNodeBuilder setId(int id) {
    return (TemplateDelegateNodeBuilder) super.setId(id);
  }

  @Override public TemplateDelegateNodeBuilder setCmdText(String cmdText) {
    Preconditions.checkState(this.cmdText == null);
    this.cmdText = cmdText;

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(cmdText);
    if (! matcher.matches()) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid 'deltemplate' command text \"" + cmdText + "\".");
    }

    this.delTemplateName = matcher.group(1);
    if (! BaseUtils.isDottedIdentifier(delTemplateName)) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid delegate template name \"" + delTemplateName + "\".");
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(matcher.group(2).trim());

    String variantExprText = attributes.get("variant");
    if (variantExprText == null) {
      this.delTemplateVariant = "";
    } else {
      ExprRootNode<?> variantExpr = ExprParseUtils.parseExprElseThrowSoySyntaxException(
          variantExprText,
          String.format("Invalid variant expression \"%s\" in 'deltemplate'.", variantExprText));
      ExprNode child = variantExpr.getChild(0);
      if (child instanceof StringNode) {
        // A string literal is being used as template variant, so the expression value can
        // immediately be evaluated.
        this.delTemplateVariant = ((StringNode) child).getValue();
        TemplateDelegateNode.verifyVariantName(delTemplateVariant);
      } else if (child instanceof GlobalNode) {
        // A global expression was used as template variant. The expression will be stored and later
        // resolved into a value when the global expressions are resolved.
        delTemplateVariantExpr = variantExpr;
        this.templateNameForUserMsgs = delTemplateName + ":" + (((GlobalNode) child).getName());
      } else {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid variant expression \"" + variantExprText + "\" in 'deltemplate'" +
                " (must be a string literal that contains an identifier or an integer global).");
      }
    }

    if (delTemplateVariant != null) {
      // The variant value is already available (i.e. we either have a string literal or no variant
      // was defined). In these cases we can already define a template key.
      this.delTemplateKey = new DelTemplateKey(delTemplateName, delTemplateVariant);
      this.templateNameForUserMsgs = delTemplateKey.toString();
    }

    this.delPriority = soyFileHeaderInfo.defaultDelPriority;
    if (delPriority < 0 || delPriority > TemplateNode.MAX_PRIORITY) {
      throw SoySyntaxException.createWithoutMetaInfo(String.format(
          "Invalid delegate template priority %s (valid range is 0 to %s).",
          delPriority, TemplateNode.MAX_PRIORITY));
    }

    setAutoescapeCmdText(attributes);
    setRequireCssCmdText(attributes);
    setCssBaseCmdText(attributes);

    genInternalTemplateNameHelper();

    return this;
  }

  /**
   * Alternative to {@code setCmdText()} that sets command text info directly as opposed to having
   * it parsed from the command text string. The cmdText field will be set to a canonical string
   * generated from the given info.
   *
   * @param delTemplateName The delegate template name.
   * @param delTemplateVariant The delegate template variant.
   * @param delPriority The delegate priority.
   * @param autoescapeMode The mode of autoescaping for this template.
   * @param contentKind Strict mode context. Nonnull iff autoescapeMode is strict.
   * @param requiredCssNamespaces CSS namespaces required to render the template.
   * @return This builder.
   */
  public TemplateDelegateNodeBuilder setCmdTextInfo(
      String delTemplateName, String delTemplateVariant, int delPriority,
      AutoescapeMode autoescapeMode, ContentKind contentKind,
      ImmutableList<String> requiredCssNamespaces) {

    Preconditions.checkState(this.cmdText == null);
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delTemplateName));
    Preconditions.checkArgument(
        delTemplateVariant.length() == 0 || BaseUtils.isIdentifier(delTemplateVariant));
    Preconditions.checkArgument(0 <= delPriority && delPriority <= TemplateNode.MAX_PRIORITY);
    Preconditions.checkArgument((contentKind != null) == (autoescapeMode == AutoescapeMode.STRICT));

    this.delTemplateName = delTemplateName;
    this.delTemplateVariant = delTemplateVariant;
    this.delTemplateKey = new DelTemplateKey(delTemplateName, delTemplateVariant);
    this.templateNameForUserMsgs = delTemplateKey.toString();
    this.delPriority = delPriority;
    setAutoescapeInfo(autoescapeMode, contentKind);
    setRequiredCssNamespaces(requiredCssNamespaces);

    String cmdText = delTemplateName +
        ((delTemplateVariant.length() == 0) ? "" : " variant=\"" + delTemplateVariant + "\"") +
        " autoescape=\"" + autoescapeMode.getAttributeValue() + "\"";
    if (contentKind != null) {
      cmdText += " kind=\"" + NodeContentKinds.toAttributeValue(contentKind) + '"';
    }
    if (!requiredCssNamespaces.isEmpty()) {
      cmdText += " requirecss=\"" + Joiner.on(", ").join(requiredCssNamespaces) + "\"";
    }
    this.cmdText = cmdText;

    genInternalTemplateNameHelper();

    return this;
  }

  /**
   * Private helper for both setCmdText() and setCmdTextInfo() to generate and set the internal-use
   * partial template name and template name.
   */
  private void genInternalTemplateNameHelper() {
    Preconditions.checkState(id != null);

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
    setTemplateNames(generatedTemplateName, generatedPartialTemplateName);
  }

  @Override public TemplateDelegateNodeBuilder setSoyDoc(String soyDoc) {
    if (soyDoc == null) {
      throw SoySyntaxException.createWithoutMetaInfo(
          (delTemplateName != null) ?
              "Encountered delegate template " + delTemplateName + " without SoyDoc." :
              "Encountered delegate template without SoyDoc.");
    }
    return (TemplateDelegateNodeBuilder) super.setSoyDoc(soyDoc);
  }

  @Override public TemplateDelegateNodeBuilder setHeaderDecls(List<DeclInfo> declInfos) {
    return (TemplateDelegateNodeBuilder) super.setHeaderDecls(declInfos);
  }

  @Override public TemplateDelegateNode build() {
    Preconditions.checkState(id != null && isSoyDocSet && cmdText != null);

    return new TemplateDelegateNode(
        this, soyFileHeaderInfo, delTemplateName, delTemplateVariant,
        delTemplateVariantExpr, delTemplateKey, delPriority,
        params);
  }
}
