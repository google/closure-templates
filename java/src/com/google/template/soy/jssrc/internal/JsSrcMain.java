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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesPass;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;
import javax.annotation.Nullable;

/** Main entry point for the JS Src backend (output target). */
public class JsSrcMain {
  /** The scope object that manages the API call scope. */
  private final SoyScopedData.Enterable apiCallScope;

  private final SoyTypeRegistry typeRegistry;

  /**
   * @param apiCallScope The scope object that manages the API call scope.
   */
  public JsSrcMain(SoyScopedData.Enterable apiCallScope, SoyTypeRegistry typeRegistry) {
    this.apiCallScope = apiCallScope;
    this.typeRegistry = typeRegistry;
  }

  /**
   * Generates JS source code given a Soy parse tree, an options object, and an optional bundle of
   * translated messages.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param fileSetMetadata The template registry that contains all the template information.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   */
  public List<String> genJsSrc(
      SoyFileSetNode soyTree,
      FileSetMetadata fileSetMetadata,
      SoyJsSrcOptions jsSrcOptions,
      @Nullable SoyMsgBundle msgBundle,
      ErrorReporter errorReporter) {

    // VeLogInstrumentationVisitor add html attributes for {velog} commands and also run desugaring
    // pass since code generator does not understand html nodes (yet).
    new VeLogInstrumentationVisitor().exec(soyTree);
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
          .gen(soyTree, fileSetMetadata, errorReporter);
    }
  }

  static GenJsCodeVisitor createVisitor(
      SoyJsSrcOptions options,
      SoyTypeRegistry typeRegistry,
      BidiGlobalDir dir,
      ErrorReporter errorReporter) {
    JavaScriptValueFactoryImpl javaScriptValueFactory =
        new JavaScriptValueFactoryImpl(dir, errorReporter);
    VisitorsState visitorsState = new VisitorsState(options, javaScriptValueFactory, typeRegistry);
    return visitorsState.createGenJsCodeVisitor();
  }
}
