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
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.SortedSet;

/**
 * A visitor to generate a set of Closure JS library names required by the function plugins used by
 * this template.
 *
 */
final class GenFunctionPluginRequiresVisitor {

  /** Set storage for the i18n namespaces */
  private SortedSet<String> requiredJsLibNames;

  SortedSet<String> exec(SoyFileNode soyFile) {
    requiredJsLibNames = Sets.newTreeSet();

    GenFunctionPluginRequiresHelperVisitor helperVisitor =
        new GenFunctionPluginRequiresHelperVisitor();

    SoyTreeUtils.execOnAllV2Exprs(soyFile, helperVisitor);

    return requiredJsLibNames;
  }

  private final class GenFunctionPluginRequiresHelperVisitor
      extends AbstractExprNodeVisitor<SortedSet<String>> {

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      SoyFunction soyFunction = node.getSoyFunction();
      if (soyFunction instanceof SoyLibraryAssistedJsSrcFunction) {
        requiredJsLibNames.addAll(
            ((SoyLibraryAssistedJsSrcFunction) soyFunction).getRequiredJsLibNames());
      }
      visitChildren(node);
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }
}
