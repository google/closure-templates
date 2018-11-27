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
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;

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

  private final ImmutableMap<String, ? extends SoyPrintDirective> printDirectives;

  /**
   * This injected ctor provides a blank constructor that is filled, in normal compiler operation,
   * with the core and basic directives defined in com.google.template.soy.{basic,core}directives,
   * and any custom directives supplied on the command line.
   *
   * @param soyDirectivesMap Map of all SoyPrintDirectives (name to directive) such that {@code
   *     soyDirectivesMap.get(key).getName().equals(key)} for all key in {@code
   *     soyDirectivesMap.keySet()}.
   */
  public ContextualAutoescaper(
      final ImmutableMap<String, ? extends SoyPrintDirective> soyDirectivesMap) {
    // Compute the set of directives that are escaping directives.
    this.printDirectives = soyDirectivesMap;
  }

  /**
   * Rewrites the given Soy files so that dynamic output is properly escaped according to the
   * context in which it appears.
   *
   * @param fileSet Modified in place.
   * @return Extra templates which were derived from templates under fileSet and which must be
   *     compiled with fileSet to produce a correct output. See {@link DerivedTemplateUtils} for an
   *     explanation of these.
   */
  public void rewrite(SoyFileSetNode fileSet, ErrorReporter errorReporter) {
    ImmutableListMultimap<String, TemplateNode> templatesByName =
        findTemplates(fileSet.getChildren());

    // Inferences collects all the typing decisions we make and escaping modes we choose.
    Inferences inferences = new Inferences(templatesByName);

    for (SoyFileNode file : fileSet.getChildren()) {
      if (file.getSoyFileKind() != SoyFileKind.SRC) {
        continue; // we don't need to inspect non SRC files
      }
      for (TemplateNode templateNode : file.getChildren()) {
        try {
          // In strict mode, the author specifies the kind of SanitizedContent to produce, and
          // thus the context in which to escape.  In deprecated-contextual, it is always HTML.
          Context startContext =
              Context.getStartContextForContentKind(
                  MoreObjects.firstNonNull(
                      templateNode.getContentKind(), SanitizedContentKind.HTML));
          InferenceEngine.inferTemplateEndContext(
              templateNode, startContext, inferences, errorReporter);
        } catch (SoyAutoescapeException e) {
          reportError(errorReporter, e);
        }
      }
    }
    if (errorReporter.hasErrors()) {
      return;
    }
    // Now that we know we don't fail with exceptions, apply the changes to the given files.
    new Rewriter(inferences, fileSet.getNodeIdGenerator(), printDirectives).rewrite(fileSet);
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

  /**
   * Fills in the {@link Inferences} template name to node map.
   *
   * @param files Modified in place.
   */
  private static ImmutableListMultimap<String, TemplateNode> findTemplates(
      Iterable<? extends SoyFileNode> files) {
    ImmutableListMultimap.Builder<String, TemplateNode> builder = ImmutableListMultimap.builder();
    for (SoyFileNode file : files) {
      // skip indirect deps.  An earlier compiler pass will have already reported an error if a
      // source template calls an indirect dep template directly. So we should never need to look up
      // an indirect template (since we only examine source templates).
      if (file.getSoyFileKind() == SoyFileKind.INDIRECT_DEP) {
        continue;
      }
      for (TemplateNode template : file.getChildren()) {
        String templateName;
        if (template instanceof TemplateBasicNode || template instanceof TemplateElementNode) {
          templateName = template.getTemplateName();
        } else {
          templateName = ((TemplateDelegateNode) template).getDelTemplateName();
        }
        builder.put(templateName, template);
      }
    }
    return builder.build();
  }
}
