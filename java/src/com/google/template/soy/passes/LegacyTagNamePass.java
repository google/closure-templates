/*
 * Copyright 2020 Google Inc.
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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import java.util.HashSet;
import java.util.Set;

/**
 * Rewrites {@code <{legacyTagName($tag)}>} to {@code <{$tag}>} and disallows all other print nodes
 * that name HTML tags.
 */
@RunAfter(ResolvePluginsPass.class)
@RunBefore(ResolveExpressionTypesPass.class)
final class LegacyTagNamePass implements CompilerFilePass {

  private static final SoyErrorKind ILLEGAL_USE =
      SoyErrorKind.of("''legacyDynamicTag'' may only be used to name an HTML tag.");

  private static final SoyErrorKind NEED_WRAP =
      SoyErrorKind.of("A dynamic tag name should be wrapped in the ''legacyDynamicTag'' function.");

  private final ErrorReporter errorReporter;

  LegacyTagNamePass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    Set<FunctionNode> correctlyPlaced = new HashSet<>();

    for (HtmlTagNode tagNode : SoyTreeUtils.getAllNodesOfType(file, HtmlTagNode.class)) {
      TagName name = tagNode.getTagName();
      if (name.isStatic()) {
        continue;
      }
      PrintNode printNode = name.getDynamicTagName();
      ExprNode exprNode = printNode.getExpr().getRoot();
      if (exprNode.getKind() == Kind.FUNCTION_NODE
          && ((FunctionNode) exprNode).getSoyFunction() == BuiltinFunction.LEGACY_DYNAMIC_TAG) {
        FunctionNode functionNode = (FunctionNode) exprNode;
        if (functionNode.numChildren() == 1) {
          printNode.getExpr().clearChildren();
          printNode.getExpr().addChild(functionNode.getChild(0));
        } else {
          // ResolvePluginsPass will tag this as an error since function arity is 1.
        }
        correctlyPlaced.add(functionNode);
      } else {
        // Eventually all other cases should be disallowed.
        errorReporter.warn(printNode.getExpr().getSourceLocation(), NEED_WRAP);
      }
    }

    // No other uses of legacyDynamicTag are allowed.
    for (FunctionNode fn :
        SoyTreeUtils.getAllFunctionInvocations(file, BuiltinFunction.LEGACY_DYNAMIC_TAG)) {
      if (!correctlyPlaced.contains(fn)) {
        errorReporter.report(fn.getSourceLocation(), ILLEGAL_USE);
      }
    }
  }
}
