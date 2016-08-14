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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.html.AbstractHtmlSoyNodeVisitor;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.internal.HighLevelJsCodeBuilder.ConditionBuilder;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.parsepasses.contextautoesc.ContentSecurityPolicyPass;
import com.google.template.soy.passes.FindIndirectParamsVisitor;
import com.google.template.soy.passes.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.passes.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.shared.internal.CodeBuilder;
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
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.XidNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeOps;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;
import com.google.template.soy.types.proto.SoyProtoType;

import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
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
  @VisibleForTesting CodeBuilder<JsExpr> jsCodeBuilder;

  @VisibleForTesting HighLevelJsCodeBuilder highLevelJsCodeBuilder;

  /** The current stack of replacement JS expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  protected Deque<Map<String, JsExpr>> localVarTranslations;

  /** The GenJsExprsVisitor used for the current template. */
  protected GenJsExprsVisitor genJsExprsVisitor;

  /** The assistant visitor for msgs used for the current template (lazily initialized). */
  @VisibleForTesting GenJsCodeVisitorAssistantForMsgs assistantForMsgs;

  /** The GenDirectivePluginRequiresVisitor for the current template. */
  private GenDirectivePluginRequiresVisitor genDirectivePluginRequiresVisitor;

  protected TemplateRegistry templateRegistry;

  /** Type operators. */
  private final SoyTypeOps typeOps;

  /** The accumulated set of all JS namespaces required so far. */
  private Set<String> alreadyRequiredNamespaces;

  protected ErrorReporter errorReporter;

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
      jsFilesContents = Lists.newArrayList();
      jsCodeBuilder = null;
      highLevelJsCodeBuilder = null;
      localVarTranslations = null;
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
  void visitForTesting(SoyNode node, ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    visit(node);
  }


  @Override protected void visitChildren(ParentSoyNode<?> node) {

    // If the block is empty or if the first child cannot initilize the output var, we must
    // initialize the output var.
    if (node.numChildren() == 0 || !canInitOutputVarVisitor.exec(node.getChild(0))) {
      jsCodeBuilder.initOutputVarIfNecessary();
    }

    List<JsExpr> consecChildrenJsExprs = Lists.newArrayList();

    for (SoyNode child : node.getChildren()) {

      if (isComputableAsJsExprsVisitor.exec(child)) {
        consecChildrenJsExprs.addAll(genJsExprsVisitor.exec(child));

      } else {
        // We've reached a child that is not computable as JS expressions.

        // First add the JsExprs from preceding consecutive siblings that are computable as JS
        // expressions (if any).
        if (!consecChildrenJsExprs.isEmpty()) {
          jsCodeBuilder.addToOutputVar(consecChildrenJsExprs);
          consecChildrenJsExprs.clear();
        }

        // Now append the code for this child.
        visit(child);
      }
    }

    // Add the JsExprs from the last few children (if any).
    if (!consecChildrenJsExprs.isEmpty()) {
      jsCodeBuilder.addToOutputVar(consecChildrenJsExprs);
      consecChildrenJsExprs.clear();
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
  protected CodeBuilder<JsExpr> createCodeBuilder() {
    return new JsCodeBuilder();
  }

  /**
   * @return The CodeBuilder used for generating file contents.
   */
  protected CodeBuilder<JsExpr> getJsCodeBuilder() {
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

    jsCodeBuilder = createCodeBuilder();
    highLevelJsCodeBuilder = new HighLevelJsCodeBuilderImpl(jsCodeBuilder);
    alreadyRequiredNamespaces = new LinkedHashSet<>();

    jsCodeBuilder.appendLine("// This file was automatically generated from ",
                             node.getFileName(), ".");
    jsCodeBuilder.appendLine("// Please don't edit this file by hand.");

    // Output a section containing optionally-parsed compiler directives in comments. Since these
    // are comments, they are not controlled by an option, and will be removed by minifiers that do
    // not understand them.
    jsCodeBuilder.appendLine();
    jsCodeBuilder.appendLine("/**");
    String fileOverviewDescription = node.getNamespace() == null
        ? ""
        : " Templates in namespace " + node.getNamespace() + ".";
    jsCodeBuilder.appendLine(" * @fileoverview", fileOverviewDescription);
    if (node.getDelPackageName() != null) {
      jsCodeBuilder.appendLine(" * @modName {", node.getDelPackageName(), "}");
    }
    addJsDocToProvideDelTemplates(node);
    addJsDocToRequireDelTemplates(node);
    addCodeToRequireCss(node);
    jsCodeBuilder.appendLine(" * @public").appendLine(" */");

    // Add code to define JS namespaces or add provide/require calls for Closure Library.
    jsCodeBuilder.appendLine();
    templateAliases = AliasUtils.IDENTITY_ALIASES;

    if (jsSrcOptions.shouldGenerateGoogModules()) {
      templateAliases = AliasUtils.createTemplateAliases(node);

      addCodeToDeclareGoogModule(node);
      addCodeToRequireGeneralDeps(node);
      addCodeToRequireGoogModules(node);
    } else if (jsSrcOptions.shouldProvideRequireSoyNamespaces()) {
      addCodeToProvideSoyNamespace(node);
      if (jsSrcOptions.shouldProvideBothSoyNamespacesAndJsFunctions()) {
        addCodeToProvideJsFunctions(node);
      }
      jsCodeBuilder.appendLine();
      addCodeToRequireGeneralDeps(node);
      addCodeToRequireSoyNamespaces(node);
    } else if (jsSrcOptions.shouldProvideRequireJsFunctions()) {
      if (jsSrcOptions.shouldProvideBothSoyNamespacesAndJsFunctions()) {
        addCodeToProvideSoyNamespace(node);
      }
      addCodeToProvideJsFunctions(node);
      jsCodeBuilder.appendLine();
      addCodeToRequireGeneralDeps(node);
      addCodeToRequireJsFunctions(node);
    } else {
      addCodeToDefineJsNamespaces(node);
    }

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      jsCodeBuilder.appendLine().appendLine();
      visit(template);
    }

    jsFilesContents.add(jsCodeBuilder.getCode());
    jsCodeBuilder = null;
    highLevelJsCodeBuilder = null;
  }

  /**
   * Appends requirecss jsdoc tags in the file header section.
   * @param soyFile The file with the templates..
   */
  private void addCodeToRequireCss(SoyFileNode soyFile) {

    SortedSet<String> requiredCssNamespaces = Sets.newTreeSet();
    requiredCssNamespaces.addAll(soyFile.getRequiredCssNamespaces());
    for (TemplateNode template : soyFile.getChildren()) {
      requiredCssNamespaces.addAll(template.getRequiredCssNamespaces());
    }

    // NOTE: CSS requires in JS can only be done on a file by file basis at this time.  Perhaps in
    // the future, this might be supported per function.
    for (String requiredCssNamespace : requiredCssNamespaces) {
      jsCodeBuilder.appendLine(" * @requirecss {", requiredCssNamespace, "}");
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to define JS namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToDefineJsNamespaces(SoyFileNode soyFile) {

    SortedSet<String> jsNamespaces = Sets.newTreeSet();
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
        jsCodeBuilder.appendLine("if (typeof ", jsNamespace, " == 'undefined') { ",
                                 (hasDot ? "" : "var "), jsNamespace, " = {}; }");
      }
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to provide Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideSoyNamespace(SoyFileNode soyFile) {
    if (soyFile.getNamespace() != null) {
      highLevelJsCodeBuilder.declareNamespace(soyFile.getNamespace());
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
   * @param soyFile The node we're visiting.
   */
  private void addCodeToDeclareGoogModule(SoyFileNode soyFile) {
    String exportNamespace = getGoogModuleNamespace(soyFile.getNamespace());
    highLevelJsCodeBuilder.declareModule(exportNamespace);
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
    for (CallBasicNode callNode : SoytreeUtils.getAllNodesOfType(soyFile, CallBasicNode.class)) {
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
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideJsFunctions(SoyFileNode soyFile) {

    SortedSet<String> templateNames = Sets.newTreeSet();
    for (TemplateNode template : soyFile.getChildren()) {
      templateNames.add(template.getTemplateName());
    }
    for (String templateName : templateNames) {
      highLevelJsCodeBuilder.provide(templateName);
    }
  }

  private void addJsDocToProvideDelTemplates(SoyFileNode soyFile) {

    SortedSet<String> delTemplateNames = Sets.newTreeSet();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template instanceof TemplateDelegateNode) {
        delTemplateNames.add(delTemplateNamer.getDelegateName((TemplateDelegateNode) template));
      }
    }
    for (String delTemplateName : delTemplateNames) {
      jsCodeBuilder.appendLine(" * @hassoydeltemplate {", delTemplateName, "}");
    }
  }

  private void addJsDocToRequireDelTemplates(SoyFileNode soyFile) {

    SortedSet<String> delTemplateNames = Sets.newTreeSet();
    for (CallDelegateNode delCall :
        SoytreeUtils.getAllNodesOfType(soyFile, CallDelegateNode.class)) {
      delTemplateNames.add(delTemplateNamer.getDelegateName(delCall));
    }
    for (String delTemplateName : delTemplateNames) {
      jsCodeBuilder.appendLine(" * @hassoydelcall {", delTemplateName, "}");
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require general dependencies.
   * @param soyFile The node we're visiting.
   */
  protected void addCodeToRequireGeneralDeps(SoyFileNode soyFile) {

    // TODO(user): keep track of JS symbols that are actually printed,
    // so no extraRequires are necessary.
    addGoogRequire("soy", true);
    addGoogRequire("soydata", true);

    SortedSet<String> requiredObjectTypes = ImmutableSortedSet.of();
    if (hasStrictParams(soyFile)) {
      requiredObjectTypes = getRequiredObjectTypes(soyFile);
      addGoogRequire("goog.asserts", true);
      addGoogRequire("soy.asserts", true);
    }
    if (hasInjectedParams(soyFile)) {
      addGoogRequire("goog.asserts", true);
    }

    if (jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir()) {
      // Suppress extraRequire because it may be unused (b/25672094).
      addGoogRequire(GOOG_IS_RTL_NAMESPACE, true);
    }

    if (SoytreeUtils.hasNodesOfType(soyFile, MsgPluralNode.class, MsgSelectNode.class)) {
      addGoogRequire(GOOG_MESSAGE_FORMAT_NAMESPACE, false);
    }

    if (SoytreeUtils.hasNodesOfType(soyFile, XidNode.class)) {
      addGoogRequire("xid", false);
    }

    SortedSet<String> pluginRequiredJsLibNames = Sets.newTreeSet();
    pluginRequiredJsLibNames.addAll(genDirectivePluginRequiresVisitor.exec(soyFile));
    pluginRequiredJsLibNames.addAll(new GenFunctionPluginRequiresVisitor().exec(soyFile));
    for (String namespace : pluginRequiredJsLibNames) {
      addGoogRequire(namespace, false);
    }

    if (!requiredObjectTypes.isEmpty()) {
      jsCodeBuilder.appendLine();
      for (String requiredType : requiredObjectTypes) {
        addGoogRequire(requiredType, false);
      }
    }
  }

  /**
   * Emit goog.require for namespace if namespace has not already been emitted.
   *
   * @param namespace The JS namespace to require if not already required.
   * @param suppressExtra Whether to add a {@code @suppress {extraRequire}} annotation for requires
   *     that may be unused.
   */
  protected void addGoogRequire(String namespace, boolean suppressExtra) {
    if (!alreadyRequiredNamespaces.contains(namespace)) {
      if (suppressExtra) {
        jsCodeBuilder.appendLine("/** @suppress {extraRequire} */");
      }
      jsCodeBuilder.appendLine("goog.require('" + namespace + "');");
      alreadyRequiredNamespaces.add(namespace);
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require goog.module dependencies. This
   * file should produce one or more calls to goog.module.get. Note that this does not cause the
   * modules to be pulled in by Closure and an additional goog.require for the same namespace is
   * needed in addCodeToRequireGeneralDeps.
   * @param soyFile The node we're visiting.
   */
  protected void addCodeToRequireGoogModuleDeps(SoyFileNode soyFile) {
    // NO-OP
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireSoyNamespaces(SoyFileNode soyFile) {

    String prevCalleeNamespace = null;
    Set<String> calleeNamespaces = Sets.newTreeSet();
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
        addGoogRequire(calleeNamespace, false);
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
      addGoogRequire(require, false);
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
          : "!" + NodeContentKinds.toJsSanitizedContentCtorName(node.getContentKind());
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

    localVarTranslations = new ArrayDeque<>();
    genJsExprsVisitor =
        genJsExprsVisitorFactory.create(localVarTranslations, templateAliases, errorReporter);
    assistantForMsgs = null;

    // ------ Generate JS Doc. ------
    if (jsSrcOptions.shouldGenerateJsdoc()) {
      jsCodeBuilder.appendLine("/**");
      if (useStrongTyping) {
        genParamsRecordType(node);
      } else {
        jsCodeBuilder.appendLine(" * @param {Object<string, *>=} opt_data");
      }
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
    if (!SoytreeUtils.getAllNodesOfType(node, NullCoalescingOpNode.class).isEmpty()
        || !SoytreeUtils.getAllNodesOfType(node, SwitchNode.class).isEmpty()) {
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
    for (VarRefNode ref : SoytreeUtils.getAllNodesOfType(node, VarRefNode.class)) {
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
    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());

    // Type check parameters.
    genParamTypeChecks(node);

    JsExpr resultJsExpr;
    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as JS
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the JS expressions and return the result.

      List<JsExpr> templateBodyJsExprs = genJsExprsVisitor.exec(node);
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
        resultJsExpr = JsExprUtils.concatJsExprsForceString(templateBodyJsExprs);
      } else {
        // The template is strict. Thus, it applies an escaping directive to *every* print command,
        // which means that no print command produces a number, which means that there is no danger
        // of a plus operator between two print commands doing numeric addition instead of string
        // concatenation. And since a strict template needs to return SanitizedContent, it is ok to
        // get an expression that produces SanitizedContent, which is indeed possible with an
        // escaping directive that produces SanitizedContent. Thus, we do not have to be extra
        // careful when concatenating the expressions in the list.
        resultJsExpr = JsExprUtils.concatJsExprs(templateBodyJsExprs);
      }
    } else {
      // Case 2: Normal case.

      jsCodeBuilder.pushOutputVar("output");
      visitChildren(node);
      resultJsExpr = new JsExpr("output", Integer.MAX_VALUE);
      jsCodeBuilder.popOutputVar();
    }

    if (node.getContentKind() != null) {
      // Templates with autoescape="strict" return the SanitizedContent wrapper for its kind:
      // - Call sites are wrapped in an escaper. Returning SanitizedContent prevents re-escaping.
      // - The topmost call into Soy returns a SanitizedContent. This will make it easy to take
      // the result of one template and feed it to another, and also to confidently assign sanitized
      // HTML content to innerHTML. This does not use the internal-blocks variant.
      resultJsExpr = JsExprUtils.maybeWrapAsSanitizedContent(
          node.getContentKind(), resultJsExpr);
    }
    jsCodeBuilder.appendLine("return ", resultJsExpr.getText(), ";");

    localVarTranslations.pop();
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
              jsCodeBuilder,
              localVarTranslations,
              templateAliases,
              genJsExprsVisitor,
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
    jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));
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
    JsExpr valueJsExpr =
        jsExprTranslator.translateToJsExpr(
            node.getValueExpr(), localVarTranslations, errorReporter);
    jsCodeBuilder.appendLine("var ", generatedVarName, " = ", valueJsExpr.getText(), ";");

    // Add a mapping for generating future references to this local var.
    localVarTranslations.peek().put(
        node.getVarName(), new JsExpr(generatedVarName, Integer.MAX_VALUE));
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
      localVarTranslations.peek().put(node.getVarName(), new JsExpr(msgVar, Integer.MAX_VALUE));
      return;
    }

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
    jsCodeBuilder.pushOutputVar(generatedVarName);

    visitChildren(node);

    jsCodeBuilder.popOutputVar();
    localVarTranslations.pop();

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
    localVarTranslations.peek().put(
        node.getVarName(), new JsExpr(generatedVarName, Integer.MAX_VALUE));
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
      jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));
    } else {
      generateNonExpressionIfNode(node);
    }
  }

  /**
   * Generates the JavaScript code for an {if} block that cannot be done as an expression.
   */
  protected void generateNonExpressionIfNode(IfNode node) {
    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        JsExpr condJsExpr =
            jsExprTranslator.translateToJsExpr(
                icn.getExprUnion(), localVarTranslations, errorReporter);
        if (icn.getCommandName().equals("if")) {
          jsCodeBuilder.appendLine("if (", condJsExpr.getText(), ") {");
        } else {  // "elseif" block
          jsCodeBuilder.appendLine("} else if (", condJsExpr.getText(), ") {");
        }

        jsCodeBuilder.increaseIndent();
        visit(icn);
        jsCodeBuilder.decreaseIndent();

      } else if (child instanceof IfElseNode) {
        IfElseNode ien = (IfElseNode) child;

        jsCodeBuilder.appendLine("} else {");

        jsCodeBuilder.increaseIndent();
        visit(ien);
        jsCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }

    jsCodeBuilder.appendLine("}");
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
              jsExprTranslator.translateToJsExpr(caseExpr, localVarTranslations, errorReporter);
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
  private String coerceTypeForSwitchComparison(
      @Nullable ExprRootNode v2Expr,
      @Nullable String v1Expr) {
    String jsExpr =
        jsExprTranslator
            .translateToJsExpr(v2Expr, v1Expr, localVarTranslations, errorReporter)
            .getText();
    if (v2Expr != null) {
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
    } else {
      // soy v1, do nothing
      return jsExpr;
    }
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
    JsExpr dataRefJsExpr =
        jsExprTranslator.translateToJsExpr(
            node.getExpr(), node.getExprText(), localVarTranslations, errorReporter);
    highLevelJsCodeBuilder
        .declareVariable(listVarName)
        .withValue(dataRefJsExpr.getText())
        .declareVariable(listLenVarName)
        .withValue(listVarName + ".length");

    // If has 'ifempty' node, add the wrapper 'if' statement.
    boolean hasIfemptyNode = node.numChildren() == 2;
    ConditionBuilder condition = hasIfemptyNode
        ? highLevelJsCodeBuilder._if(listLenVarName + " > 0")
        : null;

    // Generate code for nonempty case.
    visit(nonEmptyNode);

    // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
    if (hasIfemptyNode) {
      condition._else();
      // Generate code for empty case.
      visit(node.getChild(1));
      condition.endif();
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

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, JsExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(
        baseVarName, new JsExpr(dataVarName, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isFirst",
        new JsExpr(indexVarName + " == 0", Operator.EQUAL.getPrecedence()));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isLast",
        new JsExpr(indexVarName + " == " + listLenVarName + " - 1",
            Operator.EQUAL.getPrecedence()));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__index", new JsExpr(indexVarName, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

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
                .translateToJsExpr(range.increment().get(), localVarTranslations, errorReporter)
                .getText()
            : "1" /* default */;
    String initJsExprText =
        range.start().isPresent()
            ? jsExprTranslator
                .translateToJsExpr(range.start().get(), localVarTranslations, errorReporter)
                .getText()
            : "0" /* default */;
    String limitJsExprText =
        jsExprTranslator
            .translateToJsExpr(range.limit(), localVarTranslations, errorReporter)
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

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, JsExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(varName, new JsExpr(varName + nodeId, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

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
    JsExpr callExpr =
        genCallCodeUtils.genCallExpr(node, localVarTranslations, templateAliases, errorReporter);
    jsCodeBuilder.addToOutputVar(ImmutableList.of(callExpr));
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {

    // This node should only be visited when it's not computable as JS expressions, because this
    // method just generates the code to define the temporary 'param<n>' variable.
    if (isComputableAsJsExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'param<n>' when not computable as JS expressions.");
    }

    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
    jsCodeBuilder.pushOutputVar("param" + node.getId());

    visitChildren(node);

    jsCodeBuilder.popOutputVar();
    localVarTranslations.pop();
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
      List<JsExpr> logMsgJsExprs = genJsExprsVisitor.execOnChildren(node);
      JsExpr logMsgJsExpr = JsExprUtils.concatJsExprs(logMsgJsExprs);
      jsCodeBuilder.appendLine("window.console.log(", logMsgJsExpr.getText(), ");");

    } else {
      // Must build log msg in a local var logMsg_s##.
      localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
      jsCodeBuilder.pushOutputVar("logMsg_s" + node.getId());

      visitChildren(node);

      jsCodeBuilder.popOutputVar();
      localVarTranslations.pop();

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
        localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
        visitChildren((BlockNode) node);
        localVarTranslations.pop();

      } else {
        visitChildren((ParentSoyNode<?>) node);
      }

      return;
    }

    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Simply generate JS expressions for this node and add them to the current output var.
      jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));

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
  private void genParamsRecordType(TemplateNode node) {
    Set<String> paramNames = Sets.newHashSet();

    // Generate members for explicit params.
    StringBuilder sb = new StringBuilder();
    sb.append(" * @param {{");
    boolean first = true;
    for (TemplateParam param : node.getParams()) {
      if (first) {
        first = false;
      } else {
        sb.append(",");
      }
      sb.append("\n *    ");
      sb.append(genParamAlias(param.name()));
      sb.append(": ");
      String jsType = genParamTypeExpr(param.type());
      if (jsType.equals("?")) {
        // Workaround for jscompiler bug.
        jsType = "(?)";
      }
      sb.append(jsType);
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
        if (first) {
          first = false;
        } else {
          sb.append(",");
        }
        sb.append("\n *    ");
        sb.append(genParamAlias(indirectParamName));
        sb.append(": ");
        sb.append(genParamTypeExpr(indirectParamType));
      }
    }
    sb.append("\n * }} opt_data");
    jsCodeBuilder.appendLine(sb.toString());
  }

  /**
   * Generate the type expression for a single Soy parameter.
   */
  private String genParamTypeExpr(SoyType type) {
    return JsSrcUtils.getJsTypeExpr(type, true, true);
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
      String paramVal = TranslateToJsExprVisitor.genCodeForParamAccess(
          paramName, param.isInjected(), paramType);
      String paramAlias = genParamAlias(paramName);
      boolean isAliasedLocalVar = false;
      switch (paramType.getKind()) {
        case ANY:
        case UNKNOWN:
          // Do nothing
          break;

        case STRING:
          genParamTypeChecksUsingGeneralAssert(
              paramName, paramAlias, paramVal, param.isInjected(), paramType,
              "goog.isString({0}) || ({0} instanceof goog.soy.data.SanitizedContent)",
              "string|goog.soy.data.SanitizedContent");
          isAliasedLocalVar = true;
          break;

        case BOOL:
          genParamTypeChecksUsingGeneralAssert(
              paramName, paramAlias, "!!" + paramVal, param.isInjected(), paramType,
              "goog.isBoolean({0}) || {0} === 1 || {0} === 0",
              "boolean");
          isAliasedLocalVar = true;
          break;

        case INT:
        case FLOAT:
        case LIST:
        case RECORD:
        case MAP: {
          String assertionFunction = null;
          switch (param.type().getKind()) {
            case INT:
            case FLOAT:
              assertionFunction = "goog.asserts.assertNumber";
              break;

            case LIST:
              assertionFunction = "goog.asserts.assertArray";
              break;

            case RECORD:
            case MAP:
              assertionFunction = "goog.asserts.assertObject";
              break;

            default:
              throw new AssertionError();
          }

          jsCodeBuilder.appendLine(
              "var " + paramAlias + " = " + assertionFunction
              + "(" + paramVal + ", \"expected parameter '" + paramName + "' of type "
              + param.type() + ".\");");
          isAliasedLocalVar = true;
          break;
        }

        case OBJECT:
          if (param.type() instanceof SoyProtoType) {
            // Detect if it's a map that has been created from a proto, and
            // if so extract the proto value.
            paramVal = extractProtoFromMap(paramVal);
          }
          jsCodeBuilder.appendLine("var " + paramName +
              " = goog.asserts.assertInstanceof(" + paramVal + ", " +
              getJsTypeName(param.type()) + ", \"expected parameter '" + paramName +
              "' of type " + getJsTypeName(param.type()) + ".\");");
          isAliasedLocalVar = true;
          break;

        case ENUM:
          jsCodeBuilder.appendLine(
              "var " + paramAlias + " = goog.asserts.assertNumber("
              + paramVal + ", \"expected param '" + paramName + "' of type "
              + param.type() + ".\");");
          isAliasedLocalVar = true;
          break;

        case UNION:
          UnionType unionType = (UnionType) param.type();
          if (containsProtoObjectType(unionType)) {
            paramVal = extractProtoFromMap(paramVal);
          }
          genParamTypeChecksUsingGeneralAssert(
              paramName, paramAlias, paramVal, param.isInjected(), paramType,
              genUnionTypeTests(unionType),
              JsSrcUtils.getJsTypeExpr(param.type(), false, false));
          isAliasedLocalVar = true;
          break;

        default:
          if (param.type() instanceof SanitizedType) {
            String typeName = getJsTypeName(param.type());
            // We allow string or unsanitized type to be passed where a
            // sanitized type is specified - it just means that the text will
            // be escaped.
            genParamTypeChecksUsingGeneralAssert(
                paramName, paramAlias, paramVal, param.isInjected(), paramType,
                "({0} instanceof " + typeName +
                ") || ({0} instanceof soydata.UnsanitizedText) || goog.isString({0})",
                typeName);
            isAliasedLocalVar = true;
            break;
          }

          throw new AssertionError("Unsupported type: " + param.type());
      }

      if (isAliasedLocalVar) {
        localVarTranslations.peek().put(
            paramName, new JsExpr(paramAlias, Integer.MAX_VALUE));
      }
    }
  }

  /**
   * Generate code to test an input param against each of the member types of a union.
   */
  private String genUnionTypeTests(UnionType unionType) {
    Set<String> typeTests = Sets.newTreeSet();
    boolean hasNumber = false;
    for (SoyType memberType : unionType.getMembers()) {
      switch (memberType.getKind()) {
        case ANY:
        case UNKNOWN:
          // Unions generally should not include 'any' as a member, but just in
          // case they do we should handle it. Since 'any' does not include null,
          // the test simply ensures that the value is not null.
          typeTests.add("{0} != null");
          break;

        case NULL:
          // Handled separately, see below.
          break;

        case BOOL:
          typeTests.add("goog.isBoolean({0}) || {0} === 1 || {0} === 0");
          break;

        case STRING:
          typeTests.add("goog.isString({0})");
          typeTests.add("({0} instanceof goog.soy.data.SanitizedContent)");
          break;

        case INT:
        case FLOAT:
        case ENUM:
          // Since int and float both map to number, don't do this test twice.
          if (!hasNumber) {
            typeTests.add("goog.isNumber({0})");
            hasNumber = true;
          }
          break;

        case LIST:
          typeTests.add("goog.isArray({0})");
          break;

        case RECORD:
        case MAP:
          typeTests.add("goog.isObject({0})");
          break;

        case OBJECT:
          String jsType = getJsTypeName(memberType);
          if (memberType instanceof SoyProtoType) {
            // Detect if it's a map that has been created from a proto, and
            // if so extract the proto value.
            if (unionType.isNullable()) {
              // Nullability test comes before this, so we can simplify the condition.
              typeTests.add("(({0}.$jspbMessageInstance || {0}) instanceof " + jsType + ")");
            } else {
              typeTests.add(extractProtoFromMap("{0}"));
            }
          } else {
            typeTests.add("({0} instanceof " + jsType + ")");
          }
          break;

        default:
          if (memberType instanceof SanitizedType) {
            // For sanitized kinds, an unwrapped string is also considered valid.
            // (It will be auto-escaped.)  But we don't want to test for this multiple
            // times if there are multiple sanitized kinds.
            typeTests.add("({0} instanceof " + getJsTypeName(memberType) + ")");
            typeTests.add("({0} instanceof soydata.UnsanitizedText)");
            typeTests.add("goog.isString({0})");
            break;
          }
          throw new AssertionError("Unsupported union member type: " + memberType);
      }
    }

    String result = Joiner.on(" || ").join(typeTests);
    // Null test needs to come first which is why it's not included in the set.
    if (unionType.isNullable()) {
      // Note that null == undefined, so this checks for undefined as well.
      result = "{0} == null || " + result;
    }
    return result;
  }

  /**
   * Generate code to check the type of a parameter using the generic assert()
   * function instead of type-specific asserts.
   * @param paramName The Soy name of the parameter.
   * @param paramAlias The name of the local variable which stores the value of the param.
   * @param paramVal The value expression of the parameter, which might be
   *     an expression in some cases but will usually be opt_params.somename.
   * @param isInjected True iff the parameter is on the {@code $ij} injected data map.
   * @param paramType The type of the parameter.
   * @param typePredicate JS which tests whether the parameter is the correct type.
   *     This is a format string - the {0} format field will be replaced with the
   *     parameter value.
   * @param jsDocTypeExpr JSDoc type expression to cast the value to if the type test
   *     succeeds.
   */
  private void genParamTypeChecksUsingGeneralAssert(
      String paramName, String paramAlias, String paramVal, boolean isInjected, SoyType paramType,
      String typePredicate, String jsDocTypeExpr) {
    // The opt_param.name value that will be type-tested.
    String paramAccessVal = TranslateToJsExprVisitor.genCodeForParamAccess(
        paramName, isInjected, paramType);
    jsCodeBuilder.appendLine(
        "soy.asserts.assertType(" + MessageFormat.format(typePredicate, paramAccessVal) +
        ", '" + paramName + "', " + paramAccessVal + ", " +
        "'" + jsDocTypeExpr + "');");

    // The type-cast expression.
    jsCodeBuilder.appendLine(
        "var " + paramAlias + " = /** @type {" + jsDocTypeExpr + "} */ (" + paramVal + ");");
  }

  /**
   * Generate a name for the local variable which will store the value of a
   * parameter, avoiding collision with JavaScript reserved words.
   */
  private String genParamAlias(String paramName) {
    return JsSrcUtils.isReservedWord(paramName) ? "param$" + paramName : paramName;
  }

  /**
   * Generates the code that extracts the original protobuf from the generated map.
   */
  private String extractProtoFromMap(String mapVal) {
    return "(" + mapVal + " && " + mapVal + ".$jspbMessageInstance || " + mapVal + ")";
  }


  /**
   * Returns true if the union contains a proto object (not enum) type.
   */
  private boolean containsProtoObjectType(UnionType unionType) {
    for (SoyType memberType : unionType.getMembers()) {
      if (memberType.getKind() == SoyType.Kind.OBJECT && memberType instanceof SoyProtoType) {
        return true;
      }
    }
    return false;
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
   * Return true if any template in this file has @inject params.
   */
  private boolean hasInjectedParams(SoyFileNode soyFile) {
    for (TemplateNode template : soyFile.getChildren()) {
      if (!template.getInjectedParams().isEmpty()) {
        return true;
      }
    }
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
    SortedSet<String> requiredObjectTypes = Sets.newTreeSet();
    FieldImportsVisitor fieldImportsVisitor
        = new FieldImportsVisitor(requiredObjectTypes);
    for (TemplateNode template : soyFile.getChildren()) {
      SoytreeUtils.execOnAllV2Exprs(template, fieldImportsVisitor);
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
    return requiredObjectTypes;
  }

  private static boolean shouldGenerateGoogRequire(SoyType type) {
    return type.getKind() == SoyType.Kind.OBJECT
        || (type.getKind() == SoyType.Kind.ENUM && type instanceof SoyProtoType);
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
      extractImportsFromType(baseType, node.getFieldName(), SoyBackendKind.JS_SRC);
      visit(node.getBaseExprChild());
    }

    /**
     * Finds imports required for a field access looking through aggregate
     * types.
     */
    private void extractImportsFromType(
        SoyType baseType, String fieldName, SoyBackendKind backendKind) {
      if (baseType instanceof SoyObjectType) {
        Set<String> importedSymbols = ((SoyObjectType) baseType).getFieldAccessImports(
            fieldName, backendKind);
        imports.addAll(importedSymbols);
      } else {
        // TODO: Is there any way to fold over sub-types of aggregate types?
        if (baseType instanceof UnionType) {
          for (SoyType memberBaseType : ((UnionType) baseType).getMembers()) {
            extractImportsFromType(memberBaseType, fieldName, backendKind);
          }
        }
      }
    }
  }
}
