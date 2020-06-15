/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;

/**
 * Inserts directives into print commands by looking at the context in which a print appears, and
 * derives templates and rewrites calls so that each template is entered only in contexts consistent
 * with its escaping conventions.
 *
 * <p>E.g. it will {@link ContextualAutoescaper#rewrite rewrite} <xmp class=prettyprint> {template
 * example autoescape="contextual"}
 *
 * <p>Hello, {$world}! {/template} </xmp> to <xmp class=prettyprint> {template example
 * autoescape="contextual"}
 *
 * <p>Hello, {$world |escapeHtml}! {/template} </xmp>
 *
 */
public final class ContextualAutoescaper {

  @VisibleForTesting
  static final String AUTOESCAPE_ERROR_PREFIX =
      "Invalid or ambiguous syntax prevents Soy from escaping this template correctly:\n";

  private static final SoyErrorKind AUTOESCAPE_ERROR =
      SoyErrorKind.of(AUTOESCAPE_ERROR_PREFIX + "{0}", StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter errorReporter;
  private final ImmutableList<? extends SoyPrintDirective> printDirectives;

  /**
   * This injected ctor provides a blank constructor that is filled, in normal compiler operation,
   * with the core and basic directives defined in com.google.template.soy.{basic,core}directives,
   * and any custom directives supplied on the command line.
   *
   * @param soyDirectives All SoyPrintDirectives
   */
  public ContextualAutoescaper(
      ErrorReporter errorReporter, ImmutableList<? extends SoyPrintDirective> soyDirectives) {
    this.errorReporter = errorReporter;
    this.printDirectives = soyDirectives;
  }

  /**
   * Rewrites the given Soy files so that dynamic output is properly escaped according to the
   * context in which it appears.
   *
   * <p>The rewriting consists entirely of inserting print directives on print, call and msg nodes.
   *
   * @param sourceFiles The files to rewrite
   * @param registry The registry to look up information about callees
   */
  public Inferences annotate(ImmutableList<SoyFileNode> sourceFiles) {
    Inferences inferences = new Inferences();
    // Inferences collects all the typing decisions we make and escaping modes we choose.
    for (SoyFileNode file : sourceFiles) {
      inferences.setTemplateRegistry(
          file.hasTemplateRegistry() ? file.getTemplateRegistry() : TemplateRegistry.EMPTY);
      for (TemplateNode templateNode : file.getTemplates()) {
        try {
          // The author specifies the kind of SanitizedContent to produce, and thus the context in
          // which to escape.
          Context startContext =
              Context.getStartContextForContentKind(templateNode.getContentKind());
          InferenceEngine.inferTemplateEndContext(
              templateNode, startContext, inferences, errorReporter);
        } catch (SoyAutoescapeException e) {
          reportError(errorReporter, e);
        }
      }
    }
    if (errorReporter.hasErrors()) {
      return null;
    }
    return inferences;
  }

  /**
   * Rewrites the given Soy files so that dynamic output is properly escaped according to the
   * context in which it appears.
   *
   * <p>The rewriting consists entirely of inserting print directives on print, call and msg nodes.
   *
   * @param sourceFiles The files to rewrite
   * @param idGenerator the Id generate to use to add new directives, if necessary
   * @param inferences The registry to look up information about callees
   */
  public void rewrite(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, Inferences inferences) {
    // Now that we know we don't fail with exceptions, apply the changes to the given files.
    Rewriter rewriter = new Rewriter(inferences, idGenerator, printDirectives);
    for (SoyFileNode file : sourceFiles) {
      rewriter.rewrite(file);
    }
  }

  /** Reports an autoescape exception. */
  private void reportError(ErrorReporter errorReporter, SoyAutoescapeException e) {
    // First, get to the root cause of the exception, and assemble an error message indicating
    // the full call stack that led to the failure.
    String message = "- " + e.getOriginalMessage();
    while (e.getCause() instanceof SoyAutoescapeException) {
      e = (SoyAutoescapeException) e.getCause();
      message += "\n- " + e.getMessage();
    }
    errorReporter.report(e.getSourceLocation(), AUTOESCAPE_ERROR, message);
  }
}
