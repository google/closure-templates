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
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.TemplateNode.Priority;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.List;

/**
 * Builder for TemplateDelegateNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TemplateDelegateNodeBuilder extends TemplateNodeBuilder {
  private static final SoyErrorKind INVALID_VARIANT_STRING =
      SoyErrorKind.of("Invalid variant ''{0}'' value must be an identifier.");
  private static final SoyErrorKind INVALID_VARIANT_INTEGER =
      SoyErrorKind.of("Invalid variant ''{0}'' value must non-negative.");

  private static final SoyErrorKind INVALID_VARIANT_EXPR =
      SoyErrorKind.of(
          "Invalid variant expression "
              + "(must be a string literal containing an identifier or global expression).");

  /** The delegate template name. */
  private String delTemplateName;

  /** Expression that will evaluate to the value of a delegate template variant. */
  private ExprRootNode delTemplateVariantExpr;

  /** The delegate priority. */
  private Priority delPriority;

  /** @param soyFileHeaderInfo Info from the containing Soy file's header declarations. */
  public TemplateDelegateNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo, ErrorReporter errorReporter) {
    super(soyFileHeaderInfo, errorReporter);
  }

  @Override
  public TemplateDelegateNodeBuilder setId(int id) {
    return (TemplateDelegateNodeBuilder) super.setId(id);
  }

  @Override
  public TemplateDelegateNodeBuilder setSourceLocation(SourceLocation location) {
    return (TemplateDelegateNodeBuilder) super.setSourceLocation(location);
  }

  @Override
  public TemplateNodeBuilder setCommandValues(
      Identifier templateName, List<CommandTagAttribute> attrs) {
    this.cmdText = templateName.identifier() + " " + Joiner.on(' ').join(attrs);
    setCommonCommandValues(attrs);

    this.delTemplateName = templateName.identifier();
    for (CommandTagAttribute attribute : attrs) {
      Identifier name = attribute.getName();
      if (COMMON_ATTRIBUTE_NAMES.contains(name.identifier())) {
        continue;
      }
      switch (name.identifier()) {
        case "variant":
          // need to get variant parsing out of this.  maybe we can expose some sort of limited
          // primitiveOrGlobal parsing solution?
          ExprNode variantExpr = attribute.valueAsExpr(errorReporter);
          validateVariantExpression(variantExpr, errorReporter);
          this.delTemplateVariantExpr = new ExprRootNode(variantExpr);
          break;
        default:
          errorReporter.report(
              name.location(),
              CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY,
              name.identifier(),
              "deltemplate",
              ImmutableList.builder().addAll(COMMON_ATTRIBUTE_NAMES).add("variant").build());
      }
    }

    this.delPriority = soyFileHeaderInfo.priority;
    genInternalTemplateNameHelper();
    return this;
  }

  private static void validateVariantExpression(
      ExprNode primitiveNode, final ErrorReporter reporter) {
    switch (primitiveNode.getKind()) {
      case STRING_NODE:
        StringNode sn = (StringNode) primitiveNode;
        if (sn.getValue().length() > 0 && !(BaseUtils.isIdentifier(sn.getValue()))) {
          reporter.report(sn.getSourceLocation(), INVALID_VARIANT_STRING, sn.getValue());
        }
        break;
      case INTEGER_NODE:
        IntegerNode in = (IntegerNode) primitiveNode;
        if (in.getValue() < 0) {
          reporter.report(in.getSourceLocation(), INVALID_VARIANT_INTEGER, in.getValue());
        }
        break;
      case GLOBAL_NODE:
        GlobalNode gn = (GlobalNode) primitiveNode;
        if (gn.isResolved()) {
          validateVariantExpression(gn.getValue(), reporter);
        } else {
          gn.onResolve(
              new GlobalNode.ResolutionCallback() {
                @Override
                public void onResolve(PrimitiveNode value) {
                  validateVariantExpression(value, reporter);
                }
              });
        }
        break;
      default:
        reporter.report(primitiveNode.getSourceLocation(), INVALID_VARIANT_EXPR);
    }
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
      String delTemplateName,
      String delTemplateVariant,
      Priority delPriority,
      AutoescapeMode autoescapeMode,
      SanitizedContentKind contentKind,
      ImmutableList<String> requiredCssNamespaces) {

    Preconditions.checkState(this.sourceLocation != null);
    Preconditions.checkState(this.cmdText == null);
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delTemplateName));
    Preconditions.checkArgument(
        delTemplateVariant.length() == 0 || BaseUtils.isIdentifier(delTemplateVariant));
    Preconditions.checkArgument((contentKind != null) == (autoescapeMode == AutoescapeMode.STRICT));

    this.delTemplateName = delTemplateName;
    this.delTemplateVariantExpr =
        new ExprRootNode(
            new StringNode(delTemplateVariant, QuoteStyle.SINGLE, this.sourceLocation));
    this.delPriority = delPriority;
    setAutoescapeInfo(autoescapeMode, contentKind, sourceLocation);
    setRequiredCssNamespaces(requiredCssNamespaces);
    String cmdText =
        delTemplateName
            + ((delTemplateVariant.length() == 0) ? "" : " variant=\"" + delTemplateVariant + "\"")
            + " autoescape=\""
            + autoescapeMode.getAttributeValue()
            + "\"";
    if (contentKind != null) {
      cmdText += " kind=\"" + contentKind.asAttributeValue() + '"';
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
    // encode all the deltemplate information into the name to get a unique string
    // though... it might make more sense for this to not have a user visible name given that the
    // calling convention is indirect.
    String variant = "";
    if (delTemplateVariantExpr != null) {
      // this is hacky.  perhaps we should come up with a less ambiguous strategy
      ExprNode expr = delTemplateVariantExpr.getRoot();
      if (expr instanceof StringNode) {
        variant = ((StringNode) expr).getValue();
      } else {
        variant = expr.toSourceString();
      }
    }
    String delPackageTemplateAndVariantStr =
        (soyFileHeaderInfo.delPackageName == null ? "" : soyFileHeaderInfo.delPackageName)
            + "_"
            + delTemplateName.replace('.', '_')
            + "_"
            + variant;
    delPackageTemplateAndVariantStr = delPackageTemplateAndVariantStr.replace('.', '_');
    // Generate the actual internal-use template name.
    String generatedPartialTemplateName = ".__deltemplate_" + delPackageTemplateAndVariantStr;
    String generatedTemplateName = soyFileHeaderInfo.namespace + generatedPartialTemplateName;
    setTemplateNames(generatedTemplateName, generatedPartialTemplateName);
  }

  @Override
  public TemplateDelegateNodeBuilder setSoyDoc(String soyDoc, SourceLocation soyDocLocation) {
    return (TemplateDelegateNodeBuilder) super.setSoyDoc(soyDoc, soyDocLocation);
  }

  @Override
  public TemplateDelegateNodeBuilder addParams(Iterable<? extends TemplateParam> allParams) {
    return (TemplateDelegateNodeBuilder) super.addParams(allParams);
  }

  @Override
  public TemplateDelegateNode build() {
    Preconditions.checkState(id != null && cmdText != null);

    return new TemplateDelegateNode(
        this,
        soyFileHeaderInfo,
        delTemplateName,
        delTemplateVariantExpr,
        delPriority,
        params);
  }
}
