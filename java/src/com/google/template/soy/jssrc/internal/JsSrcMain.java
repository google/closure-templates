/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesPass;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Main entry point for the JS Src backend (output target).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class JsSrcMain {
  /** The scope object that manages the API call scope. */
  private final SoyScopedData.Enterable apiCallScope;

  private final SoyTypeRegistry typeRegistry;

  /** @param apiCallScope The scope object that manages the API call scope. */
  public JsSrcMain(SoyScopedData.Enterable apiCallScope, SoyTypeRegistry typeRegistry) {
    this.apiCallScope = apiCallScope;
    this.typeRegistry = typeRegistry;
  }

  /**
   * Generates JS source code given a Soy parse tree, an options object, and an optional bundle of
   * translated messages.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param templateRegistry The template registry that contains all the template information.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   */
  public List<String> genJsSrc(
      SoyFileSetNode soyTree,
      TemplateRegistry templateRegistry,
      SoyJsSrcOptions jsSrcOptions,
      @Nullable SoyMsgBundle msgBundle,
      ErrorReporter errorReporter) {

    // VeLogInstrumentationVisitor add html attributes for {velog} commands and also run desugaring
    // pass since code generator does not understand html nodes (yet).
    new VeLogInstrumentationVisitor(templateRegistry).exec(soyTree);
    BidiGlobalDir bidiGlobalDir =
        SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(
            jsSrcOptions.getBidiGlobalDir(), jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir());
    try (SoyScopedData.InScope inScope = apiCallScope.enter(msgBundle, bidiGlobalDir)) {
      // Replace MsgNodes.
      if (jsSrcOptions.shouldGenerateGoogMsgDefs()) {
        Preconditions.checkState(
            bidiGlobalDir != null,
            "If enabling shouldGenerateGoogMsgDefs, must also set bidi global directionality.");
      } else {
        Preconditions.checkState(
            bidiGlobalDir == null || bidiGlobalDir.isStaticValue(),
            "If using bidiGlobalIsRtlCodeSnippet, must also enable shouldGenerateGoogMsgDefs.");
        new InsertMsgsVisitor(msgBundle, errorReporter).insertMsgs(soyTree);
      }
      // Combine raw text nodes before codegen.
      new CombineConsecutiveRawTextNodesPass().run(soyTree);
      return createVisitor(jsSrcOptions, typeRegistry, inScope.getBidiGlobalDir(), errorReporter)
          .gen(soyTree, templateRegistry, errorReporter);
    }
  }

  static GenJsCodeVisitor createVisitor(
      final SoyJsSrcOptions options,
      SoyTypeRegistry typeRegistry,
      BidiGlobalDir dir,
      ErrorReporter errorReporter) {
    final DelTemplateNamer delTemplateNamer = new DelTemplateNamer();
    final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor =
        new IsComputableAsJsExprsVisitor();
    final JavaScriptValueFactoryImpl javaScriptValueFactory =
        new JavaScriptValueFactoryImpl(options, dir, errorReporter);
    CanInitOutputVarVisitor canInitOutputVarVisitor =
        new CanInitOutputVarVisitor(isComputableAsJsExprsVisitor);
    // This supplier is used to break a circular dependency between GenCallCodeUtils and
    // GenJsExprsVisitorFactory.  The reason this cycle exists is due complex, but could be
    // eliminated if we got rid of the whole 'iscomputableasjsexprs' concept in this backend.
    // TODO(lukes): fix the cycle by eliminating IsComputableAsJsExprsVisitor
    class GenCallCodeUtilsSupplier implements Supplier<GenCallCodeUtils> {
      GenJsExprsVisitorFactory factory;

      @Override
      public GenCallCodeUtils get() {
        return new GenCallCodeUtils(delTemplateNamer, isComputableAsJsExprsVisitor, factory);
      }
    }
    GenCallCodeUtilsSupplier supplier = new GenCallCodeUtilsSupplier();
    GenJsExprsVisitorFactory genJsExprsVisitorFactory =
        new GenJsExprsVisitorFactory(
            javaScriptValueFactory, supplier, isComputableAsJsExprsVisitor);
    supplier.factory = genJsExprsVisitorFactory;

    return new GenJsCodeVisitor(
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
