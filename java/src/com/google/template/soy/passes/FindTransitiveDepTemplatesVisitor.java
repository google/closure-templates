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

package com.google.template.soy.passes;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.passes.FindTransitiveDepTemplatesVisitor.TransitiveDepTemplatesInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Visitor for finding the set of templates transitively called by a given template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} should be called on a {@code TemplateNode}.
 *
 * <p>If you need to call this visitor for multiple templates in the same tree (without modifying
 * the tree), it's more efficient to reuse the same instance of this visitor because we memoize
 * results from previous calls to exec.
 *
 */
public final class FindTransitiveDepTemplatesVisitor
    extends AbstractSoyNodeVisitor<TransitiveDepTemplatesInfo> {

  /** Return value for {@code FindTransitiveDepTemplatesVisitor}. */
  public static class TransitiveDepTemplatesInfo {

    /** Set of templates transitively called by the root template(s). Sorted by template name. */
    public final ImmutableSortedSet<TemplateNode> depTemplateSet;

    /** @param depTemplateSet Set of templates transitively called by the root template(s). */
    public TransitiveDepTemplatesInfo(Set<TemplateNode> depTemplateSet) {
      this.depTemplateSet =
          ImmutableSortedSet.copyOf(
              new Comparator<TemplateNode>() {
                @Override
                public int compare(TemplateNode o1, TemplateNode o2) {
                  return o1.getTemplateName().compareTo(o2.getTemplateName());
                }
              },
              depTemplateSet);
    }

    /**
     * Merges multiple TransitiveDepTemplatesInfo objects (which may be TransitiveDepTemplatesInfo
     * objects) into a single TransitiveDepTemplatesInfo, i.e. where the depTemplateSet is the union
     * of the given info objects' depTemplateSets.
     */
    public static TransitiveDepTemplatesInfo merge(
        Iterable<? extends TransitiveDepTemplatesInfo> infosToMerge) {

      ImmutableSet.Builder<TemplateNode> depTemplateSetBuilder = ImmutableSet.builder();

      for (TransitiveDepTemplatesInfo infoToMerge : infosToMerge) {
        depTemplateSetBuilder.addAll(infoToMerge.depTemplateSet);
      }

      return new TransitiveDepTemplatesInfo(depTemplateSetBuilder.build());
    }

    // Note: No need to override equals() and hashCode() here because we reuse instances of this
    // class, which means the default behavior using object identity is sufficient.

  }

  // -----------------------------------------------------------------------------------------------
  // Class for info collected about a specific template during a pass.

  /**
   * Class for info collected about a specific template during a pass.
   *
   * <p>We also refer to this as the unfinished info, as opposed to TransitiveDepTemplatesInfo,
   * which is the finished info.
   */
  private static class TemplateVisitInfo {

    /** The root template that this info object is for. */
    public final TemplateNode rootTemplate;

    /** The template's position in the visit order of the templates visited during this pass. */
    public final int visitOrdinal;

    /**
     * If nonnull, then this is a reference to the info object for the earliest known equivalent
     * template, where "equivalent" means that either template can reach the other via calls (thus
     * they should have the same finished TransitiveDepTemplatesInfo at the end), and "earliest" is
     * by visit order of the templates visited during this pass.
     *
     * <p>Note: If nonnull, then the fields below (depTemplateSet, hasExternalCalls, hasDelCalls)
     * may be incorrect even after the visit to the template has completed, because the correct info
     * will be retrieved via this reference.
     */
    public TemplateVisitInfo visitInfoOfEarliestEquivalent;

    /**
     * Set of templates transitively called by the root template.
     *
     * <p>Note: May be incomplete if visitInfoOfEarliestEquivalent is nonnull.
     */
    public Set<TemplateNode> depTemplateSet;

    /** Cached value of the finished info if previously computed, else null. */
    private TransitiveDepTemplatesInfo finishedInfo;

    public TemplateVisitInfo(TemplateNode template, int visitOrdinal) {
      this.rootTemplate = template;
      this.visitOrdinal = visitOrdinal;
      this.visitInfoOfEarliestEquivalent = null;
      this.depTemplateSet = Sets.newHashSet();
      this.finishedInfo = null;
    }

    /**
     * Updates the reference to the earliest known equivalent template's visit info, unless we
     * already knew about the same or an even earlier equivalent.
     *
     * @param visitInfoOfNewEquivalent A newly discovered earlier equivalent template's visit info.
     */
    public void maybeUpdateEarliestEquivalent(TemplateVisitInfo visitInfoOfNewEquivalent) {
      Preconditions.checkArgument(visitInfoOfNewEquivalent != this);
      if (this.visitInfoOfEarliestEquivalent == null
          || visitInfoOfNewEquivalent.visitOrdinal
              < this.visitInfoOfEarliestEquivalent.visitOrdinal) {
        this.visitInfoOfEarliestEquivalent = visitInfoOfNewEquivalent;
      }
    }

    /**
     * Incorporates finished info of a callee into this info object.
     *
     * @param calleeFinishedInfo The finished info to incorporate.
     */
    public void incorporateCalleeFinishedInfo(TransitiveDepTemplatesInfo calleeFinishedInfo) {
      depTemplateSet.addAll(calleeFinishedInfo.depTemplateSet);
    }

    /**
     * Incorporates visit info of a callee into this info object.
     *
     * @param calleeVisitInfo The visit info to incorporate.
     * @param activeTemplateSet The set of currently active templates (templates that we are in the
     *     midst of visiting, where the visit call has begun but has not ended).
     */
    public void incorporateCalleeVisitInfo(
        TemplateVisitInfo calleeVisitInfo, Set<TemplateNode> activeTemplateSet) {

      if (calleeVisitInfo.visitInfoOfEarliestEquivalent == null
          || calleeVisitInfo.visitInfoOfEarliestEquivalent == this) {
        // Cases 1 and 2: The callee doesn't have an earliest known equivalent (case 1), or it's the
        // current template (case 2). We handle these together because in either case, we don't need
        // to inherit the earliest known equivalent from the callee.
        incorporateCalleeVisitInfoHelper(calleeVisitInfo);

      } else if (activeTemplateSet.contains(
          calleeVisitInfo.visitInfoOfEarliestEquivalent.rootTemplate)) {
        // Case 3: The callee knows about some earlier equivalent (not this template) in the active
        // visit path. Any earlier equivalent of the callee is also an equivalent of this template.
        maybeUpdateEarliestEquivalent(calleeVisitInfo.visitInfoOfEarliestEquivalent);
        incorporateCalleeVisitInfoHelper(calleeVisitInfo);

      } else {
        // Case 4: The callee's earliest known equivalent is not active (visit to that equivalent
        // template has already ended). In this case, we instead want to incorporate that equivalent
        // template's info (which should already have incorporated all of the callee's info, making
        // the callee's own info unnecessary).
        incorporateCalleeVisitInfo(
            calleeVisitInfo.visitInfoOfEarliestEquivalent, activeTemplateSet);
      }
    }

    /** Private helper for incorporateCalleeVisitInfo(). */
    private void incorporateCalleeVisitInfoHelper(TemplateVisitInfo calleeVisitInfo) {
      depTemplateSet.addAll(calleeVisitInfo.depTemplateSet);
    }

    /**
     * Converts this (unfinished) visit info into a (finished) TransitiveDepTemplatesInfo.
     *
     * <p>Caches the result so that only one finished info object is created even if called multiple
     * times.
     *
     * @return The finished info object.
     */
    public TransitiveDepTemplatesInfo toFinishedInfo() {
      if (finishedInfo == null) {
        if (visitInfoOfEarliestEquivalent != null) {
          finishedInfo = visitInfoOfEarliestEquivalent.toFinishedInfo();
        } else {
          finishedInfo = new TransitiveDepTemplatesInfo(depTemplateSet);
        }
      }
      return finishedInfo;
    }
  }

  // -----------------------------------------------------------------------------------------------
  // FindTransitiveDepTemplatesVisitor body.

  /** Registry of all templates in the Soy tree. */
  private final TemplateRegistry templateRegistry;

  /**
   * Map from template node to finished info containing memoized info that was found in previous
   * passes (previous calls to exec).
   */
  @VisibleForTesting Map<TemplateNode, TransitiveDepTemplatesInfo> templateToFinishedInfoMap;

  /** Visit info for the current template whose body we're visiting. */
  private TemplateVisitInfo currTemplateVisitInfo;

  /**
   * Stack of active visit infos corresponding to the current visit/call path, i.e. for templates
   * that we are in the midst of visiting, where the visit call has begun but has not ended
   */
  private Deque<TemplateVisitInfo> activeTemplateVisitInfoStack;

  /** Set of active templates, where "active" means the same thing as above. */
  private Set<TemplateNode> activeTemplateSet;

  /** Map from visited template (visit may or may not have ended) to visit info. */
  private Map<TemplateNode, TemplateVisitInfo> visitedTemplateToInfoMap;

  /** @param templateRegistry Map from template name to TemplateNode to use during the pass. */
  public FindTransitiveDepTemplatesVisitor(TemplateRegistry templateRegistry) {
    this.templateRegistry = checkNotNull(templateRegistry);
    templateToFinishedInfoMap = Maps.newHashMap();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Note: This method is not thread-safe. If you need to get transitive dep templates info in a
   * thread-safe manner, then please use {@link #execOnAllTemplates}() in a thread-safe manner.
   */
  @Override
  public TransitiveDepTemplatesInfo exec(SoyNode rootTemplate) {

    Preconditions.checkArgument(rootTemplate instanceof TemplateNode);
    TemplateNode rootTemplateCast = (TemplateNode) rootTemplate;

    // If finished in a previous pass (previous call to exec), just return the finished info.
    if (templateToFinishedInfoMap.containsKey(rootTemplateCast)) {
      return templateToFinishedInfoMap.get(rootTemplateCast);
    }

    // Initialize vars for the pass.
    currTemplateVisitInfo = null;
    activeTemplateVisitInfoStack = new ArrayDeque<>();
    activeTemplateSet = Sets.newHashSet();
    visitedTemplateToInfoMap = Maps.newHashMap();

    visit(rootTemplateCast);

    if (!activeTemplateVisitInfoStack.isEmpty() || !activeTemplateSet.isEmpty()) {
      throw new AssertionError();
    }

    // Convert visit info to finished info for all visited templates.
    for (TemplateVisitInfo templateVisitInfo : visitedTemplateToInfoMap.values()) {
      templateToFinishedInfoMap.put(
          templateVisitInfo.rootTemplate, templateVisitInfo.toFinishedInfo());
    }

    return templateToFinishedInfoMap.get(rootTemplateCast);
  }

  /**
   * Computes transitive dep templates info for multiple templates.
   *
   * <p>Note: This method returns a map from root template to TransitiveDepTemplatesInfo for the
   * given root templates. If you wish to obtain a single TransitiveDepTemplatesInfo object that
   * contains the combined info for all of the given root templates, then use
   * TransitiveDepTemplatesInfo.merge(resultMap.values()) where resultMap is the result returned by
   * this method.
   *
   * <p>Note: This method is not thread-safe. If you need to get transitive dep templates info in a
   * thread-safe manner, then please use {@link #execOnAllTemplates}() in a thread-safe manner.
   *
   * @param rootTemplates The root templates to compute transitive dep templates info for.
   * @return Map from root template to TransitiveDepTemplatesInfo.
   */
  public ImmutableMap<TemplateNode, TransitiveDepTemplatesInfo> execOnMultipleTemplates(
      Iterable<TemplateNode> rootTemplates) {

    ImmutableMap.Builder<TemplateNode, TransitiveDepTemplatesInfo> resultBuilder =
        ImmutableMap.builder();

    for (TemplateNode rootTemplate : rootTemplates) {
      resultBuilder.put(rootTemplate, exec(rootTemplate));
    }

    return resultBuilder.build();
  }

  /**
   * Computes transitive dep templates info for all templates in a Soy tree.
   *
   * <p>Note: This method returns a map from root template to TransitiveDepTemplatesInfo for all
   * templates in the given Soy tree. If you wish to obtain a single TransitiveDepTemplatesInfo
   * object that contains the combined info for all templates in the given Soy tree, then use
   * TransitiveDepTemplatesInfo.merge(resultMap.values()) where resultMap is the result returned by
   * this method.
   *
   * <p>Note: This method is not thread-safe. If you need to get transitive dep templates info in a
   * thread-safe manner, be sure to call this method only once and then use the precomputed map.
   *
   * @param soyTree A full Soy tree.
   * @return Map from root template to TransitiveDepTemplatesInfo for all templates in the given Soy
   *     tree. The returned map is deeply immutable (TransitiveDepTemplatesInfo is immutable).
   */
  public ImmutableMap<TemplateNode, TransitiveDepTemplatesInfo> execOnAllTemplates(
      SoyFileSetNode soyTree) {
    List<TemplateNode> allTemplates =
        SoyTreeUtils.getAllNodesOfType(soyTree, TemplateNode.class, false);
    return execOnMultipleTemplates(allTemplates);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitTemplateNode(TemplateNode node) {

    if (templateToFinishedInfoMap.containsKey(node)) {
      throw new AssertionError(); // already visited in some previous pass (previous call to exec)
    }
    if (visitedTemplateToInfoMap.containsKey(node)) {
      throw new AssertionError(); // already visited during the current pass
    }

    currTemplateVisitInfo = new TemplateVisitInfo(node, visitedTemplateToInfoMap.size());
    visitedTemplateToInfoMap.put(node, currTemplateVisitInfo);

    // Add this template.
    currTemplateVisitInfo.depTemplateSet.add(node);

    // Visit the template body to find calls to recurse on.
    visitChildren(node);

    currTemplateVisitInfo = null;
  }

  @Override
  protected void visitCallBasicNode(CallBasicNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    TemplateBasicNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());

    // If the callee is null (i.e. not within the Soy file set), then this is an external call.
    if (callee == null) {
      return;
    }

    // Visit the callee template.
    processCalleeHelper(callee);
  }

  @Override
  protected void visitCallDelegateNode(CallDelegateNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    // Visit all the possible callee templates.
    ImmutableList<TemplateDelegateNode> potentialCallees =
        templateRegistry
            .getDelTemplateSelector()
            .delTemplateNameToValues()
            .get(node.getDelCalleeName());
    for (TemplateDelegateNode delCallee : potentialCallees) {
      processCalleeHelper(delCallee);
    }
  }

  /** Private helper for visitCallBasicNode() and visitCallDelegateNode(). */
  private void processCalleeHelper(TemplateNode callee) {

    if (templateToFinishedInfoMap.containsKey(callee)) {
      // Case 1: The callee was already finished in a previous pass (previous call to exec).
      currTemplateVisitInfo.incorporateCalleeFinishedInfo(templateToFinishedInfoMap.get(callee));

    } else if (callee == currTemplateVisitInfo.rootTemplate) {
      // Case 2: The callee is the current template (direct recursive call). Nothing to do here.

    } else if (activeTemplateSet.contains(callee)) {
      // Case 3: The callee is an ancestor in our depth-first visit tree. The callee (i.e.
      // ancestor) is "equivalent" to the current template because either template can reach the
      // the other via calls. In this case, we may change the field visitInfoOfEarliestEquivalent
      // (unless we had previously already found an earlier equivalent).
      currTemplateVisitInfo.maybeUpdateEarliestEquivalent(visitedTemplateToInfoMap.get(callee));

    } else if (visitedTemplateToInfoMap.containsKey(callee)) {
      // Case 4: The callee was visited sometime earlier in the current pass, and that visit has
      // already ended since the callee is not in the activeTemplateSet (case 3 above).
      currTemplateVisitInfo.incorporateCalleeVisitInfo(
          visitedTemplateToInfoMap.get(callee), activeTemplateSet);

    } else {
      // Case 5: The callee is a new template we've never visited.

      activeTemplateVisitInfoStack.push(currTemplateVisitInfo);
      activeTemplateSet.add(currTemplateVisitInfo.rootTemplate);
      visit(callee);
      currTemplateVisitInfo = activeTemplateVisitInfoStack.pop();
      activeTemplateSet.remove(currTemplateVisitInfo.rootTemplate);

      currTemplateVisitInfo.incorporateCalleeVisitInfo(
          visitedTemplateToInfoMap.get(callee), activeTemplateSet);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
