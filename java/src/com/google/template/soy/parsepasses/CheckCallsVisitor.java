/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocParam;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateDivision;

import java.util.List;
import java.util.Set;


/**
 * Visitor for running some sanity checks on calls.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class CheckCallsVisitor extends AbstractSoyNodeVisitor<List<String>> {


  /** A template registry built from the Soy tree. */
  private TemplateRegistry templateRegistry;


  @Override public List<String> exec(SoyNode soyNode) {

    Preconditions.checkArgument(soyNode instanceof SoyFileSetNode);

    templateRegistry = new TemplateRegistry((SoyFileSetNode) soyNode);
    super.exec(soyNode);

    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitCallNode(CallNode node) {

    // Recurse.
    visitChildren(node);

    // If all the data keys being passed are listed using 'param' commands, then check that all
    // required params of the callee are included.
    if (! node.isPassingData()) {

      // Get the callee node (basic or delegate).
      TemplateNode callee;
      if (node instanceof CallBasicNode) {
        callee = templateRegistry.getBasicTemplate(((CallBasicNode) node).getCalleeName());
      } else {
        Set<DelegateTemplateDivision> divisions =
            templateRegistry.getDelTemplateDivisionsForAllVariants(
                ((CallDelegateNode) node).getDelCalleeName());
        if (divisions != null) {
          callee = Iterables.get(
              Iterables.getFirst(divisions, null).delPackageNameToDelTemplateMap.values(), 0);
        } else {
          callee = null;
        }
      }

      // Do the check if the callee node has SoyDoc.
      if (callee != null && callee.getSoyDocParams() != null) {
        // Get param keys passed by caller.
        Set<String> callerParamKeys = Sets.newHashSet();
        for (CallParamNode callerParam : node.getChildren()) {
          callerParamKeys.add(callerParam.getKey());
        }
        // Check param keys required by callee.
        List<String> missingParamKeys = Lists.newArrayListWithCapacity(2);
        for (SoyDocParam calleeParam : callee.getSoyDocParams()) {
          if (calleeParam.isRequired && ! callerParamKeys.contains(calleeParam.key)) {
            missingParamKeys.add(calleeParam.key);
          }
        }
        // Report errors.
        if (missingParamKeys.size() > 0) {
          String errorMsgEnd = (missingParamKeys.size() == 1) ?
              "param '" + missingParamKeys.get(0) + "'" : "params " + missingParamKeys;
          throw SoySyntaxExceptionUtils.createWithNode(
              String.format(
                  "Call to '%s' is missing required %s.",
                  callee.getTemplateNameForUserMsgs(), errorMsgEnd),
              node);
        }
      }
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
