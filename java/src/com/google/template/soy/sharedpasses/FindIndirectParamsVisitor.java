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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocParam;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Visitor for finding the indirect params of a given template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a {@code TemplateNode}.
 *
 * <p> Note that obviously we can only find indirect params that appear in the same Soy file
 * set. Thus we return a {@code IndirectParamsInfo} object that includes a field indicating
 * whether we encountered any external callees that we couldn't recurse on.
 *
 * @author Kai Huang
 */
public class FindIndirectParamsVisitor extends AbstractSoyNodeVisitor<IndirectParamsInfo> {


  /**
   * Return value for {@code FindIndirectParamsVisitor}.
   */
  public static class IndirectParamsInfo {

    /** Map from indirect param key to the param's SoyDoc info. */
    public final ImmutableMap<String, SoyDocParam> indirectParams;

    /** Multimap from param key (direct or indirect) to transitive callees that explicitly list the
     *  param. */
    public final Multimap<String, TemplateNode> paramKeyToCalleesMultimap;

    /** Whether the template (that the pass was run on) may have external indirect params (due to
     *  external calls that pass all data). */
    public final boolean mayHaveExternalIndirectParams;

    /**
     * @param indirectParams  Indirect params of the template (that the pass was run on).
     * @param paramKeyToCalleesMultimap Multimap from param key to callees that explicitly list the
     *     param.
     * @param mayHaveExternalIndirectParams Whether the template (that the pass was run on) may have
     *     external indirect params (due to external calls that pass all data).
     */
    public IndirectParamsInfo(
        ImmutableMap<String, SoyDocParam> indirectParams,
        Multimap<String, TemplateNode> paramKeyToCalleesMultimap,
        boolean mayHaveExternalIndirectParams) {
      this.indirectParams = indirectParams;
      this.paramKeyToCalleesMultimap = paramKeyToCalleesMultimap;
      this.mayHaveExternalIndirectParams = mayHaveExternalIndirectParams;
    }
  }


  /**
   * Private value class to hold the info we need to know about a caller in the stack.
   */
  private static class CallerFrame {

    /** The caller node. */
    public final TemplateNode caller;

    /** Set of param keys passed explicitly in the call (using the 'param' command). */
    public final Set<String> callParamKeys;

    /**
     * @param caller The caller node.
     * @param callParamKeys Set of param keys passed explicitly in the call (using the 'param'
     *     command).
     */
    public CallerFrame(TemplateNode caller, Set<String> callParamKeys) {
      this.caller = caller;
      this.callParamKeys = callParamKeys;
    }
  }


  /**
   * Whether to only visit each callee once, even if it appears in multiple call paths. This
   * eliminates the theoretical exponential run time of the pass, and still reports the same map
   * of {@code indirectParams}. So when this pass is used for checking correctness of params that
   * are listed in SoyDoc, setting this option to true is sufficient. However, if this pass is used
   * for generating info for users, and the option {@code shouldLimitResultsToOneCalleePerCallPath}
   * is set to true, then this option should be set to false in order to gather the most accurate
   * information in the {@code paramKeyToCalleesMultimap}.
   */
  private final boolean shouldOnlyVisitEachCalleeOnce;

  /**
   * When multiple callees in a specific call path list the same param in their SoyDocs, whether we
   * should report only the first callee or all callees. (In the case where the template that this
   * pass is being run on also lists the param, setting this option to false means we don't report
   * any callees.)
   *
   * This flag should be set to true when this pass is used for checking correctness of params that
   * are listed in SoyDoc. This flag should be set to false when this pass is used for generating
   * info for users about the templates and their parameters.
   */
  private final boolean shouldLimitResultsToOneCalleePerCallPath;

  /** Map from template name to TemplateNode used during the pass. */
  private Map<String, TemplateNode> templateNameToNodeMap;

  /** Whether we're at the start of the pass. */
  private boolean isStartOfPass;

  /** The current template whose body we're visiting. */
  private TemplateNode currTemplate;

  /** The set of templates we've visited already (during this pass). */
  private Set<TemplateNode> visitedTemplates;

  /** Memoized map from template to set of param keys listed in the template's SoyDoc. */
  private Map<TemplateNode, Set<String>> memoizedListedParamKeys;

  /** The stack of callers to reach the current location (during this pass). */
  private Deque<CallerFrame> callerStack;

  /** Map from indirect param key to the param's SoyDoc info. */
  private LinkedHashMap<String, SoyDocParam> indirectParams;

  /** Multimap from param key (direct or indirect) to callees that explicitly list the param. */
  private Multimap<String, TemplateNode> paramKeyToCalleesMultimap;

  /** Whether the template (that the pass was run on) may have external indirect params (due to
   *  external calls that pass all data). */
  private boolean mayHaveExternalIndirectParams;


  /**
   * @param shouldOnlyVisitEachCalleeOnce Whether to only visit each callee once, even if it appears
   *     in multiple call paths. This eliminates the theoretical exponential run time of the pass,
   *     and still reports the same map of {@code indirectParams}. So when this pass is used for
   *     checking correctness of params that are listed in SoyDoc, setting this option to true is
   *     sufficient. However, if this pass is used for generating info for users, and the option
   *     {@code shouldLimitResultsToOneCalleePerCallPath} is set to true, then this option should
   *     be set to false in order to gather the most accurate information in the
   *     {@code paramKeyToCalleesMultimap}.
   * @param shouldLimitResultsToOneCalleePerCallPath When multiple callees in a specific call path
   *     list the same param in their SoyDocs, whether we should report only the first callee or all
   *     callees. (In the case where the template that this pass is being run on also lists the
   *     param, setting this option to false means we don't report any callees.)
   *     This flag should be set to true when this pass is used for checking correctness of params
   *     that are listed in SoyDoc. This flag should be set to false when this pass is used for
   *     generating info for users about the templates and their parameters.
   * @param templateNameToNodeMap Map from template name to TemplateNode to use during the pass.
   */
  public FindIndirectParamsVisitor(
      boolean shouldOnlyVisitEachCalleeOnce, boolean shouldLimitResultsToOneCalleePerCallPath,
      @Nullable Map<String, TemplateNode> templateNameToNodeMap) {
    this.shouldOnlyVisitEachCalleeOnce = shouldOnlyVisitEachCalleeOnce;
    this.shouldLimitResultsToOneCalleePerCallPath = shouldLimitResultsToOneCalleePerCallPath;
    this.templateNameToNodeMap = templateNameToNodeMap;
  }


  @Override protected void setup() {
    isStartOfPass = true;
    currTemplate = null;
    visitedTemplates = Sets.newHashSet();
    memoizedListedParamKeys = Maps.newHashMap();
    callerStack = new ArrayDeque<CallerFrame>();
    indirectParams = Maps.newLinkedHashMap();
    paramKeyToCalleesMultimap = LinkedHashMultimap.create();
    mayHaveExternalIndirectParams = false;
  }


  @Override protected IndirectParamsInfo getResult() {
    return new IndirectParamsInfo(ImmutableMap.copyOf(indirectParams), paramKeyToCalleesMultimap,
                                  mayHaveExternalIndirectParams);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(TemplateNode node) {

    // Note: The way we've designed this pass, only the template that the pass was called on will
    // be directly visited, and this should of course happen at the start of the pass. For callees,
    // we simply call visitChildren(callee). So any time after the start of the pass, there should
    // be no visits to TemplateNode.
    if (!isStartOfPass) {
      throw new AssertionError(
          "In the way this pass is designed, callee templates should not be visited. Instead, we" +
          " call visitChildren() directly on the callee.");
    }
    isStartOfPass = false;

    // Build templateNameToNodeMap if necessary.
    if (templateNameToNodeMap == null) {
      SoyFileSetNode soyTree = (SoyFileSetNode) node.getParent().getParent();
      templateNameToNodeMap = Maps.newHashMap();
      for (SoyFileNode soyFile : soyTree.getChildren()) {
        for (TemplateNode template : soyFile.getChildren()) {
          templateNameToNodeMap.put(template.getTemplateName(), template);
        }
      }
    }

    currTemplate = node;
    visitedTemplates.add(node);
    visitChildren(node);
  }


  @Override protected void visitInternal(CallNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    // We only want to recurse on calls that pass all data.
    if (!node.isPassingAllData()) {
      return;
    }

    TemplateNode callee = templateNameToNodeMap.get(node.getCalleeName());

    // Note the template may be null because we allow calls to external templates not within this
    // Soy file set.
    if (callee == null) {
      mayHaveExternalIndirectParams = true;
      return;
    }

    // If we're only supposed to visit each callee once, check if callee has been visited.
    if (shouldOnlyVisitEachCalleeOnce && visitedTemplates.contains(callee)) {
      return;
    }

    // ------ Process the callee. ------

    // Get the set of params that are passed explicitly in this call.
    Set<String> callParamKeys = Sets.newHashSet();
    for (CallParamNode callParamNode : node.getChildren()) {
      callParamKeys.add(callParamNode.getKey());
    }

    // Add caller frame.
    CallerFrame callerFrame = new CallerFrame(currTemplate, callParamKeys);
    callerStack.push(callerFrame);

    // We must not recurse on templates that are already in the callee stack.
    if (isTemplateInStack(callee)) {
      return;
    }

    // Add the params listed by the callee.
    for (SoyDocParam param : callee.getSoyDocParams()) {
      if (isParamPassedExplicitly(param.key)) {
        continue;  // param is actually not being passed by data="all"
      }
      if (shouldLimitResultsToOneCalleePerCallPath && isParamListedByEarlierTemplate(param.key)) {
        continue;  // param has already been reported for this particular call path
      }
      if (!indirectParams.containsKey(param.key)) {
        indirectParams.put(param.key, param);
      }
      paramKeyToCalleesMultimap.put(param.key, callee);
    }

    // Visit callee body to recurse on further indirect callees.
    currTemplate = callee;
    visitedTemplates.add(callee);
    visitChildren(callee);

    // Remove caller frame and restore old currTemplate.
    CallerFrame poppedCallerFrame = callerStack.pop();
    if (poppedCallerFrame != callerFrame) {
      throw new AssertionError();
    }
    currTemplate = callerFrame.caller;
  }


  /**
   * Private helper for {@code visitInternal(CallNode)} to check whether a template is already in
   * the caller stack.
   * @param template The template to check.
   * @return Whether the given template is already in the caller stack.
   */
  private boolean isTemplateInStack(TemplateNode template) {

    for (CallerFrame callerFrame : callerStack) {
      if (callerFrame.caller == template) {
        return true;
      }
    }
    return false;
  }


  /**
   * Private helper for {@code visitInternal(CallNode)} to check whether a param key is being
   * passed explicitly in the current call path.
   * @param paramKey The param key to check.
   * @return Whether the given param key is being passed explicitly in the current call path.
   */
  private boolean isParamPassedExplicitly(String paramKey) {

    for (CallerFrame callerFrame : callerStack) {
      if (callerFrame.callParamKeys.contains(paramKey)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Private helper for {@code visitInternal(CallNode)} to check whether a param key is already
   * listed in the SoyDoc of an earlier template in the current call path.
   * @param paramKey The param key to check.
   * @return Whether the given param key is already listed in the SoyDoc of an earlier template in
   *     the current call path.
   */
  private boolean isParamListedByEarlierTemplate(String paramKey) {

    for (CallerFrame callerFrame : callerStack) {
      TemplateNode caller = callerFrame.caller;
      Set<String> callerListedParamKeys = memoizedListedParamKeys.get(caller);

      if (callerListedParamKeys == null) {
        callerListedParamKeys = Sets.newHashSet();
        for (SoyDocParam param : caller.getSoyDocParams()) {
          callerListedParamKeys.add(param.key);
        }
        memoizedListedParamKeys.put(caller, callerListedParamKeys);
      }

      if (callerListedParamKeys.contains(paramKey)) {
        return true;
      }
    }

    return false;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for other nodes.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    visitChildren(node);
  }

}
