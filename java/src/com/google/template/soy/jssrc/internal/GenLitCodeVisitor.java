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

package com.google.template.soy.jssrc.internal;

import static com.google.template.soy.jssrc.dsl.Expression.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Statement.assign;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_DATA;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.JsImplNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.ConstVar;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Generates Lit-HTML code based off of a Soy template. This is basically a shim. */
public final class GenLitCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {

  private final FileSetMetadata fileSetMetadata;
  private TemplateAliases templateAliases = null;
  private JsCodeBuilder jsCodeBuilder;
  private List<String> jsFilesContents;
  private ErrorReporter errorReporter;
  private SoyToJsVariableMappings topLevelSymbols;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  private final IsComputableAsLitTemplateVisitor isComputableAsLitTemplateVisitor;

  /** The GenLitExprVisitorFactory used by this instance. */
  private final GenLitExprVisitor.GenLitExprVisitorFactory genLitExprVisitorFactory;

  private final JavaScriptValueFactoryImpl javaScriptValueFactory;

  private final UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
  private final CodeChunk.Generator codeGenerator = CodeChunk.Generator.create(nameGenerator);

  GenLitCodeVisitor(
      FileSetMetadata fileSetMetadata,
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      IsComputableAsLitTemplateVisitor isComputableAsLitTemplateVisitor,
      GenLitExprVisitor.GenLitExprVisitorFactory genLitExprVisitorFactory) {
    this.fileSetMetadata = fileSetMetadata;
    this.javaScriptValueFactory = javaScriptValueFactory;
    this.isComputableAsLitTemplateVisitor = isComputableAsLitTemplateVisitor;
    this.genLitExprVisitorFactory = genLitExprVisitorFactory;
  }

  @Override
  protected void visit(SoyNode node) {
    try {
      super.visit(node);
    } catch (RuntimeException e) {
      throw new AssertionError(
          "error from : " + node.getKind() + " @ " + node.getSourceLocation(), e);
    }
  }

  public List<String> gen(SoyFileSetNode node, ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    try {
      jsFilesContents = new ArrayList<>();
      jsCodeBuilder = null;
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
    String exportNamespace = soyFile.getNamespace() + ".lit";
    header.append("goog.module('").append(exportNamespace).append("');\n\n");
  }

  @Override
  protected void visitSoyFileNode(SoyFileNode node) {
    StringBuilder file = new StringBuilder();

    file.append("// This file was automatically generated by the Soy compiler.\n")
        .append("// Please don't edit this file by hand.\n")
        // This "source" comment makes Code Search link the gencode to the Soy source:
        .append("// source: ")
        .append(node.getFilePath().path())
        .append('\n');

    if (node.numChildren() == 0) {
      jsFilesContents.add(file.toString());
      return;
    }

    // Output a section containing optionally-parsed compiler directives in comments. Since these
    // are comments, they are not controlled by an option, and will be removed by minifiers that do
    // not understand them.
    file.append("\n");
    String fileOverviewDescription = "Templates in namespace " + node.getNamespace() + ".";
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    jsDocBuilder.addAnnotation("fileoverview", fileOverviewDescription);
    file.append(jsDocBuilder.build());
    file.append("\n\n");
    jsCodeBuilder = new JsCodeBuilder();

    templateAliases = AliasUtils.createTemplateAliases(node, fileSetMetadata);

    addCodeToDeclareGoogModule(file, node);

    topLevelSymbols = SoyToJsVariableMappings.newEmpty();

    for (ImportNode importNode : node.getImports()) {
      visit(importNode);
    }
    for (ConstNode constant : node.getConstants()) {
      jsCodeBuilder.appendLine().appendLine();
      visit(constant);
    }
    node.getExterns().stream()
        .map(ExternNode::getJsImpl)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(
            jsExtern -> {
              jsCodeBuilder.appendLine().appendLine();
              visit(jsExtern);
            });

    // Add code for each template.
    for (TemplateNode template : node.getTemplates()) {
      jsCodeBuilder.appendLine().appendLine();
      visit(template);
    }
    jsCodeBuilder.appendGoogRequiresTo(file);
    jsCodeBuilder.appendCodeTo(file);
    jsFilesContents.add(file.toString());
    jsCodeBuilder = null;
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    SanitizedContentKind kind = node.getContentKind();
    // Attribute templates do not have an equivalent type in lit-html
    if (kind == SanitizedContentKind.ATTRIBUTES
        || kind == SanitizedContentKind.JS // TODO(user): not yet sure how to support these
        || kind == SanitizedContentKind.CSS // TODO(user): not yet sure how to support these
        || node instanceof TemplateDelegateNode
        || node.getVisibility() == Visibility.PRIVATE) {
      return;
    }
    ImmutableList.Builder<Statement> declarations = ImmutableList.builder();
    String templateName = node.getTemplateName();
    String alias = templateAliases.get(templateName);
    String partialName = node.getLocalTemplateSymbol();
    Expression aliasExp = dottedIdNoRequire(alias);
    JsDoc jsDoc = generateFunctionJsDoc(alias, kind);

    Expression function = Expression.function(jsDoc, generateFunctionBody(node));

    declarations.add(VariableDeclaration.builder(alias).setJsDoc(jsDoc).setRhs(function).build());
    declarations.add(assign(JsRuntime.EXPORTS.dotAccess(partialName), aliasExp));
    if (!hasOnlyImplicitParams(node)) {
      declarations.add(
          aliasExp
              .dotAccess("Params")
              .asStatement(
                  JsDoc.builder()
                      .addParameterizedAnnotation("typedef", genParamsRecordType(node))
                      .build()));
    }
    jsCodeBuilder.append(Statement.of(declarations.build()));
  }

  /** Generate the JSDoc for the opt_data parameter. */
  private String genParamsRecordType(TemplateNode node) {

    // Generate members for explicit params.
    Map<String, String> record = new LinkedHashMap<>();
    for (TemplateParam param : node.getParams()) {
      if (param.isImplicit()) {
        continue;
      }
      JsType jsType = JsType.forLitSrc(param.type());
      String typeForRecord = jsType.typeExprForRecordMember(/* isOptional= */ !param.isRequired());
      record.put(param.name(), typeForRecord);
      for (GoogRequire require : jsType.getGoogRequires()) {
        jsCodeBuilder.addGoogRequire(require);
      }
    }
    return JsType.toRecord(record);
  }

  private boolean hasOnlyImplicitParams(TemplateNode node) {
    for (TemplateParam param : node.getParams()) {
      if (!param.isImplicit()) {
        return false;
      }
    }
    return true;
  }

  private JsDoc generateFunctionJsDoc(String alias, SanitizedContentKind kind) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    jsDocBuilder.addParam(StandardNames.DOLLAR_DATA, "!" + alias + ".Params");
    if (kind == SanitizedContentKind.TEXT) {
      jsDocBuilder.addParameterizedAnnotation("return", "string");
    } else {
      jsDocBuilder.addParameterizedAnnotation("return", "lit_element.TemplateResult");
    }
    return jsDocBuilder.build();
  }

  private Statement generateFunctionBody(TemplateNode node) {
    if (!isComputableAsLitTemplateVisitor.exec(node)) {
      return Statement.throwValue(
          Expression.construct(
              Expression.id("Error"),
              Expression.stringLiteral("No implementation yet for the lit soy backend")));
    }

    // TODO(user): Handle IJ data
    // TODO(user): Template stubbing support (not in scope for the prototype, though).
    // TODO(user): Runtime type check for data/params

    TranslationContext templateTranslationContext =
        TranslationContext.of(
            SoyToJsVariableMappings.startingWith(topLevelSymbols), codeGenerator, nameGenerator);
    GenLitExprVisitor genLitExprVisitor =
        genLitExprVisitorFactory.create(templateTranslationContext, templateAliases, errorReporter);
    Expression functionBody = genLitExprVisitor.exec(node);
    return Statement.returnValue(functionBody);
  }

  @Override
  protected void visitImportNode(ImportNode node) {
    if (node.getImportType() == ImportType.TEMPLATE) {
      node.getIdentifiers().forEach(id -> visitImportNode(node, id, node.getModuleType()));
    }
  }

  private void visitImportNode(ImportNode node, ImportedVar var, SoyType parentType) {
    if (parentType != null
        && parentType.getKind() == Kind.TEMPLATE_MODULE
        && var.type().getKind() != Kind.TEMPLATE_TYPE) {
      // This is a constant import.
      String namespace = namespaceForPath(node.getSourceFilePath());
      namespace = templateAliases.getNamespaceAlias(namespace);
      Expression translation = dottedIdNoRequire(namespace + "." + var.getSymbol());
      if (parentType.getKind() != Kind.FUNCTION) {
        translation = translation.call(JsRuntime.SOY_INTERNAL_CALL_MARKER);
      }
      topLevelSymbols.put(var.name(), translation);
    }
    var.getNestedTypes().forEach(name -> visitImportNode(node, var.nested(name), var.type()));
  }

  private String namespaceForPath(SourceFilePath path) {
    return fileSetMetadata.getFile(path).getNamespace();
  }

  @Override
  protected void visitConstNode(ConstNode node) {
    ConstVar var = node.getVar();

    ImmutableList.Builder<Statement> declarations = ImmutableList.builder();

    JsDoc jsDoc =
        JsDoc.builder()
            .addAnnotation(node.isExported() ? "public" : "private")
            .addParameterizedAnnotation(
                "return", getJsTypeForParamForDeclaration(var.type()).typeExpr())
            .build();

    String partialName = var.name();
    String alias = partialName;

    Expression aliasExp = dottedIdNoRequire(alias);

    Expression constantGetterFunction =
        Expression.function(jsDoc, Statement.returnValue(translateExpr(node.getExpr())));

    declarations.add(
        VariableDeclaration.builder(alias).setJsDoc(jsDoc).setRhs(constantGetterFunction).build());
    if (node.isExported()) {
      declarations.add(assign(JsRuntime.EXPORTS.dotAccess(partialName), aliasExp));
    }

    jsCodeBuilder.append(Statement.of(declarations.build()));

    topLevelSymbols.put(var.name(), aliasExp.call(JsRuntime.SOY_INTERNAL_CALL_MARKER));
  }

  @Override
  protected void visitJsImplNode(JsImplNode node) {
    ExternNode externNode = node.getParent();
    String externName = externNode.getIdentifier().originalName();

    // Skip if we handled this impl already, e.g. a prev extern overload.
    if (topLevelSymbols.has(externName)) {
      return;
    }

    GoogRequire externRequire =
        GoogRequire.createWithAlias(node.module(), node.module().replace('.', '$'));
    ;
    Expression externReference =
        dottedIdNoRequire(externRequire.alias()).dotAccess(node.function());
    ;
    jsCodeBuilder.addGoogRequire(externRequire);
    topLevelSymbols.put(externName, externReference);

    if (externNode.isExported()) {
      Expression export = JsRuntime.EXPORTS.dotAccess(externName);
      jsCodeBuilder.append(Statement.assign(export, externReference));
    }
  }

  /** Gets the type to use for a parameter in record type declarations. */
  private JsType getJsTypeForParamForDeclaration(SoyType paramType) {
    return JsType.forJsSrc(paramType);
  }

  private TranslateExprNodeVisitor getExprTranslator() {

    return new TranslateExprNodeVisitor(
        javaScriptValueFactory,
        TranslationContext.of(
            SoyToJsVariableMappings.startingWith(topLevelSymbols), codeGenerator, nameGenerator),
        templateAliases,
        errorReporter,
        OPT_DATA);
  }

  private Expression translateExpr(ExprNode expr) {
    return getExprTranslator().exec(expr);
  }

  @Override
  protected void visitChildren(ParentSoyNode<?> node) {
    for (SoyNode child : node.getChildren()) {
      visit(child);
    }
  }
}
