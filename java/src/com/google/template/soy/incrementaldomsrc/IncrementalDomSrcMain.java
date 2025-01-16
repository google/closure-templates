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

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesPass;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Collections;
import java.util.List;

/** Main entry point for the Incremental DOM JS Src backend (output target). */
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
      FileSetMetadata registry,
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

  static GenJsCodeVisitor createVisitor(
      SoyJsSrcOptions options,
      SoyTypeRegistry typeRegistry,
      BidiGlobalDir dir,
      ErrorReporter errorReporter) {
    JavaScriptValueFactoryImpl javaScriptValueFactory =
        new JavaScriptValueFactoryImpl(dir, errorReporter);
    IdomVisitorsState visitorsState =
        new IdomVisitorsState(options, javaScriptValueFactory, typeRegistry);
    return visitorsState.createGenJsCodeVisitor();
  }
}
