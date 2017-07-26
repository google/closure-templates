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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.conformance.Requirement.RequirementTypeCase;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.io.IOException;
import java.io.InputStream;
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
  public static SoyConformance create(Iterable<ByteSource> conformanceConfigs) {
    ImmutableList.Builder<ConformanceConfig> builder = new ImmutableList.Builder<>();
    for (ByteSource config : conformanceConfigs) {
      try {
        ConformanceConfig cc = fromByteSource(config);
        builder.add(cc);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return new SoyConformance(RuleWithWhitelists.forConformanceConfigs(builder.build()));
  }

  private static ConformanceConfig fromByteSource(ByteSource configSource) throws IOException {
    ConformanceConfig config;
    try (InputStream stream = configSource.openStream()) {
      config = ConformanceConfig.parseFrom(stream);
    }
    for (Requirement r : config.getRequirementList()) {
      Preconditions.checkArgument(r.hasErrorMessage(), "requirement missing error message");
      Preconditions.checkArgument(
          r.getRequirementTypeCase() != RequirementTypeCase.REQUIREMENTTYPE_NOT_SET,
          "requirement missing type");
    }
    return config;
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
