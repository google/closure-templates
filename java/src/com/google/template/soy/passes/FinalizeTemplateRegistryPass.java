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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.Metadata;
import com.google.template.soy.soytree.SoyFileNode;
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
  private final Supplier<FileSetMetadata> registryFromDeps;
  private final Consumer<FileSetMetadata> fullRegSetter;

  public FinalizeTemplateRegistryPass(
      ErrorReporter errorReporter,
      Supplier<FileSetMetadata> registryFromDeps,
      Consumer<FileSetMetadata> fullRegSetter) {
    this.errorReporter = errorReporter;
    this.registryFromDeps = registryFromDeps;
    this.fullRegSetter = fullRegSetter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    fullRegSetter.accept(
        Metadata.metadataForAst(registryFromDeps.get(), sourceFiles, errorReporter, null));
    return Result.CONTINUE;
  }
}
