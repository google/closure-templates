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

package com.google.template.soy.passes;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Checks various rules regarding the use of delegates (including delegate packages, delegate
 * templates, and delegate calls).
 *
 */
final class CheckDelegatesPass extends CompilerFileSetPass {

  private static final SoyErrorKind CALL_TO_DELTEMPLATE =
      SoyErrorKind.of("''call'' to delegate template ''{0}'' (expected ''delcall'').");
  private static final SoyErrorKind CROSS_PACKAGE_DELCALL =
      SoyErrorKind.of(
          "Found illegal call from ''{0}'' to ''{1}'', which is in a different delegate package.");
  private static final SoyErrorKind DELCALL_TO_BASIC_TEMPLATE =
      SoyErrorKind.of("''delcall'' to basic template ''{0}'' (expected ''call'').");
  private static final SoyErrorKind DELTEMPLATES_WITH_DIFFERENT_PARAM_DECLARATIONS =
      SoyErrorKind.of(
          "Found delegate template with same name ''{0}'' but different param declarations "
              + "compared to the definition at {1}.");
  private static final SoyErrorKind STRICT_DELTEMPLATES_WITH_DIFFERENT_CONTENT_KIND =
      SoyErrorKind.of(
          "If one deltemplate has strict autoescaping, all its peers must also be strictly"
              + " autoescaped with the same content kind: {0} != {1}. Conflicting definition at"
              + " {2}.");
  private static final SoyErrorKind DELTEMPLATES_WITH_DIFFERENT_STRICT_HTML_MODE =
      SoyErrorKind.of(
          "Found delegate template with same name ''{0}'' but different strict html mode "
              + "compared to the definition at {1}.");

  private final ErrorReporter errorReporter;

  CheckDelegatesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    // Perform checks that only involve templates (uses templateRegistry only, no traversal).
    checkTemplates(registry);

    for (SoyFileNode fileNode : sourceFiles) {
      for (TemplateNode template : fileNode.getChildren()) {
        String currTemplateNameForUserMsgs = template.getTemplateNameForUserMsgs();
        String currDelPackageName = template.getDelPackageName();
        for (CallBasicNode callNode :
            SoyTreeUtils.getAllNodesOfType(template, CallBasicNode.class)) {
          checkCallBasicNode(callNode, registry, currDelPackageName, currTemplateNameForUserMsgs);
        }
        for (CallDelegateNode callNode :
            SoyTreeUtils.getAllNodesOfType(template, CallDelegateNode.class)) {
          checkCallDelegateNode(callNode, registry);
        }
      }
    }
    return Result.CONTINUE;
  }

  /** Performs checks that only involve templates (uses templateRegistry only). */
  private void checkTemplates(TemplateRegistry templateRegistry) {

    DelTemplateSelector<TemplateMetadata> selector = templateRegistry.getDelTemplateSelector();

    // Check that all delegate templates with the same name have the same declared params,
    // content kind, and strict html mode.
    for (Collection<TemplateMetadata> delTemplateGroup :
        selector.delTemplateNameToValues().asMap().values()) {
      TemplateMetadata firstDelTemplate = null;
      // loop over all members of the deltemplate group looking for a source template.
      for (TemplateMetadata delTemplate : delTemplateGroup) {
        if (firstDelTemplate == null) {
          firstDelTemplate = delTemplate;
        }
        // preferentially use a source template
        if (delTemplate.getSoyFileKind() == SoyFileKind.SRC) {
          firstDelTemplate = delTemplate;
          break;
        }
      }
      if (firstDelTemplate == null) {
        // group must be empty
        continue;
      }
      Set<TemplateMetadata.Parameter> firstRequiredParamSet = getRequiredParamSet(firstDelTemplate);
      SanitizedContentKind firstContentKind = firstDelTemplate.getContentKind();
      boolean firstStrictHtml =
          firstDelTemplate.isStrictHtml() && firstContentKind == SanitizedContentKind.HTML;
      // loop over all members of the deltemplate group.
      for (TemplateMetadata delTemplate : delTemplateGroup) {
        if (firstDelTemplate == delTemplate) {
          continue; // skip
        }
        // Not first template encountered.
        Set<TemplateMetadata.Parameter> currRequiredParamSet = getRequiredParamSet(delTemplate);
        if (!paramSetsEqual(currRequiredParamSet, firstRequiredParamSet)) {
          errorReporter.report(
              firstDelTemplate.getSourceLocation(),
              DELTEMPLATES_WITH_DIFFERENT_PARAM_DECLARATIONS,
              delTemplate.getDelTemplateName(),
              delTemplate.getSourceLocation().toString());
        }
        if (delTemplate.getContentKind() != firstContentKind) {
          // TODO: This is only *truly* a requirement if the strict mode deltemplates are
          // being called by contextual templates. For a strict-to-strict call, everything
          // is escaped at runtime at the call sites. You could imagine delegating between
          // either a plain-text or rich-html template. However, most developers will write
          // their deltemplates in a parallel manner, and will want to know when the
          // templates differ. Plus, requiring them all to be the same early-on will allow
          // future optimizations to avoid the run-time checks, so it's better to start out
          // as strict as possible and only open up if needed.
          errorReporter.report(
              firstDelTemplate.getSourceLocation(),
              STRICT_DELTEMPLATES_WITH_DIFFERENT_CONTENT_KIND,
              String.valueOf(delTemplate.getContentKind()),
              String.valueOf(firstContentKind),
              delTemplate.getSourceLocation().toString());
        }
        // Check if all del templates have the same settings of strict HTML mode.
        // We do not need to check {@code ContentKind} again since we already did that earlier
        // in this pass.
        if (delTemplate.isStrictHtml() != firstStrictHtml) {
          errorReporter.report(
              firstDelTemplate.getSourceLocation(),
              DELTEMPLATES_WITH_DIFFERENT_STRICT_HTML_MODE,
              delTemplate.getDelTemplateName(),
              delTemplate.getSourceLocation().toString());
        }
      }
    }
  }

  private static boolean paramSetsEqual(
      Set<TemplateMetadata.Parameter> s1, Set<TemplateMetadata.Parameter> s2) {
    // We can use Set equality because we normalize parameters with toComparable().
    return s1.equals(s2);
  }

  private static Set<TemplateMetadata.Parameter> getRequiredParamSet(TemplateMetadata delTemplate) {
    return delTemplate.getParameters().stream()
        .filter(TemplateMetadata.Parameter::isRequired)
        .map(TemplateMetadata.Parameter::toComparable)
        .collect(Collectors.toSet());
  }

  private void checkCallBasicNode(
      CallBasicNode node,
      TemplateRegistry templateRegistry,
      @Nullable String currDelPackageName,
      String currTemplateNameForUserMsgs) {

    String calleeName = node.getCalleeName();

    // Check that the callee name is not a delegate template name.
    if (templateRegistry.getDelTemplateSelector().hasDelTemplateNamed(calleeName)) {
      errorReporter.report(node.getSourceLocation(), CALL_TO_DELTEMPLATE, calleeName);
    }

    // Check that the callee is either not in a delegate package or in the same delegate package.
    TemplateMetadata callee = templateRegistry.getBasicTemplateOrElement(calleeName);
    if (callee != null) {
      String calleeDelPackageName = callee.getDelPackageName();
      if (calleeDelPackageName != null && !calleeDelPackageName.equals(currDelPackageName)) {
        if (node.getNearestAncestor(CallParamContentNode.class) == null) {
          errorReporter.report(
              node.getSourceLocation(),
              CROSS_PACKAGE_DELCALL,
              currTemplateNameForUserMsgs,
              callee.getTemplateName());
        } else {
          // downgrade to a warning for backwards compatibility reasons.  This pass used to have a
          // bug where it failed to inspect CallParamContentNode and thus missed a number of call
          // sites...and people depend on it.
          // luckily this particular error doesn't seem very important. it doesn't violate Soy's
          errorReporter.warn(
              node.getSourceLocation(),
              CROSS_PACKAGE_DELCALL,
              currTemplateNameForUserMsgs,
              callee.getTemplateName());
        }
      }
    }
  }

  private void checkCallDelegateNode(CallDelegateNode node, TemplateRegistry templateRegistry) {

    String delCalleeName = node.getDelCalleeName();

    // Check that the callee name is not a basic template name.
    if (templateRegistry.getBasicTemplateOrElement(delCalleeName) != null) {
      errorReporter.report(node.getSourceLocation(), DELCALL_TO_BASIC_TEMPLATE, delCalleeName);
    }
  }
}
