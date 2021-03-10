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

import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.internal.exemptions.NamespaceExemptions;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.function.Supplier;

/**
 * Reports an error if two source files have the same namespace.
 *
 * <p>This is a limited check since conflicts may be in completely separate compilation units.
 */
@RunAfter(FinalizeTemplateRegistryPass.class)
final class BanDuplicateNamespacesPass implements CompilerFileSetPass {
  private static final SoyErrorKind DUPLICATE_NAMESPACE =
      SoyErrorKind.of(
          "Found another files ''{0}'' with the same namespace.  All files must have unique"
              + " namespaces.");
  private static final SoyErrorKind DUPLICATE_NAMESPACE_WARNING =
      SoyErrorKind.of(
          "Found another files ''{0}'' with the same namespace.  All files should have unique"
              + " namespaces. This will soon become an error.");
  private final ErrorReporter errorReporter;
  private final Supplier<TemplateRegistry> fileSetTemplateRegistry;

  BanDuplicateNamespacesPass(
      ErrorReporter errorReporter, Supplier<TemplateRegistry> fileSetTemplateRegistry) {
    this.errorReporter = errorReporter;
    this.fileSetTemplateRegistry = fileSetTemplateRegistry;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator nodeIdGen) {
    ImmutableSetMultimap<String, String> namespaceToFiles =
        fileSetTemplateRegistry.get().getAllTemplates().stream()
            .collect(
                toImmutableSetMultimap(
                    BanDuplicateNamespacesPass::namespace,
                    t -> t.getSourceLocation().getFilePath().path()));
    for (SoyFileNode sourceFile : sourceFiles) {
      ImmutableSet<String> filePaths = namespaceToFiles.get(sourceFile.getNamespace());
      if (filePaths.size() > 1) {
        String filePath = sourceFile.getFilePath().path();
        String otherFiles =
            filePaths.stream().filter(path -> !path.equals(filePath)).collect(joining(", "));
        if (NamespaceExemptions.isKnownDuplicateNamespace(sourceFile.getNamespace())) {
          errorReporter.warn(
              sourceFile.getNamespaceDeclaration().getSourceLocation(),
              DUPLICATE_NAMESPACE_WARNING,
              otherFiles);
        } else {
          errorReporter.report(
              sourceFile.getNamespaceDeclaration().getSourceLocation(),
              DUPLICATE_NAMESPACE,
              otherFiles);
        }
      }
    }
    return Result.CONTINUE;
  }

  private static String namespace(TemplateMetadata meta) {
    return meta.getTemplateName().substring(0, meta.getTemplateName().lastIndexOf('.'));
  }
}
