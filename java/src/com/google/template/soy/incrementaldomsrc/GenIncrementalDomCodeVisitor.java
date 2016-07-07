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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.html.HtmlAttributeNode;
import com.google.template.soy.html.HtmlCloseTagNode;
import com.google.template.soy.html.HtmlDefinitions;
import com.google.template.soy.html.HtmlOpenTagEndNode;
import com.google.template.soy.html.HtmlOpenTagNode;
import com.google.template.soy.html.HtmlOpenTagStartNode;
import com.google.template.soy.html.HtmlVoidTagNode;
import com.google.template.soy.incrementaldomsrc.GenIncrementalDomExprsVisitor.GenIncrementalDomExprsVisitorFactory;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.CanInitOutputVarVisitor;
import com.google.template.soy.jssrc.internal.GenCallCodeUtils;
import com.google.template.soy.jssrc.internal.GenDirectivePluginRequiresVisitor;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitorAssistantForMsgs;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor;
import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.jssrc.internal.JsExprTranslator;
import com.google.template.soy.jssrc.internal.JsSrcUtils;
import com.google.template.soy.jssrc.internal.TemplateAliases;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.shared.internal.CodeBuilder;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeOps;

import java.util.Deque;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Generates a series of JavaScript control statements and function calls for rendering one or more
 * templates as HTML. This heavily leverages {@link GenJsCodeVisitor}, adding logic to print the
 * function calls and changing how statements are combined.
 */
public final class GenIncrementalDomCodeVisitor extends GenJsCodeVisitor {

  private static final SoyErrorKind PRINT_ATTR_INVALID_KIND =
      SoyErrorKind.of("For Incremental DOM, '{print}' statements in attributes context can only be "
          + "of kind attributes (since they must compile to semantic attribute declarations).  {0} "
          + "is not allowed.");

  private static final SoyErrorKind PRINT_ATTR_INVALID_VALUE =
      SoyErrorKind.of(
          "Attribute values that cannot be evalutated to simple expressions are not yet supported "
              + "for Incremental DOM code generation.");

  private static final SoyErrorKind NULL_COALESCING_NON_EMPTY =
      SoyErrorKind.of(
          "The only supported conditional for attribute and HTML values in incremental DOM is "
          + "'{'$value ?: '''''}'.  The right operand must be empty.");

  private static final String NAMESPACE_EXTENSION = ".incrementaldom";
  private static final String KEY_ATTRIBUTE_NAME = "key";
  private static int idGenerator = 0;

  @Inject
  GenIncrementalDomCodeVisitor(
      SoyJsSrcOptions jsSrcOptions,
      JsExprTranslator jsExprTranslator,
      IncrementalDomDelTemplateNamer incrementalDomDelTemplateNamer,
      IncrementalDomGenCallCodeUtils genCallCodeUtils,
      IsComputableAsIncrementalDomExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenIncrementalDomExprsVisitorFactory genIncrementalDomExprsVisitorFactory,
      GenDirectivePluginRequiresVisitor genDirectivePluginRequiresVisitor,
      SoyTypeOps typeOps) {
    super(
        jsSrcOptions,
        jsExprTranslator,
        incrementalDomDelTemplateNamer,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        genIncrementalDomExprsVisitorFactory,
        genDirectivePluginRequiresVisitor,
        typeOps);
  }

  @Override protected CodeBuilder<JsExpr> createCodeBuilder() {
    return new IncrementalDomCodeBuilder();
  }

  @Override protected IncrementalDomCodeBuilder getJsCodeBuilder() {
    return (IncrementalDomCodeBuilder) super.getJsCodeBuilder();
  }

  /**
   * Changes module namespaces, adding an extension of '.incrementaldom' to allow it to co-exist
   * with templates generated by jssrc.
   */
  @Override protected String getGoogModuleNamespace(String soyNamespace) {
    return soyNamespace + NAMESPACE_EXTENSION;
  }

  @Override protected void addCodeToRequireGeneralDeps(SoyFileNode soyFile) {
    super.addCodeToRequireGeneralDeps(soyFile);
    // Need to make sure goog.asserts is pulled in because there may be some cases (e.g.
    // no templates with parameters) where the js code generation does not pull it in. This is
    // required for generating calls to itext().
    addGoogRequire("goog.asserts", true /* suppressExtra */);
    addGoogRequire("goog.string", true /* suppressExtra */);

    getJsCodeBuilder().appendLine("var IncrementalDom = goog.require('incrementaldom');")
      .appendLine("var ie_open = IncrementalDom.elementOpen;")
      .appendLine("var ie_close = IncrementalDom.elementClose;")
      .appendLine("var ie_void = IncrementalDom.elementVoid;")
      .appendLine("var ie_open_start = IncrementalDom.elementOpenStart;")
      .appendLine("var ie_open_end = IncrementalDom.elementOpenEnd;")
      .appendLine("var itext = IncrementalDom.text;")
      .appendLine("var iattr = IncrementalDom.attr;");
  }

  @Override
  protected String getJsTypeName(SoyType type) {
    return IncrementalDomSrcUtils.getJsTypeName(type);
  }

  @Override protected String getTemplateReturnType(TemplateNode node) {
    // TODO(sparhami) need to deal with URI types properly (like the JS code gen does) so that the
    // usage is safe. For now, don't include any return type so compilation will fail if someone
    // tries to create a template of kind="uri".
    if (node.getContentKind() == ContentKind.TEXT) {
      return "string";
    }

    // This template does not return any content but rather contains Incremental DOM instructions.
    return "void";
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    getJsCodeBuilder().setContentKind(node.getContentKind());
    super.visitTemplateNode(node);
  }

  @Override protected void generateFunctionBody(TemplateNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    boolean isTextTemplate = isTextContent(node.getContentKind());
    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());

    // Note: we do not try to combine this into a single return statement if the content is
    // computable as a JsExpr. A JavaScript compiler, such as Closure Compiler, is able to perform
    // the transformation.
    if (isTextTemplate) {
      jsCodeBuilder.appendLine("var output = '';");
      jsCodeBuilder.pushOutputVar("output");
    }

    genParamTypeChecks(node);
    visitChildren(node);

    if (isTextTemplate) {
      jsCodeBuilder.appendLine("return output;");
      jsCodeBuilder.popOutputVar();
    }

    localVarTranslations.pop();
  }

  /**
   * Visits the children of a ParentSoyNode. This function is overridden to not do all of the work
   * that {@link GenJsCodeVisitor} does.
   */
  @Override protected void visitChildren(ParentSoyNode<?> node) {
    for (SoyNode child : node.getChildren()) {
      visit(child);
    }
  }

  /**
   * Generates the content of a {@code let} or {@code param} statement. For HTML and attribute
   * let/param statements, the generated instructions inside the node are wrapped in a function
   * which will be optionally passed to another template and invoked in the correct location. All
   * other kinds of let statements are generated as a simple variable.
   */
  private void visitLetParamContentNode(RenderUnitNode node, String generatedVarName) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    ContentKind prevContentKind = jsCodeBuilder.getContentKind();

    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
    jsCodeBuilder.pushOutputVar(generatedVarName);
    jsCodeBuilder.setContentKind(node.getContentKind());

    // The html transform step, performed by HTMLTransformVisitor, ensures that
    // we always have a content kind specified.
    Preconditions.checkState(node.getContentKind() != null);

    switch(node.getContentKind()) {
      case HTML:
      case ATTRIBUTES:
        jsCodeBuilder.appendLine("var " + generatedVarName, " = function() {");
        jsCodeBuilder.increaseIndent();
        visitChildren(node);
        jsCodeBuilder.decreaseIndent();
        jsCodeBuilder.appendLine("};");
        break;
      default:
        jsCodeBuilder.appendLine("var ", generatedVarName, " = '';");
        visitChildren(node);
        break;
    }

    jsCodeBuilder.setContentKind(prevContentKind);
    jsCodeBuilder.popOutputVar();
    localVarTranslations.pop();
  }

  /**
   * Generates the content of a {@code let} statement. For HTML and attribute let statements, the
   * generated instructions inside the node are wrapped in a function which will be optionally
   * passed to another template and invoked in the correct location. All other kinds of let/param
   * statements are generated as a simple variable.
   */
  @Override protected void visitLetContentNode(LetContentNode node) {
    // TODO(slaks): Call base class for non-HTML to get {msg} inlining.
    String generatedVarName = node.getUniqueVarName();
    visitLetParamContentNode(node, generatedVarName);
    localVarTranslations.peek().put(
        node.getVarName(), new JsExpr(generatedVarName, Integer.MAX_VALUE));
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    String generatedVarName = "param" + node.getId();
    visitLetParamContentNode(node, generatedVarName);
  }

  @Override protected void visitCallNode(CallNode node) {
    // If this node has any CallParamContentNode children those contents are not computable as JS
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJsExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    JsExpr callExpr =
        genCallCodeUtils.genCallExpr(node, localVarTranslations, templateAliases, errorReporter);
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    ContentKind currentContentKind = jsCodeBuilder.getContentKind();

    switch (currentContentKind) {
      case ATTRIBUTES:
        // Invoke the function to run the Incremental DOM attribute declarations that it contains.
        jsCodeBuilder.appendLine(callExpr.getText() + ";");
        break;
      case HTML:
        Optional<ContentKind> kind = templateRegistry.getCallContentKind(node);
        // We are in a type of compilation where we don't have information on external templates
        // such as dynamic recompilation.
        if (!kind.isPresent()) {
          generateDynamicTextCall(callExpr.getText());
        } else if (isTextContent(kind.get())) {
          generateTextCall(callExpr.getText());
        } else {
          jsCodeBuilder.appendLine(callExpr.getText() + ";");
        }
        break;
      case JS:
      case URI:
      case TRUSTED_RESOURCE_URI:
      case CSS:
      case TEXT:
        // If the current content kind (due to a let, param or template) is a text-like, simply
        // concatentate the result of the call to the current output variable.
        jsCodeBuilder.addToOutputVar(ImmutableList.of(callExpr));
        break;
    }
  }

  /**
   * Generates calls in HTML/Attributes content as non-JsExprs, since Incremental DOM instructions
   * are needed and not a JavaScript expression.
   */
  @Override protected void visitIfNode(IfNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    ContentKind currentContentKind = jsCodeBuilder.getContentKind();

    if (currentContentKind == ContentKind.ATTRIBUTES || currentContentKind == ContentKind.HTML) {
      super.generateNonExpressionIfNode(node);
    } else {
      super.visitIfNode(node);
    }
  }

  /**
   * Generates a call to generate a text node, asserting that the value generated by the expression
   * is not null. Generates code that looks like:
   *
   * <pre>
   *   itext((goog.asserts.assert((foo) != null), foo));
   * </pre>
   *
   * <p>
   * If asserts are enabled, the expression evaluates to `foo`, as expressions in JavaScript
   * evaluate to the right most comma-delimited part.
   * </p><p>
   * If asserts are not enabled and the assert part of the expression is dropped by a JavaScript
   * compiler (e.g. Closure Compiler), then the expression simply becomes `foo`.
   * </p>
   */
  private void generateTextCall(String exprText) {
    String text = "(goog.asserts.assert((" + exprText + ") != null), " + exprText + ")";
    getJsCodeBuilder().appendLine("itext(", text, ");");
  }

  /**
   * Executes the expression and, if the return value exists, will execute it within an itext.
   * @param exprText
   */
  private void generateDynamicTextCall(String exprText) {
    // TODO(sparhami): Make an idom version of soyutils.js and move this logic there.
    // You should probably either make generateTextCall always call that, or add a boolean isDynamic
    // parameter.
    String dynamicString = "dyn" + (idGenerator++);
    getJsCodeBuilder().appendLine("var " + dynamicString + " = " + exprText + ";");
    getJsCodeBuilder().appendLine("if (typeof " + dynamicString + " == 'function') "
        + dynamicString + "(); "
        + "else if (" + dynamicString + " != null) itext(" + dynamicString + ");");
  }

  /**
   * Determines if a given type of content represents text or some sort of HTML.
   * @param contentKind The kind of content to check.
   * @return True if the content represents text, false otherwise.
   */
  private boolean isTextContent(ContentKind contentKind) {
    return contentKind != ContentKind.HTML && contentKind != ContentKind.ATTRIBUTES;
  }

  /**
   * Prints both the static and dynamic attributes for the current node.
   * @param attributes
   */
  private void printStaticAndDynamicAttributes(List<StandaloneNode> attributes) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();

    // For now, no separating of static and dynamic attributes
    if (!attributes.isEmpty()) {
      jsCodeBuilder.append(", null");
      jsCodeBuilder.increaseIndent();
      jsCodeBuilder.appendLineEnd(",");
      printAttributeList(attributes);
      jsCodeBuilder.decreaseIndent();
    }
  }

  /**
   * Prints a list of attribute values, concatenating the results together
   * @param node The node containing the attribute values
   */
  private void printAttributeValues(HtmlAttributeNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    List<StandaloneNode> children = node.getChildren();

    if (children.isEmpty()) {
      // No attribute value, e.g. "<button disabled></button>". Need to put an empty string so that
      // the runtime knows to create an attribute.
      jsCodeBuilder.append("''");
    } else {
      if (!isComputableAsJsExprsVisitor.execOnChildren(node)) {
        errorReporter.report(node.getSourceLocation(), PRINT_ATTR_INVALID_VALUE);
        return;
      }

      jsCodeBuilder.addToOutput(genJsExprsVisitor.execOnChildren(node));
    }
  }

  /**
   * Prints one or more attributes as a comma separated list off attribute name, attribute value
   * pairs on their own line. This looks like:
   *
   * <pre>
   *     'attr1', 'value1',
   *     'attr2', 'value2'
   * </pre>
   *
   * @param attributes The attributes to print
   */
  private void printAttributeList(List<StandaloneNode> attributes) {
    if (attributes.isEmpty()) {
       return;
    }

    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();

    jsCodeBuilder.increaseIndent();
    int lastIndex = attributes.size() - 1;
    for (int i = 0; i < lastIndex; ++i) {
      printAttribute(attributes.get(i));
      jsCodeBuilder.appendLineEnd(",");
    }
    printAttribute(attributes.get(lastIndex));
    jsCodeBuilder.decreaseIndent();
  }

  private void printAttribute(StandaloneNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    // The children of an HtmlOpenTagNode and HtmlVoidTagNode are always HtmlAttributeNodes. Since
    // HtmlAttributeNodes are StandaloneNodes, their parents must be have children of type
    // StandaloneNode.
    HtmlAttributeNode htmlAttributeNode = (HtmlAttributeNode) node;
    jsCodeBuilder.appendLineStart("'", htmlAttributeNode.getName(), "', ");
    printAttributeValues(htmlAttributeNode);
  }

  /**
   * Emits a close tag. For example:
   *
   * <pre>
   * &lt;ie_close('div');&gt;
   * </pre>
   */
  private void emitClose(String tagName) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("ie_close('", tagName, "');");
  }

  /**
   * Visits the {@link HtmlAttributeNode}, this only occurs when we have something like:
   *
   * <pre>
   * &lt;div {if $condition}attr="value"{/if}&gt;
   * </pre>
   *
   * or in a let/param of kind attributes, e.g.
   *
   * <pre>
   * {let $attrs kind="attributes"}
   *   attr="value"
   * {/let}
   * </pre>
   *
   * If no attributes are conditional, then the HtmlAttributeNode will be a child of the
   * corresponding {@link HtmlOpenTagNode}/{@link HtmlVoidTagNode} and will not be visited directly.
   * Note that the value itself could still be conditional in that case.
   *
   * <pre>
   * &lt;div disabled="{if $disabled}true{else}false{/if}"&gt;
   * </pre>
   *
   * This method prints the attribute declaration calls. For example, it would print the call to
   * iattr from the first example, resulting in:
   *
   * <pre>
   * if (condition) {
   *   iattr(attr, "value");
   * }
   * </pre>
   */
  @Override protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    jsCodeBuilder.appendLineStart("iattr('", node.getName(), "', ");
    printAttributeValues(node);
    jsCodeBuilder.appendLineEnd(");");
  }

  /**
   * Visits an {@link HtmlOpenTagNode}, which occurs when an HTML tag is opened with no conditional
   * attributes. For example:
   * <pre>
   * &lt;div attr="value" attr2="{$someVar}"&gt;...&lt;/div&gt;
   * </pre>
   * generates
   * <pre>
   * ie_open('div', null,
   *     'attr', 'value',
   *     'attr2', someVar);
   * </pre>
   */
  @Override protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    Optional<String> keyValue = getKeyNodeValue(node);
    List<StandaloneNode> attributes = node.getChildren();
    if (!keyValue.isPresent() && attributes.isEmpty()) {
      jsCodeBuilder.appendLineStart("ie_open('", node.getTagName(), "'");
    } else {
      jsCodeBuilder.appendLineStart("ie_open('", node.getTagName(), "', ", keyValue.or("null"));
      printStaticAndDynamicAttributes(attributes);
    }
    jsCodeBuilder.appendLineEnd(");");
    jsCodeBuilder.increaseIndent();

    if (HtmlDefinitions.HTML5_VOID_ELEMENTS.contains(node.getTagName())) {
      emitClose(node.getTagName());
    }
  }

  /**
   * Gets the 'key' for an element to use in Incremental DOM to be used in the ie_open or
   * ie_void calls.
   * <pre>
   * &lt;div key="test" /div&gt;
   * </pre>
   * generates
   * <pre>
   * ie_void('div', 'test')
   * </pre>
   * @param parentNode The SoyNode representing the parent.
   * @return An optional string containing the JavaScript expression to retrieve the key.
   */
  private Optional<String> getKeyNodeValue(ParentNode<StandaloneNode> parentNode) {
    for (StandaloneNode childNode : parentNode.getChildren()) {
      if (!(childNode instanceof HtmlAttributeNode)) {
        continue;
      }

      HtmlAttributeNode htmlAttributeNode = (HtmlAttributeNode) childNode;
      if (htmlAttributeNode.getName().equals(KEY_ATTRIBUTE_NAME)) {
        Preconditions.checkState(
            isComputableAsJsExprsVisitor.execOnChildren(htmlAttributeNode),
            "Attribute values that cannot be evalutated to simple expressions is not yet supported "
                + "for Incremental DOM code generation");
        List<JsExpr> jsExprs = genJsExprsVisitor.execOnChildren(htmlAttributeNode);
        return Optional.of(JsExprUtils.concatJsExprs(jsExprs).getText());
      }
    }
    return Optional.absent();
  }

  /**
   * Visits an {@link HtmlCloseTagNode}, which occurs when an HTML tag is closed. For example:
   * <pre>
   * &lt;/div&gt;
   * </pre>
   * generates
   * <pre>
   * ie_close('div');
   * </pre>
   *
   */
  @Override protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
    if (!HtmlDefinitions.HTML5_VOID_ELEMENTS.contains(node.getTagName())) {
      emitClose(node.getTagName());
    }
  }

  /**
   * Visits an {@link HtmlOpenTagStartNode}, which occurs at the end of an open tag containing
   * children that are not {@link HtmlAttributeNode}s. For example,
   *
   * <pre>
   * &lt;div {$attrs} attr="value"&gt;
   * </pre>
   * The opening bracket and tag translate to
   * <pre>
   * ie_open_start('div');
   * </pre>
   */
  @Override protected void visitHtmlOpenTagStartNode(HtmlOpenTagStartNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    jsCodeBuilder.appendLine("ie_open_start('", node.getTagName(), "');");
    jsCodeBuilder.increaseIndentTwice();
    jsCodeBuilder.setContentKind(ContentKind.ATTRIBUTES);
  }

  /**
   * Visits an {@link HtmlOpenTagEndNode}, which occurs at the end of an open tag containing
   * children that are not {@link HtmlAttributeNode}s. For example,
   *
   * <pre>
   * &lt;div {$attrs} attr="value"&gt;
   * </pre>
   * The closing bracket translates to
   * <pre>
   * ie_open_end();
   * </pre>
   */
  @Override protected void visitHtmlOpenTagEndNode(HtmlOpenTagEndNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    jsCodeBuilder.decreaseIndentTwice();
    jsCodeBuilder.appendLine("ie_open_end();");
    jsCodeBuilder.increaseIndent();
    jsCodeBuilder.setContentKind(ContentKind.HTML);

    if (HtmlDefinitions.HTML5_VOID_ELEMENTS.contains(node.getTagName())) {
      emitClose(node.getTagName());
    }
  }

  /**
   * Visits an {@link HtmlVoidTagNode}, which is equivalent to an {@link HtmlOpenTagNode} followed
   * immediately by an {@link HtmlCloseTagNode}
   *
   * Example:
   * <pre>
   *   &lt;div attr="value" attr2="{$someVar}"&gt;&lt;/div&gt;
   * </pre>
   * generates
   * <pre>
   *   ie_void('div', null,
   *       'attr', 'value',
   *       'attr2', someVar);
   * </pre>
   */
  @Override protected void visitHtmlVoidTagNode(HtmlVoidTagNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    Optional<String> keyValue = getKeyNodeValue(node);
    List<StandaloneNode> attributes = node.getChildren();
    if (!keyValue.isPresent() && attributes.isEmpty()) {
      jsCodeBuilder.appendLineStart("ie_void('", node.getTagName(), "'");
    } else {
      jsCodeBuilder.appendLineStart("ie_void('", node.getTagName(), "', ", keyValue.or("null"));
      printStaticAndDynamicAttributes(attributes);
    }
    jsCodeBuilder.appendLineEnd(");");
  }

  /**
   * Visits a {@link RawTextNode}, which occurs either as a child of any BlockNode or the 'child'
   * of an HTML tag. Note that in the soy tree, tags and their logical HTML children do not have a
   * parent-child relationship, but are rather siblings. For example:
   * <pre>
   * &lt;div&gt;Hello world&lt;/div&gt;
   * </pre>
   * The text "Hello world" translates to
   * <pre>
   * itext('Hello world');
   * </pre>
   */
  @Override protected void visitRawTextNode(RawTextNode node) {
    String text = BaseUtils.escapeToSoyString(node.getRawText(), true);
    if (node.getHtmlContext() == HtmlContext.HTML_PCDATA) {
      // Note - we don't use generateTextCall since this text can never be null.
      getJsCodeBuilder().appendLine("itext(", text, ");");
    } else {
      getJsCodeBuilder().addToOutputVar(ImmutableList.of(new JsExpr(text, Integer.MAX_VALUE)));
    }
  }

  /**
   * Visit an {@link PrintNode}, with special cases for a variable being printed within an attribute
   * declaration or as HTML content.
   * <p>
   * For attributes, if the variable is of kind attributes, it is invoked. Any other kind of
   * variable is an error.
   * </p>
   * <p>
   * For HTML, if the variable is of kind HTML, it is invoked. Any other kind of variable gets
   * wrapped in a call to {@code itext}, resulting in a Text node.
   * </p>
   */
  @Override protected void visitPrintNode(PrintNode node) {
    ExprUnion exprUnion = node.getExprUnion();
    ExprRootNode expr = exprUnion.getExpr();
    List<ExprNode> exprNodes = expr.getChildren();
    ExprNode firstNode = exprNodes.get(0);

    // TODO(sparhami): Raise an error if there are any directives.
    switch (node.getHtmlContext()) {
      case HTML_TAG:
        if (tryGenerateFunctionCall(SoyType.Kind.ATTRIBUTES, firstNode)
            == GenerateFunctionCallResult.INDIRECT_NODE) {
          // Inside an HTML tag, we cannot emit indirect calls (like itext); the only valid commands
          // are idom iattr() calls (which direct ATTRIBUTES functions will call).  If we can't emit
          // the print node as a direct call, give up and report an error.
          errorReporter.report(node.getSourceLocation(), PRINT_ATTR_INVALID_KIND,
              firstNode.getType().getKind());
        }
        break;
      case HTML_PCDATA:
        // If the expression is an HTML function, generateDynamicTextCall() will call it.
        // But if we statically know that it's an HTML function, we can call it directly.
        if (tryGenerateFunctionCall(SoyType.Kind.HTML, firstNode)
            == GenerateFunctionCallResult.INDIRECT_NODE) {
          StringBuilder exprText = new StringBuilder();
          for (JsExpr jsExpr : genJsExprsVisitor.exec(node)) {
            exprText.append(jsExpr.getText());
          }
          generateDynamicTextCall(exprText.toString());
        }
        break;
      default:
        super.visitPrintNode(node);
        break;
    }
  }

  private enum GenerateFunctionCallResult {
    /** We emitted a direct call in jsCodeBuilder; no further action is necessary. */
    EMITTED,
    /** This node cannot be printed at all; we reported an error. No further action is necessary. */
    ILLEGAL_NODE,
    /** This node cannot be called directly, but it might be printable as dynamic text. */
    INDIRECT_NODE
  }

  /**
   * Emits a call to a value of type ATTRIBUTES or HTML, which is actually a JS function. Currently,
   * the only supported expressions for this operation are direct variable references and {X ?: ''}.
   * @param expectedKind The kind of content that the expression must match.
   */
  private GenerateFunctionCallResult tryGenerateFunctionCall(
      SoyType.Kind expectedKind, ExprNode expr) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();

    if (expr instanceof VarRefNode && expr.getType().getKind() == expectedKind) {
      VarRefNode varRefNode = (VarRefNode) expr;
      String varName = JsSrcUtils.getVariableName(varRefNode.getName(), localVarTranslations);
      jsCodeBuilder.appendLine(varName, "();");
      return GenerateFunctionCallResult.EMITTED;
    }

    if (!(expr instanceof NullCoalescingOpNode)) {
        return GenerateFunctionCallResult.INDIRECT_NODE;
    }

    // ResolveExpressionTypesVisitor will resolve {$attributes ?: ''} to String because '' is not of
    // type ATTRIBUTES.  Therefore, we must check the type of the first operand, not the whole node.
    NullCoalescingOpNode opNode = (NullCoalescingOpNode) expr;
    if (!(opNode.getLeftChild() instanceof VarRefNode)
        || !(opNode.getRightChild() instanceof StringNode)
        || opNode.getLeftChild().getType().getKind() != expectedKind) {
      return GenerateFunctionCallResult.INDIRECT_NODE;
    }
    if (!((StringNode) opNode.getRightChild()).getValue().isEmpty()) {
      errorReporter.report(expr.getSourceLocation(), NULL_COALESCING_NON_EMPTY);
      return GenerateFunctionCallResult.ILLEGAL_NODE;
    }
    VarRefNode varRefNode = (VarRefNode) opNode.getLeftChild();
    String varName = JsSrcUtils.getVariableName(varRefNode.getName(), localVarTranslations);
    jsCodeBuilder.appendLine("if (", varName, ") ", varName, "();");
    return GenerateFunctionCallResult.EMITTED;
  }

  @Override protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    String msgExpression;
    switch(node.getHtmlContext()) {
      case HTML_PCDATA:
        new AssistantForHtmlMsgs(this /* master */, jsSrcOptions, jsExprTranslator,
                genCallCodeUtils, isComputableAsJsExprsVisitor, getJsCodeBuilder(),
                localVarTranslations, templateAliases, genJsExprsVisitor, errorReporter)
            .generateMsgGroupCode(node);
        break;
      // Messages in attribute values are plain text. However, since the translated content includes
      // entities (because other Soy backends treat these messages as HTML source), we must unescape
      // the translations before passing them to the idom APIs.
      case HTML_NORMAL_ATTR_VALUE:
        msgExpression = new AssistantForAttributeMsgs(this /* master */, jsSrcOptions,
                jsExprTranslator, genCallCodeUtils, isComputableAsJsExprsVisitor,
                getJsCodeBuilder(), localVarTranslations, templateAliases, genJsExprsVisitor,
            errorReporter)
            .generateMsgGroupVariable(node);
        msgExpression = "goog.string.unescapeEntities(" + msgExpression + ")";
        getJsCodeBuilder().addToOutputVar(ImmutableList.of(
            new JsExpr(msgExpression, Integer.MAX_VALUE)));
        break;
      default:
        msgExpression = getAssistantForMsgs().generateMsgGroupVariable(node);
        getJsCodeBuilder().addToOutputVar(ImmutableList.of(
            new JsExpr(msgExpression, Integer.MAX_VALUE)));
        break;
    }
  }
  
  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    visitChildren(node);
  }

  /**
   * Handles <code>{msg}</code> commands in attribute context for idom.
   * The literal text in the translated message must be unescaped after translation, because we pass
   * the text directly to DOM text APIs, whereas translators write HTML with entities. Therefore, we
   * must first escape all interpolated placeholders (which can only be TEXT values).
   *
   * In non-idom, this happens in the contextual auto-escaper.
   */
  private static final class AssistantForAttributeMsgs extends GenJsCodeVisitorAssistantForMsgs {
    AssistantForAttributeMsgs(
        GenIncrementalDomCodeVisitor master,
        SoyJsSrcOptions jsSrcOptions,
        JsExprTranslator jsExprTranslator,
        GenCallCodeUtils genCallCodeUtils,
        IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
        CodeBuilder<JsExpr> jsCodeBuilder,
        Deque<Map<String, JsExpr>> localVarTranslations,
        TemplateAliases functionAliases,
        GenJsExprsVisitor genJsExprsVisitor,
        ErrorReporter errorReporter) {
      super(master, jsSrcOptions, jsExprTranslator, genCallCodeUtils, isComputableAsJsExprsVisitor,
          jsCodeBuilder, localVarTranslations, functionAliases, genJsExprsVisitor, errorReporter);
    }

    @Override
    protected JsExpr genGoogMsgPlaceholderExpr(MsgPlaceholderNode msgPhNode) {
      String expr = super.genGoogMsgPlaceholderExpr(msgPhNode).getText();
      return new JsExpr("soy.$$escapeHtml(" + expr + ")", Integer.MAX_VALUE);
    }
  }
}
