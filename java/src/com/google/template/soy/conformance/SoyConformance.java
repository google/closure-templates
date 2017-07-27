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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for collecting Soy conformance violations. Performs a single pass over the AST, aggregating
 * results from different conformance rules.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class SoyConformance {

  /**
   * Returns a new SoyConformance object that enforces the rules in the given configs
   *
   * <p>The config files are expected to be text protos of type {@link ConformanceConfig}.
   */
  public static SoyConformance create(ValidatedConformanceConfig conformanceConfig) {
    return new SoyConformance(conformanceConfig.getRules());
  }

  private final ImmutableList<RuleWithWhitelists> rules;

  SoyConformance(ImmutableList<RuleWithWhitelists> rules) {
    this.rules = rules;
  }

  /** Performs the overall check. */
  public void check(SoyFileNode file, final ErrorReporter errorReporter) {
    // first filter to only the rules that need to be checked for this file.
    final List<Rule<?>> rulesForFile = new ArrayList<>(rules.size());
    String filePath = file.getFilePath();
    for (RuleWithWhitelists rule : rules) {
      if (rule.shouldCheckConformanceFor(filePath)) {
        rulesForFile.add(rule.getRule());
      }
    }
    if (rulesForFile.isEmpty()) {
      return;
    }
    SoyTreeUtils.visitAllNodes(
        file,
        new NodeVisitor<Node, Boolean>() {
          @Override
          public Boolean exec(Node node) {
            for (Rule<?> rule : rulesForFile) {
              rule.doCheckConformance(node, errorReporter);
            }
            // always visit all children
            return true;
          }
        });
  }
}
