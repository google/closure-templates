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
import static com.google.template.soy.jssrc.dsl.Expression.EMPTY_OBJECT_LITERAL;
import static com.google.template.soy.jssrc.dsl.Expression.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.number;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;
import static com.google.template.soy.jssrc.dsl.Statement.assign;
import static com.google.template.soy.jssrc.dsl.Statement.forLoop;
import static com.google.template.soy.jssrc.dsl.Statement.ifStatement;
import static com.google.template.soy.jssrc.dsl.Statement.returnValue;
import static com.google.template.soy.jssrc.dsl.Statement.switchValue;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_DEBUG;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_OBJECT;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_REQUIRE;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_GET_DELTEMPLATE_ID;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_REGISTER_DELEGATE_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.WINDOW_CONSOLE_LOG;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentOrdainerFunction;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentOrdainerFunctionForInternalBlocks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.ConditionalBuilder;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.SwitchBuilder;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.passes.IndirectParamsCalculator;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.passes.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.shared.RangeArgs;
import com.google.template.soy.shared.internal.FindCalleesNotInFileVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnknownType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.CheckReturnValue;

/**
 * Visitor for generating full JS code (i.e. statements) for parse tree nodes.
 *
 * <p>Precondition: MsgNode should not exist in the tree.
 *
 * <p>{@link #gen} should be called on a full parse tree. JS source code will be generated for all
 * the Soy files. The return value is a list of strings, each string being the content of one
 * generated JS file (corresponding to one Soy file).
 *
 */
public class GenJsCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {

  /** Regex pattern to look for dots in a template name. */

  /** The options for generating JS source code. */
  protected final SoyJsSrcOptions jsSrcOptions;

  protected final JavaScriptValueFactoryImpl javaScriptValueFactory;

  /** Instance of DelTemplateNamer to use. */
  private final DelTemplateNamer delTemplateNamer;

  /** Instance of GenCallCodeUtils to use. */
  protected final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  protected final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** The CanInitOutputVarVisitor used by this instance. */
  private final CanInitOutputVarVisitor canInitOutputVarVisitor;

  /** Factory for creating an instance of GenJsExprsVisitor. */
  private final GenJsExprsVisitorFactory genJsExprsVisitorFactory;

  /** The contents of the generated JS files. */
  private List<String> jsFilesContents;

  /** The CodeBuilder to build the current JS file being generated (during a run). */
  @VisibleForTesting JsCodeBuilder jsCodeBuilder;

  /** The GenJsExprsVisitor used for the current template. */
  protected GenJsExprsVisitor genJsExprsVisitor;

  /** The assistant visitor for msgs used for the current template (lazily initialized). */
  @VisibleForTesting GenJsCodeVisitorAssistantForMsgs assistantForMsgs;

  protected TemplateRegistry templateRegistry;

  private final SoyTypeRegistry typeRegistry;

  protected ErrorReporter errorReporter;
  protected TranslationContext templateTranslationContext;

  protected List<Statement> staticVarDeclarations;

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name. This is created on a per {@link SoyFileNode} basis.
   */
  protected TemplateAliases templateAliases;

  protected GenJsCodeVisitor(
      SoyJsSrcOptions jsSrcOptions,
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      DelTemplateNamer delTemplateNamer,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory,
      SoyTypeRegistry typeRegistry) {
    this.jsSrcOptions = jsSrcOptions;
    this.javaScriptValueFactory = javaScriptValueFactory;
    this.delTemplateNamer = delTemplateNamer;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.canInitOutputVarVisitor = canInitOutputVarVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;
    this.typeRegistry = typeRegistry;
  }

  public List<String> gen(
      SoyFileSetNode node, TemplateRegistry registry, ErrorReporter errorReporter) {
    this.templateRegistry = checkNotNull(registry);
    this.errorReporter = checkNotNull(errorReporter);
    try {
      jsFilesContents = new ArrayList<>();
      jsCodeBuilder = null;
      genJsExprsVisitor = null;
      assistantForMsgs = null;
      visit(node);
      return jsFilesContents;
    } finally {
      this.templateRegistry = null;
      this.errorReporter = null;
    }
  }

  /** @deprecated Call {@link #gen} instead. */
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

  /**
   * This method must only be called by assistant visitors, in particular
   * GenJsCodeVisitorAssistantForMsgs.
   */
  public Statement visitForUseByAssistantsAsCodeChunk(SoyNode node) {
    return doVisitReturningCodeChunk(node, false);
  }

  /** TODO: tests should use {@link #gen} instead. */
  @VisibleForTesting
  void visitForTesting(SoyNode node, TemplateRegistry registry, ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.templateRegistry = registry;
    visit(node);
  }

  @Override
  protected void visitChildren(ParentSoyNode<?> node) {

    // If the block is empty or if the first child cannot initialize the output var, we must
    // initialize the output var.
    if (node.numChildren() == 0 || !canInitOutputVarVisitor.exec(node.getChild(0))) {
      jsCodeBuilder.initOutputVarIfNecessary();
    }

    // For children that are computed by GenJsExprsVisitor, try to process as many of them as we can
    // before adding to outputVar.
    //
    // output += 'a' + 'b';
    // is preferable to
    // output += 'a';
    // output += 'b';
    // This is because it is actually easier for the jscompiler to optimize.

    List<Expression> consecChunks = new ArrayList<>();

    for (SoyNode child : node.getChildren()) {
      if (isComputableAsJsExprsVisitor.exec(child)) {
        consecChunks.addAll(genJsExprsVisitor.exec(child));
      } else {
        if (!consecChunks.isEmpty()) {
          jsCodeBuilder.addChunksToOutputVar(consecChunks);
          consecChunks.clear();
        }
        visit(child);
      }
    }

    if (!consecChunks.isEmpty()) {
      jsCodeBuilder.addChunksToOutputVar(consecChunks);
      consecChunks.clear();
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      visit(soyFile);
    }
  }

  /** @return A new CodeBuilder to create the contents of a file with. */
  protected JsCodeBuilder createCodeBuilder() {
    return new JsCodeBuilder();
  }

  /** @return A child CodeBuilder that inherits from the current builder. */
  protected JsCodeBuilder createChildJsCodeBuilder() {
    return new JsCodeBuilder(jsCodeBuilder);
  }

  /** @return The CodeBuilder used for generating file contents. */
  protected JsCodeBuilder getJsCodeBuilder() {
    return jsCodeBuilder;
  }

  /**
   * Visits the children of the given node, returning a {@link CodeChunk} encapsulating its
   * JavaScript code. The chunk is indented one level from the current indent level.
   *
   * <p>This is needed to prevent infinite recursion when a visit() method needs to visit its
   * children and return a CodeChunk.
   *
   * <p>Unlike {@link TranslateExprNodeVisitor}, GenJsCodeVisitor does not return anything as the
   * result of visiting a subtree. To get recursive chunk-building, we use a hack, swapping out the
   * {@link JsCodeBuilder} and using the unsound {@link
   * Statement#treatRawStringAsStatementLegacyOnly} API.
   */
  protected Statement visitChildrenReturningCodeChunk(ParentSoyNode<?> node) {
    return doVisitReturningCodeChunk(node, true);
  }

  /** Do not use directly; use {@link #visitChildrenReturningCodeChunk} instead. */
  private Statement doVisitReturningCodeChunk(SoyNode node, boolean visitChildren) {
    // Replace jsCodeBuilder with a child JsCodeBuilder.
    JsCodeBuilder original = jsCodeBuilder;
    jsCodeBuilder = createChildJsCodeBuilder();

    // Visit body.
    // set indent to 0 since when rendering the CodeChunk, everything will get re-indented
    jsCodeBuilder.setIndent(0);

    if (visitChildren) {
      visitChildren((ParentSoyNode<?>) node);
    } else {
      visit(node);
    }

    Statement chunk =
        Statement.treatRawStringAsStatementLegacyOnly(
            jsCodeBuilder.getCode(), jsCodeBuilder.googRequires());

    jsCodeBuilder = original;

    return chunk;
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
    StringBuilder file = new StringBuilder();

    file.append("// This file was automatically generated by the Soy compiler.\n")
        .append("// Please don't edit this file by hand.\n")
        // This "source" comment makes Code Search link the gencode to the Soy source:
        .append("// source: ")
        .append(node.getFilePath())
        .append('\n');

    // Output a section containing optionally-parsed compiler directives in comments. Since these
    // are comments, they are not controlled by an option, and will be removed by minifiers that do
    // not understand them.
    file.append("\n");
    String fileOverviewDescription = "Templates in namespace " + node.getNamespace() + ".";
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    jsDocBuilder.addAnnotation("fileoverview", fileOverviewDescription);
    if (node.getDelPackageName() != null) {
      jsDocBuilder.addParameterizedAnnotation("modName", node.getDelPackageName());
    }
    addJsDocToProvideDelTemplates(jsDocBuilder, node);
    addJsDocToRequireDelTemplates(jsDocBuilder, node);
    addCodeToRequireCss(jsDocBuilder, node);
    jsDocBuilder.addAnnotation("public");
    file.append(jsDocBuilder.build());
    file.append("\n\n");

    // Add code to define JS namespaces or add provide/require calls for Closure Library.
    templateAliases = AliasUtils.IDENTITY_ALIASES;
    jsCodeBuilder = createCodeBuilder();

    if (jsSrcOptions.shouldGenerateGoogModules()) {
      templateAliases = AliasUtils.createTemplateAliases(node);

      addCodeToDeclareGoogModule(file, node);
      addCodeToRequireGoogModules(node);
    } else if (jsSrcOptions.shouldProvideRequireSoyNamespaces()) {
      addCodeToProvideSoyNamespace(file, node);
      file.append('\n');
      addCodeToRequireSoyNamespaces(node);
    } else {
      throw new AssertionError("impossible");
    }

    // add declarations for the ijdata params
    // This takes advantage of the way '@record' types in closure are 'open'.
    // Unfortunately clutz doesn't understand this:
    // https://github.com/angular/clutz/issues/832
    // So TS users will not be able to see the property definitions.
    // The most practical solution to that is for soy to generate its own .d.ts files.
    Map<String, SoyType> ijData = getAllIjDataParams(node);
    if (!ijData.isEmpty()) {
      GoogRequire require = GoogRequire.create("goog.soy");
      jsCodeBuilder.appendLine();
      for (Map.Entry<String, SoyType> entry : ijData.entrySet()) {
        jsCodeBuilder.appendLine();
        jsCodeBuilder.appendLine(
            JsDoc.builder()
                // Because every declaration can declare a type, we can get errors if they don't
                // declare identical types.  There isn't a good way to force identical declarations
                // so we just suppress the duplicate error warning.
                .addParameterizedAnnotation("suppress", "duplicate")
                // declare every field as optional.  This is because if a template is unused and
                // declares an ij param we don't want to force people to supply a value.
                .addParameterizedAnnotation(
                    "type",
                    getJsTypeForParamForDeclaration(entry.getValue()).typeExpr() + "|undefined")
                .build()
                .toString());
        jsCodeBuilder.append(
            require
                .reference()
                .dotAccess("IjData")
                .dotAccess("prototype")
                .dotAccess(entry.getKey())
                .asStatement());
      }
    }

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      jsCodeBuilder.appendLine().appendLine();
      staticVarDeclarations = new ArrayList<>();
      visit(template);
      if (!staticVarDeclarations.isEmpty()) {
        jsCodeBuilder.append(Statement.of(staticVarDeclarations));
      }
    }
    jsCodeBuilder.appendGoogRequiresTo(file);
    jsCodeBuilder.appendCodeTo(file);
    jsFilesContents.add(file.toString());
    jsCodeBuilder = null;
  }

  private Map<String, SoyType> getAllIjDataParams(SoyFileNode node) {
    Map<String, SoyType> params = new LinkedHashMap<>();
    for (TemplateNode template : node.getChildren()) {
      for (TemplateParam param : template.getInjectedParams()) {
        SoyType oldType = params.put(param.name(), param.type());
        if (oldType != null) {
          // merge the types
          params.put(
              param.name(),
              typeRegistry.getOrCreateUnionType(Arrays.asList(param.type(), oldType)));
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
  private static void addCodeToRequireCss(JsDoc.Builder header, SoyFileNode soyFile) {

    SortedSet<String> requiredCssNamespaces = new TreeSet<>();
    requiredCssNamespaces.addAll(soyFile.getRequiredCssNamespaces());
    for (TemplateNode template : soyFile.getChildren()) {
      requiredCssNamespaces.addAll(template.getRequiredCssNamespaces());
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
   * @param header
   * @param soyFile The node we're visiting.
   */
  private static void addCodeToProvideSoyNamespace(StringBuilder header, SoyFileNode soyFile) {
    header.append("goog.provide('").append(soyFile.getNamespace()).append("');\n");
  }

  /**
   * @param soyNamespace The namespace as declared by the user.
   * @return The namespace to import/export templates.
   */
  protected String getGoogModuleNamespace(String soyNamespace) {
    return soyNamespace;
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to generate a module definition.
   *
   * @param header
   * @param soyFile The node we're visiting.
   */
  private void addCodeToDeclareGoogModule(StringBuilder header, SoyFileNode soyFile) {
    String exportNamespace = getGoogModuleNamespace(soyFile.getNamespace());
    header.append("goog.module('").append(exportNamespace).append("');\n\n");
  }

  /**
   * Generates the module imports and aliasing. This generates code like the following:
   *
   * <pre>
   * var $import1 = goog.require('some.namespace');
   * var $templateAlias1 = $import1.tmplOne;
   * var $templateAlias2 = $import1.tmplTwo;
   * var $import2 = goog.require('other.namespace');
   * ...
   * </pre>
   *
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireGoogModules(SoyFileNode soyFile) {
    int counter = 1;

    // Get all the unique calls in the file.
    Set<String> calls = new HashSet<>();
    for (CallBasicNode callNode : SoyTreeUtils.getAllNodesOfType(soyFile, CallBasicNode.class)) {
      calls.add(callNode.getCalleeName());
    }

    // Map all the unique namespaces to the templates in those namespaces.
    SetMultimap<String, String> namespaceToTemplates = TreeMultimap.create();
    for (String call : calls) {
      namespaceToTemplates.put(call.substring(0, call.lastIndexOf('.')), call);
    }

    for (String namespace : namespaceToTemplates.keySet()) {
      // Skip the file's own namespace as there is nothing to import/alias.
      if (namespace.equals(soyFile.getNamespace())) {
        continue;
      }

      // Add a require of the module
      String namespaceAlias = "$import" + counter++;
      String importNamespace = getGoogModuleNamespace(namespace);
      jsCodeBuilder.append(
          VariableDeclaration.builder(namespaceAlias)
              .setRhs(GOOG_REQUIRE.call(stringLiteral(importNamespace)))
              .build());
      // Alias all the templates used from the module
      for (String fullyQualifiedName : namespaceToTemplates.get(namespace)) {
        String alias = templateAliases.get(fullyQualifiedName);
        String shortName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.'));
        jsCodeBuilder.append(
            VariableDeclaration.builder(alias)
                .setRhs(dottedIdNoRequire(namespaceAlias + shortName))
                .build());
      }
    }
  }

  private void addJsDocToProvideDelTemplates(JsDoc.Builder header, SoyFileNode soyFile) {

    SortedSet<String> delTemplateNames = new TreeSet<>();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template instanceof TemplateDelegateNode) {
        delTemplateNames.add(delTemplateNamer.getDelegateName((TemplateDelegateNode) template));
      }
    }
    for (String delTemplateName : delTemplateNames) {
      header.addParameterizedAnnotation("hassoydeltemplate", delTemplateName);
    }
  }

  private void addJsDocToRequireDelTemplates(JsDoc.Builder header, SoyFileNode soyFile) {

    SortedSet<String> delTemplateNames = new TreeSet<>();
    for (CallDelegateNode delCall :
        SoyTreeUtils.getAllNodesOfType(soyFile, CallDelegateNode.class)) {
      delTemplateNames.add(delTemplateNamer.getDelegateName(delCall));
    }
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

    String prevCalleeNamespace = null;
    Set<String> calleeNamespaces = new TreeSet<>();
    for (CallBasicNode node : new FindCalleesNotInFileVisitor().exec(soyFile)) {
      String calleeNotInFile = node.getCalleeName();
      int lastDotIndex = calleeNotInFile.lastIndexOf('.');
      calleeNamespaces.add(calleeNotInFile.substring(0, lastDotIndex));
    }

    for (String calleeNamespace : calleeNamespaces) {
      if (calleeNamespace.length() > 0 && !calleeNamespace.equals(prevCalleeNamespace)) {
        jsCodeBuilder.addGoogRequire(GoogRequire.create(calleeNamespace));
        prevCalleeNamespace = calleeNamespace;
      }
    }
  }

  /**
   * @param node The template node that is being generated
   * @return The JavaScript type of the content generated by this template.
   */
  protected String getTemplateReturnType(TemplateNode node) {
    // For strict autoescaping templates, the result is actually a typesafe wrapper.
    // We prepend "!" to indicate it is non-nullable.
    return node.getContentKind() == SanitizedContentKind.TEXT
        ? "string"
        : "!" + NodeContentKinds.toJsSanitizedContentCtorName(node.getContentKind());
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
    String templateName = node.getTemplateName();
    String partialName = node.getPartialTemplateName().substring(1);
    String alias;

    if (jsSrcOptions.shouldGenerateGoogModules() && node instanceof TemplateDelegateNode) {
      alias = partialName;
    } else {
      alias = templateAliases.get(templateName);
    }
    Expression aliasExp = dottedIdNoRequire(alias);

    // TODO(lukes): reserve all the namespace prefixes that are in scope
    // TODO(lukes): use this for all local variable declarations
    UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
    CodeChunk.Generator codeGenerator = CodeChunk.Generator.create(nameGenerator);
    templateTranslationContext =
        TranslationContext.of(
            SoyToJsVariableMappings.forNewTemplate(), codeGenerator, nameGenerator);
    genJsExprsVisitor =
        genJsExprsVisitorFactory.create(templateTranslationContext, templateAliases, errorReporter);
    assistantForMsgs = null;

    JsDoc jsDoc = generateFunctionJsDoc(node, alias);
    Expression function = Expression.function(jsDoc, generateFunctionBody(node, alias));
    ImmutableList.Builder<Statement> declarations = ImmutableList.builder();

    if (jsSrcOptions.shouldGenerateGoogModules()) {
      declarations.add(VariableDeclaration.builder(alias).setJsDoc(jsDoc).setRhs(function).build());
      // don't export deltemplates or private templates
      if (!(node instanceof TemplateDelegateNode) && node.getVisibility() == Visibility.PUBLIC) {
        declarations.add(assign(JsRuntime.EXPORTS.dotAccess(partialName), aliasExp));
      }
    } else {
      declarations.add(Statement.assign(aliasExp, function, jsDoc));
    }

    // ------ Add the @typedef of opt_data. ------
    if (!node.getParams().isEmpty()) {
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
                assign(aliasExp.dotAccess("soyTemplateName"), stringLiteral(templateName)))
            .build());

    // ------ If delegate template, generate a statement to register it. ------
    if (node instanceof TemplateDelegateNode) {
      TemplateDelegateNode nodeAsDelTemplate = (TemplateDelegateNode) node;
      declarations.add(
          SOY_REGISTER_DELEGATE_FN
              .call(
                  SOY_GET_DELTEMPLATE_ID.call(
                      stringLiteral(delTemplateNamer.getDelegateName(nodeAsDelTemplate))),
                  stringLiteral(nodeAsDelTemplate.getDelTemplateVariant()),
                  number(nodeAsDelTemplate.getDelPriority().getValue()),
                  aliasExp)
              .asStatement());
    }

    jsCodeBuilder.append(Statement.of(declarations.build()));
  }

  protected JsDoc generateFunctionJsDoc(TemplateNode node, String alias) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    if (!node.getParams().isEmpty()) {
      jsDocBuilder.addParam("opt_data", alias + ".Params");
    } else {
      jsDocBuilder.addParam("opt_data", "Object<string, *>=");
    }
    jsDocBuilder.addGoogRequire(GoogRequire.createTypeRequire("goog.soy"));
    // TODO(lukes): remove |Object<string, *> and only add the '=/?' if ij data is truly optional
    jsDocBuilder.addParam("opt_ijData", "(?goog.soy.IjData|?Object<string, *>)=");
    jsDocBuilder.addParam("opt_ijData_deprecated", "(?goog.soy.IjData|?Object<string, *>)=");

    String returnType = getTemplateReturnType(node);
    jsDocBuilder.addParameterizedAnnotation("return", returnType);
    // TODO(b/11787791): make the checkTypes suppression more fine grained.
    jsDocBuilder.addParameterizedAnnotation("suppress", "checkTypes");
    if (node.getVisibility() == Visibility.PRIVATE) {
      jsDocBuilder.addAnnotation("private");
    }
    return jsDocBuilder.build();
  }

  /** Generates the function body. */
  @CheckReturnValue
  protected Statement generateFunctionBody(TemplateNode node, String alias) {
    ImmutableList.Builder<Statement> bodyStatements = ImmutableList.builder();
    bodyStatements.add(
        Statement.assign(
            JsRuntime.OPT_IJ_DATA,
            id("opt_ijData_deprecated")
                .or(JsRuntime.OPT_IJ_DATA, templateTranslationContext.codeGenerator())
                .castAs("!goog.soy.IjData")));
    if (node instanceof TemplateElementNode) {
      TemplateElementNode elementNode = (TemplateElementNode) node;
      for (TemplateStateVar stateVar : elementNode.getStateVars()) {
        Expression expr = getExprTranslator().exec(stateVar.defaultValue());
        // A  state variable can be something like ns.foo.FooProto|null. Without
        // this cast, access to this variable can trigger JS conformance errors
        // due to unknown type.
        if (!stateVar.type().equals(stateVar.defaultValue().getType())) {
          expr = expr.castAs(JsType.forJsSrc(stateVar.type()).typeExpr());
        }
        bodyStatements.add(VariableDeclaration.builder(stateVar.name()).setRhs(expr).build());
      }
    }
    // Generate statement to ensure data is defined, if necessary.
    if (new ShouldEnsureDataIsDefinedVisitor().exec(node)) {
      bodyStatements.add(
          assign(
              OPT_DATA,
              OPT_DATA.or(EMPTY_OBJECT_LITERAL, templateTranslationContext.codeGenerator())));
    }

    // Type check parameters.
    bodyStatements.add(genParamTypeChecks(node, alias));

    SanitizedContentKind kind = node.getContentKind();
    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as JS
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the JS expressions and return the result.

      List<Expression> templateBodyChunks = genJsExprsVisitor.exec(node);
      // The template is strict. Thus, it applies an escaping directive to *every* print command,
      // which means that no print command produces a number, which means that there is no danger
      // of a plus operator between two print commands doing numeric addition instead of string
      // concatenation. And since a strict template needs to return SanitizedContent, it is ok to
      // get an expression that produces SanitizedContent, which is indeed possible with an
      // escaping directive that produces SanitizedContent. Thus, we do not have to be extra
      // careful when concatenating the expressions in the list.
      bodyStatements.add(
          returnValue(sanitize(CodeChunkUtils.concatChunks(templateBodyChunks), kind)));
    } else {
      // Case 2: Normal case.

      jsCodeBuilder.pushOutputVar("output");
      Statement codeChunk = visitChildrenReturningCodeChunk(node);
      jsCodeBuilder.popOutputVar();
      bodyStatements.add(Statement.of(codeChunk, returnValue(sanitize(id("output"), kind))));
    }
    return Statement.of(bodyStatements.build());
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

  protected GenJsCodeVisitorAssistantForMsgs getAssistantForMsgs() {
    if (assistantForMsgs == null) {
      assistantForMsgs =
          new GenJsCodeVisitorAssistantForMsgs(
              /* master= */ this,
              jsSrcOptions,
              genCallCodeUtils,
              isComputableAsJsExprsVisitor,
              templateAliases,
              genJsExprsVisitor,
              templateTranslationContext,
              errorReporter);
    }
    return assistantForMsgs;
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    // TODO(b/33382980): This is not ideal and leads to less than optimal code generation.
    // ideally genJsExprsVisitor could be used here, but it doesn't work due to the way we need
    // to handle placeholder generation.
    Expression msgVar = getAssistantForMsgs().generateMsgGroupVariable(node);
    getJsCodeBuilder().addChunkToOutputVar(msgVar);
  }

  @Override
  protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    throw new AssertionError();
  }

  @Override
  protected void visitPrintNode(PrintNode node) {
    jsCodeBuilder.addChunksToOutputVar(genJsExprsVisitor.exec(node));
  }

  /**
   * Example:
   *
   * <pre>
   *   {let $boo: $foo.goo[$moo] /}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   var boo35 = opt_data.foo.goo[opt_data.moo];
   * </pre>
   */
  @Override
  protected void visitLetValueNode(LetValueNode node) {

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    Expression value = translateExpr(node.getExpr());
    jsCodeBuilder.append(VariableDeclaration.builder(generatedVarName).setRhs(value).build());

    // Add a mapping for generating future references to this local var.
    templateTranslationContext
        .soyToJsVariableMappings()
        .put(node.getVarName(), id(generatedVarName));
  }

  /**
   * Example:
   *
   * <pre>
   *   {let $boo}
   *     Hello {$name}
   *   {/let}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   var boo35 = 'Hello ' + opt_data.name;
   * </pre>
   */
  @Override
  protected void visitLetContentNode(LetContentNode node) {
    String generatedVarName = node.getUniqueVarName();
    Expression generatedVar = id(generatedVarName);

    // Generate code to define the local var.
    jsCodeBuilder.pushOutputVar(generatedVarName);

    visitChildren(node);

    jsCodeBuilder.popOutputVar();

    if (node.getContentKind() != SanitizedContentKind.TEXT) {
      // If the let node had a content kind specified, it was autoescaped in the corresponding
      // context. Hence the result of evaluating the let block is wrapped in a SanitizedContent
      // instance of the appropriate kind.

      // The expression for the constructor of SanitizedContent of the appropriate kind (e.g.,
      // "soydata.VERY_UNSAFE.ordainSanitizedHtml"), or null if the node has no 'kind' attribute.
      // Introduce a new variable for this value since it has a different type from the output
      // variable (SanitizedContent vs String) and this will enable optimizations in the jscompiler
      String wrappedVarName = node.getVarName() + "__wrapped" + node.getId();
      jsCodeBuilder.append(
          VariableDeclaration.builder(wrappedVarName)
              .setRhs(
                  sanitizedContentOrdainerFunctionForInternalBlocks(node.getContentKind())
                      .call(generatedVar))
              .build());
      generatedVar = id(wrappedVarName);
    }

    // Add a mapping for generating future references to this local var.
    templateTranslationContext.soyToJsVariableMappings().put(node.getVarName(), generatedVar);
  }

  /**
   * Example:
   *
   * <pre>
   *   {if $boo.foo &gt; 0}
   *     ...
   *   {/if}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   if (opt_data.boo.foo &gt; 0) {
   *     ...
   *   }
   * </pre>
   */
  @Override
  protected void visitIfNode(IfNode node) {

    if (isComputableAsJsExprsVisitor.exec(node)) {
      jsCodeBuilder.addChunksToOutputVar(genJsExprsVisitor.exec(node));
    } else {
      generateNonExpressionIfNode(node);
    }
  }

  /**
   * Generates the JavaScript code for an {if} block that cannot be done as an expression.
   *
   * <p>TODO(user): Instead of interleaving JsCodeBuilders like this, consider refactoring
   * GenJsCodeVisitor to return CodeChunks for each sub-Template level SoyNode.
   */
  protected void generateNonExpressionIfNode(IfNode node) {
    ConditionalBuilder conditional = null;

    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode condNode = (IfCondNode) child;

        // Convert predicate.
        Expression predicate = translateExpr(condNode.getExpr());
        // Convert body.
        Statement consequent = visitChildrenReturningCodeChunk(condNode);
        // Add if-block to conditional.
        if (conditional == null) {
          conditional = ifStatement(predicate, consequent);
        } else {
          conditional.addElseIf(predicate, consequent);
        }

      } else if (child instanceof IfElseNode) {
        // Convert body.
        Statement trailingElse = visitChildrenReturningCodeChunk((IfElseNode) child);
        // Add else-block to conditional.
        conditional.setElse(trailingElse);
      } else {
        throw new AssertionError();
      }
    }

    jsCodeBuilder.append(conditional.build());
  }

  /**
   * Example:
   *
   * <pre>
   *   {switch $boo}
   *     {case 0}
   *       ...
   *     {case 1, 2}
   *       ...
   *     {default}
   *       ...
   *   {/switch}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   switch (opt_data.boo) {
   *     case 0:
   *       ...
   *       break;
   *     case 1:
   *     case 2:
   *       ...
   *       break;
   *     default:
   *       ...
   *   }
   * </pre>
   */
  @Override
  protected void visitSwitchNode(SwitchNode node) {

    Expression switchOn = coerceTypeForSwitchComparison(node.getExpr());
    SwitchBuilder switchBuilder = switchValue(switchOn);
    for (SoyNode child : node.getChildren()) {
      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;
        ImmutableList.Builder<Expression> caseChunks = ImmutableList.builder();
        for (ExprNode caseExpr : scn.getExprList()) {
          Expression caseChunk = translateExpr(caseExpr);
          caseChunks.add(caseChunk);
        }
        Statement body = visitChildrenReturningCodeChunk(scn);
        switchBuilder.addCase(caseChunks.build(), body);
      } else if (child instanceof SwitchDefaultNode) {
        Statement body = visitChildrenReturningCodeChunk((SwitchDefaultNode) child);
        switchBuilder.setDefault(body);
      } else {
        throw new AssertionError();
      }
    }
    jsCodeBuilder.append(switchBuilder.build());
  }

  // js switch statements use === for comparing the switch expr to the cases.  In order to preserve
  // soy equality semantics for sanitized content objects we need to coerce cases and switch exprs
  // to strings.
  private Expression coerceTypeForSwitchComparison(ExprRootNode expr) {
    Expression switchOn = translateExpr(expr);
    SoyType type = expr.getType();
    // If the type is possibly a sanitized content type then we need to toString it.
    if (SoyTypes.makeNullable(StringType.getInstance()).isAssignableFrom(type)
        || type.equals(AnyType.getInstance())
        || type.equals(UnknownType.getInstance())) {
      CodeChunk.Generator codeGenerator = templateTranslationContext.codeGenerator();
      Expression tmp = codeGenerator.declarationBuilder().setRhs(switchOn).build().ref();
      return Expression.ifExpression(GOOG_IS_OBJECT.call(tmp), tmp.dotAccess("toString").call())
          .setElse(tmp)
          .build(codeGenerator);
    }
    // For everything else just pass through.  switching on objects/collections is unlikely to
    // have reasonably defined behavior.
    return switchOn;
  }

  protected TranslateExprNodeVisitor getExprTranslator() {
    return new TranslateExprNodeVisitor(
        javaScriptValueFactory, templateTranslationContext, errorReporter);
  }

  protected Expression translateExpr(ExprNode expr) {
    return getExprTranslator().exec(expr);
  }

  private Expression genCodeForParamAccess(String paramName, TemplateParam param) {
    return getExprTranslator().genCodeForParamAccess(paramName, param);
  }

  /**
   * Example:
   *
   * <pre>
   *   {for $foo in $boo.foos}
   *     ...
   *   {ifempty}
   *     ...
   *   {/for}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   var foo2List = opt_data.boo.foos;
   *   var foo2ListLen = foo2List.length;
   *   if (foo2ListLen > 0) {
   *     ...
   *   } else {
   *     ...
   *   }
   * </pre>
   */
  @Override
  protected void visitForNode(ForNode node) {
    boolean hasIfempty = (node.numChildren() == 2);
    // NOTE: below we call id(varName) on a number of variables instead of using
    // VariableDeclaration.ref(),  this is because the refs() might be referenced on the other side
    // of a call to visitChildrenReturningCodeChunk.
    // That will break the normal behavior of FormattingContext being able to tell whether or not an
    // initial statement has already been generated because it eagerly coerces the value to a
    // string.  This will lead to redundant variable declarations.
    // When visitChildrenReturningCodeChunk is gone, this can be cleaned up, but for now we have to
    // manually decide where to declare the variables.
    List<Statement> statements = new ArrayList<>();
    // Build some local variable names.
    ForNonemptyNode nonEmptyNode = (ForNonemptyNode) node.getChild(0);
    String varPrefix = nonEmptyNode.getVarName() + node.getId();

    // TODO(b/32224284): A more consistent pattern for local variable management.
    String limitName = varPrefix + "ListLen";
    Expression limitInitializer;
    Optional<RangeArgs> args = RangeArgs.createFromNode(node);
    Function<Expression, Expression> getDataItemFunction;
    if (args.isPresent()) {
      RangeArgs range = args.get();
      // if any of the expressions are too expensive, allocate local variables for them
      final Expression start =
          maybeStashInLocal(
              range.start().isPresent() ? translateExpr(range.start().get()) : Expression.number(0),
              varPrefix + "_RangeStart",
              statements);
      final Expression end =
          maybeStashInLocal(translateExpr(range.limit()), varPrefix + "_RangeEnd", statements);
      final Expression step =
          maybeStashInLocal(
              range.increment().isPresent()
                  ? translateExpr(range.increment().get())
                  : Expression.number(1),
              varPrefix + "_RangeStep",
              statements);
      // the logic we want is
      // step * (end-start) < 0 ? 0 : ( (end-start)/step + ((end-start) % step == 0 ? 0 : 1));
      // but given that all javascript numbers are doubles we can simplify this somewhat.
      // Math.max(0, Match.ceil((end - start)/step))
      // should yield identical results.
      limitInitializer =
          dottedIdNoRequire("Math.max")
              .call(
                  number(0), dottedIdNoRequire("Math.ceil").call(end.minus(start).divideBy(step)));
      // optimize for foreach over a range
      getDataItemFunction = index -> start.plus(index.times(step));
    } else {
      // Define list var and list-len var.
      Expression dataRef = translateExpr(node.getExpr());
      final String listVarName = varPrefix + "List";
      Expression listVar = VariableDeclaration.builder(listVarName).setRhs(dataRef).build().ref();
      // does it make sense to store this in a variable?
      limitInitializer = listVar.dotAccess("length");
      getDataItemFunction = index -> id(listVarName).bracketAccess(index);
    }

    // Generate the foreach body as a CodeChunk.
    Expression limit = id(limitName);
    statements.add(VariableDeclaration.builder(limitName).setRhs(limitInitializer).build());
    Statement foreachBody = handleForeachLoop(nonEmptyNode, limit, getDataItemFunction);

    if (hasIfempty) {
      // If there is an ifempty node, wrap the foreach body in an if statement and append the
      // ifempty body as the else clause.
      Statement ifemptyBody = visitChildrenReturningCodeChunk(node.getChild(1));
      Expression limitCheck = limit.op(Operator.GREATER_THAN, number(0));

      foreachBody = ifStatement(limitCheck, foreachBody).setElse(ifemptyBody).build();
    }
    statements.add(foreachBody);
    jsCodeBuilder.append(Statement.of(statements));
  }

  private Expression maybeStashInLocal(
      Expression expr, String varName, List<Statement> statements) {
    if (expr.isCheap()) {
      return expr;
    }
    statements.add(VariableDeclaration.builder(varName).setRhs(expr).build());
    return id(varName);
  }

  /**
   * Example:
   *
   * <pre>
   *   {for $foo in $boo.foos}
   *     ...
   *   {/for}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   for (var foo2Index = 0; foo2Index &lt; foo2ListLen; foo2Index++) {
   *     var foo2Data = foo2List[foo2Index];
   *     ...
   *   }
   * </pre>
   */
  private Statement handleForeachLoop(
      ForNonemptyNode node,
      Expression limit,
      Function<Expression, Expression> getDataItemFunction) {
    // Build some local variable names.
    String varName = node.getVarName();
    String varPrefix = varName + node.getForNodeId();

    // TODO(b/32224284): A more consistent pattern for local variable management.
    String loopIndexName = varPrefix + "Index";
    String dataName = varPrefix + "Data";

    Expression loopIndex = id(loopIndexName);
    VariableDeclaration data =
        VariableDeclaration.builder(dataName).setRhs(getDataItemFunction.apply(loopIndex)).build();

    // Populate the local var translations with the translations from this node.
    templateTranslationContext
        .soyToJsVariableMappings()
        .put(varName, id(dataName))
        .put(varName + "__isFirst", loopIndex.doubleEquals(number(0)))
        .put(varName + "__isLast", loopIndex.doubleEquals(limit.minus(number(1))))
        .put(varName + "__index", loopIndex);

    // Generate the loop body.
    Statement foreachBody = Statement.of(data, visitChildrenReturningCodeChunk(node));

    // Create the entire for block.
    return forLoop(loopIndexName, limit, foreachBody);
  }

  @Override
  protected void visitForNonemptyNode(ForNonemptyNode node) {
    // should be handled by handleForeachLoop
    throw new UnsupportedOperationException();
  }

  /**
   * Example:
   *
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo: 88 /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}
   *       Hello {$name}
   *     {/param}
   *   {/call}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   output += some.func(opt_data);
   *   output += some.func(opt_data.boo.foo);
   *   output += some.func({goo: 88});
   *   output += some.func(soy.$$assignDefaults({goo: 'Hello ' + opt_data.name}, opt_data.boo);
   * </pre>
   */
  @Override
  protected void visitCallNode(CallNode node) {

    // If this node has any CallParamContentNode children those contents are not computable as JS
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJsExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    // Add the call's result to the current output var.
    Expression call =
        genCallCodeUtils.gen(
            node, templateAliases, templateTranslationContext, errorReporter, getExprTranslator());
    jsCodeBuilder.addChunkToOutputVar(call);
  }

  @Override
  protected void visitCallParamContentNode(CallParamContentNode node) {

    // This node should only be visited when it's not computable as JS expressions, because this
    // method just generates the code to define the temporary 'param<n>' variable.
    if (isComputableAsJsExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'param<n>' when not computable as JS expressions.");
    }

    jsCodeBuilder.pushOutputVar("param" + node.getId());

    visitChildren(node);

    jsCodeBuilder.popOutputVar();
  }

  /**
   * Example:
   *
   * <pre>
   *   {log}Blah {$boo}.{/log}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   window.console.log('Blah ' + opt_data.boo + '.');
   * </pre>
   *
   * <p>If the log msg is not computable as JS exprs, then it will be built in a local var
   * logMsg_s##, e.g.
   *
   * <pre>
   *   var logMsg_s14 = ...
   *   window.console.log(logMsg_s14);
   * </pre>
   */
  @Override
  protected void visitLogNode(LogNode node) {

    if (isComputableAsJsExprsVisitor.execOnChildren(node)) {
      List<Expression> logMsgChunks = genJsExprsVisitor.execOnChildren(node);

      jsCodeBuilder.append(WINDOW_CONSOLE_LOG.call(CodeChunkUtils.concatChunks(logMsgChunks)));
    } else {
      // Must build log msg in a local var logMsg_s##.
      String outputVarName = "logMsg_s" + node.getId();
      jsCodeBuilder.pushOutputVar(outputVarName);

      visitChildren(node);

      jsCodeBuilder.popOutputVar();

      jsCodeBuilder.append(WINDOW_CONSOLE_LOG.call(id(outputVarName)));
    }
  }

  @Override
  protected void visitKeyNode(KeyNode node) {
    // Do nothing. Outside of incremental dom, key nodes are a no-op.
  }

  /**
   * Example:
   *
   * <pre>
   *   {debugger}
   * </pre>
   *
   * generates
   *
   * <pre>
   *   debugger;
   * </pre>
   */
  @Override
  protected void visitDebuggerNode(DebuggerNode node) {
    jsCodeBuilder.appendLine("debugger;");
  }

  @Override
  protected void visitVeLogNode(VeLogNode node) {
    // no need to do anything, the VeLogInstrumentationVisitor has already handled these.
    if (!node.needsSyntheticVelogNode()) {
      visitChildren(node);
      return;
    }
    // Create synthetic velog nodes. These will be removed in JS.
    FunctionNode funcNode =
        new FunctionNode(
            Identifier.create(VeLogFunction.NAME, node.getSourceLocation()),
            VeLogFunction.INSTANCE,
            node.getSourceLocation());
    funcNode.addChild(node.getVeDataExpression().copy(new CopyState()));
    if (node.getLogonlyExpression() != null) {
      funcNode.addChild(node.getLogonlyExpression().copy(new CopyState()));
    }
    jsCodeBuilder.addChunksToOutputVar(
        ImmutableList.of(
            Expression.stringLiteral("<velog"),
            getExprTranslator().exec(funcNode),
            Expression.stringLiteral(">")));
    visitChildren(node);
    jsCodeBuilder.addChunksToOutputVar(ImmutableList.of(Expression.stringLiteral("</velog>")));
  }

  @Override
  protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    // PlaceholderNodes just wrap other nodes with placeholder metadata which is processed by the
    // GenJsCodeVisitorAssistentForMsgs
    visitChildren(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Simply generate JS expressions for this node and add them to the current output var.
      jsCodeBuilder.addChunksToOutputVar(genJsExprsVisitor.exec(node));

    } else {
      // Need to implement visit*Node() for the specific case.
      throw new UnsupportedOperationException("implement visit*Node for" + node.getKind());
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers

  /** Generate the JSDoc for the opt_data parameter. */
  private String genParamsRecordType(TemplateNode node) {
    Set<String> paramNames = new HashSet<>();

    // Generate members for explicit params.
    Map<String, String> record = new LinkedHashMap<>();
    for (TemplateParam param : node.getParams()) {
      JsType jsType = getJsTypeForParamForDeclaration(param.type());
      record.put(
          param.name(), jsType.typeExprForRecordMember(/* isOptional= */ !param.isRequired()));
      for (GoogRequire require : jsType.getGoogRequires()) {
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
        new IndirectParamsCalculator(templateRegistry)
            .calculateIndirectParams(templateRegistry.getMetadata(node));
    // If there are any calls outside of the file set, then we can't know
    // the complete types of any indirect params. In such a case, we can simply
    // omit the indirect params from the function type signature, since record
    // types in JS allow additional undeclared fields to be present.
    if (!ipi.mayHaveIndirectParamsInExternalCalls && !ipi.mayHaveIndirectParamsInExternalDelCalls) {
      for (String indirectParamName : ipi.indirectParamTypes.keySet()) {
        if (paramNames.contains(indirectParamName)) {
          continue;
        }
        Collection<SoyType> paramTypes = ipi.indirectParamTypes.get(indirectParamName);
        SoyType combinedType = SoyTypes.computeLowestCommonType(typeRegistry, paramTypes);
        // Note that Union folds duplicate types and flattens unions, so if
        // the combinedType is already a union this will do the right thing.
        // TODO: detect cases where nullable is not needed (requires flow
        // analysis to determine if the template is always called.)
        SoyType indirectParamType =
            typeRegistry.getOrCreateUnionType(combinedType, NullType.getInstance());
        JsType jsType = getJsTypeForParamForDeclaration(indirectParamType);
        // NOTE: we do not add goog.requires for indirect types.  This is because it might introduce
        // strict deps errors.  This should be fine though since the transitive soy template that
        // actually has the param will add them.
        record.put(indirectParamName, jsType.typeExprForRecordMember(/* isOptional= */ true));
      }
    }
    StringBuilder sb = new StringBuilder();
    sb.append("{\n *  ");
    Joiner.on(",\n *  ").withKeyValueSeparator(": ").appendTo(sb, record);
    // trailing comma in record is important in case the last record member is the
    // unknown type
    sb.append(",\n * }");
    return sb.toString();
  }

  protected final Statement genParamDefault(
      TemplateParam param,
      Expression paramTempVar,
      String alias,
      JsType defaultType,
      boolean declareStatic) {
    checkArgument(param.hasDefault());

    Statement defaultValueAssignment;
    Expression defaultValue = translateExpr(param.defaultValue());
    if (defaultValue.isCheap()) {
      defaultValueAssignment = Statement.assign(paramTempVar, defaultValue);
    } else {
      Statement staticVar;
      Expression staticVarRef;
      JsDoc jsDoc =
          JsDoc.builder()
              .addParameterizedAnnotation(
                  "private", defaultType.typeExprForRecordMember(/* isOptional= */ true))
              .build();
      if (jsSrcOptions.shouldGenerateGoogModules()) {
        String varName = String.format("%s$defaultValue$%s", alias, param.name());
        staticVar = VariableDeclaration.builder(varName).setJsDoc(jsDoc).build();
        staticVarRef = id(varName);
      } else {
        staticVarRef =
            dottedIdNoRequire(alias).dotAccess(String.format("defaultValue$%s_", param.name()));
        staticVar = staticVarRef.asStatement(jsDoc);
      }
      if (declareStatic) {
        staticVarDeclarations.add(staticVar);
        defaultValueAssignment =
            Statement.of(
                Statement.assign(paramTempVar, staticVarRef),
                Statement.ifStatement(
                        Expression.not(JsRuntime.GOOG_IS_DEF.call(paramTempVar)),
                        Statement.assign(paramTempVar, staticVarRef.assign(defaultValue)))
                    .build());
      } else {
        defaultValueAssignment =
            Statement.assign(
                paramTempVar,
                JsRuntime.GOOG_ASSERTS_ASSERT.call(
                    staticVarRef,
                    stringLiteral("cached default value will be initialized during render")));
      }
    }
    return Statement.ifStatement(
            Expression.not(JsRuntime.GOOG_IS_DEF.call(paramTempVar)), defaultValueAssignment)
        .build();
  }

  /**
   * Generate code to verify the runtime types of the input params. Also typecasts the input
   * parameters and assigns them to local variables for use in the template.
   *
   * @param node the template node.
   */
  @CheckReturnValue
  protected Statement genParamTypeChecks(TemplateNode node, String alias) {
    ImmutableList.Builder<Statement> declarations = ImmutableList.builder();
    for (TemplateParam param : node.getAllParams()) {
      String paramName = param.name();
      SoyType paramType = param.type();
      CodeChunk.Generator generator = templateTranslationContext.codeGenerator();
      Expression paramChunk = genCodeForParamAccess(paramName, param);
      JsType jsType = getJsTypeForParamTypeCheck(paramType);
      // The opt_param.name value that will be type-tested.
      String paramAlias = genParamAlias(paramName);
      Expression coerced =
          jsType.getValueCoercion(paramChunk, generator, /* hasDefault=*/ param.hasDefault());
      if (coerced != null) {
        // since we have coercion logic, dump into a temporary
        paramChunk = generator.declarationBuilder().setRhs(coerced).build().ref();
      }
      if (param.hasDefault()) {
        if (coerced == null) {
          paramChunk = generator.declarationBuilder().setRhs(paramChunk).build().ref();
        }
        declarations.add(
            genParamDefault(
                param,
                paramChunk,
                alias,
                getJsTypeForParamForDeclaration(paramType),
                /* declareStatic= */ true));
      }
      // The param value to assign
      Expression value;
      Optional<Expression> soyTypeAssertion =
          jsType.getSoyTypeAssertion(paramChunk, paramName, generator);
      // The type-cast expression.
      if (soyTypeAssertion.isPresent()) {
        value = soyTypeAssertion.get();
      } else {
        value = paramChunk;
      }

      VariableDeclaration.Builder declarationBuilder =
          VariableDeclaration.builder(paramAlias)
              .setRhs(value)
              .setGoogRequires(jsType.getGoogRequires());
      declarationBuilder.setJsDoc(
          JsDoc.builder()
              .addParameterizedAnnotation(
                  "type", getJsTypeForParamForDeclaration(paramType).typeExpr())
              .build());
      VariableDeclaration declaration = declarationBuilder.build();
      declarations.add(declaration);

      templateTranslationContext
          .soyToJsVariableMappings()
          // TODO(lukes): this should really be declartion.ref() but we cannot do that until
          // everything is on the code chunk api.
          .put(paramName, id(paramAlias));
    }
    return Statement.of(declarations.build());
  }

  /** Gets the type to use for a parameter in record type declarations. */
  protected JsType getJsTypeForParamForDeclaration(SoyType paramType) {
    return JsType.forJsSrc(paramType);
  }

  /** Gets the type to use for a parameter in runtime assertions. */
  protected JsType getJsTypeForParamTypeCheck(SoyType paramType) {
    return getJsTypeForParamForDeclaration(paramType);
  }

  /**
   * Generate a name for the local variable which will store the value of a parameter, avoiding
   * collision with JavaScript reserved words.
   */
  private String genParamAlias(String paramName) {
    return JsSrcUtils.isReservedWord(paramName) ? "param$" + paramName : paramName;
  }
}
