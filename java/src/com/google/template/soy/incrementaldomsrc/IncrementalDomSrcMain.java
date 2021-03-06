/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.incrementaldomsrc;

import com.google.common.base.Supplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.incrementaldomsrc.GenIncrementalDomExprsVisitor.GenIncrementalDomExprsVisitorFactory;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.CanInitOutputVarVisitor;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesPass;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Collections;
import java.util.List;

/**
 * Main entry point for the Incremental DOM JS Src backend (output target).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public class IncrementalDomSrcMain {

  /** The scope object that manages the API call scope. */
  private final SoyScopedData.Enterable apiCallScope;

  private final SoyTypeRegistry typeRegistry;

  public IncrementalDomSrcMain(SoyScopedData.Enterable apiCallScope, SoyTypeRegistry typeRegistry) {
    this.apiCallScope = apiCallScope;
    this.typeRegistry = typeRegistry;
  }

  /**
   * Generates Incremental DOM JS source code given a Soy parse tree, an options object, and an
   * optional bundle of translated messages.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param registry The template registry that contains all the template information.
   * @param options The compilation options relevant to this backend.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   */
  public List<String> genJsSrc(
      SoyFileSetNode soyTree,
      TemplateRegistry registry,
      SoyIncrementalDomSrcOptions options,
      ErrorReporter errorReporter) {

    SoyJsSrcOptions incrementalJSSrcOptions = options.toJsSrcOptions();

    BidiGlobalDir bidiGlobalDir =
        SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(
            incrementalJSSrcOptions.getBidiGlobalDir(),
            incrementalJSSrcOptions.getUseGoogIsRtlForBidiGlobalDir());
    try (SoyScopedData.InScope inScope = apiCallScope.enter(/* msgBundle= */ null, bidiGlobalDir)) {
      // Do the code generation.

      new HtmlContextVisitor().exec(soyTree);
      // If any errors are reported in {@code HtmlContextVisitor}, we should not continue.
      // Return an empty list here, {@code SoyFileSet} will throw an exception.
      if (errorReporter.hasErrors()) {
        return Collections.emptyList();
      }

      UnescapingVisitor.unescapeRawTextInHtml(soyTree);
      TransformSkipNodeVisitor.reparentSkipNodes(soyTree);

      new RemoveUnnecessaryEscapingDirectives(bidiGlobalDir).run(soyTree);
      // some of the above passes may slice up raw text nodes, recombine them.
      new CombineConsecutiveRawTextNodesPass().run(soyTree);
      return createVisitor(
              incrementalJSSrcOptions, typeRegistry, inScope.getBidiGlobalDir(), errorReporter)
          .gen(soyTree, registry, errorReporter);
    }
  }

  static GenIncrementalDomCodeVisitor createVisitor(
      final SoyJsSrcOptions options,
      SoyTypeRegistry typeRegistry,
      BidiGlobalDir dir,
      ErrorReporter errorReporter) {
    final IncrementalDomDelTemplateNamer delTemplateNamer = new IncrementalDomDelTemplateNamer();
    final IsComputableAsIncrementalDomExprsVisitor isComputableAsJsExprsVisitor =
        new IsComputableAsIncrementalDomExprsVisitor();
    final JavaScriptValueFactoryImpl javaScriptValueFactory =
        new JavaScriptValueFactoryImpl(dir, errorReporter);
    CanInitOutputVarVisitor canInitOutputVarVisitor =
        new CanInitOutputVarVisitor(isComputableAsJsExprsVisitor);
    // TODO(lukes): eliminate this supplier.  See commend in JsSrcMain for more information.
    class GenCallCodeUtilsSupplier implements Supplier<IncrementalDomGenCallCodeUtils> {
      GenIncrementalDomExprsVisitorFactory factory;

      @Override
      public IncrementalDomGenCallCodeUtils get() {
        return new IncrementalDomGenCallCodeUtils(
            delTemplateNamer, isComputableAsJsExprsVisitor, factory);
      }
    }
    GenCallCodeUtilsSupplier supplier = new GenCallCodeUtilsSupplier();
    GenIncrementalDomExprsVisitorFactory genJsExprsVisitorFactory =
        new GenIncrementalDomExprsVisitorFactory(
            javaScriptValueFactory, supplier, isComputableAsJsExprsVisitor);
    supplier.factory = genJsExprsVisitorFactory;

    return new GenIncrementalDomCodeVisitor(
        options,
        javaScriptValueFactory,
        delTemplateNamer,
        supplier.get(),
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        genJsExprsVisitorFactory,
        typeRegistry);
  }
}
