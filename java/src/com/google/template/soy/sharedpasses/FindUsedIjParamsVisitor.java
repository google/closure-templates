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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.sharedpasses.FindUsedIjParamsVisitor.UsedIjParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
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
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Visitor for finding the used injected params of a given template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Note that we can only recurse on non-external non-delegate callees to find used injected
 * params. (We can't recurse on delegates because we can't tell at compile time which delegate will
 * be called, if any.) Thus we return a {@code UsedIjParamsInfo} object that includes a field
 * indicating whether we encountered any external/delegate callees that we couldn't recurse on.
 *
 * <p> {@link #exec} should be called on a {@code TemplateNode}.
 *
 */
public class FindUsedIjParamsVisitor extends AbstractSoyNodeVisitor<UsedIjParamsInfo> {


  /**
   * Return value for {@code FindUsedIjParamsVisitor}.
   */
  public static class UsedIjParamsInfo {

    /** Multimap from used injected param key to transitive callees that use the param. */
    public final Multimap<String, TemplateNode> usedIjParamToCalleesMultimap;

    /** Whether the template (that the pass was run on) may have injected params indirectly used in
     *  external calls or delegate calls. */
    public final boolean mayHaveExternalUsedIjParams;

    /**
     * @param usedIjParamToCalleesMultimap Multimap from used injected param key to transitive
     *     callees that use the param.
     * @param mayHaveExternalUsedIjParams Whether the template (that the pass was run on) may have
     *     injected params indirectly used in external calls or delegate calls.
     */
    public UsedIjParamsInfo(Multimap<String, TemplateNode> usedIjParamToCalleesMultimap,
        boolean mayHaveExternalUsedIjParams) {
      this.usedIjParamToCalleesMultimap = usedIjParamToCalleesMultimap;
      this.mayHaveExternalUsedIjParams = mayHaveExternalUsedIjParams;
    }
  }


  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;

  /** The set of templates we've visited already (during pass). */
  private Set<TemplateNode> visitedTemplates;

  /** The current template whose body we're visiting (during pass). */
  private TemplateNode currTemplate;

  /** The stack of callers to reach the current location (during this pass). */
  private Deque<TemplateNode> callerStack;

  /** Multimap from used injected param key to transitive callees that use the param. */
  private Multimap<String, TemplateNode> usedIjParamToCalleesMultimap;

  /** Whether the template (that the pass was run on) may have injected params indirectly used in
   *  external calls or delegate calls. */
  private boolean mayHaveExternalUsedIjParams;


  /**
   * @param templateRegistry Map from template name to TemplateNode to use during the pass.
   */
  public FindUsedIjParamsVisitor(@Nullable TemplateRegistry templateRegistry) {
    this.templateRegistry = templateRegistry;
  }


  @Override public UsedIjParamsInfo exec(SoyNode node) {

    visitedTemplates = Sets.newHashSet();
    currTemplate = null;
    callerStack = new ArrayDeque<TemplateNode>();
    usedIjParamToCalleesMultimap = LinkedHashMultimap.create();
    mayHaveExternalUsedIjParams = false;

    visit(node);

    return new UsedIjParamsInfo(usedIjParamToCalleesMultimap, mayHaveExternalUsedIjParams);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitTemplateNode(TemplateNode node) {

    // Build templateRegistry if necessary.
    if (templateRegistry == null) {
      SoyFileSetNode soyTree = (SoyFileSetNode) node.getParent().getParent();
      templateRegistry = new TemplateRegistry(soyTree);
    }

    // Record that we've visited this template.
    visitedTemplates.add(node);

    // Add the injected params locally used in this template.
    FindUsedIjParamsInExprHelperVisitor helperVisitor = new FindUsedIjParamsInExprHelperVisitor();
    SoytreeUtils.execOnAllV2Exprs(node, helperVisitor);
    Set<String> locallyUsedIjParams = helperVisitor.getResult();

    for (String param : locallyUsedIjParams) {
      usedIjParamToCalleesMultimap.put(param, node);
    }

    // Visit the template body to find calls to recurse on.
    currTemplate = node;
    visitChildren(node);
  }


  @Override protected void visitCallBasicNode(CallBasicNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    TemplateBasicNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());

    // Note the template may be null because we allow calls to external templates not within this
    // Soy file set.
    if (callee == null) {
      mayHaveExternalUsedIjParams = true;
      return;
    }

    // Visit the callee template.
    visitCalleeHelper(callee);
  }


  @Override protected void visitCallDelegateNode(CallDelegateNode node) {

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);

    // Important: There may be other delegate implementations not being compiled together with this
    // delegate call. Thus, we need to take the same precautions as for external calls.
    mayHaveExternalUsedIjParams = true;

    // Visit all the possible callee templates.
    List<DelegateTemplateDivision> delTemplateDivisions =
        templateRegistry.getSortedDelegateTemplateDivisions(node.getDelCalleeName());
    if (delTemplateDivisions != null) {
      for (DelegateTemplateDivision division : delTemplateDivisions) {
        for (TemplateDelegateNode delCallee : division.delPackageNameToDelTemplateMap.values()) {
          visitCalleeHelper(delCallee);
        }
      }
    }
  }


  private void visitCalleeHelper(TemplateNode callee) {

    // We must not revisit the current template or any templates already in the callee stack.
    if (callee == currTemplate || callerStack.contains(callee)) {
      return;
    }

    // Ensure we don't visit the same template more than once.
    if (visitedTemplates.contains(callee)) {
      return;
    }

    // Visit the callee template.
    callerStack.push(currTemplate);
    visit(callee);
    currTemplate = callerStack.pop();
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
