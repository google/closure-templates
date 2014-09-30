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

package com.google.template.soy.jssrc.internal;

import com.google.common.collect.Sets;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoytreeUtils;

import java.util.Map;
import java.util.SortedSet;

import javax.inject.Inject;

/**
 * A visitor to generate a set of Closure JS library names required by the function plugins used by
 * this template.
 *
 */
class GenFunctionPluginRequiresVisitor {


  /** Map of all SoyLibraryAssistedJsSrcFunctions */
  private final Map<String, SoyLibraryAssistedJsSrcFunction> soyLibraryAssistedJsSrcFunctionsMap;

  /** Set storage for the i18n namespaces */
  private SortedSet<String> requiredJsLibNames;

  @Inject
  public GenFunctionPluginRequiresVisitor(
      Map<String, SoyLibraryAssistedJsSrcFunction> soyLibraryAssistedJsSrcFunctionsMap) {
    this.soyLibraryAssistedJsSrcFunctionsMap = soyLibraryAssistedJsSrcFunctionsMap;
  }


  public SortedSet<String> exec(SoyFileNode soyFile) {
    requiredJsLibNames = Sets.newTreeSet();

    GenFunctionPluginRequiresHelperVisitor helperVisitor =
        new GenFunctionPluginRequiresHelperVisitor();

    SoytreeUtils.execOnAllV2Exprs(soyFile, helperVisitor);

    return requiredJsLibNames;
  }


  private class GenFunctionPluginRequiresHelperVisitor
     extends AbstractExprNodeVisitor<SortedSet<String>> {


    @Override protected void visitFunctionNode(FunctionNode node) {
      String functionName = node.getFunctionName();
      if (soyLibraryAssistedJsSrcFunctionsMap.containsKey(functionName)) {
        requiredJsLibNames.addAll(
            soyLibraryAssistedJsSrcFunctionsMap.get(functionName).getRequiredJsLibNames());
      }

      visitChildren(node);
    }


    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }


}
