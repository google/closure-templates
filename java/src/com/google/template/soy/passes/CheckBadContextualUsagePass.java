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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Lists;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnknownType;

/** Checks if HTML is printed only from HTML context. */
final class CheckBadContextualUsagePass extends CompilerFileSetPass {

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

  private final ErrorReporter errorReporter;

  CheckBadContextualUsagePass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    ImmutableListMultimap<String, TemplateDelegateNode> deltemplates =
        registry.getDelTemplateSelector().delTemplateNameToValues();
    for (SoyFileNode fileNode : sourceFiles) {
      for (TemplateNode template : fileNode.getChildren()) {
        // TODO(jakubvrana): Warn against {call} too.
        for (CallDelegateNode node : getAllNodesOfType(template, CallDelegateNode.class)) {
          checkCallDelegateNode(node, deltemplates);
        }
        for (PrintNode node : getAllNodesOfType(template, PrintNode.class)) {
          checkPrintNode(node);
        }
      }
    }
  }

  private void checkCallDelegateNode(
      CallDelegateNode node, ImmutableListMultimap<String, TemplateDelegateNode> deltemplates) {
    if (!allowsHtml(node.getHtmlContext())) {
      String name = node.getDelCalleeName();
      for (TemplateDelegateNode deltemplate : deltemplates.get(name)) {
        if (deltemplate.getContentKind() == SanitizedContentKind.HTML) {
          errorReporter.report(node.getSourceLocation(), CALLS_HTML_FROM_NON_HTML);
        }
        // Other parts of the compiler ensure that all known delegates have the same kind.
        break;
      }
    }
  }

  private void checkPrintNode(PrintNode node) {
    if (!allowsHtml(node.getHtmlContext())) {
      boolean isHtml;
      ContentKind contentKindOfPrintDirectives = getContentKindOfPrintDirectives(node);
      if (contentKindOfPrintDirectives == null) {
        SoyType type = node.getExpr().getRoot().getType();
        isHtml =
            type != UnknownType.getInstance()
                && type != AnyType.getInstance()
                && type.isAssignableFrom(HtmlType.getInstance());
      } else {
        isHtml = contentKindOfPrintDirectives == ContentKind.HTML;
      }
      if (isHtml) {
        errorReporter.report(node.getSourceLocation(), PRINTS_HTML_FROM_NON_HTML);
      }
    }
  }

  private ContentKind getContentKindOfPrintDirectives(PrintNode node) {
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

  private boolean allowsHtml(HtmlContext htmlContext) {
    return htmlContext == HtmlContext.HTML_PCDATA;
  }
}
