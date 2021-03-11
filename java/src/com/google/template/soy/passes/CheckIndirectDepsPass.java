/*
 * Copyright 2011 Google Inc.
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
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.function.Supplier;

/** Checks that all calls are to direct, and not indirect, deps. */
@RunAfter(SoyConformancePass.class) // not really needed
public final class CheckIndirectDepsPass implements CompilerFileSetPass {

  private static final SoyErrorKind CALL_TO_INDIRECT_DEPENDENCY =
      SoyErrorKind.of(
          "Call is satisfied only by indirect dependency {0}. Add it as a direct dependency."
          ,
          StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter errorReporter;
  private final Supplier<TemplateRegistry> templateRegistryFull;

  public CheckIndirectDepsPass(
      ErrorReporter errorReporter, Supplier<TemplateRegistry> templateRegistryFull) {
    this.errorReporter = errorReporter;
    this.templateRegistryFull = templateRegistryFull;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    sourceFiles.stream()
        .flatMap(f -> SoyTreeUtils.allNodesOfType(f, TemplateLiteralNode.class))
        .forEach(this::checkTemplateLiteralNode);
    return Result.CONTINUE;
  }

  // TODO(gboyer): Consider some deltemplate checking, but it's hard to make a coherent case for
  // deltemplates since it's legitimate to have zero implementations, or to have the implementation
  // in a different part of the dependency graph (if it's late-bound).
  private void checkTemplateLiteralNode(TemplateLiteralNode node) {
    TemplateMetadata callee =
        templateRegistryFull.get().getBasicTemplateOrElement(node.getResolvedName());

    if (callee == null) {
      return;
    }

    SoyFileKind calleeKind = callee.getSoyFileKind();
    String callerFilePath = node.getSourceLocation().getFilePath().path();
    String calleeFilePath = callee.getSourceLocation().getFilePath().path();
    if (calleeKind == SoyFileKind.INDIRECT_DEP) {
      errorReporter.report(
          node.getSourceLocation(),
          CALL_TO_INDIRECT_DEPENDENCY,
          calleeFilePath);
    }
  }
}
