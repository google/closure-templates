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

import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_ATTR;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_ELEMENT_CLOSE;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_ELEMENT_OPEN;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_ELEMENT_OPEN_END;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_ELEMENT_OPEN_START;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_TEXT;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_PRINT;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_RENDER_DYNAMIC_CONTENT;
import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.CodeChunk.declare;
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;
import static com.google.template.soy.jssrc.dsl.CodeChunk.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_ASSERTS_ASSERT;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_STRING_UNESCAPE_ENTITIES;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_ESCAPE_HTML;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.html.HtmlDefinitions;
import com.google.template.soy.html.IncrementalHtmlAttributeNode;
import com.google.template.soy.html.IncrementalHtmlCloseTagNode;
import com.google.template.soy.html.IncrementalHtmlOpenTagNode;
import com.google.template.soy.incrementaldomsrc.GenIncrementalDomExprsVisitor.GenIncrementalDomExprsVisitorFactory;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.internal.CanInitOutputVarVisitor;
import com.google.template.soy.jssrc.internal.GenCallCodeUtils;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitorAssistantForMsgs;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor;
import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.jssrc.internal.JsCodeBuilder;
import com.google.template.soy.jssrc.internal.JsExprTranslator;
import com.google.template.soy.jssrc.internal.TemplateAliases;
import com.google.template.soy.jssrc.internal.TranslationContext;
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
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeOps;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Generates a series of JavaScript control statements and function calls for rendering one or more
 * templates as HTML. This heavily leverages {@link GenJsCodeVisitor}, adding logic to print the
 * function calls and changing how statements are combined.
 */
public final class GenIncrementalDomCodeVisitor extends GenJsCodeVisitor {

  private static final SoyErrorKind PRINT_ATTR_INVALID_KIND =
      SoyErrorKind.of(
          "For Incremental DOM, '{print}' statements in attributes context can only be "
              + "of kind attributes (since they must compile to semantic attribute declarations)."
              + "{0} is not allowed.");

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

  @Inject
  GenIncrementalDomCodeVisitor(
      SoyJsSrcOptions jsSrcOptions,
      JsExprTranslator jsExprTranslator,
      IncrementalDomDelTemplateNamer incrementalDomDelTemplateNamer,
      IncrementalDomGenCallCodeUtils genCallCodeUtils,
      IsComputableAsIncrementalDomExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenIncrementalDomExprsVisitorFactory genIncrementalDomExprsVisitorFactory,
      SoyTypeOps typeOps) {
    super(
        jsSrcOptions,
        jsExprTranslator,
        incrementalDomDelTemplateNamer,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        genIncrementalDomExprsVisitorFactory,
        typeOps);
  }

  @Override
  protected JsCodeBuilder createCodeBuilder() {
    return new IncrementalDomCodeBuilder();
  }

  @Override
  protected IncrementalDomCodeBuilder createChildJsCodeBuilder() {
    return new IncrementalDomCodeBuilder(getJsCodeBuilder());
  }

  @Override
  protected IncrementalDomCodeBuilder getJsCodeBuilder() {
    return (IncrementalDomCodeBuilder) super.getJsCodeBuilder();
  }

  /**
   * Changes module namespaces, adding an extension of '.incrementaldom' to allow it to co-exist
   * with templates generated by jssrc.
   */
  @Override
  protected String getGoogModuleNamespace(String soyNamespace) {
    return soyNamespace + NAMESPACE_EXTENSION;
  }

  @Override
  protected String getTemplateReturnType(TemplateNode node) {
    // TODO(sparhami) need to deal with URI types properly (like the JS code gen does) so that the
    // usage is safe. For now, don't include any return type so compilation will fail if someone
    // tries to create a template of kind="uri".
    if (node.getContentKind() == ContentKind.TEXT) {
      return "string";
    }

    // This template does not return any content but rather contains Incremental DOM instructions.
    return "void";
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    getJsCodeBuilder().setContentKind(node.getContentKind());
    super.visitTemplateNode(node);
  }

  @Override
  protected void generateFunctionBody(TemplateNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    boolean isTextTemplate = isTextContent(node.getContentKind());

    // Note: we do not try to combine this into a single return statement if the content is
    // computable as a JsExpr. A JavaScript compiler, such as Closure Compiler, is able to perform
    // the transformation.
    if (isTextTemplate) {
      jsCodeBuilder.appendLine("var output = '';");
      // We do our own initialization, so mark it as such.
      jsCodeBuilder.pushOutputVar("output").setOutputVarInited();
    }

    genParamTypeChecks(node);
    visitChildren(node);

    if (isTextTemplate) {
      jsCodeBuilder.appendLine("return output;");
      jsCodeBuilder.popOutputVar();
    }
  }

  /**
   * Visits the children of a ParentSoyNode. This function is overridden to not do all of the work
   * that {@link GenJsCodeVisitor} does.
   */
  @Override
  protected void visitChildren(ParentSoyNode<?> node) {
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

    // We do our own initialization, so mark it as such.
    jsCodeBuilder.pushOutputVar(generatedVarName).setOutputVarInited();
    jsCodeBuilder.setContentKind(node.getContentKind());

    // The html transform step, performed by HTMLTransformVisitor, ensures that
    // we always have a content kind specified.
    Preconditions.checkState(node.getContentKind() != null);

    switch (node.getContentKind()) {
      case HTML:
      case ATTRIBUTES:
        jsCodeBuilder.appendLine("var " + generatedVarName, " = function() {");
        jsCodeBuilder.increaseIndent();
        visitChildren(node);
        jsCodeBuilder.decreaseIndent();
        jsCodeBuilder.appendLine("};");
        break;
      default:
        jsCodeBuilder.append(declare(generatedVarName, LITERAL_EMPTY_STRING));
        visitChildren(node);
        break;
    }

    jsCodeBuilder.setContentKind(prevContentKind);
    jsCodeBuilder.popOutputVar();
  }

  /**
   * Generates the content of a {@code let} statement. For HTML and attribute let statements, the
   * generated instructions inside the node are wrapped in a function which will be optionally
   * passed to another template and invoked in the correct location. All other kinds of let/param
   * statements are generated as a simple variable.
   */
  @Override
  protected void visitLetContentNode(LetContentNode node) {
    // TODO(slaks): Call base class for non-HTML to get {msg} inlining.
    String generatedVarName = node.getUniqueVarName();
    visitLetParamContentNode(node, generatedVarName);
    templateTranslationContext
        .soyToJsVariableMappings()
        .put(node.getVarName(), id(generatedVarName));
  }

  @Override
  protected void visitCallParamContentNode(CallParamContentNode node) {
    String generatedVarName = "param" + node.getId();
    visitLetParamContentNode(node, generatedVarName);
  }

  @Override
  protected void visitCallNode(CallNode node) {
    // If this node has any CallParamContentNode children those contents are not computable as JS
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJsExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    CodeChunk.WithValue call =
        genCallCodeUtils.gen(node, templateAliases, templateTranslationContext, errorReporter);
    switch (getJsCodeBuilder().getContentKind()) {
      case ATTRIBUTES:
        getJsCodeBuilder().append(call);
        break;
      case HTML:
        Optional<ContentKind> kind = templateRegistry.getCallContentKind(node);
        // We are in a type of compilation where we don't have information on external templates
        // such as dynamic recompilation.
        if (!kind.isPresent()) {
          call = SOY_IDOM_RENDER_DYNAMIC_CONTENT.call(call);
        } else if (isTextContent(kind.get())) {
          call = generateTextCall(call);
        }
        getJsCodeBuilder().append(call);
        break;
      case JS:
      case URI:
      case TRUSTED_RESOURCE_URI:
      case CSS:
      case TEXT:
        // If the current content kind (due to a let, param or template) is a text-like, simply
        // concatentate the result of the call to the current output variable.
        getJsCodeBuilder().addChunkToOutputVar(call);
        break;
    }
  }

  /**
   * Generates calls in HTML/Attributes content as non-JsExprs, since Incremental DOM instructions
   * are needed and not a JavaScript expression.
   */
  @Override
  protected void visitIfNode(IfNode node) {
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
   *   var $tmp = foo;
   *   goog.asserts.assert($tmp != null);
   *   IncrementalDom.text($tmp);
   * </pre>
   *
   * <p>If asserts are enabled, the expression evaluates to `foo`, as expressions in JavaScript
   * evaluate to the right most comma-delimited part.
   *
   * <p>If asserts are not enabled and the assert part of the expression is dropped by a JavaScript
   * compiler (e.g. Closure Compiler), then the expression simply becomes `foo`.
   */
  private CodeChunk.WithValue generateTextCall(CodeChunk.WithValue textValue) {
    Generator cg = templateTranslationContext.codeGenerator();
    CodeChunk.WithValue var = cg.declare(textValue).ref();
    return INCREMENTAL_DOM_TEXT
        .call(var)
        .withInitialStatements(
            ImmutableList.of(
                GOOG_ASSERTS_ASSERT.call(var.doubleNotEquals(CodeChunk.WithValue.LITERAL_NULL))));
  }

  /**
   * Determines if a given type of content represents text or some sort of HTML.
   *
   * @param contentKind The kind of content to check.
   * @return True if the content represents text, false otherwise.
   */
  private boolean isTextContent(ContentKind contentKind) {
    return contentKind != ContentKind.HTML && contentKind != ContentKind.ATTRIBUTES;
  }

  /**
   * Visits the {@link IncrementalHtmlAttributeNode}. The attribute nodes will typically be children
   * of the corresponding {@link IncrementalHtmlOpenTagNode} or in a let/param of kind attributes,
   * e.g.
   *
   * <pre>
   * {let $attrs kind="attributes"}
   *   attr="value"
   * {/let}
   * </pre>
   *
   * This method prints the attribute declaration calls. For example, given
   *
   * <pre>
   * &lt;div {if $condition}attr="value"{/if}&gt;
   * </pre>
   *
   * it would print the call to {@code incrementalDom.attr}, resulting in:
   *
   * <pre>
   * if (condition) {
   *   IncrementalDom.attr(attr, "value");
   * }
   * </pre>
   */
  @Override
  protected void visitIncrementalHtmlAttributeNode(IncrementalHtmlAttributeNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    jsCodeBuilder.append(
        INCREMENTAL_DOM_ATTR.call(
            stringLiteral(node.getName()), CodeChunkUtils.concatChunks(getAttributeValues(node))));
  }

  /** Returns a list of attribute values. */
  private List<CodeChunk.WithValue> getAttributeValues(IncrementalHtmlAttributeNode node) {
    if (node.getChildren().isEmpty()) {
      // No attribute value, e.g. "<button disabled></button>". Need to put an empty string so that
      // the runtime knows to create an attribute.
      return ImmutableList.of(LITERAL_EMPTY_STRING);
    }

    if (!isComputableAsJsExprsVisitor.execOnChildren(node)) {
      errorReporter.report(node.getSourceLocation(), PRINT_ATTR_INVALID_VALUE);
      return ImmutableList.of();
    }

    return genJsExprsVisitor.execOnChildren(node);
  }

  /**
   * Visits the subtree of a node and wraps the resulting code in a pair of {@code
   * incrementalDom.elementOpenStart} and {@code incrementalDom.elementOpenEnd} calls.
   */
  private void emitOpenStartEndAndVisitSubtree(IncrementalHtmlOpenTagNode node, String tagName) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();

    List<CodeChunk.WithValue> args = new ArrayList<>();
    args.add(stringLiteral(tagName));

    CodeChunk.WithValue keyValue = maybeGetKeyNodeValue(node);
    if (keyValue != null) {
      args.add(keyValue);
    }

    jsCodeBuilder.append(INCREMENTAL_DOM_ELEMENT_OPEN_START.call(args));

    jsCodeBuilder.increaseIndentTwice();
    visitChildren(node);
    jsCodeBuilder.decreaseIndentTwice();

    jsCodeBuilder.append(INCREMENTAL_DOM_ELEMENT_OPEN_END.call());
  }

  /**
   * Visits an {@link IncrementalHtmlOpenTagNode}, which occurs when an HTML tag is opened with no
   * conditional attributes. For example:
   *
   * <pre>
   * &lt;div attr="value" attr2="{$someVar}"&gt;...&lt;/div&gt;
   * </pre>
   *
   * generates
   *
   * <pre>
   * IncrementalDom.elementOpen('div');
   * IncrementalDom.attr('attr', 'value');
   * IncrementalDom.attr('attr2', someVar);
   * IncrementalDom.elementClose();
   * </pre>
   */
  @Override
  protected void visitIncrementalHtmlOpenTagNode(IncrementalHtmlOpenTagNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();

    if (node.getChildren().isEmpty()) {
      List<CodeChunk.WithValue> args = new ArrayList<>();
      args.add(stringLiteral(node.getTagName()));

      CodeChunk.WithValue keyValue = maybeGetKeyNodeValue(node);
      if (keyValue != null) {
        args.add(keyValue);
      }

      jsCodeBuilder.append(INCREMENTAL_DOM_ELEMENT_OPEN.call(args));
    } else {
      emitOpenStartEndAndVisitSubtree(node, node.getTagName());
    }
    jsCodeBuilder.increaseIndent();

    if (HtmlDefinitions.HTML5_VOID_ELEMENTS.contains(node.getTagName())) {
      emitClose(node.getTagName());
    }
  }

  /**
   * Gets the 'key' for an element to use in Incremental DOM to be used in the {@code
   * incrementalDom.elementOpen} or {@code incrementalDom.elementVoid} calls.
   *
   * <pre>
   * &lt;div key="test" /div&gt;
   * </pre>
   *
   * generates
   *
   * <pre>
   * incrementalDom.elementVoid('div', 'test')
   * </pre>
   *
   * @param parentNode The SoyNode representing the parent.
   * @return A string containing the JavaScript expression to retrieve the key, or null if the
   *     parent has no attribute child.
   */
  @Nullable
  private CodeChunk.WithValue maybeGetKeyNodeValue(IncrementalHtmlOpenTagNode parentNode) {
    for (StandaloneNode childNode : parentNode.getChildren()) {
      if (!(childNode instanceof IncrementalHtmlAttributeNode)) {
        continue;
      }

      IncrementalHtmlAttributeNode htmlAttributeNode = (IncrementalHtmlAttributeNode) childNode;
      if (htmlAttributeNode.getName().equals(KEY_ATTRIBUTE_NAME)) {
        Preconditions.checkState(
            isComputableAsJsExprsVisitor.execOnChildren(htmlAttributeNode),
            "Attribute values that cannot be evalutated to simple expressions is not yet supported "
                + "for Incremental DOM code generation");
        List<CodeChunk.WithValue> chunks = genJsExprsVisitor.execOnChildren(htmlAttributeNode);

        // OK to use concatChunks() instead of concatChunksForceString(), children are guaranteed
        // to be string (RawTextNode or PrintNode)
        return CodeChunkUtils.concatChunks(chunks);
      }
    }
    return null;
  }

  /**
   * Visits an {@link IncrementalHtmlCloseTagNode}, which occurs when an HTML tag is closed. For
   * example:
   *
   * <pre>
   * &lt;/div&gt;
   * </pre>
   *
   * generates
   *
   * <pre>
   * incrementalDom.elementClose('div');
   * </pre>
   */
  @Override
  protected void visitIncrementalHtmlCloseTagNode(IncrementalHtmlCloseTagNode node) {
    if (!HtmlDefinitions.HTML5_VOID_ELEMENTS.contains(node.getTagName())) {
      emitClose(node.getTagName());
    }
  }

  /**
   * Emits a close tag. For example:
   *
   * <pre>
   * &lt;incrementalDom.elementClose('div');&gt;
   * </pre>
   */
  private void emitClose(String tagName) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();

    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.append(INCREMENTAL_DOM_ELEMENT_CLOSE.call(stringLiteral(tagName)));
  }

  /**
   * Visits a {@link RawTextNode}, which occurs either as a child of any BlockNode or the 'child' of
   * an HTML tag. Note that in the soy tree, tags and their logical HTML children do not have a
   * parent-child relationship, but are rather siblings. For example:
   *
   * <pre>
   * &lt;div&gt;Hello world&lt;/div&gt;
   * </pre>
   *
   * The text "Hello world" translates to
   *
   * <pre>
   * incrementalDom.text('Hello world');
   * </pre>
   */
  @Override
  protected void visitRawTextNode(RawTextNode node) {
    CodeChunk.WithValue textArg = stringLiteral(node.getRawText());
    JsCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    if (node.getHtmlContext() == HtmlContext.HTML_PCDATA) {
      // Note - we don't use generateTextCall since this text can never be null.
      jsCodeBuilder.append(INCREMENTAL_DOM_TEXT.call(textArg));
    } else {
      jsCodeBuilder.addChunkToOutputVar(textArg);
    }
  }

  /**
   * Visit an {@link PrintNode}, with special cases for a variable being printed within an attribute
   * declaration or as HTML content.
   *
   * <p>For attributes, if the variable is of kind attributes, it is invoked. Any other kind of
   * variable is an error.
   *
   * <p>For HTML, if the variable is of kind HTML, it is invoked. Any other kind of variable gets
   * wrapped in a call to {@code incrementalDom.text}, resulting in a Text node.
   */
  @Override
  protected void visitPrintNode(PrintNode node) {
    ExprUnion exprUnion = node.getExprUnion();
    ExprRootNode expr = exprUnion.getExpr();
    List<ExprNode> exprNodes = expr.getChildren();
    ExprNode firstNode = exprNodes.get(0);

    // TODO(sparhami): Raise an error if there are any directives.
    switch (node.getHtmlContext()) {
      case HTML_TAG:
        if (tryGenerateFunctionCall(SoyType.Kind.ATTRIBUTES, firstNode)
            == GenerateFunctionCallResult.INDIRECT_NODE) {
          // Inside an HTML tag, we cannot emit indirect calls (like incrementalDom.text); the only
          // valid commands
          // are idom incrementalDom.attr() calls (which direct ATTRIBUTES functions will call).
          // If we can't emit the print node as a direct call, give up and report an error.
          errorReporter.report(
              node.getSourceLocation(), PRINT_ATTR_INVALID_KIND, firstNode.getType().getKind());
        }
        break;
      case HTML_PCDATA:
        // If the expression is an HTML function, print() will call it.
        // But if we statically know that it's an HTML function, we can call it directly.
        if (tryGenerateFunctionCall(SoyType.Kind.HTML, firstNode)
            == GenerateFunctionCallResult.INDIRECT_NODE) {
          List<CodeChunk.WithValue> chunks = genJsExprsVisitor.exec(node);
          CodeChunk.WithValue printCall = SOY_IDOM_PRINT.call(CodeChunkUtils.concatChunks(chunks));
          JsCodeBuilder codeBuilder = getJsCodeBuilder();
          codeBuilder.append(printCall);
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
   *
   * @param expectedKind The kind of content that the expression must match.
   */
  private GenerateFunctionCallResult tryGenerateFunctionCall(
      SoyType.Kind expectedKind, ExprNode expr) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();

    if (expr instanceof VarRefNode && expr.getType().getKind() == expectedKind) {
      VarRefNode varRefNode = (VarRefNode) expr;
      CodeChunk.WithValue call =
          templateTranslationContext.soyToJsVariableMappings().get(varRefNode.getName()).call();
      jsCodeBuilder.append(call);
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
    CodeChunk.WithValue varName =
        templateTranslationContext.soyToJsVariableMappings().get(varRefNode.getName());
    CodeChunk conditionalCall = CodeChunk.ifStatement(varName, varName.call()).build();
    jsCodeBuilder.append(conditionalCall);
    return GenerateFunctionCallResult.EMITTED;
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    String msgExpression;
    switch (node.getHtmlContext()) {
      case HTML_PCDATA:
        new AssistantForHtmlMsgs(
                this /* master */,
                jsSrcOptions,
                jsExprTranslator,
                genCallCodeUtils,
                isComputableAsJsExprsVisitor,
                templateAliases,
                genJsExprsVisitor,
                templateTranslationContext,
                errorReporter)
            .generateMsgGroupCode(node);
        break;
        // Messages in attribute values are plain text. However, since the translated content
        // includes entities (because other Soy backends treat these messages as HTML source), we
        // must unescape the translations before passing them to the idom APIs.
      case HTML_NORMAL_ATTR_VALUE:
        msgExpression =
            new AssistantForAttributeMsgs(
                    this /* master */,
                    jsSrcOptions,
                    jsExprTranslator,
                    genCallCodeUtils,
                    isComputableAsJsExprsVisitor,
                    templateAliases,
                    genJsExprsVisitor,
                    templateTranslationContext,
                    errorReporter)
                .generateMsgGroupVariable(node);
        getJsCodeBuilder()
            .addChunkToOutputVar(GOOG_STRING_UNESCAPE_ENTITIES.call(id(msgExpression)));
        break;
      default:
        msgExpression = getAssistantForMsgs().generateMsgGroupVariable(node);
        getJsCodeBuilder().addChunkToOutputVar(id(msgExpression));
        break;
    }
  }

  @Override
  protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    visitChildren(node);
  }

  /**
   * Handles <code>{msg}</code> commands in attribute context for idom. The literal text in the
   * translated message must be unescaped after translation, because we pass the text directly to
   * DOM text APIs, whereas translators write HTML with entities. Therefore, we must first escape
   * all interpolated placeholders (which can only be TEXT values).
   *
   * <p>In non-idom, this happens in the contextual auto-escaper.
   */
  private static final class AssistantForAttributeMsgs extends GenJsCodeVisitorAssistantForMsgs {
    AssistantForAttributeMsgs(
        GenIncrementalDomCodeVisitor master,
        SoyJsSrcOptions jsSrcOptions,
        JsExprTranslator jsExprTranslator,
        GenCallCodeUtils genCallCodeUtils,
        IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
        TemplateAliases functionAliases,
        GenJsExprsVisitor genJsExprsVisitor,
        TranslationContext translationContext,
        ErrorReporter errorReporter) {
      super(
          master,
          jsSrcOptions,
          jsExprTranslator,
          genCallCodeUtils,
          isComputableAsJsExprsVisitor,
          functionAliases,
          genJsExprsVisitor,
          translationContext,
          errorReporter);
    }

    @Override
    protected CodeChunk.WithValue genGoogMsgPlaceholder(MsgPlaceholderNode msgPhNode) {
      CodeChunk.WithValue toEscape = super.genGoogMsgPlaceholder(msgPhNode);
      return SOY_ESCAPE_HTML.call(toEscape);
    }
  }
}
