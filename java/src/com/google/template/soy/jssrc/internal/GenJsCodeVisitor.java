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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.jssrc.dsl.CodeChunk.declare;
import static com.google.template.soy.jssrc.dsl.CodeChunk.dottedId;
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;
import static com.google.template.soy.jssrc.dsl.CodeChunk.number;
import static com.google.template.soy.jssrc.dsl.CodeChunk.return_;
import static com.google.template.soy.jssrc.dsl.CodeChunk.stringLiteral;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.html.AbstractHtmlSoyNodeVisitor;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.ConditionalBuilder;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.parsepasses.contextautoesc.ContentSecurityPolicyPass;
import com.google.template.soy.passes.FindIndirectParamsVisitor;
import com.google.template.soy.passes.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.passes.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.shared.internal.FindCalleesNotInFileVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNode.RangeArgs;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.XidNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeOps;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;
import com.google.template.soy.types.proto.Protos;
import com.google.template.soy.types.proto.SoyProtoType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Visitor for generating full JS code (i.e. statements) for parse tree nodes.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * <p> {@link #gen} should be called on a full parse tree. JS source code will be generated for
 * all the Soy files. The return value is a list of strings, each string being the content of one
 * generated JS file (corresponding to one Soy file).
 *
 */
public class GenJsCodeVisitor extends AbstractHtmlSoyNodeVisitor<List<String>> {

  private static final SoyErrorKind NON_NAMESPACED_TEMPLATE =
      SoyErrorKind.of(
          "Using the option to provide/require Soy namespaces, but called template "
              + "does not reside in a namespace.");

  /** Regex pattern to look for dots in a template name. */
  private static final Pattern DOT = Pattern.compile("\\.");

  /** Regex pattern for an integer. */
  private static final Pattern INTEGER = Pattern.compile("-?\\d+");

  /** Namespace to goog.require when useGoogIsRtlForBidiGlobalDir is in force. */
  private static final String GOOG_IS_RTL_NAMESPACE = "goog.i18n.bidi";

  /** Namespace to goog.require when a plural/select message is encountered. */
  private static final String GOOG_MESSAGE_FORMAT_NAMESPACE = "goog.i18n.MessageFormat";


  /** The options for generating JS source code. */
  protected final SoyJsSrcOptions jsSrcOptions;

  /** Instance of JsExprTranslator to use. */
  protected final JsExprTranslator jsExprTranslator;

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

  /** The GenDirectivePluginRequiresVisitor for the current template. */
  private GenDirectivePluginRequiresVisitor genDirectivePluginRequiresVisitor;

  protected TemplateRegistry templateRegistry;

  /** Type operators. */
  private final SoyTypeOps typeOps;

  protected ErrorReporter errorReporter;
  protected TranslationContext templateTranslationContext;

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name. This is created on a per {@link SoyFileNode} basis.
   */
  @VisibleForTesting protected TemplateAliases templateAliases;

  @Inject
  protected GenJsCodeVisitor(
      SoyJsSrcOptions jsSrcOptions,
      JsExprTranslator jsExprTranslator,
      DelTemplateNamer delTemplateNamer,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory,
      GenDirectivePluginRequiresVisitor genDirectivePluginRequiresVisitor,
      SoyTypeOps typeOps) {
    this.jsSrcOptions = jsSrcOptions;
    this.jsExprTranslator = jsExprTranslator;
    this.delTemplateNamer = delTemplateNamer;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.canInitOutputVarVisitor = canInitOutputVarVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;
    this.genDirectivePluginRequiresVisitor = genDirectivePluginRequiresVisitor;
    this.typeOps = typeOps;
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

  /** TODO: tests should use {@link #gen} instead. */
  @VisibleForTesting
  void visitForTesting(
      SoyNode node, ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    visit(node);
  }


  @Override protected void visitChildren(ParentSoyNode<?> node) {

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

    List<CodeChunk.WithValue> consecChunks = new ArrayList<>();

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

  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      visit(soyFile);
    }
  }

  /**
   * @return A new CodeBuilder to create the contents of a file with.
   */
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
   * Example:
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
  @Override protected void visitSoyFileNode(SoyFileNode node) {

    if (node.getSoyFileKind() != SoyFileKind.SRC) {
      return;  // don't generate code for deps
    }

    StringBuilder file = new StringBuilder();

    file.append("// This file was automatically generated from ")
        .append(node.getFileName())
        .append(".\n");
    file.append("// Please don't edit this file by hand.\n");

    // Output a section containing optionally-parsed compiler directives in comments. Since these
    // are comments, they are not controlled by an option, and will be removed by minifiers that do
    // not understand them.
    file.append("\n");
    file.append("/**\n");
    String fileOverviewDescription = node.getNamespace() == null
        ? ""
        : " Templates in namespace " + node.getNamespace() + ".";
    file.append(" * @fileoverview").append(fileOverviewDescription).append('\n');
    file.append(" * @suppress {missingRequire}\n");
    if (node.getDelPackageName() != null) {
      file.append(" * @modName {").append(node.getDelPackageName()).append("}\n");
    }
    addJsDocToProvideDelTemplates(file, node);
    addJsDocToRequireDelTemplates(file, node);
    addCodeToRequireCss(file, node);
    file.append(" * @public\n").append(" */\n\n");

    // Add code to define JS namespaces or add provide/require calls for Closure Library.
    templateAliases = AliasUtils.IDENTITY_ALIASES;
    jsCodeBuilder = createCodeBuilder();

    if (jsSrcOptions.shouldGenerateGoogModules()) {
      templateAliases = AliasUtils.createTemplateAliases(node);

      addCodeToDeclareGoogModule(file, node);
      addCodeToRequireGeneralDeps(node);
      addCodeToRequireGoogModules(node);
    } else if (jsSrcOptions.shouldProvideRequireSoyNamespaces()) {
      addCodeToProvideSoyNamespace(file, node);
      if (jsSrcOptions.shouldProvideBothSoyNamespacesAndJsFunctions()) {
        addCodeToProvideJsFunctions(file, node);
      }
      file.append('\n');
      addCodeToRequireGeneralDeps(node);
      addCodeToRequireSoyNamespaces(node);
    } else if (jsSrcOptions.shouldProvideRequireJsFunctions()) {
      if (jsSrcOptions.shouldProvideBothSoyNamespacesAndJsFunctions()) {
        addCodeToProvideSoyNamespace(file, node);
      }
      addCodeToProvideJsFunctions(file, node);
      file.append('\n');
      addCodeToRequireGeneralDeps(node);
      addCodeToRequireJsFunctions(node);
    } else {
      addCodeToDefineJsNamespaces(file, node);
    }

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      jsCodeBuilder.appendLine().appendLine();
      visit(template);
    }
    jsCodeBuilder.appendGoogRequires(file);
    jsCodeBuilder.appendCode(file);
    jsFilesContents.add(file.toString());
    jsCodeBuilder = null;
  }

  /**
   * Appends requirecss jsdoc tags in the file header section.
   *
   * @param soyFile The file with the templates..
   */
  private static void addCodeToRequireCss(StringBuilder header, SoyFileNode soyFile) {

    SortedSet<String> requiredCssNamespaces = new TreeSet<>();
    requiredCssNamespaces.addAll(soyFile.getRequiredCssNamespaces());
    for (TemplateNode template : soyFile.getChildren()) {
      requiredCssNamespaces.addAll(template.getRequiredCssNamespaces());
    }

    // NOTE: CSS requires in JS can only be done on a file by file basis at this time.  Perhaps in
    // the future, this might be supported per function.
    for (String requiredCssNamespace : requiredCssNamespaces) {
      header.append(" * @requirecss {").append(requiredCssNamespace).append("}\n");
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to define JS namespaces.
   *
   * @param header
   * @param soyFile The node we're visiting.
   */
  private void addCodeToDefineJsNamespaces(StringBuilder header, SoyFileNode soyFile) {

    SortedSet<String> jsNamespaces = new TreeSet<>();
    for (TemplateNode template : soyFile.getChildren()) {
      String templateName = template.getTemplateName();
      Matcher dotMatcher = DOT.matcher(templateName);
      while (dotMatcher.find()) {
        jsNamespaces.add(templateName.substring(0, dotMatcher.start()));
      }
    }

    for (String jsNamespace : jsNamespaces) {
      boolean hasDot = jsNamespace.indexOf('.') >= 0;
      // If this is a top level namespace and the option to declare top level
      // namespaces is turned off, skip declaring it.
      if (jsSrcOptions.shouldDeclareTopLevelNamespaces() || hasDot) {
        header
            .append("if (typeof ")
            .append(jsNamespace)
            .append(" == 'undefined') { ")
            .append(hasDot ? "" : "var ")
            .append(jsNamespace)
            .append(" = {}; }\n");
      }
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to provide Soy namespaces.
   *
   * @param header
   * @param soyFile The node we're visiting.
   */
  private static void addCodeToProvideSoyNamespace(StringBuilder header, SoyFileNode soyFile) {
    if (soyFile.getNamespace() != null) {
      header.append("goog.provide('").append(soyFile.getNamespace()).append("');\n");
    }
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
      jsCodeBuilder.appendLine("var ", namespaceAlias, " = goog.require('", importNamespace, "');");

      // Alias all the templates used from the module
      for (String fullyQualifiedName : namespaceToTemplates.get(namespace)) {
        String alias = templateAliases.get(fullyQualifiedName);
        String shortName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.'));
        jsCodeBuilder.appendLine("var ", alias, " = ", namespaceAlias, shortName, ";");
      }
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to provide template JS functions.
   *
   * @param soyFile The node we're visiting.
   */
  private static void addCodeToProvideJsFunctions(StringBuilder header, SoyFileNode soyFile) {

    SortedSet<String> templateNames = new TreeSet<>();
    for (TemplateNode template : soyFile.getChildren()) {
      templateNames.add(template.getTemplateName());
    }
    for (String templateName : templateNames) {
      header.append("goog.provide('").append(templateName).append("');\n");
    }
  }

  private void addJsDocToProvideDelTemplates(StringBuilder header, SoyFileNode soyFile) {

    SortedSet<String> delTemplateNames = new TreeSet<>();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template instanceof TemplateDelegateNode) {
        delTemplateNames.add(delTemplateNamer.getDelegateName((TemplateDelegateNode) template));
      }
    }
    for (String delTemplateName : delTemplateNames) {
      header.append(" * @hassoydeltemplate {").append(delTemplateName).append("}\n");
    }
  }

  private void addJsDocToRequireDelTemplates(StringBuilder header, SoyFileNode soyFile) {

    SortedSet<String> delTemplateNames = new TreeSet<>();
    for (CallDelegateNode delCall :
        SoyTreeUtils.getAllNodesOfType(soyFile, CallDelegateNode.class)) {
      delTemplateNames.add(delTemplateNamer.getDelegateName(delCall));
    }
    for (String delTemplateName : delTemplateNames) {
      header.append(" * @hassoydelcall {").append(delTemplateName).append("}\n");
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require general dependencies.
   * @param soyFile The node we're visiting.
   */
  protected void addCodeToRequireGeneralDeps(SoyFileNode soyFile) {

    // TODO(user): keep track of JS symbols that are actually printed,
    // so no extraRequires are necessary.
    jsCodeBuilder.addGoogRequire("soy", true);
    jsCodeBuilder.addGoogRequire("soydata", true);

    SortedSet<String> requiredObjectTypes = ImmutableSortedSet.of();
    if (hasStrictParams(soyFile)) {
      requiredObjectTypes = getRequiredObjectTypes(soyFile);
      // soy.asserts is definitely used if we have strict params
      jsCodeBuilder.addGoogRequire("soy.asserts", false);
    }

    if (jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir()) {
      // Suppress extraRequire because it may be unused (b/25672094).
      jsCodeBuilder.addGoogRequire(GOOG_IS_RTL_NAMESPACE, true);
    }

    if (SoyTreeUtils.hasNodesOfType(soyFile, MsgPluralNode.class, MsgSelectNode.class)) {
      jsCodeBuilder.addGoogRequire(GOOG_MESSAGE_FORMAT_NAMESPACE, false);
    }

    if (SoyTreeUtils.hasNodesOfType(soyFile, XidNode.class)) {
      jsCodeBuilder.addGoogRequire("xid", false);
    }

    SortedSet<String> pluginRequiredJsLibNames = new TreeSet<>();
    pluginRequiredJsLibNames.addAll(genDirectivePluginRequiresVisitor.exec(soyFile));
    pluginRequiredJsLibNames.addAll(new GenFunctionPluginRequiresVisitor().exec(soyFile));
    for (String namespace : pluginRequiredJsLibNames) {
      jsCodeBuilder.addGoogRequire(namespace, false);
    }

    if (!requiredObjectTypes.isEmpty()) {
      for (String requiredType : requiredObjectTypes) {
        jsCodeBuilder.addGoogRequire(requiredType, false);
      }
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireSoyNamespaces(SoyFileNode soyFile) {

    String prevCalleeNamespace = null;
    Set<String> calleeNamespaces = new TreeSet<>();
    for (CallBasicNode node : new FindCalleesNotInFileVisitor().exec(soyFile)) {
      String calleeNotInFile = node.getCalleeName();
      int lastDotIndex = calleeNotInFile.lastIndexOf('.');
      if (lastDotIndex == -1) {
        errorReporter.report(node.getSourceLocation(), NON_NAMESPACED_TEMPLATE);
        continue;
      }
      calleeNamespaces.add(calleeNotInFile.substring(0, lastDotIndex));
    }

    for (String calleeNamespace : calleeNamespaces) {
      if (calleeNamespace.length() > 0 && !calleeNamespace.equals(prevCalleeNamespace)) {
        jsCodeBuilder.addGoogRequire(calleeNamespace, false);
        prevCalleeNamespace = calleeNamespace;
      }
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require template JS functions.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireJsFunctions(SoyFileNode soyFile) {
    SortedSet<String> requires = new TreeSet<>();
    for (CallBasicNode node : new FindCalleesNotInFileVisitor().exec(soyFile)) {
      requires.add(node.getCalleeName());
    }
    for (String require : requires) {
      jsCodeBuilder.addGoogRequire(require, false);
    }
  }

  /**
   * @param node The template node that is being generated
   * @return The JavaScript type of the content generated by this template.
   */
  protected String getTemplateReturnType(TemplateNode node) {
    // For strict autoescaping templates, the result is actually a typesafe wrapper.
    // We prepend "!" to indicate it is non-nullable.
    return (node.getContentKind() == null)
        ? "string"
        : "!" + NodeContentKinds.toJsSanitizedContentReturnType(node.getContentKind());
  }

  /**
   * Outputs a {@link TemplateNode}, generating the function open and close, along with a a debug
   * template name.
   *
   * <p>If aliasing is not performed (which is always the case for V1 templates), this looks like:
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
  @Override protected void visitTemplateNode(TemplateNode node) {
    boolean useStrongTyping = hasStrictParams(node);

    String templateName = node.getTemplateName();
    String partialName = node.getPartialTemplateName();
    String alias;
    boolean addToExports = jsSrcOptions.shouldGenerateGoogModules();

    if (addToExports && node instanceof TemplateDelegateNode) {
      alias = node.getPartialTemplateName().substring(1);
    } else {
      alias = templateAliases.get(templateName);
    }

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

    String paramsRecordType = null;

    // ------ Generate JS Doc. ------
    if (jsSrcOptions.shouldGenerateJsdoc()) {
      jsCodeBuilder.appendLine("/**");
      jsCodeBuilder.append(" * @param {");
      if (useStrongTyping) {
        // TODO(lukes): use the typedef here
        paramsRecordType = genParamsRecordType(node);
        jsCodeBuilder.append(paramsRecordType);
      } else {
        jsCodeBuilder.append("Object<string, *>=");
      }
      jsCodeBuilder.appendLine("} opt_data");
      jsCodeBuilder.appendLine(" * @param {(null|undefined)=} opt_ignored");
      jsCodeBuilder.appendLine(" * @param {Object<string, *>=} opt_ijData");
      String returnType = getTemplateReturnType(node);
      jsCodeBuilder.appendLine(" * @return {", returnType, "}");
      String suppressions = "checkTypes";
      jsCodeBuilder.appendLine(" * @suppress {" + suppressions + "}");
      if (node.getVisibility() == Visibility.PRIVATE) {
        jsCodeBuilder.appendLine(" * @private");
      }
      jsCodeBuilder.appendLine(" */");
    }

    // ------ Generate function definition up to opening brace. ------
    if (addToExports) {
      jsCodeBuilder.appendLine("function ", alias, "(opt_data, opt_ignored, opt_ijData) {");
    } else {
      jsCodeBuilder.appendLine(alias, " = function(opt_data, opt_ignored, opt_ijData) {");
    }
    jsCodeBuilder.increaseIndent();
    // If there are any null coalescing operators or switch nodes then we need to generate an
    // additional temporary variable.
    if (!SoyTreeUtils.getAllNodesOfType(node, NullCoalescingOpNode.class).isEmpty()
        || !SoyTreeUtils.getAllNodesOfType(node, SwitchNode.class).isEmpty()) {
      jsCodeBuilder.appendLine("var $$temp;");
    }

    // Generate statement to ensure data is defined, if necessary.
    if (new ShouldEnsureDataIsDefinedVisitor().exec(node)) {
      jsCodeBuilder.appendLine("opt_data = opt_data || {};");
    }
    if (shouldEnsureIjDataIsDefined(node)) {
      jsCodeBuilder.appendLine("opt_ijData = opt_ijData || {};");
    }

    // ------ Generate function body. ------
    generateFunctionBody(node);

    // ------ Generate function closing brace and add to exports if necessary. ------
    jsCodeBuilder.decreaseIndent();
    if (addToExports) {
      jsCodeBuilder.appendLine("}");
      jsCodeBuilder.appendLine("exports.", partialName.substring(1), " = ", alias, ";");
    } else {
      jsCodeBuilder.appendLine("};");
    }

    // ------ Add the @typedef of opt_data. ------
    if (paramsRecordType != null) {
      jsCodeBuilder.appendLine("/**");
      jsCodeBuilder.appendLine(" * @typedef {", paramsRecordType, "}");
      jsCodeBuilder.appendLine(" */");
      jsCodeBuilder.appendLine(alias + ".Params;");
    }

    // ------ Add the fully qualified template name to the function to use in debug code. ------
    jsCodeBuilder.appendLine("if (goog.DEBUG) {");
    jsCodeBuilder.increaseIndent();
    jsCodeBuilder.appendLine(alias + ".soyTemplateName = '" + templateName + "';");
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("}");

    // ------ If delegate template, generate a statement to register it. ------
    if (node instanceof TemplateDelegateNode) {
      TemplateDelegateNode nodeAsDelTemplate = (TemplateDelegateNode) node;
      String delTemplateIdExprText =
          "soy.$$getDelTemplateId('" + delTemplateNamer.getDelegateName(nodeAsDelTemplate) + "')";
      String delTemplateVariantExprText = "'" + nodeAsDelTemplate.getDelTemplateVariant() + "'";
      jsCodeBuilder.appendLine(
          "soy.$$registerDelegateFn(",
          delTemplateIdExprText, ", ", delTemplateVariantExprText, ", ",
          nodeAsDelTemplate.getDelPriority().toString(), ", ",
          alias, ");");
    }
  }

  /**
   * Returns true if the given template should ensure that the {@code opt_ijData} param is defined.
   *
   * <p>The current logic exists for CSP support which is enabled by default.  CSP support works by
   * generating references to an {@code $ij} param called {@code csp_nonce}, so to ensure that
   * templates are compatible we only need to ensure the opt_ijData param is available is if the
   * template references {@code $ij.csp_nonce}.
   */
  private static boolean shouldEnsureIjDataIsDefined(TemplateNode node) {
    for (VarRefNode ref : SoyTreeUtils.getAllNodesOfType(node, VarRefNode.class)) {
      if (ref.isDollarSignIjParameter()) {
        if (ref.getName().equals(ContentSecurityPolicyPass.CSP_NONCE_VARIABLE_NAME)) {
          return true;
        }
      } else if (ref.getDefnDecl().isInjected() && ref.getDefnDecl().kind() == VarDefn.Kind.PARAM) {
        // if it is an {@inject } param then we will generate unconditional type assertions that
        // dereference opt_ijData.  So there is no need to ensure it is defined.
        return false;
      }
    }
    return false;
  }

  /**
   * Generates the function body.
   */
  protected void generateFunctionBody(TemplateNode node) {
    // Type check parameters.
    genParamTypeChecks(node);

    CodeChunk.WithValue templateBody;
    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as JS
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the JS expressions and return the result.

      List<CodeChunk.WithValue> templateBodyChunks = genJsExprsVisitor.exec(node);
      if (node.getContentKind() == null) {
        // The template is not strict. Thus, it may not apply an escaping directive to *every* print
        // command, which means that some of its print commands could produce a number. Thus, there
        // is a danger that a plus operator between two expressions in the list will do numeric
        // addition instead of string concatenation. Furthermore, a non-strict template always needs
        // to return a string, but if there is just one expression in the list, and we return it as
        // is, we may not always produce a string (since an escaping directive may not be getting
        // applied in that expression at all, or a directive might be getting applied that produces
        // SanitizedContent). We thus call a method that makes sure to return an expression that
        // produces a string and is in no danger of using numeric addition when concatenating the
        // expressions in the list.
        templateBody = CodeChunkUtils.concatChunksForceString(templateBodyChunks);
      } else {
        // The template is strict. Thus, it applies an escaping directive to *every* print command,
        // which means that no print command produces a number, which means that there is no danger
        // of a plus operator between two print commands doing numeric addition instead of string
        // concatenation. And since a strict template needs to return SanitizedContent, it is ok to
        // get an expression that produces SanitizedContent, which is indeed possible with an
        // escaping directive that produces SanitizedContent. Thus, we do not have to be extra
        // careful when concatenating the expressions in the list.
        templateBody = CodeChunkUtils.concatChunks(templateBodyChunks);
      }
    } else {
      // Case 2: Normal case.

      jsCodeBuilder.pushOutputVar("output");
      visitChildren(node);
      templateBody = id("output");
      jsCodeBuilder.popOutputVar();
    }

    if (node.getContentKind() != null) {
      // Templates with autoescape="strict" return the SanitizedContent wrapper for its kind:
      // - Call sites are wrapped in an escaper. Returning SanitizedContent prevents re-escaping.
      // - The topmost call into Soy returns a SanitizedContent. This will make it easy to take
      //   the result of one template and feed it to another, and also to confidently assign
      //   sanitized HTML content to innerHTML. This does not use the internal-blocks variant,
      //   and so will wrap empty strings.
      templateBody = CodeChunkUtils.wrapAsSanitizedContent(
          node.getContentKind(),
          templateBody,
          false /* not an internal block */);
    }
    jsCodeBuilder.append(return_(templateBody));
  }

  protected GenJsCodeVisitorAssistantForMsgs getAssistantForMsgs() {
    if (assistantForMsgs == null) {
      assistantForMsgs =
          new GenJsCodeVisitorAssistantForMsgs(
              this /* master */,
              jsSrcOptions,
              jsExprTranslator,
              genCallCodeUtils,
              isComputableAsJsExprsVisitor,
              templateAliases,
              genJsExprsVisitor,
              templateTranslationContext,
              errorReporter);
    }
    return assistantForMsgs;
  }

  @Override protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    throw new AssertionError("Inconceivable! LetContentNode should catch this directly.");
  }

  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    throw new AssertionError();
  }

  @Override protected void visitPrintNode(PrintNode node) {
    jsCodeBuilder.addChunksToOutputVar(genJsExprsVisitor.exec(node));
  }

  /**
   * Example:
   * <pre>
   *   {let $boo: $foo.goo[$moo] /}
   * </pre>
   * might generate
   * <pre>
   *   var boo35 = opt_data.foo.goo[opt_data.moo];
   * </pre>
   */
  @Override protected void visitLetValueNode(LetValueNode node) {

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    CodeChunk.WithValue value = jsExprTranslator.translateToCodeChunk(
        node.getValueExpr(), templateTranslationContext, errorReporter);
    jsCodeBuilder.append(declare(generatedVarName, value));

    // Add a mapping for generating future references to this local var.
    templateTranslationContext
        .soyToJsVariableMappings()
        .put(
            node.getVarName(),
            id(generatedVarName));
  }

  /**
   * Example:
   * <pre>
   *   {let $boo}
   *     Hello {$name}
   *   {/let}
   * </pre>
   * might generate
   * <pre>
   *   var boo35 = 'Hello ' + opt_data.name;
   * </pre>
   */
  @Override protected void visitLetContentNode(LetContentNode node) {
    // Optimization: {msg} nodes emit statements and result in a JsExpr with a single variable.  Use
    // that variable (typically the MSG_* from getMsg) as-is instead of wrapping a new var around it
    if (node.getChildren().size() == 1 && node.getChild(0) instanceof MsgFallbackGroupNode) {
      String msgVar = getAssistantForMsgs()
          .generateMsgGroupVariable((MsgFallbackGroupNode) node.getChild(0));
      templateTranslationContext
          .soyToJsVariableMappings()
          .put(
              node.getVarName(),
              id(msgVar));
      return;
    }

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    jsCodeBuilder.pushOutputVar(generatedVarName);

    visitChildren(node);

    jsCodeBuilder.popOutputVar();

    if (node.getContentKind() != null) {
      // If the let node had a content kind specified, it was autoescaped in the corresponding
      // context. Hence the result of evaluating the let block is wrapped in a SanitizedContent
      // instance of the appropriate kind.

      // The expression for the constructor of SanitizedContent of the appropriate kind (e.g.,
      // "soydata.VERY_UNSAFE.ordainSanitizedHtml"), or null if the node has no 'kind' attribute.
      final String sanitizedContentOrdainer =
          NodeContentKinds.toJsSanitizedContentOrdainerForInternalBlocks(node.getContentKind());

      jsCodeBuilder.appendLine(generatedVarName, " = ", sanitizedContentOrdainer, "(",
          generatedVarName, ");");
    }

    // Add a mapping for generating future references to this local var.
    templateTranslationContext
        .soyToJsVariableMappings()
        .put(
            node.getVarName(),
            id(generatedVarName));
  }

  /**
   * Example:
   * <pre>
   *   {if $boo.foo &gt; 0}
   *     ...
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   if (opt_data.boo.foo &gt; 0) {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitIfNode(IfNode node) {

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
        // Convert predicate.
        CodeChunk.WithValue predicate =
            jsExprTranslator.translateToCodeChunk(
                ((IfCondNode) child).getExprUnion(), templateTranslationContext, errorReporter);

        // Replace jsCodeBuilder with a child JsCodeBuilder.
        JsCodeBuilder originalCodeBuilder = jsCodeBuilder;
        jsCodeBuilder = createChildJsCodeBuilder();

        // Visit body.
        jsCodeBuilder.increaseIndent();
        visit(child);
        jsCodeBuilder.decreaseIndent();

        // Convert body to CodeChunk.

        CodeChunk consequent = jsCodeBuilder.getCodeAsChunkLegacyOnly();

        // Swap the original JsCodeBuilder back in, but preserve indent levels.
        originalCodeBuilder.setIndent(jsCodeBuilder.getIndent());
        jsCodeBuilder = originalCodeBuilder;

        // Add if-block to conditional.
        if (conditional == null) {
          conditional =
              templateTranslationContext.codeGenerator().newChunk().if_(predicate, consequent);
        } else {
          conditional.elseif_(predicate, consequent);
        }

      } else if (child instanceof IfElseNode) {
        // Replace jsCodeBuilder with a child JsCodeBuilder.
        JsCodeBuilder originalCodeBuilder = jsCodeBuilder;
        jsCodeBuilder = this.createChildJsCodeBuilder();

        // Visit body.
        jsCodeBuilder.increaseIndent();
        visit(child);
        jsCodeBuilder.decreaseIndent();

        // Convert body to CodeChunk.
        CodeChunk trailingElse = jsCodeBuilder.getCodeAsChunkLegacyOnly();

        // Swap the original JsCodeBuilder back in, but preserve indent levels.
        originalCodeBuilder.setIndent(jsCodeBuilder.getIndent());
        jsCodeBuilder = originalCodeBuilder;

        // Add else-block to conditional.
        conditional.else_(trailingElse);
      } else {
        throw new AssertionError();
      }
    }

    jsCodeBuilder.append(conditional.endif().build());
  }

  /**
   * Example:
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
   * might generate
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
  @Override protected void visitSwitchNode(SwitchNode node) {

    String switchExpr = coerceTypeForSwitchComparison(node.getExpr(), node.getExprText());
    jsCodeBuilder.appendLine("switch (", switchExpr, ") {");
    jsCodeBuilder.increaseIndent();

    for (SoyNode child : node.getChildren()) {

      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;

        for (ExprNode caseExpr : scn.getExprList()) {
          JsExpr caseJsExpr =
              jsExprTranslator
                  .translateToCodeChunk(caseExpr, templateTranslationContext, errorReporter)
                  .assertExpr(); // TODO(user): remove
          jsCodeBuilder.appendLine("case ", caseJsExpr.getText(), ":");
        }

        jsCodeBuilder.increaseIndent();
        visit(scn);
        jsCodeBuilder.appendLine("break;");
        jsCodeBuilder.decreaseIndent();

      } else if (child instanceof SwitchDefaultNode) {
        SwitchDefaultNode sdn = (SwitchDefaultNode) child;

        jsCodeBuilder.appendLine("default:");

        jsCodeBuilder.increaseIndent();
        visit(sdn);
        jsCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }

    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("}");
  }

  // js switch statements use === for comparing the switch expr to the cases.  In order to preserve
  // soy equality semantics for sanitized content objects we need to coerce cases and switch exprs
  // to strings.
  private String coerceTypeForSwitchComparison(ExprRootNode v2Expr, String v1Expr) {
    String jsExpr =
        jsExprTranslator
            .translateToCodeChunk(v2Expr, v1Expr, templateTranslationContext, errorReporter)
            .assertExpr() // TODO(user): remove
            .getText();
    SoyType type = v2Expr.getType();
    // If the type is possibly a sanitized content type then we need to toString it.
    if (SoyTypes.makeNullable(StringType.getInstance()).isAssignableFrom(type)
        || type.equals(AnyType.getInstance())
        || type.equals(UnknownType.getInstance())) {
      return "(goog.isObject($$temp = " + jsExpr + ")) ? $$temp.toString() : $$temp";
    }
    // For everything else just pass through.  switching on objects/collections is unlikely to
    // have reasonably defined behavior.
    return jsExpr;
  }

  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {ifempty}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   var fooList2 = opt_data.boo.foos;
   *   var fooListLen2 = fooList2.length;
   *   if (fooListLen2 > 0) {
   *     ...
   *   } else {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForeachNode(ForeachNode node) {

    // Build some local variable names.
    ForeachNonemptyNode nonEmptyNode = (ForeachNonemptyNode) node.getChild(0);
    String baseVarName = nonEmptyNode.getVarName();
    String nodeId = Integer.toString(node.getId());
    String listVarName = baseVarName + "List" + nodeId;
    String listLenVarName = baseVarName + "ListLen" + nodeId;

    // Define list var and list-len var.
    CodeChunk.WithValue dataRef = jsExprTranslator.translateToCodeChunk(
        node.getExpr(), node.getExprText(), templateTranslationContext, errorReporter);

    jsCodeBuilder.append(declare(listVarName, dataRef));
    jsCodeBuilder.appendLine("var ", listLenVarName, " = ", listVarName, ".length;");

    // If has 'ifempty' node, add the wrapper 'if' statement.
    boolean hasIfemptyNode = node.numChildren() == 2;
    if (hasIfemptyNode) {
      jsCodeBuilder.appendLine("if (", listLenVarName, " > 0) {").increaseIndent();
    }

    // Generate code for nonempty case.
    visit(nonEmptyNode);

    // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
    if (hasIfemptyNode) {
      jsCodeBuilder.decreaseIndent();
      jsCodeBuilder.appendLine("} else {").increaseIndent();

      // Generate code for empty case.
      visit(node.getChild(1));
      jsCodeBuilder.decreaseIndent();
      jsCodeBuilder.appendLine("}");
    }
  }

  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   for (var fooIndex2 = 0; fooIndex2 &lt; fooListLen2; fooIndex2++) {
   *     var fooData2 = fooList2[fooIndex2];
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String foreachNodeId = Integer.toString(node.getForeachNodeId());
    String listVarName = baseVarName + "List" + foreachNodeId;
    String listLenVarName = baseVarName + "ListLen" + foreachNodeId;
    String indexVarName = baseVarName + "Index" + foreachNodeId;
    String dataVarName = baseVarName + "Data" + foreachNodeId;

    // The start of the JS 'for' loop.
    jsCodeBuilder.appendLine(
        "for (var ", indexVarName, " = 0; ",
        indexVarName, " < ", listLenVarName, "; ",
        indexVarName, "++) {");
    jsCodeBuilder.increaseIndent();
    jsCodeBuilder.appendLine("var ", dataVarName, " = ", listVarName, "[", indexVarName, "];");

    // Populate the local var translations with the translations from this node.
    templateTranslationContext
        .soyToJsVariableMappings()
        .put(
            baseVarName,
            id(dataVarName))
        .put(
            baseVarName + "__isFirst",
            id(indexVarName)
                .doubleEquals(
                    number(0)))
        .put(
            baseVarName + "__isLast",
            id(indexVarName)
                .doubleEquals(
                    id(listLenVarName)
                        .minus(
                            number(1))))
        .put(
            baseVarName + "__index",
            id(indexVarName));

    // Generate the code for the loop body.
    visitChildren(node);

    // The end of the JS 'for' loop.
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("}");
  }

  /**
   * Example:
   * <pre>
   *   {for $i in range(1, $boo)}
   *     ...
   *   {/for}
   * </pre>
   * might generate
   * <pre>
   *   var iLimit4 = opt_data.boo;
   *   for (var i4 = 1; i4 &lt; iLimit4; i4++) {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForNode(ForNode node) {

    String varName = node.getVarName();
    String nodeId = Integer.toString(node.getId());

    // Get the JS expression text for the init/limit/increment values.
    RangeArgs range = node.getRangeArgs();
    String incrementJsExprText =
        range.increment().isPresent()
            ? jsExprTranslator
                .translateToCodeChunk(
                    range.increment().get(),
                    templateTranslationContext,
                    errorReporter)
                .assertExpr() // TODO(user): remove
                .getText()
            : "1" /* default */;
    String initJsExprText =
        range.start().isPresent()
            ? jsExprTranslator
                .translateToCodeChunk(
                    range.start().get(),
                    templateTranslationContext,
                    errorReporter)
                .assertExpr() // TODO(user): remove
                .getText()
            : "0" /* default */;
    String limitJsExprText =
        jsExprTranslator
            .translateToCodeChunk(range.limit(), templateTranslationContext, errorReporter)
            .assertExpr() // TODO(user): remove
            .getText();

    // If any of the JS expressions for init/limit/increment isn't an integer, precompute its value.
    String initCode;
    if (INTEGER.matcher(initJsExprText).matches()) {
      initCode = initJsExprText;
    } else {
      initCode = varName + "Init" + nodeId;
      jsCodeBuilder.appendLine("var ", initCode, " = ", initJsExprText, ";");
    }

    String limitCode;
    if (INTEGER.matcher(limitJsExprText).matches()) {
      limitCode = limitJsExprText;
    } else {
      limitCode = varName + "Limit" + nodeId;
      jsCodeBuilder.appendLine("var ", limitCode, " = ", limitJsExprText, ";");
    }

    String incrementCode;
    if (INTEGER.matcher(incrementJsExprText).matches()) {
      incrementCode = incrementJsExprText;
    } else {
      incrementCode = varName + "Increment" + nodeId;
      jsCodeBuilder.appendLine("var ", incrementCode, " = ", incrementJsExprText, ";");
    }

    // The start of the JS 'for' loop.
    String incrementStmt = incrementCode.equals("1") ?
        varName + nodeId + "++" : varName + nodeId + " += " + incrementCode;
    jsCodeBuilder.appendLine(
        "for (var ",
        varName, nodeId, " = ", initCode, "; ",
        varName, nodeId, " < ", limitCode, "; ",
        incrementStmt,
        ") {");
    jsCodeBuilder.increaseIndent();

    // Populate the local var translations with the translations from this node.
    templateTranslationContext
        .soyToJsVariableMappings()
        .put(varName, id(varName + nodeId));

    // Generate the code for the loop body.
    visitChildren(node);

    // The end of the JS 'for' loop.
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("}");
  }

  /**
   * Example:
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
   * might generate
   * <pre>
   *   output += some.func(opt_data);
   *   output += some.func(opt_data.boo.foo);
   *   output += some.func({goo: 88});
   *   output += some.func(soy.$$assignDefaults({goo: 'Hello ' + opt_data.name}, opt_data.boo);
   * </pre>
   */
  @Override protected void visitCallNode(CallNode node) {

    // If this node has any CallParamContentNode children those contents are not computable as JS
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJsExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    // Add the call's result to the current output var.
    CodeChunk.WithValue call =
        genCallCodeUtils.gen(node, templateAliases, templateTranslationContext, errorReporter);
    jsCodeBuilder.addChunkToOutputVar(call);
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {

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
   * <pre>
   *   {log}Blah {$boo}.{/log}
   * </pre>
   * might generate
   * <pre>
   *   window.console.log('Blah ' + opt_data.boo + '.');
   * </pre>
   *
   * <p> If the log msg is not computable as JS exprs, then it will be built in a local var
   * logMsg_s##, e.g.
   * <pre>
   *   var logMsg_s14 = ...
   *   window.console.log(logMsg_s14);
   * </pre>
   */
  @Override protected void visitLogNode(LogNode node) {

    if (isComputableAsJsExprsVisitor.execOnChildren(node)) {
      List<CodeChunk.WithValue> logMsgChunks = genJsExprsVisitor.execOnChildren(node);

      jsCodeBuilder.append(
          dottedId("window.console.log").call(CodeChunkUtils.concatChunks(logMsgChunks)));
    } else {
      // Must build log msg in a local var logMsg_s##.
      jsCodeBuilder.pushOutputVar("logMsg_s" + node.getId());

      visitChildren(node);

      jsCodeBuilder.popOutputVar();

      jsCodeBuilder.appendLine("window.console.log(logMsg_s", Integer.toString(node.getId()), ");");
    }
  }

  /**
   * Example:
   * <pre>
   *   {debugger}
   * </pre>
   * generates
   * <pre>
   *   debugger;
   * </pre>
   */
  @Override protected void visitDebuggerNode(DebuggerNode node) {
    jsCodeBuilder.appendLine("debugger;");
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ParentSoyNode<?>) {

      if (node instanceof BlockNode) {
        visitChildren((BlockNode) node);
      } else {
        visitChildren((ParentSoyNode<?>) node);
      }

      return;
    }

    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Simply generate JS expressions for this node and add them to the current output var.
      jsCodeBuilder.addChunksToOutputVar(genJsExprsVisitor.exec(node));

    } else {
      // Need to implement visit*Node() for the specific case.
      throw new UnsupportedOperationException();
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers

  /**
   * Generate the JSDoc for the opt_data parameter.
   */
  private String genParamsRecordType(TemplateNode node) {
    Set<String> paramNames = new HashSet<>();

    // Generate members for explicit params.
    Map<String, String> record = new LinkedHashMap<>();
    for (TemplateParam param : node.getParams()) {
      record.put(genParamAlias(param.name()), getJsType(param.type()).typeExprForRecordMember());
      paramNames.add(param.name());
    }

    // Do the same for indirect params, if we can find them.
    // If there's a conflict between the explicitly-declared type, and the type
    // inferred from the indirect params, then the explicit type wins.
    // Also note that indirect param types may not be inferrable if the target
    // is not in the current compilation file set.
    IndirectParamsInfo ipi = new FindIndirectParamsVisitor(templateRegistry).exec(node);
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
        SoyType combinedType = typeOps.computeLowestCommonType(paramTypes);
        // Note that Union folds duplicate types and flattens unions, so if
        // the combinedType is already a union this will do the right thing.
        // TODO: detect cases where nullable is not needed (requires flow
        // analysis to determine if the template is always called.)
        SoyType indirectParamType = typeOps.getTypeRegistry()
            .getOrCreateUnionType(combinedType, NullType.getInstance());
        record.put(
            genParamAlias(indirectParamName),
            getJsType(indirectParamType).typeExprForRecordMember());
      }
    }
    StringBuilder sb = new StringBuilder();
    sb.append("{\n *  ");
    Joiner.on(",\n *  ").withKeyValueSeparator(": ").appendTo(sb, record);
    sb.append("\n * }");
    return sb.toString();
  }

  /**
   * Returns the name of the JS type used to represent the given SoyType at runtime.
   * Can be overridden by subclasses to provide a different mapping.
   */
  protected String getJsTypeName(SoyType type) {
    return JsSrcUtils.getJsTypeName(type);
  }

  /**
   * Generate code to verify the runtime types of the input params. Also typecasts the
   * input parameters and assigns them to local variables for use in the template.
   * @param node the template node.
   */
  protected void genParamTypeChecks(TemplateNode node) {
    for (TemplateParam param : node.getAllParams()) {
      if (param.declLoc() != TemplateParam.DeclLoc.HEADER) {
        continue;
      }
      String paramName = param.name();
      SoyType paramType = param.type();
      CodeChunk.Generator generator = templateTranslationContext.codeGenerator();
      CodeChunk.WithValue paramChunk =
          TranslateExprNodeVisitor.genCodeForParamAccess(
              paramName, param.isInjected());
      JsType jsType = getJsType(paramType);
      // The opt_param.name value that will be type-tested.
      String paramAlias = genParamAlias(paramName);
      CodeChunk.WithValue coerced = jsType.getValueCoercion(paramChunk, generator);
      if (coerced != null) {
        // since we have coercion logic, dump into a temporary
        paramChunk = generator.newChunk().assign(coerced).buildAsValue();
      }
      // The param value to assign
      CodeChunk.WithValue value;
      Optional<CodeChunk.WithValue> typeAssertion = jsType.getTypeAssertion(paramChunk, generator);
      // The type-cast expression.
      if (typeAssertion.isPresent()) {
        value =
            dottedId("soy.asserts.assertType")
                .call(
                    typeAssertion.get(),
                    stringLiteral(paramName),
                    paramChunk,
                    stringLiteral(jsType.typeExpr()));
      } else {
        value = paramChunk;
      }
      CodeChunk.WithValue declaration =
          declare(jsSrcOptions.shouldGenerateJsdoc() ? jsType.typeExpr() : null, paramAlias, value);
      jsCodeBuilder.append(declaration);

      templateTranslationContext
          .soyToJsVariableMappings()
          .put(paramName, id(paramAlias));
    }
  }

  private JsType getJsType(SoyType paramType) {
    boolean isIncrementalDom = !getClass().equals(GenJsCodeVisitor.class);
    return JsType.forSoyType(paramType, isIncrementalDom);
  }

  /**
   * Generate a name for the local variable which will store the value of a
   * parameter, avoiding collision with JavaScript reserved words.
   */
  private String genParamAlias(String paramName) {
    return JsSrcUtils.isReservedWord(paramName) ? "param$" + paramName : paramName;
  }

  /**
   * Return true if any template in this file has params that require strict type
   * checking (and thus require additional {@code goog.require()} statements.
   */
  private boolean hasStrictParams(SoyFileNode soyFile) {
    for (TemplateNode template : soyFile.getChildren()) {
      if (!template.getInjectedParams().isEmpty() || hasStrictParams(template)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return true if the template has at least one strict param.
   */
  private boolean hasStrictParams(TemplateNode template) {
    for (TemplateParam param : template.getParams()) {
      if (param.declLoc() == TemplateParam.DeclLoc.HEADER) {
        return true;
      }
    }
    // Note: If there are only injected params, don't use strong typing for
    // the function signature, because what it will produce is an empty struct.
    return false;
  }


  /**
   * Scan all templates, and return a list of types that will require a goog.require()
   * statement. Any template that has a type-checked parameter that is an object
   * or sanitized type (or a union containing the same) will need this.
   * @param soyFile The file containing the templates to be searched.
   * @return Types The types that need a goog.require().
   */
  private SortedSet<String> getRequiredObjectTypes(SoyFileNode soyFile) {
    SortedSet<String> requiredObjectTypes = new TreeSet<>();
    FieldImportsVisitor fieldImportsVisitor = new FieldImportsVisitor(requiredObjectTypes);
    for (TemplateNode template : soyFile.getChildren()) {
      SoyTreeUtils.execOnAllV2Exprs(template, fieldImportsVisitor);
      for (TemplateParam param : template.getAllParams()) {
        if (param.declLoc() != TemplateParam.DeclLoc.HEADER) {
          continue;
        }
        if (shouldGenerateGoogRequire(param.type())) {
          requiredObjectTypes.add(getJsTypeName(param.type()));
        } else if (param.type().getKind() == SoyType.Kind.UNION) {
          UnionType union = (UnionType) param.type();
          for (SoyType memberType : union.getMembers()) {
            if (shouldGenerateGoogRequire(memberType)) {
              requiredObjectTypes.add(getJsTypeName(memberType));
            }
          }
        }
      }
    }
    for (ProtoInitNode protoInit : SoyTreeUtils.getAllNodesOfType(soyFile, ProtoInitNode.class)) {
      SoyType type = protoInit.getType();
      requiredObjectTypes.add(getJsTypeName(type));
    }
    return requiredObjectTypes;
  }

  private static boolean shouldGenerateGoogRequire(SoyType type) {
    return type.getKind() == SoyType.Kind.PROTO || type.getKind() == SoyType.Kind.PROTO_ENUM;
  }

  /**
   * Helper class to visit all field reference expressions that result in
   * additional goog.require imports.
   */
  private static final class FieldImportsVisitor extends AbstractExprNodeVisitor<Void> {
    private final SortedSet<String> imports;

    FieldImportsVisitor(SortedSet<String> imports) {
      this.imports = imports;
    }

    @Override public Void exec(ExprNode node) {
      visit(node);
      return null;
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    @Override
    protected void visitFieldAccessNode(FieldAccessNode node) {
      SoyType baseType = node.getBaseExprChild().getType();
      extractImportsFromType(baseType, node.getFieldName());
      visit(node.getBaseExprChild());
    }

    /**
     * Finds imports required for a field access by looking through aggregate types.
     */
    private void extractImportsFromType(SoyType baseType, String fieldName) {
      if (baseType.getKind() == SoyType.Kind.PROTO) {
        FieldDescriptor desc = ((SoyProtoType) baseType).getFieldDescriptor(fieldName);

        if (desc.isExtension()) {
          imports.add(Protos.getJsExtensionImport(desc));
        }
        if (Protos.isSanitizedContentField(desc)) {
          // Repeated sanitized content fields require goog.array.map to transform the
          // array of SafeHtmlProtos to an array of SanitizedContent objects.
          if (desc.isRepeated()) {
            imports.add("goog.array");
          }
          imports.add(NodeContentKinds.toJsUnpackFunction(desc.getMessageType()));
        }
      } else {
        // TODO: Is there any way to fold over sub-types of aggregate types?
        if (baseType.getKind() == SoyType.Kind.UNION) {
          for (SoyType memberBaseType : ((UnionType) baseType).getMembers()) {
            extractImportsFromType(memberBaseType, fieldName);
          }
        }
      }
    }
  }
}
