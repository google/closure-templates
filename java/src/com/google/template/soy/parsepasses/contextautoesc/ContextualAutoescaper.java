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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
  public List<TemplateNode> rewrite(SoyFileSetNode fileSet, ErrorReporter errorReporter) {
    // Defensively copy so our loops below hold.
    List<SoyFileNode> files = ImmutableList.copyOf(fileSet.getChildren());

    ImmutableListMultimap<String, TemplateNode> templatesByName = findTemplates(files);

    // Inferences collects all the typing decisions we make, templates we derive, and escaping modes
    // we choose.
    Inferences inferences = new Inferences(fileSet.getNodeIdGenerator(), templatesByName);

    Collection<TemplateNode> allTemplates = inferences.getAllTemplates();
    TemplateCallGraph callGraph = new TemplateCallGraph(templatesByName);
    // Generate a call graph, creating a dummy root that calls all non-private template in
    // Context.PCDATA, and then type the minimal ancestor set needed to reach all contextual
    // templates whether private or not.
    // This should have the effect of being a NOP when there are no contextual templates, will type
    // all contextual templates, and will not barf on private templates that might be declared
    // autoescape="false" because they do funky things that are provably safe by human reason but
    // not by this algorithm.
    Collection<TemplateNode> thatRequireInference =
        Collections2.filter(allTemplates, REQUIRES_INFERENCE);
    Set<TemplateNode> templateNodesToType = callGraph.callersOf(thatRequireInference);
    templateNodesToType.addAll(thatRequireInference);

    Set<SourceLocation> errorLocations = new HashSet<>();
    for (TemplateNode templateNode : templateNodesToType) {
      try {
        // In strict mode, the author specifies the kind of SanitizedContent to produce, and thus
        // the context in which to escape.
        Context startContext =
            (templateNode.getContentKind() != null)
                ? Context.getStartContextForContentKind(templateNode.getContentKind())
                : Context.HTML_PCDATA;
        InferenceEngine.inferTemplateEndContext(
            templateNode, startContext, inferences, errorReporter);
      } catch (SoyAutoescapeException e) {
        reportError(errorReporter, errorLocations, e);
      }
    }

    if (!errorLocations.isEmpty()) {
      // Bail out early, since future passes won't succeed and may throw precondition errors.
      return ImmutableList.<TemplateNode>of();
    }

    // Now that we know we don't fail with exceptions, apply the changes to the given files.
    List<TemplateNode> extraTemplates = new Rewriter(inferences, printDirectives).rewrite(fileSet);

    return extraTemplates;
  }

  /** Reports an autoescape exception. */
  private void reportError(
      ErrorReporter errorReporter, Set<SourceLocation> errorLocations, SoyAutoescapeException e) {
    // First, get to the root cause of the exception, and assemble an error message indicating
    // the full call stack that led to the failure.
    String message = "- " + e.getOriginalMessage();
    while (e.getCause() instanceof SoyAutoescapeException) {
      e = (SoyAutoescapeException) e.getCause();
      message += "\n- " + e.getMessage();
    }

    // Now that we've gotten to the leaf, let's use its source location as the canonical one for
    // reporting and de-duping. (We might otherwise end up reporting a single error multiple times
    // because a single template was called by multiple other contextual templates.)
    // TODO(gboyer): Delete this logic once deprecated-contextual is removed.
    SourceLocation location = Preconditions.checkNotNull(e.getSourceLocation());
    if (!errorLocations.add(location)) {
      return;
    }
    errorReporter.report(location, AUTOESCAPE_ERROR, message);
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
      for (TemplateNode template : file.getChildren()) {
        String templateName;
        if (template instanceof TemplateBasicNode) {
          templateName = template.getTemplateName();
        } else {
          templateName = ((TemplateDelegateNode) template).getDelTemplateName();
        }
        builder.put(templateName, template);
      }
    }
    return builder.build();
  }

  private static final Predicate<TemplateNode> REQUIRES_INFERENCE =
      new Predicate<TemplateNode>() {
        @Override
        public boolean apply(TemplateNode templateNode) {
          // All strict and contextual. With strict, every template establishes its own context.
          // With contextual, even if we don't see any callers in the call graph, it still might be
          // called from another file.  This used to skip private templates, but private supposedly
          // only means the template can only be called by other templates, and even then, it is
          // not really enforced strongly by the Closure JS Compiler. (Prior to changing this,
          // there were a few templates that weren't contextually autoescaped because they were
          // private, but were still being called directly from JS.)
          return templateNode.getParent().getSoyFileKind() == SoyFileKind.SRC
              && (templateNode.getAutoescapeMode() == AutoescapeMode.STRICT
                  || templateNode.getAutoescapeMode() == AutoescapeMode.CONTEXTUAL);
        }
      };
}
