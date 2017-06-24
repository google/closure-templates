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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

  /**
   * Soy directives that cancel autoescaping (see {@link
   * SoyPrintDirective#shouldCancelAutoescape()}).
   */
  private final ImmutableSet<String> autoescapeCancellingDirectives;

  /** Maps print directive names to the content kinds they consume and produce. */
  private final Map<String, SanitizedContent.ContentKind> sanitizedContentOperators;

  /** The conclusions drawn by the last {@link #rewrite}. */
  private Inferences inferences;

  /** Raw text nodes sliced by context. */
  private ImmutableList<SlicedRawTextNode> slicedRawTextNodes;

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
    this(
        ImmutableSet.copyOf(
            Collections2.filter(
                soyDirectivesMap.keySet(),
                new Predicate<String>() {
                  @Override
                  public boolean apply(String directiveName) {
                    return soyDirectivesMap.get(directiveName).shouldCancelAutoescape();
                  }
                })),
        makeOperatorKindMap(soyDirectivesMap));
  }

  /**
   * @param autoescapeCancellingDirectives The Soy directives that cancel autoescaping (see {@link
   *     SoyPrintDirective#shouldCancelAutoescape()}).
   * @param sanitizedContentOperators Maps print directive names to the content kinds they consume
   *     and produce.
   */
  public ContextualAutoescaper(
      Iterable<String> autoescapeCancellingDirectives,
      Map<String, SanitizedContent.ContentKind> sanitizedContentOperators) {
    this.autoescapeCancellingDirectives = ImmutableSet.copyOf(autoescapeCancellingDirectives);
    this.sanitizedContentOperators = ImmutableMap.copyOf(sanitizedContentOperators);
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
  public List<TemplateNode> rewrite(
      SoyFileSetNode fileSet, TemplateRegistry registry, ErrorReporter errorReporter) {
    // Defensively copy so our loops below hold.
    List<SoyFileNode> files = ImmutableList.copyOf(fileSet.getChildren());

    // TODO(lukes): why aren't we just using the TemplateRegistry?
    Map<String, ImmutableList<TemplateNode>> templatesByName = findTemplates(files);

    // Inferences collects all the typing decisions we make, templates we derive, and escaping modes
    // we choose.
    Inferences inferences =
        new Inferences(
            autoescapeCancellingDirectives, fileSet.getNodeIdGenerator(), templatesByName);
    ImmutableList.Builder<SlicedRawTextNode> slicedRawTextNodesBuilder = ImmutableList.builder();

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
            templateNode,
            startContext,
            inferences,
            autoescapeCancellingDirectives,
            slicedRawTextNodesBuilder,
            errorReporter);
      } catch (SoyAutoescapeException e) {
        reportError(errorReporter, errorLocations, e);
      }
    }

    if (!errorLocations.isEmpty()) {
      // Bail out early, since future passes won't succeed and may throw precondition errors.
      return ImmutableList.<TemplateNode>of();
    }

    // Store inferences so that after processing, clients can access the output contexts for
    // templates.
    this.inferences = inferences;

    // Store context boundaries so that later passes can make use of element/attribute boundaries.
    this.slicedRawTextNodes = slicedRawTextNodesBuilder.build();

    runVisitorOnAllTemplatesIncludingNewOnes(
        inferences, new NonContextualTypedRenderUnitNodesVisitor(errorReporter));

    // Now that we know we don't fail with exceptions, apply the changes to the given files.
    List<TemplateNode> extraTemplates =
        new Rewriter(inferences, sanitizedContentOperators).rewrite(fileSet);

    runVisitorOnAllTemplatesIncludingNewOnes(
        inferences,
        new PerformDeprecatedNonContextualAutoescapeVisitor(
            autoescapeCancellingDirectives, errorReporter, fileSet.getNodeIdGenerator()));

    return extraTemplates;
  }

  /**
   * Runs a visitor on all templates, including newly-generated ones.
   *
   * <p>After running the inference engine, new re-contextualized templates have been generated, but
   * haven't been folded back into the SoyFileSetNode (which happens in the SoyFileSet monster
   * class).
   *
   * <p>Note this is true even for non-contextual templates. If a non-contextual template eventually
   * is called by a contextual one, the call subtree will be rewritten for the alternate context
   * (even though they remain non-contextually autoescaped).
   */
  private void runVisitorOnAllTemplatesIncludingNewOnes(
      Inferences inferences, AbstractSoyNodeVisitor<?> visitor) {
    List<TemplateNode> allTemplatesIncludingNewOnes = inferences.getAllTemplates();
    for (TemplateNode templateNode : allTemplatesIncludingNewOnes) {
      visitor.exec(templateNode);
    }
  }

  /**
   * Null if no typing has been done for the named template, or otherwise the context after a call
   * to the named template. Since we derive templates by start context at the call site, there is no
   * start context parameter.
   *
   * @param templateName A qualified template name.
   */
  public Context getTemplateEndContext(String templateName) {
    return inferences.getTemplateEndContext(templateName);
  }

  /**
   * Maps ranges of text-nodes to contexts so that later parse passes can add attributes or
   * elements.
   */
  public ImmutableList<SlicedRawTextNode> getSlicedRawTextNodes() {
    return slicedRawTextNodes;
  }

  /** Reports an autoescape exception. */
  private void reportError(
      ErrorReporter errorReporter, Set<SourceLocation> errorLocations, SoyAutoescapeException e) {
    // First, get to the root cause of the exception, and assemble an error message indicating
    // the full call stack that led to the failure.
    String message = "- " + e.getMessage();
    while (e.getCause() instanceof SoyAutoescapeException) {
      e = (SoyAutoescapeException) e.getCause();
      message += "\n- " + e.getMessage();
    }

    // Now that we've gotten to the leaf, let's use its source location as the canonical one for
    // reporting and de-duping. (We might otherwise end up reporting a single error multiple times
    // because a single template was called by multiple other contextual templates.)
    // TODO(gboyer): Delete this logic once deprecated-contextual is removed.
    SourceLocation location = Preconditions.checkNotNull(e.getSourceLocation());
    if (errorLocations.contains(location)) {
      return;
    }
    errorLocations.add(location);
    errorReporter.report(location, AUTOESCAPE_ERROR, message);
  }

  /**
   * Fills in the {@link Inferences} template name to node map.
   *
   * @param files Modified in place.
   */
  private static Map<String, ImmutableList<TemplateNode>> findTemplates(
      Iterable<? extends SoyFileNode> files) {
    final Map<String, ImmutableList.Builder<TemplateNode>> templatesByName =
        Maps.newLinkedHashMap();
    for (SoyFileNode file : files) {
      for (TemplateNode template : file.getChildren()) {
        String templateName;
        if (template instanceof TemplateBasicNode) {
          templateName = template.getTemplateName();
        } else {
          templateName = ((TemplateDelegateNode) template).getDelTemplateName();
        }
        if (!templatesByName.containsKey(templateName)) {
          templatesByName.put(templateName, ImmutableList.<TemplateNode>builder());
        }
        templatesByName.get(templateName).add(template);
      }
    }
    final ImmutableMap.Builder<String, ImmutableList<TemplateNode>> templatesByNameBuilder =
        ImmutableMap.builder();
    for (Map.Entry<String, ImmutableList.Builder<TemplateNode>> e : templatesByName.entrySet()) {
      templatesByNameBuilder.put(e.getKey(), e.getValue().build());
    }
    return templatesByNameBuilder.build();
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
          return templateNode.getAutoescapeMode() == AutoescapeMode.STRICT
              || templateNode.getAutoescapeMode() == AutoescapeMode.CONTEXTUAL;
        }
      };

  private static Map<String, SanitizedContent.ContentKind> makeOperatorKindMap(
      final ImmutableMap<String, ? extends SoyPrintDirective> soyDirectivesMap) {
    ImmutableMap.Builder<String, SanitizedContent.ContentKind> operatorKindMapBuilder =
        ImmutableMap.builder();
    for (SoyPrintDirective directive : soyDirectivesMap.values()) {
      if (directive instanceof SanitizedContentOperator) {
        operatorKindMapBuilder.put(
            directive.getName(), ((SanitizedContentOperator) directive).getContentKind());
      }
    }
    return operatorKindMapBuilder.build();
  }

  private final class NonContextualTypedRenderUnitNodesVisitor
      extends AbstractSoyNodeVisitor<Void> {

    final ErrorReporter errorReporter;

    NonContextualTypedRenderUnitNodesVisitor(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      if (node.getAutoescapeMode() == AutoescapeMode.NONCONTEXTUAL) {
        visitChildren(node);
      }
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      visitRenderUnitNode(node);
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      visitRenderUnitNode(node);
    }

    protected void visitRenderUnitNode(RenderUnitNode node) {
      if (node.getContentKind() != null) {
        // Not visiting children in this block.
        // In processing a strict block (any block with a kind), contextualAutoescaper will
        // automatically go into the children.
        // Secondly, CheckEscapingSanityVisitor makes sure that all the children {let} or {param}
        // blocks of a strict {let} or {param} block are also strict.
        ImmutableList.Builder<SlicedRawTextNode> slicedRawTextNodesBuilder =
            ImmutableList.builder();
        InferenceEngine.inferStrictRenderUnitNode(
            // As this visitor visits only non-contextual templates.
            AutoescapeMode.NONCONTEXTUAL,
            node,
            inferences,
            autoescapeCancellingDirectives,
            slicedRawTextNodesBuilder,
            errorReporter);
      } else {
        visitChildren(node);
      }
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
