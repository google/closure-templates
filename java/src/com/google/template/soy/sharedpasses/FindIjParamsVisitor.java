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

package com.google.template.soy.sharedpasses;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.sharedpasses.FindIjParamsVisitor.IjParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateDivision;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Visitor for finding the injected params used by a given template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a {@code TemplateNode}.
 *
 * <p> If you need to call this visitor for multiple templates in the same tree (without modifying
 * the tree), it's more efficient to reuse the same instance of this visitor because we memoize
 * results from previous calls to exec.
 *
 * @author Kai Huang
 */
public class FindIjParamsVisitor extends AbstractSoyNodeVisitor<IjParamsInfo> {


  /**
   * Return value for {@code FindIjParamsVisitor}.
   */
  public static class IjParamsInfo {

    /** Sorted set of inject params (i.e. the keys of the multimap below). */
    public final ImmutableSortedSet<String> ijParamSet;

    /** Multimap from injected param key to transitive callees that use the param. */
    public final ImmutableMultimap<String, TemplateNode> ijParamToCalleesMultimap;

    /** Whether the template (that the pass was run on) may have injected params indirectly used in
     *  external basic calls. */
    public final boolean mayHaveIjParamsInExternalCalls;

    /** Whether the template (that the pass was run on) may have injected params indirectly used in
     *  external delegate calls. */
    public final boolean mayHaveIjParamsInExternalDelCalls;

    /**
     * @param ijParamToCalleesMultimap Multimap from injected param key to transitive callees that
     *     use the param.
     * @param mayHaveIjParamsInExternalCalls Whether the template (that the pass was run on) may
     *     have injected params indirectly used in external basic calls.
     * @param mayHaveIjParamsInExternalDelCalls Whether the template (that the pass was run on) may
     *     have injected params indirectly used in external delegate calls.
     */
    public IjParamsInfo(
        ImmutableMultimap<String, TemplateNode> ijParamToCalleesMultimap,
        boolean mayHaveIjParamsInExternalCalls, boolean mayHaveIjParamsInExternalDelCalls) {
      this.ijParamToCalleesMultimap = ijParamToCalleesMultimap;
      this.ijParamSet = ImmutableSortedSet.copyOf(ijParamToCalleesMultimap.keySet());
      this.mayHaveIjParamsInExternalCalls = mayHaveIjParamsInExternalCalls;
      this.mayHaveIjParamsInExternalDelCalls = mayHaveIjParamsInExternalDelCalls;
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Class for info collected about a specific template during a pass.


  /**
   * Class for info collected about a specific template during a pass.
   *
   * <p> We also refer to this as the unfinished info, as opposed to IjParamsInfo, which is the
   * finished info.
   */
  private static class TemplateVisitInfo {


    /** The template that this info object is for. */
    public final TemplateNode template;

    /** The template's position in the visit order of the templates visited during this pass. */
    public final int visitOrdinal;

    /** If nonnull, then this is a reference to the info object for the earliest known equivalent
     *  template, where "equivalent" means that either template can reach the other via calls (thus
     *  they should have the same finished IjParamsInfo at the end), and "earliest" is by visit
     *  order of the templates visited during this pass.
     *  <p> Note: If nonnull, then the fields below (ijParamToCalleesMultimap,
     *  mayHaveIjParamsInExternalCalls, mayHaveIjParamsInExternalDelCalls) may be incorrect even
     *  after the visit to the template has completed, because the correct info will be retrieved
     *  via this reference. */
    public TemplateVisitInfo visitInfoOfEarliestEquivalent;

    /** Multimap from injected param key to transitive callees that use the param.
     *  <p> Note: May be incomplete if visitInfoOfEarliestEquivalent is nonnull. */
    public Multimap<String, TemplateNode> ijParamToCalleesMultimap;

    /** Whether the template may have injected params indirectly used in external basic calls.
     *  <p> Note: May be incorrect if visitInfoOfEarliestEquivalent is nonnull. */
    public boolean mayHaveIjParamsInExternalCalls;

    /** Whether the template may have injected params indirectly used in external delegate calls.
     *  <p> Note: May be incorrect if visitInfoOfEarliestEquivalent is nonnull. */
    public boolean mayHaveIjParamsInExternalDelCalls;

    /** Cached value of the finished info if previously computed, else null. */
    private IjParamsInfo finishedInfo;


    public TemplateVisitInfo(TemplateNode template, int visitOrdinal) {
      this.template = template;
      this.visitOrdinal = visitOrdinal;
      this.visitInfoOfEarliestEquivalent = null;
      this.ijParamToCalleesMultimap = HashMultimap.create();
      this.mayHaveIjParamsInExternalCalls = false;
      this.mayHaveIjParamsInExternalDelCalls = false;
      this.finishedInfo = null;
    }


    /**
     * Updates the earliest known equivalent template's visit info, unless we already knew about the
     * same or an even earlier equivalent.
     * @param visitInfoOfNewEquivalent A newly discovered earlier equivalent template's visit info.
     */
    public void maybeUpdateEarliestEquivalent(TemplateVisitInfo visitInfoOfNewEquivalent) {
      Preconditions.checkArgument(visitInfoOfNewEquivalent != this);
      if (this.visitInfoOfEarliestEquivalent == null ||
          visitInfoOfNewEquivalent.visitOrdinal < this.visitInfoOfEarliestEquivalent.visitOrdinal) {
        this.visitInfoOfEarliestEquivalent = visitInfoOfNewEquivalent;
      }
    }


    /**
     * Incorporates finished info of a callee into this info object.
     * @param calleeFinishedInfo The finished info to incorporate.
     */
    public void incorporateCalleeFinishedInfo(IjParamsInfo calleeFinishedInfo) {
      ijParamToCalleesMultimap.putAll(calleeFinishedInfo.ijParamToCalleesMultimap);
      mayHaveIjParamsInExternalCalls |=
          calleeFinishedInfo.mayHaveIjParamsInExternalCalls;
      mayHaveIjParamsInExternalDelCalls |=
          calleeFinishedInfo.mayHaveIjParamsInExternalDelCalls;
    }


    /**
     * Incorporates visit info of a callee into this info object.
     * @param calleeVisitInfo The visit info to incorporate.
     * @param activeTemplateSet The set of currently active templates (templates that we are in the
     *     midst of visiting, where the visit call has begun but has not ended).
     */
    public void incorporateCalleeVisitInfo(
        TemplateVisitInfo calleeVisitInfo, Set<TemplateNode> activeTemplateSet) {

      if (calleeVisitInfo.visitInfoOfEarliestEquivalent == null ||
          calleeVisitInfo.visitInfoOfEarliestEquivalent == this) {
        // Cases 1 and 2: The callee doesn't have an earliest known equivalent (case 1), or it's the
        // current template (case 2). We handle these together because in either case, we don't need
        // to inherit the earliest known equivalent from the callee.
        incorporateCalleeVisitInfoHelper(calleeVisitInfo);

      } else if (
          activeTemplateSet.contains(
              calleeVisitInfo.visitInfoOfEarliestEquivalent.template)) {
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


    /**
     * Private helper for incorporateCalleeVisitInfo().
     */
    private void incorporateCalleeVisitInfoHelper(TemplateVisitInfo calleeVisitInfo) {
      ijParamToCalleesMultimap.putAll(calleeVisitInfo.ijParamToCalleesMultimap);
      mayHaveIjParamsInExternalCalls |=
          calleeVisitInfo.mayHaveIjParamsInExternalCalls;
      mayHaveIjParamsInExternalDelCalls |=
          calleeVisitInfo.mayHaveIjParamsInExternalDelCalls;
    }


    /**
     * Converts this (unfinished) visit info into a (finished) IjParamsInfo.
     * <p> Caches the result so that only one finished info object is created even if called
     * multiple times.
     * @return The finished info object.
     */
    public IjParamsInfo toFinishedInfo() {
      if (finishedInfo == null) {
        if (visitInfoOfEarliestEquivalent != null) {
          finishedInfo = visitInfoOfEarliestEquivalent.toFinishedInfo();
        } else {
          finishedInfo = new IjParamsInfo(
              ImmutableMultimap.copyOf(ijParamToCalleesMultimap),
              mayHaveIjParamsInExternalCalls, mayHaveIjParamsInExternalDelCalls);
        }
      }
      return finishedInfo;
    }

  }


  // -----------------------------------------------------------------------------------------------
  // FindIjParamsVisitor body.


  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;

  /** Map from template node to finished info. If this map is not null at the start of a pass, then
   *  it contains info that was found in a previous pass (previous call to exec). */
  @VisibleForTesting
  Map<TemplateNode, IjParamsInfo> templateToFinishedInfoMap;

  /** Visit info for the current template whose body we're visiting. */
  private TemplateVisitInfo currTemplateVisitInfo;

  /** Stack of active visit infos corresponding to the current visit/call path, i.e. for templates
   *  that we are in the midst of visiting, where the visit call has begun but has not ended */
  private Deque<TemplateVisitInfo> activeTemplateVisitInfoStack;

  /** Set of active templates, where "active" means the same thing as above. */
  private Set<TemplateNode> activeTemplateSet;

  /** Map from visited template (visit may or may not have ended) to visit info. */
  private Map<TemplateNode, TemplateVisitInfo> visitedTemplateToInfoMap;


  /**
   * @param templateRegistry Map from template name to TemplateNode to use during the pass.
   */
  public FindIjParamsVisitor(@Nullable TemplateRegistry templateRegistry) {
    this.templateRegistry = templateRegistry;
  }


  /**
   * Precomputes injected params info for all templates.
   *
   * <p> Note: This method is not thread-safe. If you need to get injected params info in a
   * thread-safe manner, be sure to call this method only once and then use the precomputed map.
   *
   * @param soyTree The full Soy tree.
   * @return A map from template node to injected params info for all templates. The returned map
   *     is deeply immutable ({@code IjParamsInfo} is immutable).
   */
  public ImmutableMap<TemplateNode, IjParamsInfo> execForAllTemplates(SoyFileSetNode soyTree) {

    for (SoyFileNode soyFile : soyTree.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        exec(template);
      }
    }

    return ImmutableMap.copyOf(templateToFinishedInfoMap);
  }


  /**
   * {@inheritDoc}
   *
   * <p> Note: This method is not thread-safe. If you need to get injected params info in a
   * thread-safe manner, then please use {@link #execForAllTemplates}() in a thread-safe manner.
   */
  @Override public IjParamsInfo exec(SoyNode node) {

    Preconditions.checkArgument(node instanceof TemplateNode);
    TemplateNode nodeAsTemplate = (TemplateNode) node;

    // Build templateRegistry and initialize templateToFinishedInfoMap if necessary.
    if (templateRegistry == null) {
      SoyFileSetNode soyTree = nodeAsTemplate.getParent().getParent();
      templateRegistry = new TemplateRegistry(soyTree);
    }
    if (templateToFinishedInfoMap == null) {
      templateToFinishedInfoMap = Maps.newHashMap();
    }

    // If finished in a previous pass (previous call to exec), just return the finished info.
    if (templateToFinishedInfoMap.containsKey(nodeAsTemplate)) {
      return templateToFinishedInfoMap.get(nodeAsTemplate);
    }

    // Initialize vars for the pass.
    currTemplateVisitInfo = null;
    activeTemplateVisitInfoStack = new ArrayDeque<TemplateVisitInfo>();
    activeTemplateSet = Sets.newHashSet();
    visitedTemplateToInfoMap = Maps.newHashMap();

    visit(node);

    if (activeTemplateVisitInfoStack.size() != 0 || activeTemplateSet.size() != 0) {
      throw new AssertionError();
    }

    // Convert visit info to finished info for all visited templates.
    for (TemplateVisitInfo templateVisitInfo : visitedTemplateToInfoMap.values()) {
      templateToFinishedInfoMap.put(
          templateVisitInfo.template, templateVisitInfo.toFinishedInfo());
    }

    return templateToFinishedInfoMap.get(nodeAsTemplate);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitTemplateNode(TemplateNode node) {

    if (templateToFinishedInfoMap.containsKey(node)) {
      throw new AssertionError();  // already visited in some previous pass (previous call to exec)
    }
    if (visitedTemplateToInfoMap.containsKey(node)) {
      throw new AssertionError();  // already visited during the current pass
    }

    currTemplateVisitInfo = new TemplateVisitInfo(node, visitedTemplateToInfoMap.size());
    visitedTemplateToInfoMap.put(node, currTemplateVisitInfo);

    // Add the injected params locally used in this template.
    FindIjParamsInExprHelperVisitor helperVisitor = new FindIjParamsInExprHelperVisitor();
    SoytreeUtils.execOnAllV2Exprs(node, helperVisitor);
    Set<String> localIjParams = helperVisitor.getResult();
    for (String param : localIjParams) {
      currTemplateVisitInfo.ijParamToCalleesMultimap.put(param, node);
    }

    // Visit the template body to find calls to recurse on.
    visitChildren(node);

    currTemplateVisitInfo = null;
  }


  @Override protected void visitCallBasicNode(CallBasicNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    TemplateBasicNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());

    // Note the template may be null because we allow calls to external templates not within this
    // Soy file set.
    if (callee == null) {
      currTemplateVisitInfo.mayHaveIjParamsInExternalCalls = true;
      return;
    }

    // Visit the callee template.
    processCalleeHelper(callee);
  }


  @Override protected void visitCallDelegateNode(CallDelegateNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    // The current Soy file bundle may not contain all the delegate implementations that could
    // potentially be used.
    currTemplateVisitInfo.mayHaveIjParamsInExternalDelCalls = true;

    // Visit all the possible callee templates.
    Set<DelegateTemplateDivision> delTemplateDivisions =
        templateRegistry.getDelTemplateDivisionsForAllVariants(node.getDelCalleeName());
    if (delTemplateDivisions != null) {
      for (DelegateTemplateDivision division : delTemplateDivisions) {
        for (TemplateDelegateNode delCallee : division.delPackageNameToDelTemplateMap.values()) {
          processCalleeHelper(delCallee);
        }
      }
    }
  }


  /**
   * Private helper for visitCallBasicNode() and visitCallDelegateNode().
   */
  private void processCalleeHelper(TemplateNode callee) {

    if (templateToFinishedInfoMap.containsKey(callee)) {
      // Case 1: The callee was already finished in a previous pass (previous call to exec).
      currTemplateVisitInfo.incorporateCalleeFinishedInfo(
          templateToFinishedInfoMap.get(callee));

    } else if (callee == currTemplateVisitInfo.template) {
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
      activeTemplateSet.add(currTemplateVisitInfo.template);
      visit(callee);
      currTemplateVisitInfo = activeTemplateVisitInfoStack.pop();
      activeTemplateSet.remove(currTemplateVisitInfo.template);

      currTemplateVisitInfo.incorporateCalleeVisitInfo(
          visitedTemplateToInfoMap.get(callee), activeTemplateSet);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
