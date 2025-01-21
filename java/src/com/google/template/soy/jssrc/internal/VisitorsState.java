/*
 * Copyright 2025 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.SourceMapHelper;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor.ScopedJsTypeRegistry;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.types.SoyTypeRegistry;

/**
 * State related to the various visitor classes. These classes would perhaps be better organized as
 * inner classes of each other, but since they are large they have been split up.
 */
public class VisitorsState {

  // Set at construction:
  protected final SoyJsSrcOptions options;
  protected final JavaScriptValueFactoryImpl javaScriptValueFactory;
  protected final DelTemplateNamer delTemplateNamer;
  protected final GenCallCodeUtils genCallCodeUtils;
  protected final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;
  protected final CanInitOutputVarVisitor canInitOutputVarVisitor;
  protected final SoyTypeRegistry typeRegistry;
  protected final OutputVarHandler outputVarHandler = new OutputVarHandler();

  // Set per FileSetNode:
  protected ErrorReporter errorReporter;
  protected FileSetMetadata fileSetMetadata;

  // Set per FileNode:
  protected TemplateAliases templateAliases;
  protected SourceMapHelper sourceMapHelper;
  protected ScopedJsTypeRegistry scopedJsTypeRegistry;
  protected TranslationContext translationContext;

  // Set per TemplateNode:
  public boolean generatePositionalParamsSignature;

  public VisitorsState(
      SoyJsSrcOptions options,
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      SoyTypeRegistry typeRegistry) {
    this.options = checkNotNull(options);
    this.javaScriptValueFactory = checkNotNull(javaScriptValueFactory);
    this.delTemplateNamer = createDelTemplateNamer();
    this.isComputableAsJsExprsVisitor = createIsComputableAsJsExprsVisitor();
    this.genCallCodeUtils = createGenCallCodeUtils();
    this.canInitOutputVarVisitor = new CanInitOutputVarVisitor(isComputableAsJsExprsVisitor);
    this.typeRegistry = checkNotNull(typeRegistry);
  }

  @ForOverride
  protected DelTemplateNamer createDelTemplateNamer() {
    return new DelTemplateNamer();
  }

  @ForOverride
  protected IsComputableAsJsExprsVisitor createIsComputableAsJsExprsVisitor() {
    return new IsComputableAsJsExprsVisitor();
  }

  @ForOverride
  protected GenCallCodeUtils createGenCallCodeUtils() {
    return new GenCallCodeUtils(this, delTemplateNamer, isComputableAsJsExprsVisitor);
  }

  public GenJsCodeVisitor createGenJsCodeVisitor() {
    return new GenJsCodeVisitor(
        this,
        options,
        javaScriptValueFactory,
        delTemplateNamer,
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        typeRegistry,
        outputVarHandler);
  }

  public TranslateExprNodeVisitor createTranslateExprNodeVisitor() {
    return new TranslateExprNodeVisitor(
        javaScriptValueFactory,
        translationContext,
        templateAliases,
        errorReporter,
        scopedJsTypeRegistry,
        sourceMapHelper);
  }

  public GenJsTemplateBodyVisitor createTemplateBodyVisitor(GenJsExprsVisitor genJsExprsVisitor) {
    return new GenJsTemplateBodyVisitor(
        this,
        outputVarHandler,
        options,
        javaScriptValueFactory,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        genJsExprsVisitor,
        errorReporter,
        translationContext,
        templateAliases,
        scopedJsTypeRegistry,
        sourceMapHelper);
  }

  public GenJsExprsVisitor createJsExprsVisitor() {
    return new GenJsExprsVisitor(
        this,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        translationContext,
        errorReporter,
        templateAliases,
        scopedJsTypeRegistry,
        sourceMapHelper);
  }

  public GenJsCodeVisitorAssistantForMsgs createVisitorAssistantForMsgs(
      GenJsTemplateBodyVisitor owner, GenJsExprsVisitor genJsExprsVisitor) {
    return new GenJsCodeVisitorAssistantForMsgs(
        owner,
        options,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        genJsExprsVisitor,
        translationContext,
        errorReporter,
        outputVarHandler);
  }

  public void enterFileSet(FileSetMetadata fileSetMetadata, ErrorReporter errorReporter) {
    this.fileSetMetadata = fileSetMetadata;
    this.errorReporter = errorReporter;
  }

  public void exitFileSet() {
    this.fileSetMetadata = null;
    this.errorReporter = null;
  }

  public void enterFile(
      TranslationContext translationContext,
      ScopedJsTypeRegistry scopedJsTypeRegistry,
      TemplateAliases templateAliases,
      SourceMapHelper sourceMapHelper) {
    this.translationContext = translationContext;
    this.scopedJsTypeRegistry = scopedJsTypeRegistry;
    this.templateAliases = templateAliases;
    this.sourceMapHelper = sourceMapHelper;
  }

  public void exitFile() {
    this.translationContext = null;
    this.scopedJsTypeRegistry = null;
    this.templateAliases = null;
    this.sourceMapHelper = null;
  }
}
