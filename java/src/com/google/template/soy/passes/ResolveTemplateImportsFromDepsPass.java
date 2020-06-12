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

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.ResolveTemplateImportsPass.TemplateImportVisitor;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportsContext;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateRegistry;

/**
 * Resolve template imports that reference files in the dependencies (i.e. NOT files in the current
 * fileset).
 */
@RunBefore({
  ResolveTemplateImportsFromFileSetPass.class,
  SoyElementPass.class // Needs partial template registry of deps.
})
final class ResolveTemplateImportsFromDepsPass extends ResolveTemplateImportsPass {
  ResolveTemplateImportsFromDepsPass(ErrorReporter errorReporter) {
    super(errorReporter);
  }

  @Override
  TemplateImportVisitor createImportVisitorForFile(SoyFileNode file) {
    return new TemplateImportFromDepsVisitor(file, getFileSetTemplateRegistry(), errorReporter());
  }

  private static class TemplateImportFromDepsVisitor extends TemplateImportVisitor {
    private final TemplateRegistry fileSetTemplateRegistry;

    private TemplateImportFromDepsVisitor(
        SoyFileNode file, TemplateRegistry fileSetTemplateRegistry, ErrorReporter errorReporter) {
      super(file, fileSetTemplateRegistry, errorReporter);
      this.fileSetTemplateRegistry = fileSetTemplateRegistry;
    }

    @Override
    boolean shouldVisit(ImportNode node) {
      // For deps, only visit the nodes that come from a known file path. When we do a later pass to
      // resolve imports from the current file set, we'll throw an error if the filepath is still
      // unknown.
      return importExists(node.getImportType(), node.getPath());
    }

    /**
     * Sets the local template registry for the file's {@link ImportsContext}, since template
     * imports from deps are resolved before imports from the current fileset.
     */
    @Override
    void updateImportsContext() {
      file.getImportsContext()
          .setTemplateRegistry(
              new ImportsTemplateRegistry(fileSetTemplateRegistry, symbolsToTemplatesMap.build()));
    }
  }
}
