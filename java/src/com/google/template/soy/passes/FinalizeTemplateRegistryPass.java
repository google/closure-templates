/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.FileSetTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builds the final TemplateRegistry -- containing both deps and sources -- for subsequent passes.
 */
@RunAfter({
  // Also ElementAttributePass.class but that pass may not run depending on settings.
  SoyElementPass.class // Calls setHtmlElementMetadata
})
class FinalizeTemplateRegistryPass implements CompilerFileSetPass {

  private final ErrorReporter errorReporter;
  private final Supplier<TemplateRegistry> registryFromDeps;
  private final Consumer<TemplateRegistry> fullRegSetter;

  public FinalizeTemplateRegistryPass(
      ErrorReporter errorReporter,
      Supplier<TemplateRegistry> registryFromDeps,
      Consumer<TemplateRegistry> fullRegSetter) {
    this.errorReporter = errorReporter;
    this.registryFromDeps = registryFromDeps;
    this.fullRegSetter = fullRegSetter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    FileSetTemplateRegistry.Builder builder =
        ((FileSetTemplateRegistry) registryFromDeps.get()).toBuilder(errorReporter);
    for (SoyFileNode node : sourceFiles) {
      builder.addTemplates(
          node.getTemplates().stream()
              .map(TemplateMetadata::fromTemplate)
              .collect(toImmutableList()));
    }
    fullRegSetter.accept(builder.build());
    return Result.CONTINUE;
  }
}
