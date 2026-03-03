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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.template.soy.jssrc.dsl.Expressions.EMPTY_OBJECT_LITERAL;
import static com.google.template.soy.jssrc.dsl.Expressions.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Expressions.number;
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;
import static com.google.template.soy.jssrc.dsl.Statements.assign;
import static com.google.template.soy.jssrc.dsl.Statements.ifStatement;
import static com.google.template.soy.jssrc.dsl.Statements.returnValue;
import static com.google.template.soy.jssrc.dsl.Whitespace.BLANK_LINE;
import static com.google.template.soy.jssrc.internal.JsRuntime.EXPORTS;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_DEBUG;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_MODULE_GET;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_REQUIRE;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_ALIAS;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_VARIANT;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_GET_DELEGATE_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_GET_DELTEMPLATE_ID;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_MAKE_EMPTY_TEMPLATE_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_REGISTER_DELEGATE_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentOrdainerFunction;
import static com.google.template.soy.jssrc.internal.JsType.matchNullishToBang;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.javagencode.KytheHelper;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.Id;
import com.google.template.soy.jssrc.dsl.JsArrowFunction;
import com.google.template.soy.jssrc.dsl.JsCodeBuilder;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.LineComment;
import com.google.template.soy.jssrc.dsl.SourceMapHelper;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.Statements;
import com.google.template.soy.jssrc.dsl.StringLiteral;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.jssrc.internal.JsType.JsTypeProducer;
import com.google.template.soy.jssrc.internal.JsType.RecursiveJsTypeProducer;
import com.google.template.soy.passes.IndirectParamsCalculator;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.passes.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoImplNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.DelcallAnnotationVisitor;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.JsImplNode;
import com.google.template.soy.soytree.Metadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TypeDefNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.SymbolVar;
import com.google.template.soy.soytree.defn.SymbolVar.SymbolKind;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.AbstractIterableType;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.IndexedType;
import com.google.template.soy.types.IntersectionType;
import com.google.template.soy.types.LiteralType;
import com.google.template.soy.types.NamedType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.RecordType.Member;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * Visitor for generating full JS code (i.e. statements) for parse tree nodes.
 *
 * <p>Precondition: MsgNode should not exist in the tree.
 *
 * <p>{@link #gen} should be called on a full parse tree. JS source code will be generated for all
 * the Soy files. The return value is a list of strings, each string being the content of one
 * generated JS file (corresponding to one Soy file).
 */
public class GenJsCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {

  private static final SoyErrorKind EXTERN_NO_JS_IMPL =
      SoyErrorKind.of(
          "Extern ''{0}'' does not have a JS implementation. Either add one or don''t compile this"
              + " Soy to JS.",
          Impression.ERROR_GEN_JS_CODE_VISITOR_EXTERN_NO_JS_IMPL);

  protected static final String MODIFIABLE_DEFAULT_IMPL_SUFFIX = "__default_impl";

  protected final VisitorsState state;

  /** The options for generating JS source code. */
  protected final SoyJsSrcOptions jsSrcOptions;

  protected final JavaScriptValueFactoryImpl javaScriptValueFactory;

  /** Instance of DelTemplateNamer to use. */
  private final DelTemplateNamer delTemplateNamer;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  protected final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** The CanInitOutputVarVisitor used by this instance. */
  protected final CanInitOutputVarVisitor canInitOutputVarVisitor;

  /** The contents of the generated JS files. */
  private List<String> jsFilesContents;

  /** The CodeBuilder to build the current JS file being generated (during a run). */
  @VisibleForTesting JsCodeBuilder jsCodeBuilder;

  @VisibleForTesting protected SourceMapHelper sourceMapHelper;

  protected FileScopeJsTypeRegistry jsTypeRegistry;

  /** The GenJsExprsVisitor used for the current template. */
  protected GenJsExprsVisitor genJsExprsVisitor;

  protected FileSetMetadata fileSetMetadata;

  private final SoyTypeRegistry typeRegistry;

  protected ErrorReporter errorReporter;
  protected TranslationContext templateTranslationContext;

  protected List<Statement> staticVarDeclarations;

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name. This is created on a per {@link SoyFileNode} basis.
   */
  protected TemplateAliases templateAliases;

  protected final OutputVarHandler outputVars;

  protected GenJsCodeVisitor(
      VisitorsState state,
      SoyJsSrcOptions jsSrcOptions,
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      DelTemplateNamer delTemplateNamer,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      SoyTypeRegistry typeRegistry,
      OutputVarHandler outputVars) {
    this.state = state;
    this.jsSrcOptions = jsSrcOptions;
    this.javaScriptValueFactory = javaScriptValueFactory;
    this.delTemplateNamer = delTemplateNamer;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.canInitOutputVarVisitor = canInitOutputVarVisitor;
    this.typeRegistry = typeRegistry;
    this.outputVars = outputVars;
  }

  public List<String> gen(
      SoyFileSetNode node, FileSetMetadata registry, ErrorReporter errorReporter) {
    this.fileSetMetadata = checkNotNull(registry);
    this.errorReporter = checkNotNull(errorReporter);
    state.enterFileSet(fileSetMetadata, errorReporter);
    try {
      jsFilesContents = new ArrayList<>();
      jsCodeBuilder = null;
      genJsExprsVisitor = null;
      visit(node);
      return jsFilesContents;
    } finally {
      state.exitFileSet();
    }
  }

  /**
   * @deprecated Call {@link #gen} instead.
   */
  @Override
  @Deprecated
  public final List<String> exec(SoyNode node) {
    throw new UnsupportedOperationException();
  }

  /**
   * This method must only be called by assistant visitors, in particular
   * GenJsCodeVisitorAssistantForMsgs.
   */
  public void visitForUseByAssistants(SoyNode node) {
    visit(node);
  }

  /** TODO: tests should use {@link #gen} instead. */
  @VisibleForTesting
  void visitForTesting(SoyNode node, FileSetMetadata registry, ErrorReporter errorReporter) {
    state.errorReporter = errorReporter;
    state.fileSetMetadata = registry;
    this.templateTranslationContext =
        TranslationContext.of(
            SoyToJsVariableMappings.newEmpty(), JsSrcNameGenerators.forLocalVariables());
    this.sourceMapHelper = SourceMapHelper.NO_OP;
    state.enterFile(
        this.templateTranslationContext,
        ScopedJsTypeRegistry.PASSTHROUGH,
        AliasUtils.IDENTITY_ALIASES,
        SourceMapHelper.NO_OP);
    visit(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      visit(soyFile);
    }
  }

  /** Returns the CodeBuilder used for generating file contents. */
  protected JsCodeBuilder getJsCodeBuilder() {
    return jsCodeBuilder;
  }

  /**
   * Example:
   *
   * <pre>
   * // This file was automatically generated from my-templates.soy.
   * // Please don't edit this file by hand.
   *
   * if (typeof boo == 'undefined') { var boo = {}; }
   * if (typeof boo.foo == 'undefined') { boo.foo = {}; }
   *
   * ...
   * </pre>
   */
  @Override
  protected void visitSoyFileNode(SoyFileNode node) {
    KytheHelper kytheHelper = new KytheHelper(node.getFilePath());
    sourceMapHelper = new SourceMapHelper(node, jsSrcOptions.sourceMapMode());
    jsCodeBuilder = new JsCodeBuilder(kytheHelper, sourceMapHelper);

    jsCodeBuilder.append(
        LineComment.create("// This file was automatically generated by the Soy compiler."),
        LineComment.create("// Please don't edit this file by hand."),
        // This "source" comment makes Code Search link the gencode to the Soy source:
        LineComment.create("// source: " + node.getFilePath().path()));

    if (node.getConstants().isEmpty()
        && node.getExterns().isEmpty()
        && node.getTemplates().isEmpty()
        && node.getTypeDefs().isEmpty()) {
      // Special support for empty Soy files created with NamespaceDeclaration.EMPTY.
      jsFilesContents.add(jsCodeBuilder.getCode().toString());
      return;
    }

    // Output a section containing optionally-parsed compiler directives in comments. Since these
    // are comments, they are not controlled by an option, and will be removed by minifiers that do
    // not understand them.
    jsCodeBuilder.append(BLANK_LINE);
    String fileOverviewDescription = "Templates in namespace " + node.getNamespace() + ".";
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    jsDocBuilder.addAnnotation("fileoverview", fileOverviewDescription);
    if (isIncrementalDom()) {
      jsDocBuilder.addAnnotation("suppress", "{missingRequire} TODO(b/152440355)");
    }
    jsDocBuilder.addAnnotation("suppress", "{suspiciousCode}");
    if (!jsSrcOptions.shouldGenerateGoogModules()) {
      jsDocBuilder.addAnnotation("suppress", "{uselessCode}");
    }
    jsDocBuilder.addAnnotation(
        "suppress",
        "{strictMissingProperties} TODO(b/214874268): Remove strictMissingProperties suppression"
            + " after b/214427036 is fixed");
    if (node.getTemplates().stream().anyMatch(tmpl -> tmpl instanceof TemplateElementNode)) {
      jsDocBuilder.addParameterizedAnnotation("suppress", "extraRequire");
    }
    if (node.getModName() != null) {
      jsDocBuilder.addParameterizedAnnotation("modName", node.getModName());
      addModsAnnotation(jsDocBuilder, node);
    }
    addHasSoyDelTemplateAnnotations(jsDocBuilder, node);
    addHasSoyDelCallAnnotations(jsDocBuilder, node);
    addCodeToRequireCss(jsDocBuilder, node);
    jsDocBuilder.addAnnotation("public");
    jsCodeBuilder.append(jsDocBuilder.build(), BLANK_LINE);

    // Add code to define JS namespaces or add provide/require calls for Closure Library.
    templateAliases = AliasUtils.IDENTITY_ALIASES;

    if (jsSrcOptions.shouldGenerateGoogModules()) {
      templateAliases = AliasUtils.createTemplateAliases(node, state.fileSetMetadata);

      addCodeToDeclareGoogModule(jsCodeBuilder, node);
      addCodeToRequireGoogModules(node);
    } else if (jsSrcOptions.shouldProvideRequireSoyNamespaces()) {
      addCodeToProvideSoyNamespace(jsCodeBuilder, node);
      addCodeToRequireSoyNamespaces(node);
    } else {
      throw new AssertionError("impossible");
    }
    jsCodeBuilder.appendRequiresPlaceholder();
    jsTypeRegistry = new FileScopeJsTypeRegistry();

    // add declarations for the ijdata params
    // This takes advantage of the way '@record' types in closure are 'open'.
    // Unfortunately clutz doesn't understand this:
    // https://github.com/angular/clutz/issues/832
    // So TS users will not be able to see the property definitions.
    // The most practical solution to that is for soy to generate its own .d.ts files.
    Map<String, SoyType> ijData = getAllIjDataParams(node);
    if (!ijData.isEmpty()) {
      GoogRequire require = jsSrcOptions.shouldGenerateGoogModules() ? GOOG_SOY_ALIAS : GOOG_SOY;
      jsCodeBuilder.append(BLANK_LINE);
      for (Map.Entry<String, SoyType> entry : ijData.entrySet()) {
        jsCodeBuilder.append(BLANK_LINE);
        JsType type = getJsTypeForParamForDeclaration(entry.getValue());
        jsCodeBuilder
            .append(
                JsDoc.builder()
                    // Because every declaration can declare a type, we can get errors if they don't
                    // declare identical types.  There isn't a good way to force identical
                    // declarations so we just suppress the duplicate error warning.
                    .addParameterizedAnnotation("suppress", "duplicate")
                    // declare every field as optional.  This is because if a template is unused
                    // and declares an ij param we don't want to force people to supply a value.
                    .addParameterizedAnnotation("type", type.typeExpr() + "|undefined")
                    .build())
            .addGoogRequires(
                type.googRequires().stream()
                    .map(GoogRequire::toRequireType)
                    .collect(toImmutableList()));
        jsCodeBuilder.append(
            require
                .reference()
                .dotAccess("IjData")
                .dotAccess("prototype")
                .dotAccess(entry.getKey())
                .asStatement());
      }
    }

    // TODO(lukes): reserve all the namespace prefixes that are in scope
    // TODO(lukes): use this for all local variable declarations
    templateTranslationContext =
        TranslationContext.of(
            SoyToJsVariableMappings.newEmpty(), JsSrcNameGenerators.forLocalVariables());

    state.enterFile(templateTranslationContext, jsTypeRegistry, templateAliases, sourceMapHelper);

    for (ImportNode importNode : node.getImports()) {
      visit(importNode);
    }
    // Install all constant references in the topLevelSymbols map so that templates can find them
    for (ConstNode constant : node.getConstants()) {
      registerLocalConstant(constant);
    }
    ImmutableSet<ExternNode> externsToCodeGen =
        node.getExterns().stream().filter(this::registerLocalExtern).collect(toImmutableSet());

    for (SoyNode child : node.getChildren()) {
      if (child instanceof TypeDefNode) {
        visit(child);
      } else if (child instanceof ExternNode && externsToCodeGen.contains(child)) {
        visit(child);
      }
    }

    // Add code for each template.
    for (TemplateNode template : node.getTemplates()) {
      jsCodeBuilder.append(BLANK_LINE, BLANK_LINE);
      staticVarDeclarations = new ArrayList<>();
      visit(template);
      if (!staticVarDeclarations.isEmpty()) {
        jsCodeBuilder.append(Statements.of(staticVarDeclarations));
      }
    }
    // Constants may reference templates, put them at the bottom of the file.
    for (ConstNode constant : node.getConstants()) {
      jsCodeBuilder.append(BLANK_LINE, BLANK_LINE);
      visit(constant);
    }
    StringBuilder file = jsCodeBuilder.getCode();
    kytheHelper.appendGeneratedCodeInfo(jsSrcOptions.kytheMode(), file);
    sourceMapHelper.appendSourceMapComment(file);
    jsFilesContents.add(file.toString());
    jsCodeBuilder = null;
    sourceMapHelper = null;
    jsTypeRegistry = null;

    state.exitFile();
  }

  private Map<String, SoyType> getAllIjDataParams(SoyFileNode node) {
    Map<String, SoyType> params = new LinkedHashMap<>();
    for (TemplateNode template : node.getTemplates()) {
      for (TemplateParam param : template.getInjectedParams()) {
        SoyType oldType = params.put(param.name(), param.authoredType());
        if (oldType != null) {
          // merge the types
          params.put(
              param.name(),
              typeRegistry.getOrCreateUnionType(Arrays.asList(param.authoredType(), oldType)));
        }
      }
    }
    return params;
  }

  /**
   * Appends requirecss jsdoc tags in the file header section.
   *
   * @param soyFile The file with the templates..
   */
  private void addCodeToRequireCss(JsDoc.Builder header, SoyFileNode soyFile) {

    SortedSet<String> requiredCssNamespaces = new TreeSet<>();
    if (jsSrcOptions.dependOnCssHeader()) {
      requiredCssNamespaces.add("./" + soyFile.getFilePath().fileName());
    } else {
      requiredCssNamespaces.addAll(soyFile.getRequireCss());
      for (TemplateNode template : soyFile.getTemplates()) {
        requiredCssNamespaces.addAll(template.getRequiredCssNamespaces());
      }
    }
    // NOTE: CSS requires in JS can only be done on a file by file basis at this time.  Perhaps in
    // the future, this might be supported per function.
    for (String requiredCssNamespace : requiredCssNamespaces) {
      header.addParameterizedAnnotation("requirecss", requiredCssNamespace);
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to provide Soy namespaces.
   *
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideSoyNamespace(JsCodeBuilder jsCodeBuilder, SoyFileNode soyFile) {
    Expression expression =
        dottedIdNoRequire("goog.provide")
            .call(
                StringLiteral.builder(getGoogModuleNamespace(soyFile.getNamespace()))
                    .setSpan(getNamespaceByteSpan(soyFile))
                    .build());
    sourceMapHelper.setPrimaryLocation(
        expression, soyFile.getNamespaceDeclaration().getSourceLocation());
    jsCodeBuilder.append(expression).append(BLANK_LINE);
  }

  private static ByteSpan getNamespaceByteSpan(SoyFileNode soyFile) {
    return SoyTreeUtils.getByteSpan(
        soyFile, soyFile.getNamespaceDeclaration().getNamespaceLocation());
  }

  /**
   * Returns the namespace to import/export templates.
   *
   * @param soyNamespace The namespace as declared by the user.
   */
  protected String getGoogModuleNamespace(String soyNamespace) {
    return Preconditions.checkNotNull(soyNamespace);
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to generate a module definition.
   *
   * @param soyFile The node we're visiting.
   */
  private void addCodeToDeclareGoogModule(JsCodeBuilder jsCodeBuilder, SoyFileNode soyFile) {
    String exportNamespace = getGoogModuleNamespace(soyFile.getNamespace());
    jsCodeBuilder
        .append(
            dottedIdNoRequire("goog.module")
                .call(
                    StringLiteral.builder(exportNamespace)
                        .setSpan(getNamespaceByteSpan(soyFile))
                        .build()))
        .append(BLANK_LINE);
  }

  /**
   * Generates the module imports and aliasing. This generates code like the following:
   *
   * <pre>
   * var $import1 = goog.require('some.namespace');
   * var $import2 = goog.require('other.namespace');
   * ...
   * </pre>
   *
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireGoogModules(SoyFileNode soyFile) {
    soyFile.getImports().stream()
        .filter(i -> i.getImportType() == ImportType.TEMPLATE)
        .map(i -> namespaceForPath(i.getSourceFilePath()))
        .distinct()
        .sorted()
        .forEach(
            calleeNamespace -> {
              String namespaceAlias = templateAliases.getNamespaceAlias(calleeNamespace);
              String importNamespace = getGoogModuleNamespace(calleeNamespace);
              jsCodeBuilder.append(
                  VariableDeclaration.builder(namespaceAlias)
                      .setRhs(GOOG_REQUIRE.call(stringLiteral(importNamespace)))
                      .build());
            });
  }

  private void addModsAnnotation(JsDoc.Builder header, SoyFileNode soyFile) {
    Optional<String> mods =
        soyFile.getTemplates().stream()
            .filter(t -> t instanceof TemplateBasicNode)
            .map(TemplateBasicNode.class::cast)
            .map(TemplateBasicNode::moddedSoyNamespace)
            .filter(Objects::nonNull)
            .findFirst();
    if (mods.isPresent()) {
      header.addParameterizedAnnotation("mods", getGoogModuleNamespace(mods.get()));
    }
  }

  // TODO(b/233903480): Remove these once all deltemplates and delcalls are fully migrated.
  private void addHasSoyDelTemplateAnnotations(JsDoc.Builder header, SoyFileNode soyFile) {

    SortedSet<String> delTemplateNames = new TreeSet<>();
    for (TemplateNode template : soyFile.getTemplates()) {
      if (template instanceof TemplateDelegateNode) {
        delTemplateNames.add(delTemplateNamer.getDelegateName((TemplateDelegateNode) template));
      } else if (template instanceof TemplateBasicNode) {
        String annotationName = ((TemplateBasicNode) template).deltemplateAnnotationName();
        if (annotationName != null) {
          delTemplateNames.add(delTemplateNamer.getDelegateName(annotationName));
        }
      }
    }
    for (String delTemplateName : delTemplateNames) {
      header.addParameterizedAnnotation("hassoydeltemplate", delTemplateName);
    }
  }

  // TODO(b/233903480): Remove these once all deltemplates and delcalls are fully migrated.
  private void addHasSoyDelCallAnnotations(JsDoc.Builder header, SoyFileNode soyFile) {

    SortedSet<String> delTemplateNames = new TreeSet<>();
    for (CallDelegateNode delCall :
        SoyTreeUtils.getAllNodesOfType(soyFile, CallDelegateNode.class)) {
      delTemplateNames.add(delTemplateNamer.getDelegateName(delCall));
    }
    delTemplateNames.addAll(
        new DelcallAnnotationVisitor()
            .exec(soyFile).stream()
                .map(delTemplateNamer::getDelegateName)
                .collect(toImmutableSet()));
    for (String delTemplateName : delTemplateNames) {
      header.addParameterizedAnnotation("hassoydelcall", delTemplateName);
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require Soy namespaces.
   *
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireSoyNamespaces(SoyFileNode soyFile) {
    soyFile.getImports().stream()
        .filter(i -> i.getImportType() == ImportType.TEMPLATE)
        .map(i -> namespaceForPath(i.getSourceFilePath()))
        .distinct()
        .sorted()
        .forEach(
            calleeNamespace -> jsCodeBuilder.addGoogRequire(GoogRequire.create(calleeNamespace)));
  }

  /**
   * Returns the JavaScript type of the content generated by this template.
   *
   * @param node The template node that is being generated
   */
  protected JsType getTemplateReturnType(TemplateNode node) {
    return JsType.templateReturnTypeForJsSrc(node.getContentKind());
  }

  @Override
  protected void visitImportNode(ImportNode node) {
    if (node.getImportType() != ImportType.TEMPLATE) {
      return;
    }
    node.visitVars(
        (var) -> {
          String namespace = namespaceForPath(node.getSourceFilePath());
          if (jsSrcOptions.shouldGenerateGoogModules()) {
            namespace = templateAliases.getNamespaceAlias(namespace);
          }
          if (var.getSymbolKind() == SymbolKind.EXTERN) {
            Expression translation = dottedIdNoRequire(namespace + "." + var.getSymbol());
            templateTranslationContext.soyToJsVariableMappings().put(var.name(), translation);
          } else if (var.getSymbolKind() == SymbolKind.CONST) {
            templateTranslationContext
                .soyToJsVariableMappings()
                .put(
                    var.name(),
                    JsRuntime.SOY_GET_CONST.call(
                        dottedIdNoRequire(namespace + "." + var.getSymbol()),
                        JsRuntime.SOY_INTERNAL_CALL_MARKER));
          }
        });
  }

  @Override
  protected void visitTypeDefNode(TypeDefNode node) {
    NamedType thisNamedType = node.asNamedType();
    jsTypeRegistry.addTypeMap(
        thisNamedType,
        JsType.localTypedef("!" + topLevelName(node, node.isExported(), node.getName())));

    SoyType type = node.getType();
    ByteSpan imputesSpan = SoyTreeUtils.getByteSpan(node, node.getNameLocation());

    ImmutableSet<SoyType> recordMembers;
    if (type instanceof IntersectionType) {
      recordMembers = ((IntersectionType) type).getMembers();
    } else if (type instanceof RecordType) {
      recordMembers = ImmutableSet.of(type);
    } else {
      // If this is not a record type we can just use @typedef.
      JsType jsType = getJsTypeForParam(type);
      JsDoc.Builder jsDoc =
          JsDoc.builder()
              .addParameterizedAnnotation("typedef", jsType.typeExpr())
              .addGoogRequires(jsType);
      jsCodeBuilder.append(
          topLevelDecl(node, node.isExported(), node.getName(), jsDoc, imputesSpan));
      return;
    }

    // Any record type, whether intersection or just record needs to be @record (more like
    // TypeScript's interface declaration).
    JsDoc.Builder jsDoc = JsDoc.builder().addAnnotation("record");

    List<Statement> properties = new ArrayList<>();

    Expression recordClass = topLevelLhs(node, node.isExported(), node.getName(), null);
    for (SoyType member : recordMembers) {
      if (member instanceof NamedType) {
        JsType memberType = getJsTypeForParam(member);
        jsDoc.addAnnotation("extends", memberType.typeExprForExtends()).addGoogRequires(memberType);
      } else if (member instanceof RecordType) {
        for (Member recordMember : ((RecordType) member).getMembers()) {
          SoyType checkedType = recordMember.checkedType();

          // For every member create a @typedef so that we can implement IndexedType.
          String memberTypedefName =
              IndexedType.jsSynthenticTypeDefName(node.getName(), recordMember.name());
          JsType memberTypedefType = getJsTypeForParam(checkedType);
          jsCodeBuilder.append(
              topLevelDecl(
                  node,
                  true, // Must always be public because of indexed types. We could do flow
                  // analysis...
                  memberTypedefName,
                  JsDoc.builder()
                      .addParameterizedAnnotation("typedef", memberTypedefType.typeExpr())
                      .addGoogRequires(memberTypedefType),
                  null));

          // For every member, define a prototype property of the @record. The type of the property
          // points back to the member @typedef.
          SoyType indexedPropType =
              IndexedType.create(
                  thisNamedType, LiteralType.create(StringData.forValue(recordMember.name())));
          // JSC gets confused by ! when it references a @typedef. The ! needs to match whether
          // the source typedef is `|null` or `|undefined`.
          String memberSymbol =
              matchNullishToBang(
                  topLevelName(node, true, memberTypedefName), SoyTypes.isNullish(checkedType));
          jsTypeRegistry.addTypeMap(indexedPropType, JsType.localTypedef(memberSymbol));
          JsType indexedPropJsType = getJsTypeForParam(indexedPropType);
          JsDoc memberDoc =
              JsDoc.builder()
                  .addAnnotation("type", indexedPropJsType.typeExpr())
                  .addAnnotation("public")
                  .addGoogRequires(indexedPropJsType)
                  .build();
          properties.add(
              recordClass
                  .dotAccess("prototype")
                  .dotAccess(recordMember.name())
                  .asStatement(memberDoc));
        }
      }
    }

    jsCodeBuilder.append(
        topLevelAssignment(
            node,
            node.isExported(),
            node.getName(),
            jsDoc,
            imputesSpan,
            Expressions.function(JsDoc.getDefaultInstance(), Statements.EMPTY)));
    jsCodeBuilder.append(
        sourceMapHelper.setPrimaryLocation(Statements.of(properties), node.getSourceLocation()));
  }

  @Override
  protected void visitConstNode(ConstNode node) {
    SymbolVar var = node.getVar();
    ByteSpan imputesSpan = SoyTreeUtils.getByteSpan(node, var.nameLocation());

    JsType varType = getJsTypeForParamForDeclaration(var.authoredType());

    JsDoc.Builder jsDoc =
        JsDoc.builder()
            .addParameterizedAnnotation(
                "const", "function(!Object):" + varType.typeExprForFunctionReturn());

    String partialName = var.name();
    Expression constantExpr =
        Expressions.group(
                JsRuntime.SOY_CREATE_CONST.call(
                    JsArrowFunction.create(
                        JsDoc.builder().build(),
                        Statements.returnValue(translateExpr(node.getExpr())))))
            // Cross module code motion cannot move function calls no matter how trivial they are
            // the pureOrBreakMyCode annotation says it is ok to move the call anyway.
            .prepend(JsDoc.builder().setOverviewComment("@pureOrBreakMyCode").build());

    jsCodeBuilder.append(
        topLevelAssignment(node, node.isExported(), partialName, jsDoc, imputesSpan, constantExpr));
    for (GoogRequire require : varType.googRequires()) {
      jsCodeBuilder.addGoogRequire(require);
    }
  }

  private String topLevelName(SoyNode node, boolean exported, String partialName) {
    if (jsSrcOptions.shouldGenerateGoogModules()) {
      if (exported) {
        return "exports." + partialName;
      } else {
        return partialName;
      }
    } else {
      SoyFileNode file = node.getNearestAncestor(SoyFileNode.class);
      return getGoogModuleNamespace(file.getNamespace()) + "." + partialName;
    }
  }

  private Expression topLevelLhs(
      SoyNode node, boolean exported, String partialName, @Nullable ByteSpan byteSpan) {
    Expression nameExpr = Id.builder(partialName).setSpan(byteSpan).build();
    if (jsSrcOptions.shouldGenerateGoogModules()) {
      if (exported) {
        return EXPORTS.dotAccess(nameExpr);
      } else {
        return nameExpr;
      }
    } else {
      SoyFileNode file = node.getNearestAncestor(SoyFileNode.class);
      return dottedIdNoRequire(getGoogModuleNamespace(file.getNamespace())).dotAccess(nameExpr);
    }
  }

  /**
   * Creates a symbol at the top-level of the JS file. For exported symbols in goog.module this
   * defines a symbol directly on `exports` rather than defining a local const that then needs to be
   * copied into `exports`.
   *
   * @param node the node context, used for getting file node
   * @param exported whether the symbol is exported/public
   * @param partialName the symbol name, minus namespace, etc.
   * @param jsDoc jsdoc to apply
   * @param value the optional RHS value
   */
  private Statement topLevelAssignment(
      SoyNode node,
      boolean exported,
      String partialName,
      JsDoc.Builder jsDoc,
      @Nullable ByteSpan byteSpan,
      @Nullable Expression value) {

    Statement rv;
    if (jsSrcOptions.shouldGenerateGoogModules()) {
      if (exported) {
        Expression lhs = topLevelLhs(node, exported, partialName, byteSpan);
        rv = value != null ? assign(lhs, value, jsDoc.build()) : lhs.asStatement(jsDoc.build());
      } else {
        rv =
            VariableDeclaration.builder(Id.builder(partialName).setSpan(byteSpan).build())
                .setJsDoc(jsDoc.build())
                .setRhs(value)
                .build();
      }
    } else {
      jsDoc.addAnnotation(exported ? "public" : "private");
      Expression lhs = topLevelLhs(node, exported, partialName, byteSpan);
      rv = value != null ? assign(lhs, value, jsDoc.build()) : lhs.asStatement(jsDoc.build());
    }
    sourceMapHelper.setPrimaryLocation(rv, node.getSourceLocation());
    return rv;
  }

  private Statement topLevelDecl(
      SoyNode node,
      boolean exported,
      String partialName,
      JsDoc.Builder jsDoc,
      @Nullable ByteSpan byteSpan) {
    return topLevelAssignment(node, exported, partialName, jsDoc, byteSpan, null);
  }

  private Expression getLocalConstantExpr(ConstNode node) {
    return getLocalTopLevelMemberExpr(node, node.isExported(), node.getVar().name());
  }

  private Expression getLocalTopLevelMemberExpr(SoyNode node, boolean exported, String name) {
    SoyFileNode file = node.getNearestAncestor(SoyFileNode.class);
    Expression rv =
        jsSrcOptions.shouldGenerateGoogModules()
            ? exported ? JsRuntime.EXPORTS.dotAccess(name) : id(name)
            : dottedIdNoRequire(getGoogModuleNamespace(file.getNamespace())).dotAccess(name);
    sourceMapHelper.setPrimaryLocation(rv, node.getSourceLocation());
    return rv;
  }

  private void registerLocalConstant(ConstNode node) {
    templateTranslationContext
        .soyToJsVariableMappings()
        .put(
            node.getVar().name(),
            JsRuntime.SOY_GET_CONST.call(
                getLocalConstantExpr(node), JsRuntime.SOY_INTERNAL_CALL_MARKER));
  }

  private Statement makeRegisterDelegateFn(
      TemplateDelegateNode nodeAsDelTemplate, Expression aliasExp) {
    return SOY_REGISTER_DELEGATE_FN
        .call(
            SOY_GET_DELTEMPLATE_ID.call(
                stringLiteral(delTemplateNamer.getDelegateName(nodeAsDelTemplate))),
            stringLiteral(nodeAsDelTemplate.getDelTemplateVariant()),
            number(nodeAsDelTemplate.getDelPriority().getValue()),
            aliasExp)
        .asStatement();
  }

  protected static boolean isModifiable(TemplateNode node) {
    return node instanceof TemplateBasicNode && ((TemplateBasicNode) node).isModifiable();
  }

  protected static boolean isModifiableWithUseVariantType(TemplateNode node) {
    return isModifiable(node)
        && !SoyTypes.isNullOrUndefined(((TemplateBasicNode) node).getUseVariantType());
  }

  protected static boolean isModTemplate(TemplateNode node) {
    return node instanceof TemplateBasicNode
        && ((TemplateBasicNode) node).getModifiesExpr() != null;
  }

  protected static TemplateType getModifiedTemplateType(TemplateBasicNode node) {
    return (TemplateType) node.getModifiesExpr().getRoot().getType();
  }

  /**
   * Generates the registerDelegateFn() call for the default modifiable template referenced by
   * aliasExp.
   */
  private Statement makeRegisterDefaultFnCall(
      TemplateBasicNode nodeAsBasicTemplate, Expression aliasExp) {
    checkState(nodeAsBasicTemplate.isModifiable());
    return SOY_REGISTER_DELEGATE_FN
        .call(
            SOY_GET_DELTEMPLATE_ID.call(
                stringLiteral(delTemplateNamer.getDelegateName(nodeAsBasicTemplate))),
            stringLiteral(""),
            number(nodeAsBasicTemplate.getSoyFileHeaderInfo().getPriority().getValue()),
            aliasExp)
        .asStatement();
  }

  /** Generates the registerDelegateFn() call for the mod template referenced by aliasExp. */
  private Statement makeRegisterModFn(TemplateBasicNode nodeAsBasicTemplate, Expression aliasExp) {
    checkState(nodeAsBasicTemplate.getModifiesExpr() != null);
    TemplateLiteralNode literal =
        (TemplateLiteralNode) nodeAsBasicTemplate.getModifiesExpr().getRoot();
    return SOY_REGISTER_DELEGATE_FN
        .call(
            SOY_GET_DELTEMPLATE_ID.call(stringLiteral(delTemplateNamer.getDelegateName(literal))),
            stringLiteral(nodeAsBasicTemplate.getDelTemplateVariant()),
            number(nodeAsBasicTemplate.getSoyFileHeaderInfo().getPriority().getValue()),
            aliasExp)
        .asStatement();
  }

  /**
   * Outputs a {@link TemplateNode}, generating the function open and close, along with a a debug
   * template name.
   *
   * <p>If aliasing is not performed (which is always the case for V1 templates), this looks like:
   *
   * <pre>
   * my.namespace.func = function(opt_data, opt_sb) {
   *   ...
   * };
   * if (goog.DEBUG) {
   *   /** @type {string} * /
   *   my.namespace.func.soyTemplateName = 'my.namespace.func';
   * }
   * </pre>
   *
   * <p>If aliasing is performed, this looks like:
   *
   * <pre>
   * function $func(opt_data, opt_sb) {
   *   ...
   * }
   * exports.func = $func;
   * if (goog.DEBUG) {
   *   $func.soyTemplateName = 'my.namespace.func';
   * }
   * <p>Note that the alias is not exactly the function name as in may conflict with a reserved
   * JavaScript identifier.
   * </pre>
   */
  @Override
  protected void visitTemplateNode(TemplateNode node) {
    state.generatePositionalParamsSignature =
        GenCallCodeUtils.hasPositionalSignature(Metadata.buildTemplateType(node));
    String templateName = node.getTemplateName();
    String partialName = node.getPartialTemplateName();
    String alias;

    if (jsSrcOptions.shouldGenerateGoogModules() && node instanceof TemplateDelegateNode) {
      alias = partialName;
    } else {
      alias = templateAliases.get(templateName);
    }

    ByteSpan imputesSpan = SoyTreeUtils.getByteSpan(node, node.getTemplateNameLocation());
    Expression aliasExp = dottedIdNoRequire(alias, imputesSpan);

    // TODO(lukes): reserve all the namespace prefixes that are in scope
    // TODO(lukes): use this for all local variable declarations
    try (var unused = templateTranslationContext.enterSoyAndJsScope()) {
      genJsExprsVisitor = state.createJsExprsVisitor();

      ImmutableList.Builder<Statement> declarations = ImmutableList.builder();

      if (node instanceof TemplateDelegateNode && node.getChildren().isEmpty()) {
        TemplateDelegateNode nodeAsDelTemplate = (TemplateDelegateNode) node;
        // Don't emit anything for an empty default deltemplate, at runtime a missing entry in the
        // runtime map will be assumed to be an explicit empty template
        if (nodeAsDelTemplate.getDelTemplateVariant().isEmpty()
            && nodeAsDelTemplate.getModName() == null) {
          return;
        }
        Expression emptyFnCall = SOY_MAKE_EMPTY_TEMPLATE_FN.call(stringLiteral(templateName));
        JsDoc jsDoc = generateEmptyFunctionJsDoc(node);
        if (jsSrcOptions.shouldGenerateGoogModules()) {
          declarations.add(
              VariableDeclaration.builder(Id.create(alias))
                  .setJsDoc(jsDoc)
                  .setRhs(emptyFnCall)
                  .build());
        } else {
          declarations.add(Statements.assign(aliasExp, emptyFnCall, jsDoc));
        }
        declarations.add(makeRegisterDelegateFn(nodeAsDelTemplate, aliasExp));
        jsCodeBuilder.append(Statements.of(declarations.build()));
        return;
      }

      if (state.generatePositionalParamsSignature) {
        JsDoc jsDoc =
            generateFunctionJsDoc(
                node,
                alias,
                /* suppressCheckTypes= */ false,
                /* addVariantParam= */ isModifiableWithUseVariantType(node));
        Expression publicFunction =
            Expressions.function(jsDoc, generateDelegateFunction(node, alias));
        JsDoc positionalFunctionDoc =
            generatePositionalFunctionJsDoc(
                node, /* addVariantParam= */ isModifiableWithUseVariantType(node));
        Expression positionalFunction =
            Expressions.function(
                positionalFunctionDoc,
                isModifiable(node)
                    ? generateModTemplateSelection(
                        node, alias, templateTranslationContext.codeGenerator())
                    : generateFunctionBody(
                        node, alias, /* objectParamName= */ null, /* addStubMapLogic= */ true));

        if (jsSrcOptions.shouldGenerateGoogModules()) {
          VariableDeclaration publicDeclaration =
              VariableDeclaration.builder(Id.builder(alias).setSpan(imputesSpan).build())
                  .setJsDoc(jsDoc)
                  .setRhs(publicFunction)
                  .build();
          declarations.add(publicDeclaration);
          VariableDeclaration positionalDeclaration =
              VariableDeclaration.builder(Id.create(alias + "$"))
                  .setJsDoc(positionalFunctionDoc)
                  .setRhs(positionalFunction)
                  .build();
          declarations.add(positionalDeclaration);
          // don't export deltemplates or private templates
          if (!(node instanceof TemplateDelegateNode)
              && node.getVisibility() == Visibility.PUBLIC) {
            declarations.add(
                assign(
                    JsRuntime.EXPORTS.dotAccess(
                        Id.builder(partialName).setSpan(imputesSpan).build()),
                    publicDeclaration.ref()));
            declarations.add(
                assign(
                    JsRuntime.EXPORTS.dotAccess(partialName + "$"), positionalDeclaration.ref()));
          }
        } else {
          declarations.add(Statements.assign(aliasExp, publicFunction, jsDoc));
          declarations.add(
              Statements.assign(
                  dottedIdNoRequire(alias + "$"), positionalFunction, positionalFunctionDoc));
        }
      } else {
        JsDoc jsDoc =
            generateFunctionJsDoc(
                node,
                alias,
                /* suppressCheckTypes= */ true,
                /* addVariantParam= */ isModifiableWithUseVariantType(node));
        String objectParamType = jsDoc.params().get(1).type();
        Expression function =
            Expressions.function(
                jsDoc,
                isModifiable(node)
                    ? generateModTemplateSelection(
                        node, alias, templateTranslationContext.codeGenerator())
                    : generateFunctionBody(
                        // Remove optional type cast
                        node,
                        alias,
                        // Strip the optional suffix character "="
                        /* objectParamName= */ objectParamType.substring(
                            0, objectParamType.length() - 1),
                        /* addStubMapLogic= */ true));

        if (jsSrcOptions.shouldGenerateGoogModules()) {
          declarations.add(
              VariableDeclaration.builder(Id.builder(alias).setSpan(imputesSpan).build())
                  .setJsDoc(jsDoc)
                  .setRhs(function)
                  .build());
          // don't export deltemplates or private templates
          if (!(node instanceof TemplateDelegateNode)
              && node.getVisibility() == Visibility.PUBLIC) {
            declarations.add(
                assign(
                    JsRuntime.EXPORTS.dotAccess(
                        Id.builder(partialName).setSpan(imputesSpan).build()),
                    aliasExp));
          }
        } else {
          declarations.add(Statements.assign(aliasExp, function, jsDoc));
        }
      }

      // ------ Add the @typedef of opt_data. ------
      if (!hasOnlyImplicitParams(node)) {
        declarations.add(
            aliasExp
                .dotAccess("Params")
                .asStatement(
                    JsDoc.builder()
                        .addParameterizedAnnotation("typedef", genParamsRecordType(node))
                        .build()));
      }

      // ------ Add the fully qualified template name to the function to use in debug code. ------
      declarations.add(
          ifStatement(
                  GOOG_DEBUG,
                  assign(
                      aliasExp.dotAccess("soyTemplateName"),
                      stringLiteral(templateName),
                      JsDoc.builder()
                          .addAnnotation("nocollapse")
                          .addParameterizedAnnotation("type", "string")
                          .build()))
              .build());

      // ------ If delegate template, generate a statement to register it. ------
      if (node instanceof TemplateDelegateNode) {
        TemplateDelegateNode nodeAsDelTemplate = (TemplateDelegateNode) node;
        declarations.add(makeRegisterDelegateFn(nodeAsDelTemplate, aliasExp));
      }

      // ------ For modifiable templates, generate and register the default implementation. -----
      if (isModifiable(node) && !node.getChildren().isEmpty()) {
        String defaultImplName = alias + MODIFIABLE_DEFAULT_IMPL_SUFFIX;
        JsDoc jsDoc =
            state.generatePositionalParamsSignature
                ? generatePositionalFunctionJsDoc(node, /* addVariantParam= */ false)
                : generateFunctionJsDoc(
                    node, alias, /* suppressCheckTypes= */ true, /* addVariantParam= */ false);
        String objectParamType = jsDoc.params().get(1).type();
        Expression impl =
            Expressions.function(
                jsDoc,
                generateFunctionBody(
                    node,
                    alias,
                    /* objectParamName= */ state.generatePositionalParamsSignature
                        ? null
                        // Strip the optional suffix character "="
                        : objectParamType.substring(0, objectParamType.length() - 1),
                    /* addStubMapLogic= */ false));
        if (jsSrcOptions.shouldGenerateGoogModules()) {
          declarations.add(
              VariableDeclaration.builder(Id.create(defaultImplName))
                  .setJsDoc(jsDoc)
                  .setRhs(impl)
                  .build());
        } else {
          declarations.add(Statements.assign(dottedIdNoRequire(defaultImplName), impl, jsDoc));
        }
        TemplateBasicNode templateBasicNode = (TemplateBasicNode) node;
        declarations.add(
            makeRegisterDefaultFnCall(templateBasicNode, dottedIdNoRequire(defaultImplName)));
      }

      // ------ For mod templates, generate a statement to register it. ------
      if (isModTemplate(node)) {
        declarations.add(
            makeRegisterModFn(
                (TemplateBasicNode) node,
                dottedIdNoRequire(alias + (state.generatePositionalParamsSignature ? "$" : ""))));
      }

      jsCodeBuilder.append(
          sourceMapHelper.setPrimaryLocation(
              Statements.of(declarations.build()), node.getSourceLocation()));
      this.state.generatePositionalParamsSignature = false;
    }
  }

  protected boolean hasOnlyImplicitParams(TemplateNode node) {
    for (TemplateParam param : node.getParams()) {
      if (!param.isImplicit()) {
        return false;
      }
    }
    return true;
  }

  private TemplateParam syntheticTemplateParam(TemplateType.Parameter typeParam) {
    TemplateParam param =
        new TemplateParam(
            typeParam.getName(),
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            /* typeNode= */ null,
            /* isInjected= */ false,
            /* isImplicit= */ typeParam.isImplicit(),
            /* optional= */ !typeParam.isRequired(),
            /* desc= */ null,
            /* defaultValue= */ null);
    param.setType(typeParam.getType());
    return param;
  }

  protected final ImmutableList<TemplateParam> paramsInOrder(TemplateNode node) {
    HashMap<String, TemplateParam> paramsByName = new HashMap<>();
    for (TemplateParam param : node.getParams()) {
      paramsByName.put(param.name(), param);
    }
    if (isModTemplate(node)) {
      // Also add any parameters from the modifiable template that are missing.
      for (TemplateType.Parameter param :
          getModifiedTemplateType((TemplateBasicNode) node).getActualParameters()) {
        paramsByName.putIfAbsent(param.getName(), syntheticTemplateParam(param));
      }
    }
    // For modifies templates, use the signature of the modifiable template so that positional
    // params work.
    TemplateType templateType =
        isModTemplate(node)
            ? getModifiedTemplateType((TemplateBasicNode) node)
            : Metadata.buildTemplateType(node);
    // Use the templatemetadata so we generate parameters in the correct order as
    // expected by callers, this is defined by TemplateMetadata.
    return templateType.getActualParameters().stream()
        .map(p -> paramsByName.get(p.getName()))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  protected final JsDoc.Builder addInternalCallerParam(JsDoc.Builder jsDocBuilder) {
    return jsDocBuilder.addParam(StandardNames.ARE_YOU_AN_INTERNAL_CALLER, "!Object");
  }

  /**
   * Parameter names in positional templates are always slightly mangled and then reassigned to
   * locals with the right name.
   *
   * <p>This helps in cases where we need to narrow types since JSC type inference cannot reliably
   * track reassignments to locals across function boundaries. See b/130651307
   */
  protected static String getPositionalParamName(TemplateParam param) {
    return "p$" + param.name();
  }

  protected JsDoc generatePositionalFunctionJsDoc(TemplateNode node, boolean addVariantParam) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    addInternalCallerParam(jsDocBuilder);
    addIjDataParam(jsDocBuilder, /* forPositionalSignature= */ true);
    for (TemplateParam param : paramsInOrder(node)) {
      JsType jsType = getJsTypeForParamForDeclaration(param.authoredType());
      jsDocBuilder.addParam(
          getPositionalParamName(param), jsType.typeExpr() + (param.isRequired() ? "" : "="));
      // TODO(lukes): this should add goog.requires for the referenced types
    }
    if (addVariantParam) {
      jsDocBuilder.addParam(StandardNames.OPT_VARIANT, "string=");
    }
    addReturnTypeAndAnnotations(node, jsDocBuilder);
    // TODO(b/11787791): make the checkTypes suppression more fine grained.
    jsDocBuilder.addParameterizedAnnotation("suppress", "checkTypes");
    return jsDocBuilder.build();
  }

  protected JsDoc generateEmptyFunctionJsDoc(TemplateNode node) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    String ijDataTypeExpression = ijDataTypeExpression(jsDocBuilder);
    jsDocBuilder.addAnnotation(
        "type",
        String.format("{function(?Object<string, *>=, ?%s=):string}", ijDataTypeExpression));
    jsDocBuilder.addParameterizedAnnotation("suppress", "checkTypes");
    return jsDocBuilder.build();
  }

  protected JsDoc generateFunctionJsDoc(
      TemplateNode node, String alias, boolean suppressCheckTypes, boolean addVariantParam) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    // TODO(b/177856412): rename to something that doesn't begin with {@code opt_}
    if (hasOnlyImplicitParams(node)) {
      jsDocBuilder.addParam(StandardNames.OPT_DATA, "?Object<string, *>=");
    } else if (new ShouldEnsureDataIsDefinedVisitor().exec(node)) {
      // All parameters are optional or only owned by an indirect callee; caller doesn't need to
      // pass an object.
      jsDocBuilder.addParam(StandardNames.OPT_DATA, "?" + alias + ".Params=");
    } else {
      jsDocBuilder.addParam(StandardNames.OPT_DATA, "!" + alias + ".Params");
    }
    addIjDataParam(jsDocBuilder, /* forPositionalSignature= */ false);
    if (addVariantParam) {
      jsDocBuilder.addParam(StandardNames.OPT_VARIANT, "string=");
    }
    addReturnTypeAndAnnotations(node, jsDocBuilder);
    if (suppressCheckTypes) {
      // TODO(b/11787791): make the checkTypes suppression more fine grained.
      jsDocBuilder.addParameterizedAnnotation("suppress", "checkTypes");
    } else {
      if (Metadata.buildTemplateType(node).getActualParameters().stream()
          .anyMatch(TemplateType.Parameter::isImplicit)) {
        jsDocBuilder.addParameterizedAnnotation("suppress", "missingProperties");
      }
    }
    if (node instanceof TemplateElementNode) {
      jsDocBuilder.addParameterizedAnnotation("suppress", "uselessCode");
      jsDocBuilder.addParameterizedAnnotation("suppress", "suspiciousCode");
    }
    return jsDocBuilder.build();
  }

  protected final void addReturnTypeAndAnnotations(TemplateNode node, JsDoc.Builder jsDocBuilder) {
    var returnType = getTemplateReturnType(node);
    jsDocBuilder.addParameterizedAnnotation("return", returnType.typeExpr());
    jsDocBuilder.addGoogRequires(returnType.googRequires());
    if (node.getVisibility() == Visibility.PRIVATE) {
      jsDocBuilder.addAnnotation("private");
    } else if (node.getVisibility() == Visibility.PACKAGE) {
      jsDocBuilder.addAnnotation("package");
    }
  }

  protected ImmutableList<Expression> templateArguments(
      TemplateNode node, boolean isPositionalStyle) {
    if (isPositionalStyle) {
      return ImmutableList.of(
          Expressions.objectLiteral(
              node.getParams().stream()
                  .collect(
                      toImmutableMap(
                          p -> genParamPropAlias(p.name()),
                          p ->
                              sourceMapHelper.setPrimaryLocation(
                                  Id.builder(getPositionalParamName(p))
                                      .setSourceName(p.name())
                                      .build(),
                                  p.getSourceLocation())))),
          JsRuntime.IJ_DATA);
    }
    return ImmutableList.of(JsRuntime.OPT_DATA, JsRuntime.IJ_DATA);
  }

  protected final Statement generateStubbingTest(
      TemplateNode node, String alias, boolean isPositionalStyle) {
    return Statements.ifStatement(
            JsRuntime.GOOG_DEBUG.and(
                JsRuntime.SOY_STUBS_MAP.bracketAccess(stringLiteral(node.getTemplateName())),
                templateTranslationContext.codeGenerator()),
            Statements.returnValue(
                JsRuntime.SOY_STUBS_MAP
                    .bracketAccess(stringLiteral(node.getTemplateName()))
                    .call(templateArguments(node, isPositionalStyle))))
        .build();
  }

  /** Returns the simple type of IjData, adding requires as necessary. */
  protected String ijDataTypeExpression(JsDoc.Builder jsDocBuilder) {
    GoogRequire googSoy = jsSrcOptions.shouldGenerateGoogModules() ? GOOG_SOY_ALIAS : GOOG_SOY;
    jsDocBuilder.addGoogRequire(googSoy);
    return googSoy.alias() + ".IjData";
  }

  protected void addIjDataParam(JsDoc.Builder jsDocBuilder, boolean forPositionalSignature) {
    String ijDataTypeExpression = ijDataTypeExpression(jsDocBuilder);
    if (forPositionalSignature) {
      jsDocBuilder.addParam(StandardNames.DOLLAR_IJDATA, "!" + ijDataTypeExpression);
    } else {
      // TODO(b/177856412): rename to something that doesn't begin with {@code opt_}
      jsDocBuilder.addParam(
          StandardNames.OPT_IJDATA,
          String.format("(?%s|?Object<string, *>)=", ijDataTypeExpression));
    }
  }

  /**
   * Generates a function that simply unpacks the opt_data object and calls the positional function
   */
  @CheckReturnValue
  protected Statement generateDelegateFunction(TemplateNode templateNode, String alias) {
    ImmutableList.Builder<Statement> bodyStatements = ImmutableList.builder();
    // Generate statement to ensure data is defined, if necessary.
    if (new ShouldEnsureDataIsDefinedVisitor().exec(templateNode)) {
      bodyStatements.add(
          assign(
              OPT_DATA,
              OPT_DATA.or(EMPTY_OBJECT_LITERAL, templateTranslationContext.codeGenerator())));
    }
    bodyStatements.add(redeclareIjData(templateNode));
    List<Expression> callParams = new ArrayList<>(getFixedParamsToPositionalCall(templateNode));
    for (TemplateParam param : paramsInOrder(templateNode)) {
      callParams.add(genCodeForParamAccess(genParamPropAlias(param.name()), param));
    }
    if (isModifiableWithUseVariantType(templateNode)) {
      callParams.add(OPT_VARIANT);
    }
    boolean voidReturn =
        isIncrementalDom()
            && (templateNode.getContentKind().isHtml()
                || templateNode.getContentKind() == SanitizedContentKind.ATTRIBUTES);
    Expression callExpr = dottedIdNoRequire(alias + "$").call(callParams);
    bodyStatements.add(voidReturn ? callExpr.asStatement() : returnValue(callExpr));
    return Statements.of(bodyStatements.build());
  }

  /** Return the parameters always present in positional calls. */
  protected ImmutableList<Expression> getFixedParamsToPositionalCall(TemplateNode node) {
    return ImmutableList.of(JsRuntime.SOY_INTERNAL_CALL_MARKER, JsRuntime.IJ_DATA);
  }

  /** Return the parameters always present in non-positional calls. */
  protected ImmutableList<Expression> getFixedParamsForNonPositionalCall(TemplateNode node) {
    return ImmutableList.of(OPT_DATA, id(StandardNames.DOLLAR_IJDATA));
  }

  private boolean hasOnlyOptionalInjects(TemplateNode templateNode) {
    ImmutableList<TemplateParam> injects = templateNode.getInjectedParams();
    return !injects.isEmpty() && injects.stream().allMatch(p -> p.isExplicitlyOptional());
  }

  /**
   * Generates a statement that that assigns {@code opt_ijData} to {@code $ijData} to adjust types.
   */
  protected final Statement redeclareIjData(TemplateNode templateNode) {
    GoogRequire googSoy = jsSrcOptions.shouldGenerateGoogModules() ? GOOG_SOY_ALIAS : GOOG_SOY;
    return VariableDeclaration.builder(StandardNames.DOLLAR_IJDATA)
        .setRhs(
            (hasOnlyOptionalInjects(templateNode)
                    ? id(StandardNames.OPT_IJDATA)
                        .nullishCoalesce(
                            EMPTY_OBJECT_LITERAL, templateTranslationContext.codeGenerator())
                    : id(StandardNames.OPT_IJDATA))
                .castAs("!" + googSoy.alias() + ".IjData", ImmutableSet.of(googSoy)))
        .build();
  }

  /** Generates the shim function containing mod template selection logic. */
  protected Statement generateModTemplateSelection(
      TemplateNode node, String alias, CodeChunk.Generator codeGenerator) {
    ImmutableList.Builder<Statement> bodyStatements = ImmutableList.builder();
    if (!state.generatePositionalParamsSignature) {
      bodyStatements.add(redeclareIjData(node));
    } else {
      bodyStatements.add(
          JsRuntime.SOY_ARE_YOU_AN_INTERNAL_CALLER
              .call(id(StandardNames.ARE_YOU_AN_INTERNAL_CALLER))
              .asStatement());
    }
    bodyStatements.add(generateStubbingTest(node, alias, state.generatePositionalParamsSignature));
    Expression templateId =
        SOY_GET_DELTEMPLATE_ID.call(
            stringLiteral(delTemplateNamer.getDelegateName((TemplateBasicNode) node)));
    Expression delegateFn =
        isModifiableWithUseVariantType(node)
            ? SOY_GET_DELEGATE_FN.call(templateId, OPT_VARIANT)
            : SOY_GET_DELEGATE_FN.call(templateId);
    if (!state.generatePositionalParamsSignature) {
      bodyStatements.add(returnValue(delegateFn.call(getFixedParamsForNonPositionalCall(node))));
    } else {
      List<Expression> callParams = new ArrayList<>(getFixedParamsToPositionalCall(node));
      for (TemplateParam param : paramsInOrder(node)) {
        callParams.add(Id.create(genParamAlias(param.name())));
      }
      bodyStatements.add(returnValue(delegateFn.call(callParams)));
    }
    return Statements.of(bodyStatements.build());
  }

  /** Generates the function body. */
  @CheckReturnValue
  protected Statement generateFunctionBody(
      TemplateNode node, String alias, @Nullable String objectParamName, boolean addStubMapLogic) {
    boolean isPositionalStyle = objectParamName == null;
    ImmutableList.Builder<Statement> bodyStatements = ImmutableList.builder();
    if (!isPositionalStyle) {
      bodyStatements.add(redeclareIjData(node));
    } else {
      bodyStatements.add(
          JsRuntime.SOY_ARE_YOU_AN_INTERNAL_CALLER
              .call(id(StandardNames.ARE_YOU_AN_INTERNAL_CALLER))
              .asStatement());
    }
    if (addStubMapLogic) {
      bodyStatements.add(generateStubbingTest(node, alias, isPositionalStyle));
    }
    // Generate statement to ensure data is defined, if necessary and this is not a positional style
    // method, if this is a positional style then we don't have an opt_data field
    if (!isPositionalStyle && new ShouldEnsureDataIsDefinedVisitor().exec(node)) {
      bodyStatements.add(
          assign(
              OPT_DATA,
              OPT_DATA.or(EMPTY_OBJECT_LITERAL, templateTranslationContext.codeGenerator())));
    }

    // Type check parameters.
    bodyStatements.add(genParamTypeChecks(node, alias, isPositionalStyle));

    checkState(
        !(node instanceof TemplateElementNode)
            || ((TemplateElementNode) node).getStateVars().isEmpty(),
        "state vars in %s should've been removed by DesugarStateNodesPass",
        node.getTemplateName());

    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as JS
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the JS expressions and return the result.

      // The template is strict. Thus, it applies an escaping directive to *every* print command,
      // which means that no print command produces a number, which means that there is no danger
      // of a plus operator between two print commands doing numeric addition instead of string
      // concatenation. And since a strict template needs to return SanitizedContent, it is ok to
      // get an expression that produces SanitizedContent, which is indeed possible with an
      // escaping directive that produces SanitizedContent. Thus, we do not have to be extra
      // careful when concatenating the expressions in the list.
      bodyStatements.add(
          returnValue(
              genJsExprsVisitor.execRenderUnitNodeAsSingleExpression(
                  node, /* concatForceString= */ false)));
    } else {
      // Case 2: Normal case.
      bodyStatements.add(
          state
              .createTemplateBodyVisitor(genJsExprsVisitor)
              .execRenderUnitNodeAsStatements(
                  node, "$output", /* returnOutput= */ true, /* ordain= */ true));
    }
    return Statements.of(bodyStatements.build());
  }

  @VisibleForTesting
  Statement visitTemplateNodeChildren(TemplateNode node) {
    return Statements.of(state.createTemplateBodyVisitor(genJsExprsVisitor).visitChildren(node));
  }

  protected final Expression sanitize(Expression templateBody, SanitizedContentKind contentKind) {
    if (contentKind != SanitizedContentKind.TEXT) {
      // Templates with autoescape="strict" return the SanitizedContent wrapper for its kind:
      // - Call sites are wrapped in an escaper. Returning SanitizedContent prevents re-escaping.
      // - The topmost call into Soy returns a SanitizedContent. This will make it easy to take
      //   the result of one template and feed it to another, and also to confidently assign
      //   sanitized HTML content to innerHTML. This does not use the internal-blocks variant,
      //   and so will wrap empty strings.
      return sanitizedContentOrdainerFunction(contentKind).call(templateBody);
    }
    return templateBody;
  }

  protected TranslateExprNodeVisitor getExprTranslator() {
    return state.createTranslateExprNodeVisitor();
  }

  protected Expression translateExpr(ExprNode expr) {
    return getExprTranslator().exec(expr);
  }

  protected Expression genCodeForParamAccess(String paramName, TemplateParam param) {
    return getExprTranslator().genCodeForParamAccess(paramName, param);
  }

  @Override
  protected void visitExternNode(ExternNode node) {
    if (node.getJsImpl().isEmpty() && node.getAutoImpl().isEmpty()) {
      state.errorReporter.report(
          node.getSourceLocation(), EXTERN_NO_JS_IMPL, node.getIdentifier().identifier());
      return;
    }

    String externName = node.getIdentifier().originalName();

    if (node.getJsImpl().isPresent()) {
      JsImplNode js = node.getJsImpl().get();
      GoogRequire externRequire;
      Expression externReference;
      if (jsSrcOptions.shouldGenerateGoogModules()) {
        externRequire = GoogRequire.createWithAlias(js.module(), js.module().replace('.', '$'));
        externReference = dottedIdNoRequire(externRequire.alias()).dotAccess(js.function());
      } else {
        externRequire = GoogRequire.create(js.module());
        externReference = GOOG_MODULE_GET.call(stringLiteral(js.module())).dotAccess(js.function());
      }
      jsCodeBuilder.addGoogRequire(externRequire);

      if (node.isExported()) {
        SoyFileNode file = node.getNearestAncestor(SoyFileNode.class);
        Expression exportAlias =
            jsSrcOptions.shouldGenerateGoogModules()
                ? JsRuntime.EXPORTS
                : dottedIdNoRequire(getGoogModuleNamespace(file.getNamespace()));
        Expression export = exportAlias.dotAccess(externName);
        jsCodeBuilder
            .append(BLANK_LINE)
            .append(BLANK_LINE)
            .append(
                Statements.assign(
                    export, externReference, JsDoc.builder().addAnnotation("const").build()));
      }
    } else {
      AutoImplNode auto = node.getAutoImpl().get();
      String partialName = node.getVar().name();
      ByteSpan imputesSpan = SoyTreeUtils.getByteSpan(node, node.getIdentifier().location());

      JsDoc.Builder jsDocBuilder = JsDoc.builder();
      for (FunctionType.Parameter param : node.getType().getParameters()) {
        if (param.isVarArgs() && param.getType() instanceof AbstractIterableType) {
          JsType jsType =
              getJsTypeForParamForDeclaration(
                  ((AbstractIterableType) param.getType()).getElementType());
          jsDocBuilder
              .addVarArgsParam("p$" + param.getName(), jsType.typeExpr())
              .addGoogRequires(jsType);
        } else {
          JsType jsType = getJsTypeForParamForDeclaration(param.getType());
          jsDocBuilder.addParam("p$" + param.getName(), jsType.typeExpr()).addGoogRequires(jsType);
        }
      }
      JsType returnType = getJsTypeForParamForDeclaration(node.getType().getReturnType());
      jsDocBuilder
          .addParameterizedAnnotation("return", returnType.typeExpr())
          .addGoogRequires(returnType);

      Statement body = generateAutoExtern(auto);
      Expression function = Expressions.function(jsDocBuilder.build(), body);

      jsCodeBuilder.append(
          topLevelAssignment(
              node, node.isExported(), partialName, jsDocBuilder, imputesSpan, function));
    }
  }

  private boolean registerLocalExtern(ExternNode node) {
    String externName = node.getIdentifier().originalName();
    // Skip if we handled this impl already, e.g. a prev extern overload.
    if (templateTranslationContext.soyToJsVariableMappings().has(externName)) {
      return false;
    }
    if (node.getJsImpl().isPresent()) {
      JsImplNode js = node.getJsImpl().get();
      GoogRequire externRequire;
      Expression externReference;
      if (jsSrcOptions.shouldGenerateGoogModules()) {
        externRequire = GoogRequire.createWithAlias(js.module(), js.module().replace('.', '$'));
        externReference = dottedIdNoRequire(externRequire.alias()).dotAccess(js.function());
      } else {
        externReference = GOOG_MODULE_GET.call(stringLiteral(js.module())).dotAccess(js.function());
      }
      templateTranslationContext.soyToJsVariableMappings().put(externName, externReference);
    } else {
      String partialName = node.getVar().name();
      templateTranslationContext
          .soyToJsVariableMappings()
          .put(externName, topLevelLhs(node, node.isExported(), partialName, null));
    }
    return true;
  }

  @CheckReturnValue
  protected Statement generateAutoExtern(AutoImplNode node) {
    try (var unused = templateTranslationContext.enterSoyAndJsScope()) {
      SoyToJsVariableMappings vars = templateTranslationContext.soyToJsVariableMappings();
      for (TemplateParam param : node.getParent().getParamVars()) {
        vars.put(param, Expressions.id("p$" + param.name()));
      }
      genJsExprsVisitor = state.createJsExprsVisitor();
      return Statements.of(
          state
              .createTemplateBodyVisitor(
                  genJsExprsVisitor, OutputVarHandler.DISALLOWED, /* mutableLets= */ true)
              .visitChildren(node));
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers

  /** Generate the JSDoc for the opt_data parameter. */
  private String genParamsRecordType(TemplateNode node) {
    Set<String> paramNames = new HashSet<>();

    // Generate members for explicit params.
    Map<String, String> record = new LinkedHashMap<>();
    for (TemplateParam param : paramsInOrder(node)) {
      if (param.isImplicit()) {
        continue;
      }
      JsType jsType = getJsTypeForParamForDeclaration(param.authoredType());
      record.put(
          genParamPropAlias(param.name()),
          jsType.typeExprForRecordMember(/* isOptional= */ !param.isRequired()));
      for (GoogRequire require : jsType.googRequires()) {
        // TODO(lukes): switch these to requireTypes
        jsCodeBuilder.addGoogRequire(require);
      }
      paramNames.add(param.name());
    }

    // Do the same for indirect params, if we can find them.
    // If there's a conflict between the explicitly-declared type, and the type
    // inferred from the indirect params, then the explicit type wins.
    // Also note that indirect param types may not be inferrable if the target
    // is not in the current compilation file set.
    IndirectParamsInfo ipi =
        new IndirectParamsCalculator(state.fileSetMetadata).calculateIndirectParams(node);
    for (String indirectParamName : ipi.indirectParamTypes.keySet()) {
      if (paramNames.contains(indirectParamName)) {
        continue;
      }
      ImmutableSet<SoyType> paramTypes = ipi.indirectParamTypes.get(indirectParamName);
      SoyType combinedType = SoyTypes.computeLowestCommonType(typeRegistry, paramTypes);
      if (ipi.mayHaveIndirectParamsInExternalDelCalls && SoyTypes.hasProtoDep(combinedType)) {
        // Don't add proto types when modifiables are involved since we might not have a dep to the
        // template with the param. This matches what do for the Java builders.
        continue;
      }

      // TODO: detect cases where nullable is not needed (requires flow
      // analysis to determine if the template is always called.)
      SoyType indirectParamType = SoyTypes.unionWithNull(combinedType);
      JsType jsType = getJsTypeForParamForDeclaration(indirectParamType);

      // Omit named types that have transitive references since we don't know if they are
      // imported or not.
      if (SoyTypes.allLogicalTypes(combinedType, typeRegistry).anyMatch(t -> t instanceof NamedType)
          && jsType.googRequires().stream()
              .anyMatch(
                  req -> {
                    SourceLogicalPath path =
                        state.fileSetMetadata.getPathForNamespace(req.symbol());
                    if (path == null
                        || state.fileSetMetadata.getFile(path).getSoyFileKind()
                            == SoyFileKind.INDIRECT_DEP) {
                      return true;
                    }
                    return false;
                  })) {
        continue;
      }

      jsCodeBuilder.addGoogRequires(
          jsType.googRequires().stream()
              .map(GoogRequire::toRequireType)
              .collect(toImmutableList()));
      record.put(indirectParamName, jsType.typeExprForRecordMember(/* isOptional= */ true));
    }
    return JsType.toRecord(record);
  }

  protected final Statement genParamDefault(
      TemplateParam param,
      Expression paramTempVar,
      JsType typeForCast,
      CodeChunk.Generator codeGenerator) {
    checkArgument(param.hasDefault());

    // var = var === undefined ? default : var;
    return Statements.assign(
        paramTempVar,
        Expressions.ifExpression(
                paramTempVar.tripleEquals(Expressions.LITERAL_UNDEFINED),
                translateExpr(param.defaultValue()))
            .setElse(paramTempVar)
            .build(codeGenerator)
            .castAs(typeForCast.typeExpr(), typeForCast.googRequires()));
  }

  /**
   * Generate code to verify the runtime types of the input params. Also typecasts the input
   * parameters and assigns them to local variables for use in the template.
   *
   * @param node the template node.
   */
  @CheckReturnValue
  protected Statement genParamTypeChecks(
      TemplateNode node, String alias, boolean isPositionalStyle) {
    ImmutableList.Builder<Statement> declarations = ImmutableList.builder();
    for (TemplateParam param : node.getAllParams()) {
      String paramName = param.name();
      SoyType paramType = param.authoredType();
      // injected params are always referenced from the opt_ijData parameter
      boolean isThisParamPositional = isPositionalStyle && !param.isInjected();
      CodeChunk.Generator generator = templateTranslationContext.codeGenerator();
      String paramAlias = genParamAlias(paramName);
      Expression paramChunk =
          isThisParamPositional
              ? Id.create(getPositionalParamName(param))
              : genCodeForParamAccess(paramName, param);
      JsType jsType = getJsTypeForParamTypeCheck(paramType);
      // TODO(lukes): for positional style params we should switch to inline defaults in the
      // declaration and let the JS VM handle this.
      if (param.hasDefault()) {
        if (!isThisParamPositional) {
          // if we haven't captured the param into a mutable temporary allocate one now.
          paramChunk = generator.declarationBuilder().setMutable().setRhs(paramChunk).build().ref();
        }
        declarations.add(genParamDefault(param, paramChunk, jsType, generator));
      }
      Expression initializer =
          jsType
              .getSoyParamTypeAssertion(
                  paramChunk,
                  paramName,
                  /* paramKind= */ param.isInjected() ? "@inject" : "@param",
                  generator)
              .orElse(paramChunk);

      // Cast to a better type, if necessary and possible.
      JsType declType = getJsTypeForParamForDeclaration(paramType);
      if (!jsType.typeExpr().equals(declType.typeExpr()) && !JsSrcUtils.isReservedWord(paramName)) {
        // TODO(b/256679865): rename JS builtins here.
        initializer = initializer.castAs(jsType.typeExpr(), jsType.googRequires());
      }

      VariableDeclaration declaration =
          VariableDeclaration.builder(Id.create(paramAlias))
              .setRhs(initializer)
              .setRequires(jsType.googRequires())
              .setJsDoc(
                  JsDoc.builder().addParameterizedAnnotation("const", jsType.typeExpr()).build())
              .build();
      declarations.add(declaration);
      sourceMapHelper.setPrimaryLocation(declaration, param.getSourceLocation());

      templateTranslationContext
          .soyToJsVariableMappings()
          // TODO(lukes): this should really be declartion.ref() but we cannot do that until
          // everything is on the code chunk api.
          .put(param, declaration.varName());
    }
    return Statements.of(declarations.build());
  }

  /** Gets the type to use for a parameter in record type declarations. */
  protected JsType getJsTypeForParamForDeclaration(SoyType paramType) {
    return jsTypeRegistry.getWithDelegate(JsType.forJsSrc(), paramType);
  }

  /** Gets the effective type of a positional parameter. */
  protected JsType getJsTypeForParam(SoyType paramType) {
    return jsTypeRegistry.getWithDelegate(JsType.forJsSrc(), paramType);
  }

  /** Gets the type to use for a parameter in runtime assertions. */
  protected JsType getJsTypeForParamTypeCheck(SoyType paramType) {
    return jsTypeRegistry.getWithDelegate(JsType.forJsTypeCheck(), paramType);
  }

  /**
   * Generate a name for the local variable which will store the value of a parameter, avoiding
   * collision with JavaScript reserved words.
   */
  public static String genParamAlias(String paramName) {
    return JsSrcUtils.isReservedWord(paramName) ? "param$" + paramName : paramName;
  }

  /**
   * Generate a name for the field of a template opt_data typedef. These must not collide with
   * built-in property names of Object.
   */
  public static String genParamPropAlias(String paramName) {
    return JsSrcUtils.isPropertyOfObject(paramName) ? "param$" + paramName : paramName;
  }

  protected boolean isIncrementalDom() {
    return false;
  }

  private String namespaceForPath(SourceLogicalPath path) {
    return state.fileSetMetadata.getNamespaceForPath(path);
  }

  /**
   * A type of {@link JsTypeProducer} that always receives a producer to which to delegate. This
   * pattern is useful in the context of a JS file where some types may be local symbols. An
   * implementation of this interface can resolve certain local types and delegate other queries to
   * the provided specific delegate.
   */
  public interface ScopedJsTypeRegistry {
    ScopedJsTypeRegistry PASSTHROUGH = RecursiveJsTypeProducer::get;

    JsType getWithDelegate(RecursiveJsTypeProducer delegate, SoyType type);
  }

  /** Implementation of {@link ScopedJsTypeRegistry}. */
  protected final class FileScopeJsTypeRegistry implements ScopedJsTypeRegistry, JsTypeProducer {

    private RecursiveJsTypeProducer delegate;
    private final Map<SoyType, JsType> fileScopedTypes = new HashMap<>();

    void addTypeMap(SoyType from, JsType to) {
      fileScopedTypes.put(from, to);
    }

    @Override
    public JsType getWithDelegate(RecursiveJsTypeProducer delegate, SoyType type) {
      this.delegate = Preconditions.checkNotNull(delegate);
      JsType rv = get(type);
      this.delegate = null;
      return rv;
    }

    @Override
    public JsType get(SoyType type) {
      JsType cached = fileScopedTypes.get(type);
      if (cached != null) {
        return cached;
      }
      if (type instanceof NamedType) {
        NamedType namedType = (NamedType) type;
        String alias = templateAliases.getNullable(namedType.getFqn());
        if (alias != null) {
          return JsType.localTypedef("!" + alias);
        }
      }
      return delegate.get(type, this);
    }
  }
}
