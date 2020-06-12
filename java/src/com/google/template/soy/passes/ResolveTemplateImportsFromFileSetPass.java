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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.ResolveTemplateImportsPass.TemplateImportVisitor;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplatesPerFile.TemplateName;

/**
 * Resolve template imports that reference files in the current fileset (i.e. NOT the dependencies).
 */
@RunBefore({
  CheckTemplateHeaderVarsPass.class,
  UpgradeTemplateTypesPass.class,
  CheckNoNamedTemplateTypesPass.class,
  CheckTemplateCallsPass.class,
  CheckTemplateVisibilityPass.class
})
@RunAfter({ResolveTemplateImportsFromDepsPass.class})
final class ResolveTemplateImportsFromFileSetPass extends ResolveTemplateImportsPass {
  ResolveTemplateImportsFromFileSetPass(ErrorReporter errorReporter) {
    super(errorReporter);
  }

  @Override
  TemplateImportVisitor createImportVisitorForFile(SoyFileNode file) {
    return new TemplateImportFromFileSetVisitor(
        file, getFileSetTemplateRegistry(), errorReporter());
  }

  private static class TemplateImportFromFileSetVisitor extends TemplateImportVisitor {
    private final TemplateRegistry fileSetTemplateRegistry;

    private TemplateImportFromFileSetVisitor(
        SoyFileNode file, TemplateRegistry fileSetTemplateRegistry, ErrorReporter errorReporter) {
      super(file, fileSetTemplateRegistry, errorReporter);
      this.fileSetTemplateRegistry = fileSetTemplateRegistry;
    }

    @Override
    boolean shouldVisit(ImportNode node) {
      // Only visit the node if it hasn't already been resolved in the deps pass.
      return !node.isResolved();
    }

    /**
     * Updates the imports template registry by merging the template imports from deps with the new
     * template imports from the current file set.
     */
    @Override
    void updateImportsContext() {
      checkState(
          file.getImportsContext().hasTemplateRegistry(),
          "ResolveTemplateImportsFromFileSet cannot be called before"
              + " ResolveTemplateImportsFromDeps");

      // TODO(b/158474755): Revisit this merging business / try to not swap out the delegate
      // registry once we decide how to split up TemplateRegistry into multiple data structures.
      ImportsTemplateRegistry importsFromDeps = file.getImportsContext().getTemplateRegistry();
      file.getImportsContext()
          .overrideTemplateRegistry(
              new ImportsTemplateRegistry(
                  fileSetTemplateRegistry,
                  new ImmutableMap.Builder<String, TemplateName>()
                      .putAll(symbolsToTemplatesMap.build())
                      .putAll(importsFromDeps.getSymbolsToTemplateNamesMap())
                      .build()));
    }
  }
}
