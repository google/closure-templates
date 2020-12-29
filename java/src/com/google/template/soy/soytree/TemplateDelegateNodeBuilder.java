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
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.TemplateNode.Priority;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Builder for TemplateDelegateNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TemplateDelegateNodeBuilder extends TemplateNodeBuilder<TemplateDelegateNodeBuilder> {

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
  public TemplateDelegateNodeBuilder setCommandValues(
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
          this.delTemplateVariantExpr = attribute.valueAsExpr(errorReporter);
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

    this.delPriority = soyFileHeaderInfo.getPriority();
    genInternalTemplateNameHelper(templateName);
    return this;
  }

  /**
   * Private helper for both setCmdText() and setCmdTextInfo() to generate and set the internal-use
   * partial template name and template name.
   */
  private void genInternalTemplateNameHelper(Identifier originalNameIdentifier) {
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
    String generatedPartialTemplateName =
        partialDeltemplateTemplateName(
            delTemplateName, soyFileHeaderInfo.getDelPackageName(), variant);
    setTemplateNames(
        Identifier.create(
            generatedPartialTemplateName,
            originalNameIdentifier.identifier(),
            originalNameIdentifier.location()),
        soyFileHeaderInfo.getNamespace());
  }

  /** Returns the inferred 'partial' name for a deltemplate. */
  public static String partialDeltemplateTemplateName(
      String delTemplateName, @Nullable String delPackageName, String variant) {
    String delPackageTemplateAndVariantStr =
        (delPackageName == null ? "" : delPackageName)
            + "_"
            + delTemplateName.replace('.', '_')
            + "_"
            + variant;
    delPackageTemplateAndVariantStr = delPackageTemplateAndVariantStr.replace('.', '_');
    // Generate the actual internal-use template name.
    return "__deltemplate_" + delPackageTemplateAndVariantStr;
  }

  public static boolean isDeltemplateTemplateName(String templateName) {
    return templateName.startsWith("__deltemplate_");
  }

  @Override
  public TemplateDelegateNode build() {
    Preconditions.checkState(id != null && cmdText != null);

    return new TemplateDelegateNode(this, soyFileHeaderInfo, delTemplateName, delPriority, params);
  }

  @Override
  protected TemplateDelegateNodeBuilder self() {
    return this;
  }
}
