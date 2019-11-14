/*
 * Copyright 2014 Google Inc.
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

package com.google.template.soy.conformance;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;

/**
 * Conformance rule banning particular CSS selectors.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class BannedCssSelector extends Rule<FunctionNode> {

  private final ImmutableSet<String> bannedSelectors;
  private final boolean whenPrefix;

  BannedCssSelector(ImmutableSet<String> bannedSelectors, boolean whenPrefix, SoyErrorKind error) {
    super(error);
    this.bannedSelectors = bannedSelectors;
    this.whenPrefix = whenPrefix;
  }

  @Override
  protected void doCheckConformance(FunctionNode node, ErrorReporter errorReporter) {
    // We can't compare against the actual function name because ResolveFunctionsPass hasn't run
    // yet.
    // We can't delay running this until after the ResolveFunctions pass because then some
    // conformance rules will fail due to code injected by some of the rewriting passes.
    // TODO(lukes): all the conformance checks that blow up on the rewritten code are
    // BanTextEverywhereExceptComments rules.  We should eliminate those rules and replace with
    // more targeted bans which we can better control. Not only because this would yeild better
    // error messages, but also because the BanTextEverywhereExceptComments is super slow and adds
    // latency to compile times
    if (node.getFunctionName().equals("css")) {
      ExprNode selectorTextNode = node.numChildren() == 2 ? node.getChild(1) : node.getChild(0);
      if (selectorTextNode instanceof StringNode) {
        String selector = ((StringNode) selectorTextNode).getValue();
        if (this.whenPrefix) {
          for (String bannedSelector : bannedSelectors) {
            if (selector.startsWith(bannedSelector)) {
              errorReporter.report(selectorTextNode.getSourceLocation(), error);
            }
          }
        } else {
          if (bannedSelectors.contains(selector)) {
            errorReporter.report(selectorTextNode.getSourceLocation(), error);
          }
        }
      }
    }
  }
}
