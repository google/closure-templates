/*
 * Copyright 2018 Google Inc.
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

import static com.google.template.soy.soytree.SoyTreeUtils.getAllNodesOfType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Checks if HTML is printed only from HTML context. */
@RunAfter(FinalizeTemplateRegistryPass.class)
final class CheckBadContextualUsagePass implements CompilerFileSetPass {

  private static final SoyErrorKind CALLS_HTML_FROM_NON_HTML =
      SoyErrorKind.of(
          "Calling HTML templates from non-HTML context is not allowed. You have these options: "
              + "1. Mark the called template with kind=\"text\" or more appropriate kind. "
              + "2. Convert the HTML to plain text by '{let $html kind=\"html\"}{call ...}{/let}"
              + "{htmlToText($html)}', e.g. inside <title>. "
              + "3. Stringify the HTML by '{let $html kind=\"html\"}{call ...}{/let}"
              + "{'''' + $html}', e.g. inside <pre>.");

  private static final SoyErrorKind PRINTS_HTML_FROM_NON_HTML =
      SoyErrorKind.of(
          "Printing HTML from non-HTML context is not allowed. You have these options: "
              + "1. Change the type to non-HTML, e.g. to string or uri. "
              + "2. Convert the HTML to plain text by htmlToText($html), e.g. inside <title>. "
              + "3. Stringify the HTML by outputting '''' + $html, e.g. inside <pre>.");

  private static final SoyErrorKind CALLS_CSS_FROM_NON_CSS =
      SoyErrorKind.of(
          "Calling CSS templates from non-CSS context is not allowed. You likely need to change "
              + "the kind to \"text\".");

  private static final SoyErrorKind PRINTS_CSS_FROM_NON_CSS =
      SoyErrorKind.of(
          "Printing CSS from non-CSS context is not allowed. You likely need to change the type to "
              + "string (kind=\"text\").");

  // TODO(jakubvrana): Move to InferenceEngine and apply for other filter directives.
  private static final SoyErrorKind PRINTS_NON_TRU_FROM_TRU =
      SoyErrorKind.of("In trusted_resource_uri context, only trusted_resource_uri can be printed.");

  private static final SoyErrorKind CALLS_NON_TRU_FROM_TRU =
      SoyErrorKind.of("In trusted_resource_uri context, only trusted_resource_uri can be called.");

  private final ErrorReporter errorReporter;
  private final Supplier<TemplateRegistry> templateRegistryFull;

  CheckBadContextualUsagePass(
      ErrorReporter errorReporter, Supplier<TemplateRegistry> templateRegistryFull) {
    this.errorReporter = errorReporter;
    this.templateRegistryFull = templateRegistryFull;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode fileNode : sourceFiles) {
      for (TemplateNode template : fileNode.getTemplates()) {
        for (CallNode node : getAllNodesOfType(template, CallNode.class)) {
          checkCallNode(node, SanitizedContentKind.HTML, CALLS_HTML_FROM_NON_HTML);
          checkCallNode(node, SanitizedContentKind.CSS, CALLS_CSS_FROM_NON_CSS);
          Optional<SanitizedContentKind> calleeContentKind =
              templateRegistryFull.get().getCallContentKind(node);
          if (isTrustedResourceUri(node.getEscapingDirectives())
              && calleeContentKind.isPresent()
              && calleeContentKind.get() != SanitizedContentKind.TRUSTED_RESOURCE_URI) {
            errorReporter.report(node.getSourceLocation(), CALLS_NON_TRU_FROM_TRU);
          }
        }
        for (PrintNode node : getAllNodesOfType(template, PrintNode.class)) {
          checkPrintNode(node, SanitizedContentKind.HTML, PRINTS_HTML_FROM_NON_HTML);
          checkPrintNode(node, SanitizedContentKind.CSS, PRINTS_CSS_FROM_NON_CSS);
          if (isTrustedResourceUri(
                  Lists.transform(node.getChildren(), PrintDirectiveNode::getPrintDirective))
              && !SanitizedType.TrustedResourceUriType.getInstance()
                  .isAssignableFromLoose(node.getExpr().getType())) {
            errorReporter.report(node.getSourceLocation(), PRINTS_NON_TRU_FROM_TRU);
          }
        }
      }
    }
    return Result.CONTINUE;
  }

  private static final ImmutableMultimap<SanitizedContentKind, HtmlContext> ALLOWED_CONTEXTS =
      ImmutableMultimap.of(
          SanitizedContentKind.HTML, HtmlContext.HTML_PCDATA,
          SanitizedContentKind.HTML, HtmlContext.HTML_HTML_ATTR_VALUE,
          SanitizedContentKind.CSS, HtmlContext.CSS);

  private void checkCallNode(
      CallNode node, SanitizedContentKind contentKind, SoyErrorKind errorKind) {
    if (!ALLOWED_CONTEXTS.containsEntry(contentKind, node.getHtmlContext())) {
      Optional<SanitizedContentKind> calleeContentKind =
          templateRegistryFull.get().getCallContentKind(node);
      if (calleeContentKind.orElse(null) == contentKind) {
        errorReporter.report(node.getSourceLocation(), errorKind);
      }
    }
  }

  private void checkPrintNode(
      PrintNode node, SanitizedContentKind contentKind, SoyErrorKind errorKind) {
    if (!ALLOWED_CONTEXTS.containsEntry(contentKind, node.getHtmlContext())) {
      boolean report;
      ContentKind contentKindOfPrintDirectives = getContentKindOfPrintDirectives(node);
      if (contentKindOfPrintDirectives == null) {
        SoyType type = node.getExpr().getRoot().getType();
        report =
            !type.isAssignableFromStrict(AnyType.getInstance())
                && type.isAssignableFromStrict(SanitizedType.getTypeForContentKind(contentKind));
      } else {
        report = contentKindOfPrintDirectives.name().equals(contentKind.name());
      }
      if (report) {
        errorReporter.report(node.getSourceLocation(), errorKind);
      }
    }
  }

  private static ContentKind getContentKindOfPrintDirectives(PrintNode node) {
    for (PrintDirectiveNode printDirectiveNode : Lists.reverse(node.getChildren())) {
      if (!printDirectiveNode.isSynthetic()) {
        SoyPrintDirective printDirective = printDirectiveNode.getPrintDirective();
        return printDirective instanceof SanitizedContentOperator
            ? ((SanitizedContentOperator) printDirective).getContentKind()
            : ContentKind.TEXT;
      }
    }
    return null;
  }

  private static boolean isTrustedResourceUri(List<SoyPrintDirective> printDirectives) {
    for (SoyPrintDirective printDirectiveNode : Lists.reverse(printDirectives)) {
      if (printDirectiveNode.getName().equals("|filterTrustedResourceUri")) {
        return true;
      }
    }
    return false;
  }
}
