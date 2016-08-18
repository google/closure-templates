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

package com.google.template.soy.passes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;

import java.util.List;
import java.util.Set;

/**
 * Visitor for running some sanity checks on calls.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
final class CheckCallsVisitor extends AbstractSoyNodeVisitor<List<String>> {

  private static final SoyErrorKind MISSING_PARAM =
      SoyErrorKind.of("Call missing required {0}.");
  private static final SoyErrorKind DUPLICATE_PARAM =
      SoyErrorKind.of("Duplicate param ''{0}''.");

  /** A template registry built from the Soy tree. */
  private final TemplateRegistry templateRegistry;

  private final ErrorReporter errorReporter;

  CheckCallsVisitor(TemplateRegistry templateRegistry, ErrorReporter errorReporter) {
    this.templateRegistry = templateRegistry;
    this.errorReporter = errorReporter;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitCallNode(CallNode node) {

    // Recurse.
    visitChildren(node);

    // If all the data keys being passed are listed using 'param' commands, then check that all
    // required params of the callee are included.
    if (!node.dataAttribute().isPassingData()) {

      // Get the callee node (basic or delegate).
      TemplateNode callee = null;
      if (node instanceof CallBasicNode) {
        callee = templateRegistry.getBasicTemplate(((CallBasicNode) node).getCalleeName());
      } else {
        String delTemplateName = ((CallDelegateNode) node).getDelCalleeName();
        ImmutableList<TemplateDelegateNode> potentialCallees =
            templateRegistry
                .getDelTemplateSelector()
                .delTemplateNameToValues()
                .get(delTemplateName);
        if (!potentialCallees.isEmpty()) {
          callee = potentialCallees.get(0);
        }
      }

      // Do the check if the callee node has declared params.
      if (callee != null && callee.getParams() != null) {
        // Get param keys passed by caller.
        Set<String> callerParamKeys = Sets.newHashSet();
        for (CallParamNode callerParam : node.getChildren()) {
          boolean isUnique = callerParamKeys.add(callerParam.getKey());
          if (!isUnique) {
            // Found a duplicate param.
            errorReporter.report(
                callerParam.getSourceLocation(), DUPLICATE_PARAM, callerParam.getKey());
          }
        }
        // Check param keys required by callee.
        List<String> missingParamKeys = Lists.newArrayListWithCapacity(2);
        for (TemplateParam calleeParam : callee.getParams()) {
          if (calleeParam.isRequired() && ! callerParamKeys.contains(calleeParam.name())) {
            missingParamKeys.add(calleeParam.name());
          }
        }
        // Report errors.
        if (!missingParamKeys.isEmpty()) {
          String errorMsgEnd = (missingParamKeys.size() == 1)
                  ? "param '" + missingParamKeys.get(0) + "'"
                  : "params " + missingParamKeys;
          errorReporter.report(node.getSourceLocation(), MISSING_PARAM, errorMsgEnd);
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
