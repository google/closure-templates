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

package com.google.template.soy.exprtree.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parses expressions, running all passes of the compiler so that the resulting expression node is
 * what would be in the AST for a real compile.
 */
public final class ExpressionParser {

  private final String expression;
  private final List<String> vars;
  private final List<String> params;
  private final List<SoySourceFunction> functions;
  private final Set<GenericDescriptor> protos;
  private final List<String> experimentalFeatures;

  /** Constructs a new {@link ExpressionParser} to parse the given expression. */
  public ExpressionParser(String expression) {
    this.expression = expression;
    vars = new ArrayList<>();
    params = new ArrayList<>();
    functions = new ArrayList<>();
    protos = new HashSet<>();
    experimentalFeatures = new ArrayList<>();
  }

  /** Configures the expression to be parsed with a parameter with the given name and type. */
  public ExpressionParser withParam(String name, String type) {
    params.add(String.format("{@param %s: %s}", name, type));
    return this;
  }

  /**
   * Configures the expression to be parsed with a let variable with the given name and expression
   * value.
   */
  public ExpressionParser withVar(String name, String expression) {
    vars.add(String.format("{let $%s: %s /}", name, expression));
    return this;
  }

  /** Configures the expression to be parsed with the given function. */
  public ExpressionParser withFunction(SoySourceFunction function) {
    functions.add(function);
    return this;
  }

  /** Configures the expression to be parsed with the given proto. */
  public ExpressionParser withProto(GenericDescriptor proto) {
    protos.add(proto);
    return this;
  }

  public ExpressionParser withExperimentalFeatures(String... experimentalFeatures) {
    Collections.addAll(this.experimentalFeatures, experimentalFeatures);
    return this;
  }

  /** Parses the given expression and returns it as an {@link ExprNode}, if possible. */
  public ExprNode parse() {
    Optional<StandaloneNode> parent = parseForParentNode();
    checkArgument(
        parent.isPresent(),
        "This expression evaluates to nothing (empty string). Use #parseForParentNode instead.");
    checkArgument(
        parent.get().getKind() == Kind.PRINT_NODE,
        "This expression evaluates to a %s node. Use #parseForParentNode instead.",
        parent.get().getKind());
    return ((PrintNode) parent.get()).getExpr().getRoot();
  }

  /**
   * Parses the given expression and returns the parsed expression's parent node, if possible.
   *
   * <p>This will return absent if the given expression gets compiled away, e.g. if the expression
   * optimizes to an empty string. This will otherwise return the parent of the resulting
   * expression. This could be a {@link com.google.template.soy.soytree.RawTextNode} if the
   * expression gets optimized to a string, otherwise it'll likely be a {@link PrintNode},
   * containing the parsed expression.
   */
  public Optional<StandaloneNode> parseForParentNode() {
    List<String> lines = new ArrayList<>(params);
    lines.addAll(vars);
    lines.add(String.format("{%s}", expression));
    String contents = Joiner.on('\n').join(lines);

    SoyTypeRegistry typeRegistry = SharedTestUtils.importing(protos);

    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forFileAndImports(
                SharedTestUtils.NS,
                SharedTestUtils.buildTestTemplateContent(false, contents),
                protos.toArray(new GenericDescriptor[0]))
            .runOptimizer(true)
            .addSoySourceFunctions(functions)
            .typeRegistry(typeRegistry)
            .enableExperimentalFeatures(ImmutableList.copyOf(experimentalFeatures))
            .errorReporter(ErrorReporter.explodeOnErrorsAndIgnoreWarnings())
            .parse()
            .fileSet();

    TemplateNode template =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(fileSet, TemplateNode.class));
    if (template.numChildren() == 0) {
      return Optional.empty();
    }
    checkState(template.numChildren() == 1);
    return Optional.of(template.getChild(0));
  }
}
