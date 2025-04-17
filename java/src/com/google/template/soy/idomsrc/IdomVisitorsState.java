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

package com.google.template.soy.idomsrc;

import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.DelTemplateNamer;
import com.google.template.soy.jssrc.internal.GenCallCodeUtils;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor;
import com.google.template.soy.jssrc.internal.GenJsTemplateBodyVisitor;
import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.jssrc.internal.OutputVarHandler;
import com.google.template.soy.jssrc.internal.VisitorsState;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Deque;

/** IDOM flavor of state. */
public final class IdomVisitorsState extends VisitorsState {

  private Deque<SanitizedContentKind> contentKind;
  private String alias;

  public IdomVisitorsState(
      SoyJsSrcOptions options,
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      SoyTypeRegistry typeRegistry) {
    super(options, javaScriptValueFactory, typeRegistry);
  }

  @Override
  protected DelTemplateNamer createDelTemplateNamer() {
    return new IdomDelTemplateNamer();
  }

  @Override
  protected IsComputableAsJsExprsVisitor createIsComputableAsJsExprsVisitor() {
    return new IsComputableAsIdomExprsVisitor();
  }

  @Override
  protected GenCallCodeUtils createGenCallCodeUtils() {
    return new IdomGenCallCodeUtils(this, delTemplateNamer, isComputableAsJsExprsVisitor);
  }

  @Override
  public GenIdomCodeVisitor createGenJsCodeVisitor() {
    return new GenIdomCodeVisitor(
        this,
        options,
        javaScriptValueFactory,
        delTemplateNamer,
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        typeRegistry,
        outputVarHandler);
  }

  @Override
  public IdomTranslateExprNodeVisitor createTranslateExprNodeVisitor() {
    return new IdomTranslateExprNodeVisitor(
        javaScriptValueFactory,
        translationContext,
        templateAliases,
        errorReporter,
        scopedJsTypeRegistry,
        sourceMapHelper);
  }

  @Override
  public GenJsTemplateBodyVisitor createTemplateBodyVisitor(
      GenJsExprsVisitor genJsExprsVisitor, OutputVarHandler outputVarHandler, boolean mutableLets) {
    return new GenIdomTemplateBodyVisitor(
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
        contentKind,
        generatePositionalParamsSignature,
        fileSetMetadata,
        alias,
        scopedJsTypeRegistry,
        sourceMapHelper,
        mutableLets);
  }

  @Override
  public GenIdomExprsVisitor createJsExprsVisitor() {
    return new GenIdomExprsVisitor(
        this,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        translationContext,
        errorReporter,
        templateAliases,
        scopedJsTypeRegistry,
        sourceMapHelper);
  }

  public void enterCall(String alias, Deque<SanitizedContentKind> contentKind) {
    this.alias = alias;
    this.contentKind = contentKind;
  }

  public void exitCall() {
    alias = null;
    contentKind = null;
  }
}
