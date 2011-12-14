/*
 * Copyright 2009 Google Inc.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocParam;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateDivision;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import javax.annotation.Nullable;


/**
 * Visitor for finding the indirect params of a given template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Note that we can only recurse on non-external non-delegate callees to find indirect
 * params. (We can't recurse on delegates because we can't tell at compile time which delegate will
 * be called, if any.) Thus we return a {@code IndirectParamsInfo} object that includes a field
 * indicating whether we encountered any external/delegate callees that we couldn't recurse on.
 *
 * <p> {@link #exec} should be called on a {@code TemplateNode}.
 *
 */
public class FindIndirectParamsVisitor extends AbstractSoyNodeVisitor<IndirectParamsInfo> {


  /**
   * Return value for {@code FindIndirectParamsVisitor}.
   */
  public static class IndirectParamsInfo {

    /** Map from indirect param key to the param's SoyDoc info. */
    public final SortedMap<String, SoyDocParam> indirectParams;

    /** Multimap from param key (direct or indirect) to transitive callees that explicitly list the
     *  param. */
    public final Multimap<String, TemplateNode> paramKeyToCalleesMultimap;

    /** Whether the template (that the pass was run on) may have indirect params in external calls
     *  or delegate calls. */
    public final boolean mayHaveExternalIndirectParams;

    /**
     * @param indirectParams  Indirect params of the template (that the pass was run on).
     * @param paramKeyToCalleesMultimap Multimap from param key to callees that explicitly list the
     *     param.
     * @param mayHaveExternalIndirectParams Whether the template (that the pass was run on) may have
     *     indirect params in external calls or delegate calls.
     */
    public IndirectParamsInfo(SortedMap<String, SoyDocParam> indirectParams,
        Multimap<String, TemplateNode> paramKeyToCalleesMultimap,
        boolean mayHaveExternalIndirectParams) {
      this.indirectParams = indirectParams;
      this.paramKeyToCalleesMultimap = paramKeyToCalleesMultimap;
      this.mayHaveExternalIndirectParams = mayHaveExternalIndirectParams;
    }
  }


  /**
   * Private value class to hold the info we need to know about the call stack.
   */
  private static class CallerFrame {

    /** The current caller. */
    public final TemplateNode caller;

    /** Set of all callers in the current call stack. */
    public final Set<TemplateNode> allCallers;

    /** Set of all param keys passed explicitly (using the 'param' command) in any call in the
     *  current call stack. */
    public final Set<String> allCallParamKeys;

    /**
     * @param caller The current caller.
     * @param allCallers Set of all callers in the current call stack.
     * @param allCallParamKeys Set of all param keys passed explicitly (using the 'param' command)
     *     in any call in the current call stack.
     */
    public CallerFrame(TemplateNode caller, Set<TemplateNode> allCallers,
        Set<String> allCallParamKeys) {
      this.caller = caller;
      this.allCallers = allCallers;
      this.allCallParamKeys = allCallParamKeys;
    }
  }


  /**
   * Private value class to hold all the facets that make up a unique call situation. The meaning is
   * that if the same call situation is encountered multiple times in the pass, we only have to
   * visit the callee once for that situation. But if a new call situation is encountered, we must
   * visit the callee in that situation, even if we've previously visited the same callee under a
   * different situation.
   *
   * <p> The call situation facets include the callee (obviously) and allCallParamKeys, which is the
   * set of all param keys that were explicitly passed in the current call chain. The reason we need
   * allCallParamKeys is because, in this visitor, we're only interested in searching for indirect
   * params that may have been passed via data="all". As soon as a data key is passed explicitly in
   * the call chain, it becomes a key that was computed somewhere in the call chain, and not a key
   * that was passed via data="all". However, if this same callee is called during a different call
   * chain where the data key was not passed explicitly along the way, then that key once again
   * becomes a candidate for being an indirect param, which makes it a different situation for the
   * purpose of this visitor.
   *
   * <p> This class can be used for hash keys.
   */
  private static class CallSituation {

    /** The current callee. */
    private final TemplateNode callee;

    /** Set of all param keys passed explicitly (using the 'param' command) in any call in the
     *  current call stack, including the call to the current callee. */
    private final Set<String> allCallParamKeys;

    /**
     * @param callee The current callee.
     * @param allCallParamKeys Set of all param keys passed explicitly (using the 'param' command)
     *     in any call in the current call stack, including the call to the current callee.
     */
    public CallSituation(TemplateNode callee, Set<String> allCallParamKeys) {
      this.callee = callee;
      this.allCallParamKeys = allCallParamKeys;
    }

    @Override public boolean equals(Object other) {
      if (other == null || other.getClass() != this.getClass()) {
        return false;
      }
      CallSituation otherCallSit = (CallSituation) other;
      return otherCallSit.callee == this.callee &&
             otherCallSit.allCallParamKeys.equals(this.allCallParamKeys);
    }

    @Override public int hashCode() {
      return callee.hashCode() * 31 + allCallParamKeys.hashCode();
    }
  }


  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;

  /** Whether we're at the start of the pass. */
  private boolean isStartOfPass;

  /** The set of templates we've visited already (during pass). */
  private Set<CallSituation> visitedCallSituations;

  /** The current template whose body we're visiting (during pass). */
  private TemplateNode currTemplate;

  /** Set of new allCallers that includes the current template, for use when recursing into callees
   *  (during pass). This is built the first time a callee that needs to be visited is encountered.
   *  The same object is then reused for visits to subsequent callees of the current template. */
  private Set<TemplateNode> currNewAllCallers;

  /** The stack of info about callers to reach the current location (during pass). */
  private Deque<CallerFrame> callerStack;

  /** Map from indirect param key to the param's SoyDoc info. */
  private Map<String, SoyDocParam> indirectParams;

  /** Multimap from param key (direct or indirect) to callees that explicitly list the param. */
  private Multimap<String, TemplateNode> paramKeyToCalleesMultimap;

  /** Whether the template (that the pass was run on) may have indirect params in external calls
   *  or delegate calls. */
  private boolean mayHaveExternalIndirectParams;


  /**
   * @param templateRegistry Map from template name to TemplateNode to use during the pass.
   */
  public FindIndirectParamsVisitor(@Nullable TemplateRegistry templateRegistry) {
    this.templateRegistry = templateRegistry;
  }


  @Override public IndirectParamsInfo exec(SoyNode node) {

    isStartOfPass = true;
    visitedCallSituations = Sets.newHashSet();
    currTemplate = null;
    callerStack = new ArrayDeque<CallerFrame>();
    callerStack.add(
        new CallerFrame(null, ImmutableSet.<TemplateNode>of(), ImmutableSet.<String>of()));
    indirectParams = Maps.newHashMap();
    paramKeyToCalleesMultimap = HashMultimap.create();
    mayHaveExternalIndirectParams = false;

    visit(node);

    return new IndirectParamsInfo(ImmutableSortedMap.copyOf(indirectParams),
        paramKeyToCalleesMultimap, mayHaveExternalIndirectParams);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitTemplateNode(TemplateNode node) {

    // Build templateRegistry if necessary.
    if (templateRegistry == null) {
      SoyFileSetNode soyTree = (SoyFileSetNode) node.getParent().getParent();
      templateRegistry = new TemplateRegistry(soyTree);
    }

    if (isStartOfPass) {
      isStartOfPass = false;

    } else {
      // Add the params listed by this template.
      for (SoyDocParam param : node.getSoyDocParams()) {
        if (callerStack.peek().allCallParamKeys.contains(param.key)) {
          continue;  // param is actually not being passed by data="all"
        }
        if (! indirectParams.containsKey(param.key)) {
          indirectParams.put(param.key, param);
        }
        paramKeyToCalleesMultimap.put(param.key, node);
      }
    }

    // Visit children to recurse on callees.
    currTemplate = node;
    currNewAllCallers = null;  // built the first time it's needed
    visitChildren(node);
  }


  @Override protected void visitCallBasicNode(CallBasicNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    // We only want to recurse on calls that pass all data.
    if (!node.isPassingAllData()) {
      return;
    }

    TemplateBasicNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());

    // Note the template may be null because we allow calls to external templates not within this
    // Soy file set.
    if (callee == null) {
      mayHaveExternalIndirectParams = true;
      return;
    }

    // Visit the callee template.
    visitCalleeHelper(node, callee);
  }


  @Override protected void visitCallDelegateNode(CallDelegateNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    // We only want to recurse on calls that pass all data.
    if (!node.isPassingAllData()) {
      return;
    }

    // Important: There may be other delegate implementations not being compiled together with this
    // delegate call. Thus, we need to take the same precautions as for external calls.
    mayHaveExternalIndirectParams = true;

    // Visit all the possible callee templates.
    List<DelegateTemplateDivision> delTemplateDivisions =
        templateRegistry.getSortedDelegateTemplateDivisions(node.getDelCalleeName());
    if (delTemplateDivisions != null) {
      for (DelegateTemplateDivision division : delTemplateDivisions) {
        for (TemplateDelegateNode delCallee : division.delPackageNameToDelTemplateMap.values()) {
          visitCalleeHelper(node, delCallee);
        }
      }
    }
  }


  private void visitCalleeHelper(CallNode caller, TemplateNode callee) {

    // We must not revisit the current template or any templates already in the caller stack.
    if (callee == currTemplate || callerStack.peek().allCallers.contains(callee)) {
      return;
    }

    // Get the set of params that are passed explicitly in this call, but not already passed
    // explicitly in a previous call in the current call path. And then create the new set of
    // allCallParamKeys (reusing the old set if there are no additional call param keys).
    Set<String> prevAllCallParamKeys = callerStack.peek().allCallParamKeys;
    Set<String> additionalCallParamKeys = Sets.newHashSet();
    for (CallParamNode callParamNode : caller.getChildren()) {
      String callParamKey = callParamNode.getKey();
      if (! prevAllCallParamKeys.contains(callParamKey)) {
        additionalCallParamKeys.add(callParamKey);
      }
    }
    Set<String> newAllCallParamKeys;
    if (additionalCallParamKeys.size() > 0) {
      newAllCallParamKeys = Sets.newHashSet(prevAllCallParamKeys);
      newAllCallParamKeys.addAll(additionalCallParamKeys);
    } else {
      newAllCallParamKeys = prevAllCallParamKeys;
    }

    // Ensure we don't visit the same call situation more than once.
    CallSituation currCallSituation = new CallSituation(callee, newAllCallParamKeys);
    if (visitedCallSituations.contains(currCallSituation)) {
      return;
    }
    visitedCallSituations.add(currCallSituation);
    // Note: It's fine that the visit of the initial template doesn't get added to
    // visitedCallSituations, because we separately ensure (earlier in this method) that we don't
    // revisit the current template or any template already in the callee stack, and the initial
    // template will always satisfy one of those, so it will never be revisited. I.e. we'll never
    // get to the point where we need to use visitedCallSituations to prevent us from revisiting
    // the initial template.

    // Add caller frame.
    if (currNewAllCallers == null) {
      currNewAllCallers = Sets.newHashSet(callerStack.peek().allCallers);
      currNewAllCallers.add(currTemplate);
    }
    CallerFrame callerFrame = new CallerFrame(currTemplate, currNewAllCallers, newAllCallParamKeys);
    callerStack.push(callerFrame);

    // Visit the callee.
    visit(callee);

    // Remove caller frame and restore previous values of currTemplate and currNewAllCallers.
    CallerFrame poppedCallerFrame = callerStack.pop();
    if (poppedCallerFrame != callerFrame) {
      throw new AssertionError();
    }
    currTemplate = callerFrame.caller;
    currNewAllCallers = callerFrame.allCallers;
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
