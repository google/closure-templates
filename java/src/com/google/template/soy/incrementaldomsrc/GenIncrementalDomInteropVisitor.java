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

import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_ALIAS;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.internal.CanInitOutputVarVisitor;
import com.google.template.soy.jssrc.internal.DelTemplateNamer;
import com.google.template.soy.jssrc.internal.GenCallCodeUtils;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.jssrc.internal.JsCodeBuilder;
import com.google.template.soy.jssrc.internal.JsRuntime;
import com.google.template.soy.jssrc.internal.StandardNames;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Generates mod code to stub SoyJS with IDOM. */
public final class GenIncrementalDomInteropVisitor extends GenJsCodeVisitor {
  private List<String> jsFilesContents;
  private JsCodeBuilder codeBuilder;
  private GoogRequire idomGoogRequire;
  private GoogRequire soyJsGoogRequire;
  private final Expression shouldStub = GOOG_SOY_ALIAS.dotAccess("shouldStub");

  protected GenIncrementalDomInteropVisitor(
      SoyJsSrcOptions jsSrcOptions,
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      DelTemplateNamer delTemplateNamer,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory,
      SoyTypeRegistry typeRegistry) {
    super(
        jsSrcOptions,
        javaScriptValueFactory,
        delTemplateNamer,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        genJsExprsVisitorFactory,
        typeRegistry);
  }

  public List<String> gen(SoyFileSetNode node, ErrorReporter errorReporter) {
    try {
      jsFilesContents = new ArrayList<>();
      visit(node);
      return jsFilesContents;
    } finally {
    }
  }

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      visit(soyFile);
    }
  }
  /**
   * Helper for visitSoyFileNode(SoyFileNode) to generate a module definition.
   *
   * @param soyFile The node we're visiting.
   */
  private void addCodeToDeclareGoogModule(StringBuilder header, SoyFileNode soyFile) {
    String exportNamespace = soyFile.getNamespace() + ".idominterop";
    header.append("goog.module('").append(exportNamespace).append("');\n\n");
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    if (node.getVisibility() != Visibility.PUBLIC) {
      return;
    }
    boolean hasPositionalSignature =
        GenCallCodeUtils.hasPositionalSignature(TemplateMetadata.buildTemplateType(node));
    if (!hasPositionalSignature && node.getContentKind() == SanitizedContentKind.HTML) {
      codeBuilder.append(
          Statement.ifStatement(
                  shouldStub,
                  Statement.assign(
                      soyJsGoogRequire.dotAccess(
                          node.getPartialTemplateName() + "_" + StandardNames.SOY_STUB),
                      Expression.arrowFunction(
                          JsDoc.builder()
                              .addParam(StandardNames.DOLLAR_DATA, "?")
                              .addParam(StandardNames.DOLLAR_IJDATA, "?")
                              .build(),
                          IncrementalDomRuntime.SOY_IDOM_MAKE_HTML.call(
                              Expression.arrowFunction(
                                  JsDoc.builder().build(),
                                  idomGoogRequire
                                      .reference()
                                      .dotAccess(node.getPartialTemplateName())
                                      .call(
                                          IncrementalDomRuntime.SOY_IDOM
                                              .reference()
                                              .dotAccess("$$defaultIdomRenderer"),
                                          Expression.id(StandardNames.DOLLAR_DATA),
                                          JsRuntime.IJ_DATA))))))
              .build());
      return;
    }

    if (node.getContentKind() == SanitizedContentKind.HTML) {
      JsDoc jsDoc =
          generatePositionalFunctionJsDoc(
              node, /* addVariantParam= */ isModifiableWithUseVariantType(node));
      ArrayList<Expression> callParams =
          jsDoc.params().stream()
              .filter(p -> p.annotationType().equals("param"))
              .map(p -> Expression.id(p.paramTypeName()))
              .collect(Collectors.toCollection(ArrayList::new));
      callParams.add(
          2, IncrementalDomRuntime.SOY_IDOM.reference().dotAccess("$$defaultIdomRenderer"));
      codeBuilder.append(
          Statement.ifStatement(
                  shouldStub,
                  Statement.assign(
                      soyJsGoogRequire.dotAccess(
                          node.getPartialTemplateName() + "_" + StandardNames.SOY_STUB),
                      Expression.arrowFunction(
                          jsDoc,
                          IncrementalDomRuntime.SOY_IDOM_MAKE_HTML.call(
                              Expression.arrowFunction(
                                  JsDoc.builder().build(),
                                  idomGoogRequire
                                      .reference()
                                      .dotAccess(node.getPartialTemplateName() + "$")
                                      .call(
                                          new ImmutableList.Builder<Expression>()
                                              .addAll(callParams)
                                              .build()))))))
              .build());
    }
  }

  @Override
  protected void visitSoyNode(SoyNode node) {}

  @Override
  protected void visitChildren(ParentSoyNode<?> node) {
    for (SoyNode child : node.getChildren()) {
      if (child instanceof TemplateNode) {
        visit(child);
      }
    }
  }
}
