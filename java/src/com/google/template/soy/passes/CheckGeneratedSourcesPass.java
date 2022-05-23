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
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import java.util.regex.Pattern;

/**
 * Enforces that any generated source files contain a special comment indicating that they were
 * generated from a blessed source generator.
 */
@RunBefore(FinalizeTemplateRegistryPass.class) // Does not actually matter.
public class CheckGeneratedSourcesPass implements CompilerFileSetPass {

  private static final SoyErrorKind UNBLESSED_GENERATED_FILE =
      SoyErrorKind.of(
          "Encountered a generated Soy source file but "
              +
              "compiler was not invoked with --allow_unblessed_generated_files.");

  private static final Pattern BLESS_COMMENT = Pattern.compile("@SoySourceGenerator=[\\w\\-/]+\\b");

  private final ErrorReporter errorReporter;
  private final ImmutableSet<SourceFilePath> generatedPaths;

  public CheckGeneratedSourcesPass(
      ErrorReporter errorReporter, ImmutableSet<SourceFilePath> generatedPaths) {
    this.errorReporter = errorReporter;
    this.generatedPaths = generatedPaths;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode sourceFile : sourceFiles) {
      if (!check(sourceFile)) {
        errorReporter.report(
            sourceFile.getNamespaceDeclaration().getSourceLocation(), UNBLESSED_GENERATED_FILE);
        return Result.STOP;
      }
    }
    return Result.CONTINUE;
  }

  private boolean check(SoyFileNode sourceFile) {
    if (!generatedPaths.contains(sourceFile.getFilePath())) {
      return true;
    }
    if (sourceFile.getNamespace().equals(SoyFileHeaderInfo.EMPTY.getNamespace())) {
      // Ignore empty files to make bazel rules easier to write.
      return true;
    }

    return sourceFile.getComments().stream()
        .anyMatch(c -> BLESS_COMMENT.matcher(c.getSource()).find());
  }
}
