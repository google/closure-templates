/*
 * Copyright 2022 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.idomsrc.IdomRuntime.BUFFERING_IDOM_RENDERER;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_APPLY_ATTRS;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_APPLY_STATICS;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_ATTR;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_CLOSE;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_ELEMENT_CLOSE;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_ENTER_VELOG;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_EXIT_VELOG;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_KEEP_GOING;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_LOGGING_FUNCTION_ATTR;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_OPEN;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_OPEN_SIMPLE;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_PARAM_NAME;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_POP_KEY;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_POP_MANUAL_KEY;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_PRINT;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_PUSH_KEY;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_PUSH_MANUAL_KEY;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_TEXT;
import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM_VISIT_HTML_COMMENT;
import static com.google.template.soy.idomsrc.IdomRuntime.SOY_IDOM_CALL_DYNAMIC_ATTRIBUTES;
import static com.google.template.soy.idomsrc.IdomRuntime.SOY_IDOM_CALL_DYNAMIC_CSS;
import static com.google.template.soy.idomsrc.IdomRuntime.SOY_IDOM_CALL_DYNAMIC_HTML;
import static com.google.template.soy.idomsrc.IdomRuntime.SOY_IDOM_CALL_DYNAMIC_JS;
import static com.google.template.soy.idomsrc.IdomRuntime.SOY_IDOM_CALL_DYNAMIC_TEXT;
import static com.google.template.soy.idomsrc.IdomRuntime.SOY_IDOM_MAKE_ATTRIBUTES;
import static com.google.template.soy.idomsrc.IdomRuntime.SOY_IDOM_MAKE_HTML;
import static com.google.template.soy.idomsrc.IdomRuntime.SOY_IDOM_PRINT_DYNAMIC_ATTR;
import static com.google.template.soy.jssrc.dsl.Expressions.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_STRING_UNESCAPE_ENTITIES;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_ESCAPE_HTML;
import static com.google.template.soy.jssrc.internal.JsRuntime.XID;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.ConditionalBuilder;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsArrowFunction;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.LineComment;
import com.google.template.soy.jssrc.dsl.SourceMapHelper;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.Statements;
import com.google.template.soy.jssrc.dsl.TryCatch;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.jssrc.internal.CanInitOutputVarVisitor;
import com.google.template.soy.jssrc.internal.GenCallCodeUtils;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor.ScopedJsTypeRegistry;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitorAssistantForMsgs;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor;
import com.google.template.soy.jssrc.internal.GenJsTemplateBodyVisitor;
import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.jssrc.internal.JsRuntime;
import com.google.template.soy.jssrc.internal.OutputVarHandler;
import com.google.template.soy.jssrc.internal.TemplateAliases;
import com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor;
import com.google.template.soy.jssrc.internal.TranslationContext;
import com.google.template.soy.jssrc.internal.VisitorsState;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlCommentNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.Metadata;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Generates a series of JavaScript control statements and function calls for rendering the body of
 * a template as HTML. This heavily leverages {@link GenJsTemplateBodyVisitor}, adding logic to
 * print the function calls and changing how statements are combined.
 */
final class GenIdomTemplateBodyVisitor extends GenJsTemplateBodyVisitor {
  private final Deque<SanitizedContentKind> contentKind;
  private final List<Statement> staticVarDeclarations;
  private final boolean generatePositionalParamsSignature;
  private final FileSetMetadata fileSetMetadata;
  private final String alias;

  // Counter for static variables that are declared at the global scope.
  private int staticsCounter = 0;

  GenIdomTemplateBodyVisitor(
      VisitorsState state,
      OutputVarHandler outputVars,
      SoyJsSrcOptions jsSrcOptions,
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenJsExprsVisitor genJsExprsVisitor,
      ErrorReporter errorReporter,
      TranslationContext templateTranslationContext,
      TemplateAliases templateAliases,
      Deque<SanitizedContentKind> contentKind,
      boolean generatePositionalParamsSignature,
      FileSetMetadata fileSetMetadata,
      String alias,
      ScopedJsTypeRegistry jsTypeRegistry,
      SourceMapHelper sourceMapHelper) {
    super(
        state,
        outputVars,
        jsSrcOptions,
        javaScriptValueFactory,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        genJsExprsVisitor,
        errorReporter,
        templateTranslationContext,
        templateAliases,
        jsTypeRegistry,
        sourceMapHelper);
    this.contentKind = checkNotNull(contentKind);
    this.staticVarDeclarations = new ArrayList<>();
    this.generatePositionalParamsSignature = generatePositionalParamsSignature;
    this.fileSetMetadata = checkNotNull(fileSetMetadata);
    this.alias = checkNotNull(alias);
  }

  public List<Statement> getStaticVarDeclarations() {
    return staticVarDeclarations;
  }

  /**
   * Visits the children of a ParentSoyNode. This function is overridden to not do all of the work
   * that {@link GenJsTemplateBodyVisitor} does.
   */
  @Override
  protected List<Statement> visitChildren(ParentSoyNode<?> node) {
    List<Statement> statements = new ArrayList<>();
    for (SoyNode child : node.getChildren()) {
      statements.add(visit(child));
    }
    return statements;
  }

  /**
   * Generates the content of a {@code let} or {@code param} statement. For HTML and attribute
   * let/param statements, the generated instructions inside the node are wrapped in a function
   * which will be optionally passed to another template and invoked in the correct location. All
   * other kinds of let statements are generated as a simple variable.
   */
  private Statement visitLetParamContentNode(RenderUnitNode node, String generatedVarName) {
    // The html transform step, performed by HtmlContextVisitor, ensures that
    // we always have a content kind specified.
    checkState(node.getContentKind() != null);

    contentKind.push(node.getContentKind());

    Statement definition;
    VariableDeclaration.Builder builder = VariableDeclaration.builder(generatedVarName);
    SanitizedContentKind kind = node.getContentKind();
    if (kind.isHtml() || kind == SanitizedContentKind.ATTRIBUTES) {
      Expression constructor;
      if (kind.isHtml()) {
        constructor = SOY_IDOM_MAKE_HTML;
      } else {
        constructor = SOY_IDOM_MAKE_ATTRIBUTES;
      }
      JsDoc jsdoc =
          JsDoc.builder()
              .addParam(INCREMENTAL_DOM_PARAM_NAME, "incrementaldomlib.IncrementalDomRenderer")
              .build();
      var content = Statements.of(visitChildren(node));
      definition =
          builder.setRhs(constructor.call(Expressions.arrowFunction(jsdoc, content))).build();
    } else {
      // We do our own initialization, so mark it as such.
      String outputVarName = generatedVarName + "_output";
      outputVars.pushOutputVar(outputVarName);
      outputVars.setOutputVarInited();

      // TODO(b/246994962): Skip this definition for SanitizedContentKind.TEXT.
      definition =
          Statements.of(
              VariableDeclaration.builder(outputVarName)
                  .setMutable()
                  .setRhs(LITERAL_EMPTY_STRING)
                  .build(),
              Statements.of(visitChildren(node)),
              builder
                  .setRhs(
                      kind == SanitizedContentKind.TEXT
                          ? id(outputVarName)
                          : JsRuntime.sanitizedContentOrdainerFunction(node.getContentKind())
                              .call(id(outputVarName)))
                  .build());
      outputVars.popOutputVar();
    }

    contentKind.pop();
    return definition;
  }

  /**
   * Generates the content of a {@code let} statement. For HTML and attribute let statements, the
   * generated instructions inside the node are wrapped in a function which will be optionally
   * passed to another template and invoked in the correct location. All other kinds of let/param
   * statements are generated as a simple variable.
   */
  @Override
  protected Statement visitLetContentNode(LetContentNode node) {
    String generatedVarName = node.getUniqueVarName();
    Statement statement = visitLetParamContentNode(node, generatedVarName);
    templateTranslationContext.soyToJsVariableMappings().put(node.getVar(), id(generatedVarName));
    return statement;
  }

  @Override
  protected Statement visitCallParamContentNode(CallParamContentNode node) {
    String generatedVarName = "param" + node.getId();
    return visitLetParamContentNode(node, generatedVarName);
  }

  @Override
  protected Statement visitCallNode(CallNode node) {
    List<Statement> statements = new ArrayList<>();
    // If this node has any CallParamContentNode children those contents are not computable as JS
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJsExprsVisitor.exec(child)) {
        statements.add(visit(child));
      }
    }

    Expression call;
    Optional<SanitizedContentKind> kind = Metadata.getCallContentKind(fileSetMetadata, node);
    GenCallCodeUtils.Callee callee = genCallCodeUtils.genCallee(node, createExprTranslator());
    Supplier<Expression> objToPass =
        () -> genCallCodeUtils.genObjToPass(node, createExprTranslator());
    Optional<Supplier<List<Expression>>> positionalParameters = Optional.empty();
    if (genCallCodeUtils.canPerformPositionalCall(node)) {
      positionalParameters =
          Optional.of(
              () ->
                  genCallCodeUtils.getPositionalParams(
                      (CallBasicNode) node,
                      createExprTranslator(),
                      GenCallCodeUtils.hasVariant(node)));
    }
    boolean shouldPushKey = false;

    // TODO(lukes): instead of these helper functions callDynamicXXX for managing context mismatches
    // maybe we should create IncrementalDomRenderer subtypes that can perform the coercions, this
    // would be similar to how jbcsrc manages streaming escapers.
    Expression idomRenderer = INCREMENTAL_DOM;
    if (node.isErrorFallbackSkip()) {
      VariableDeclaration bufferRendererInit =
          VariableDeclaration.builder("renderer_call_" + node.getId())
              .setRhs(Expressions.construct(BUFFERING_IDOM_RENDERER))
              .build();
      idomRenderer = bufferRendererInit.ref();
      statements.add(bufferRendererInit);
    }
    switch (node.getHtmlContext()) {
      case HTML_TAG:
        if (!kind.isPresent() || kind.get() != SanitizedContentKind.ATTRIBUTES) {
          call =
              SOY_IDOM_CALL_DYNAMIC_ATTRIBUTES.call(
                  idomRenderer, callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
        } else {
          call =
              directCall(
                  callee,
                  node,
                  createExprTranslator(),
                  positionalParameters,
                  objToPass,
                  idomRenderer);
        }
        break;
      case CSS:
        call =
            SOY_IDOM_CALL_DYNAMIC_CSS.call(
                idomRenderer, callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
        break;
      case JS:
        call =
            SOY_IDOM_CALL_DYNAMIC_JS.call(
                idomRenderer, callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
        break;
      // stringlike kinds
      case URI:
      case TEXT:
      case HTML_ATTRIBUTE_NAME:
      case HTML_NORMAL_ATTR_VALUE:
        Expression textCall;
        if (!kind.isPresent()
            || kind.get() == SanitizedContentKind.ATTRIBUTES
            || kind.get().isHtml()) {
          if (node instanceof CallBasicNode && ((CallBasicNode) node).getVariantExpr() != null) {
            textCall =
                SOY_IDOM_CALL_DYNAMIC_TEXT.call(
                    callee.objectStyle(),
                    objToPass.get(),
                    JsRuntime.IJ_DATA,
                    createExprTranslator().exec(((CallBasicNode) node).getVariantExpr().getRoot()));
          } else {
            textCall =
                SOY_IDOM_CALL_DYNAMIC_TEXT.call(
                    callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
          }
        } else {
          // This is executed in the case of TEXT Context -> Text Template
          textCall =
              directCall(
                  callee, node, createExprTranslator(), positionalParameters, objToPass, null);
        }
        Statement callStatement =
            outputVars.addChunkToOutputVar(
                GenCallCodeUtils.applyEscapingDirectives(textCall, node));
        statements.add(
            node.isErrorFallbackSkip()
                ? TryCatch.create(
                    Statements.of(
                        callStatement,
                        idomRenderer.dotAccess("replayOn").call(INCREMENTAL_DOM).asStatement()))
                : callStatement);
        return Statements.of(statements);
      default:
        if (!kind.isPresent() || !kind.get().isHtml()) {
          if (node instanceof CallBasicNode && ((CallBasicNode) node).getVariantExpr() != null) {
            call =
                SOY_IDOM_CALL_DYNAMIC_HTML.call(
                    idomRenderer,
                    callee.objectStyle(),
                    objToPass.get(),
                    JsRuntime.IJ_DATA,
                    createExprTranslator().exec(((CallBasicNode) node).getVariantExpr().getRoot()));
          } else {
            call =
                SOY_IDOM_CALL_DYNAMIC_HTML.call(
                    idomRenderer, callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
          }
          shouldPushKey = true;
        } else {
          // This is executed in the case of HTML/ATTR -> HTML/ATTR. All other ambiguous cases are
          // passed through to runtime functions.
          call =
              directCall(
                  callee,
                  node,
                  createExprTranslator(),
                  positionalParameters,
                  objToPass,
                  idomRenderer);
          shouldPushKey = true;
        }
        break;
    }

    RenderUnitNode renderUnitNode = node.getNearestAncestor(RenderUnitNode.class);
    boolean delegatesToTemplate = false;
    if (renderUnitNode instanceof TemplateNode) {
      TemplateNode template = (TemplateNode) renderUnitNode;
      delegatesToTemplate =
          template.getHtmlElementMetadata().getIsHtmlElement()
              && !template.getHtmlElementMetadata().getFinalCallee().isEmpty();
    }
    if (shouldPushKey) {
      if (node.getKeyExpr() != null) {
        statements.add(
            INCREMENTAL_DOM_PUSH_MANUAL_KEY.call(translateExpr(node.getKeyExpr())).asStatement());
      } else if (!delegatesToTemplate) {
        statements.add(
            INCREMENTAL_DOM_PUSH_KEY
                .call(JsRuntime.XID.call(Expressions.stringLiteral(node.getTemplateCallKey())))
                .asStatement());
      }
    }
    // TODO: In reality, the CALL_X functions are really just IDOM versions of the related
    // escaping directives. Consider doing a replace instead of not using escaping directives
    // at all.
    Statement callStatement =
        node.getHtmlContext() == HtmlContext.JS
            ? outputVars.addChunkToOutputVar(call)
            : call.asStatement();
    statements.add(
        node.isErrorFallbackSkip()
            ? TryCatch.create(
                Statements.of(
                    callStatement,
                    idomRenderer.dotAccess("replayOn").call(INCREMENTAL_DOM).asStatement()))
            : callStatement);
    if (shouldPushKey) {
      if (node.getKeyExpr() != null) {
        statements.add(INCREMENTAL_DOM_POP_MANUAL_KEY.call().asStatement());
      } else if (!delegatesToTemplate) {
        statements.add(INCREMENTAL_DOM_POP_KEY.call().asStatement());
      }
    }
    return Statements.of(statements);
  }

  private static Expression directCall(
      GenCallCodeUtils.Callee callee,
      CallNode callNode,
      TranslateExprNodeVisitor exprTranslator,
      Optional<Supplier<List<Expression>>> positionalParameters,
      Supplier<Expression> paramObject,
      @Nullable Expression rendererParam) {
    List<Expression> params = new ArrayList<>();
    if (rendererParam != null) {
      params.add(rendererParam);
    }
    if (positionalParameters.isPresent()) {
      params.add(0, JsRuntime.IJ_DATA);
      params.add(0, JsRuntime.SOY_INTERNAL_CALL_MARKER);
      params.addAll(positionalParameters.get().get());
      GenCallCodeUtils.maybeAddVariantParam(callNode, exprTranslator, params);
      return callee.positionalStyle().get().call(params);
    }
    params.add(paramObject.get());
    params.add(JsRuntime.IJ_DATA);
    GenCallCodeUtils.maybeAddVariantParam(callNode, exprTranslator, params);
    return callee.objectStyle().call(params);
  }

  /**
   * Generates calls in HTML/Attributes content as non-JsExprs, since Incremental DOM instructions
   * are needed and not a JavaScript expression.
   */
  @Override
  protected Statement visitIfNode(IfNode node) {
    if (!isTextContent(contentKind.peek())) {
      return super.generateNonExpressionIfNode(node);
    } else {
      return super.visitIfNode(node);
    }
  }

  @Override
  protected Statement visitForNode(ForNode node) {
    var ret = super.visitForNode(node);
    return ret;
  }

  @Override
  protected Statement visitSwitchNode(SwitchNode node) {
    var ret = super.visitSwitchNode(node);
    return ret;
  }

  @Override
  protected Statement visitSwitchCaseNode(SwitchCaseNode node) {
    return super.visitSwitchCaseNode(node);
  }

  @Override
  protected Statement visitSwitchDefaultNode(SwitchDefaultNode node) {
    return super.visitSwitchDefaultNode(node);
  }

  /**
   * Determines if a given type of content represents text or some sort of HTML.
   *
   * @param contentKind The kind of content to check.
   * @return True if the content represents text, false otherwise.
   */
  private static boolean isTextContent(SanitizedContentKind contentKind) {
    return !contentKind.isHtml() && contentKind != SanitizedContentKind.ATTRIBUTES;
  }

  @Override
  protected Statement visitHtmlCommentNode(HtmlCommentNode node) {
    List<Statement> statements = new ArrayList<>();
    String id = "html_comment_" + node.getId();
    statements.add(
        VariableDeclaration.builder(id)
            .setMutable()
            .setRhs(Expressions.LITERAL_EMPTY_STRING)
            .build());
    outputVars.pushOutputVar(id);
    outputVars.setOutputVarInited();
    contentKind.push(SanitizedContentKind.TEXT);
    for (int i = 0; i < node.numChildren(); i++) {
      statements.add(visit(node.getChild(i)));
    }
    statements.add(INCREMENTAL_DOM_VISIT_HTML_COMMENT.call(id(id)).asStatement());
    outputVars.popOutputVar();
    contentKind.pop();
    return Statements.of(statements);
  }

  /**
   * Visits the {@link HtmlAttributeNode}. The attribute nodes will typically be children of the
   * corresponding {@link HtmlOpenTagNode} or in a let/param of kind attributes, e.g.
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
  protected Statement visitHtmlAttributeNode(HtmlAttributeNode node) {
    if (node.hasValue()) {
      // Attribute keys can only be print statements or constants. As such, the first child
      // should be the key and the second the value.
      checkState(isComputableAsJsExprsVisitor.exec(node.getChild(0)));
      return maybeLoggingFunctionAttrExpr(node)
          .orElse(
              INCREMENTAL_DOM_ATTR
                  .call(
                      // Attributes can only be print nodes or constants
                      genJsExprsVisitor.exec(node.getChild(0)).get(0),
                      Expressions.concatForceString(getAttributeValues(node)))
                  .asStatement());
    } else {
      return Statements.of(visitChildren(node)); // Prints raw text or attributes node.
    }
  }

  /**
   * If the html attribute value is a logging function return the idom renderer method to set it.
   */
  Optional<Statement> maybeLoggingFunctionAttrExpr(HtmlAttributeNode node) {
    if (node.hasValue() && (node.getChild(1) instanceof HtmlAttributeValueNode)) {
      HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) node.getChild(1);
      if (attrValue.numChildren() == 1 && attrValue.getChild(0) instanceof PrintNode) {
        PrintNode printNode = (PrintNode) attrValue.getChild(0);
        if (printNode.getExpr().getRoot() instanceof FunctionNode) {
          FunctionNode func = (FunctionNode) printNode.getExpr().getRoot();
          if (func.getSoyFunction() instanceof LoggingFunction) {
            LoggingFunction loggingNode = (LoggingFunction) func.getSoyFunction();
            return Optional.of(
                INCREMENTAL_DOM_LOGGING_FUNCTION_ATTR
                    .call(
                        genJsExprsVisitor.exec(node.getChild(0)).get(0),
                        XID.call(Expressions.stringLiteral(func.getStaticFunctionName())),
                        Expressions.arrayLiteral(
                            func.getParams().stream()
                                .map(n -> createExprTranslator().exec(n))
                                .collect(toImmutableList())),
                        Expressions.stringLiteral(loggingNode.getPlaceholder()))
                    .asStatement());
          }
        }
      }
    }
    return Optional.empty();
  }

  @Override
  protected Statement visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    // ignore quotes since idom doesn't care about them, so we just iterate the children.
    return Statements.of(visitChildren(node));
  }

  private List<Expression> getAttributeValues(HtmlAttributeNode node) {
    if (!node.hasValue()) {
      // No attribute value, e.g. "<button disabled></button>". Need to put an empty string so that
      // the runtime knows to create an attribute.
      return ImmutableList.of(LITERAL_EMPTY_STRING);
    }
    ParentSoyNode<?> value = (ParentSoyNode<?>) node.getChild(1);
    String outputVar = "html_attribute_" + node.getId();
    boolean needsToBeCoerced = false;
    // There may be HTML nodes in the children that can get coerced to a string. In this case,
    // the appending path needs to be executed.
    for (SoyNode n : value.getChildren()) {
      if (n instanceof CallNode) {
        Optional<SanitizedContentKind> kind =
            Metadata.getCallContentKind(fileSetMetadata, (CallNode) n);
        needsToBeCoerced =
            !kind.isPresent()
                || kind.get().isHtml()
                || kind.get() == SanitizedContentKind.ATTRIBUTES;
      }
    }
    if (!isComputableAsJsExprsVisitor.execOnChildren(value) || needsToBeCoerced) {
      outputVars.pushOutputVar(outputVar);
      outputVars.setOutputVarInited();
      contentKind.push(SanitizedContentKind.TEXT);
      VariableDeclaration decl =
          VariableDeclaration.builder(outputVar)
              .setMutable()
              .setRhs(Expressions.LITERAL_EMPTY_STRING)
              .build();
      Statement statement = visit(value);
      outputVars.popOutputVar();
      contentKind.pop();
      // statement could use decl (the output var), so make sure it's included first. CodeChunk's
      // automatic dependency handling doesn't work here because references to the outputVar aren't
      // via decl.ref(), although that would be a good improvement to clean this up.
      return ImmutableList.of(decl.ref().withInitialStatements(ImmutableList.of(decl, statement)));
    }
    return genJsExprsVisitor.exec(value);
  }

  /**
   * Removes static children from {@code node} and returns them in a map of key to value. For
   * attribute nodes that are known to be static, we can improve performance by adding them to the
   * statics array(http://google.github.io/incremental-dom/#statics-array).
   */
  private ImmutableMap<String, Expression> getStaticAttributes(HtmlOpenTagNode node) {
    List<HtmlAttributeNode> nodesToRemove = new ArrayList<>();
    ImmutableMap.Builder<String, Expression> builder = ImmutableMap.builder();
    // The 0th index is actually the tag itself, so we don't need to iterate over it.
    for (int i = 1; i < node.numChildren(); i++) {
      if (node.getChild(i) instanceof HtmlAttributeNode) {
        HtmlAttributeNode attrNode = (HtmlAttributeNode) node.getChild(i);
        String attributeKey = attrNode.getStaticKey();
        Expression value = getStaticContent(attrNode);
        if (attributeKey != null && value != null) {
          nodesToRemove.add(attrNode);
          builder.put(attributeKey, value);
        }
      }
    }

    for (HtmlAttributeNode child : nodesToRemove) {
      node.removeChild(child);
    }
    return builder.build();
  }

  /**
   * Extract static content from attributes, or return null if value is dynamic. Static content is
   * either an attribute value that is nonexistent or a combination of raw text and xid/css calls.
   */
  @Nullable
  private Expression getStaticContent(HtmlAttributeNode node) {
    if (!node.hasValue()) {
      return Expressions.stringLiteral("");
    }
    // This case is some control flow like a switch, if, or for loop.
    if (!(node.getChild(1) instanceof HtmlAttributeValueNode)) {
      return null;
    }
    HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) node.getChild(1);
    if (attrValue.numChildren() == 0) {
      return Expressions.stringLiteral("");
    }
    // If any children are not raw text or constant xid/css calls, return null
    for (int i = 0; i < attrValue.numChildren(); i++) {
      if (attrValue.getChild(i) instanceof RawTextNode) {
        continue;
      } else if (attrValue.getChild(i) instanceof PrintNode) {
        PrintNode n = (PrintNode) attrValue.getChild(i);
        if (n.getExpr().getRoot() instanceof FunctionNode) {
          FunctionNode fnNode = (FunctionNode) n.getExpr().getRoot();
          if (fnNode.getSoyFunction() != BuiltinFunction.XID
              && (fnNode.getSoyFunction() != BuiltinFunction.CSS || fnNode.numParams() != 1)) {
            return null; // Function call was not xid or css
          }
        } else {
          // Child is variable expression ie {$foo} or {$foo + $bar}
          return null;
        }
      } else {
        // Child was control flow.
        return null;
      }
    }
    return Expressions.concatForceString(getAttributeValues(node));
  }

  private Expression getMaybeSkip(HtmlOpenTagNode node, Statement continueStatements) {
    List<Expression> args = new ArrayList<>();
    if (node.isElementRoot() && !node.isSkipChildrenRoot()) {
      Expression paramsObject;
      if (generatePositionalParamsSignature) {
        paramsObject =
            Expressions.arrayLiteral(
                node.getNearestAncestor(TemplateNode.class).getParams().stream()
                    .map(p -> id(GenJsCodeVisitor.genParamAlias(p.name())))
                    .collect(toImmutableList()));
      } else {
        paramsObject = OPT_DATA;
      }
      args.add(
          Expressions.ifExpression(JsRuntime.GOOG_DEBUG, paramsObject)
              .setElse(Expressions.LITERAL_UNDEFINED)
              .build(templateTranslationContext.codeGenerator()));
    } else {
      args.add(Expressions.LITERAL_UNDEFINED);
    }
    args.add(
        JsArrowFunction.create(
            JsDoc.builder()
                .addParam(INCREMENTAL_DOM_PARAM_NAME, "incrementaldomlib.IncrementalDomRenderer")
                .build(),
            continueStatements));
    return INCREMENTAL_DOM_KEEP_GOING.call(args);
  }

  private Expression getOpenCall(HtmlOpenTagNode node) {
    List<Expression> args = new ArrayList<>();
    args.add(getTagNameCodeChunk(node.getTagName()));

    KeyNode keyNode = node.getKeyNode();
    Expression key = Expressions.LITERAL_UNDEFINED;
    if (keyNode == null) {
      key = JsRuntime.XID.call(Expressions.stringLiteral(node.getKeyId()));
    } else if (node.isSkipRoot() || node.isSkipChildrenRoot()) {
      key = translateExpr(node.getKeyNode().getExpr());
    }
    args.add(key);
    var openCall = node.isDynamic() ? INCREMENTAL_DOM_OPEN : INCREMENTAL_DOM_OPEN_SIMPLE;
    return openCall.call(args);
  }

  private Optional<Expression> getApplyStaticAttributes(Map<String, Expression> staticAttributes) {
    ImmutableList.Builder<Expression> staticsBuilder = ImmutableList.builder();
    for (Map.Entry<String, Expression> entry : staticAttributes.entrySet()) {
      staticsBuilder.add(Expressions.stringLiteral(entry.getKey()));
      staticsBuilder.add(entry.getValue());
    }
    // Instead of inlining the array, place the variable declaration in the global scope
    // and lazily initialize it in the template.
    if (!staticAttributes.isEmpty()) {
      String id = "_statics_" + alias + staticsCounter++;
      Expression idExpr = id(alias + id);
      Expression lazyAssignment =
          // Generator can be null because we know this evaluates to an or
          // ie alias_statics_1 || alias_statics_1 = []
          idExpr.or(
              idExpr.assign(Expressions.arrayLiteral(staticsBuilder.build())),
              /* codeGenerator= */ null);
      staticVarDeclarations.add(VariableDeclaration.builder(alias + id).build());
      return Optional.of(INCREMENTAL_DOM_APPLY_STATICS.call(lazyAssignment));
    }
    return Optional.empty();
  }

  private Expression getApplyAttrs(HtmlOpenTagNode node) {
    List<Statement> statements = new ArrayList<>();
    for (int i = 1; i < node.numChildren(); i++) {
      statements.add(visit(node.getChild(i)));
    }
    return INCREMENTAL_DOM_APPLY_ATTRS.call().withInitialStatements(statements);
  }

  /**
   * Visits an {@link HtmlOpenTagNode} and emits appropriate attr/staticAttr calls and maybe a self
   * close tag.
   *
   * <pre>
   * &lt;div attr="value" attr2="{$someVar}"&gt;...&lt;/div&gt;
   * </pre>
   *
   * generates
   *
   * <pre>
   * open('div');
   * attr('attr', 'value');
   * attr('attr2', someVar);
   * applyAttrs()
   * ...
   *
   * close()
   * </pre>
   */
  @Override
  protected Statement visitHtmlOpenTagNode(HtmlOpenTagNode node) {
    List<Statement> statements = new ArrayList<>();
    String tagName = node.getTagName().getTagString();
    if (tagName != null && Ascii.equalsIgnoreCase(tagName, "script")) {
      scriptOutputVar = "script_" + staticsCounter++;
      outputVars.pushOutputVar(scriptOutputVar);
      outputVars.setOutputVarInited();
      statements.add(
          VariableDeclaration.builder(scriptOutputVar)
              .setMutable()
              .setRhs(LITERAL_EMPTY_STRING)
              .build());
      contentKind.push(SanitizedContentKind.JS);
    }
    if (node.isSkipRoot()) {
      return Statements.of(statements);
    }
    statements.add(LineComment.create(node.getSourceLocation().toString()).asStatement());
    if (node.getKeyNode() != null) {
      // Push key BEFORE emitting `elementOpen`. Later, for `elementOpen` calls of keyed elements,
      // we do not specify any key.
      Expression key = translateExpr(node.getKeyNode().getExpr());
      statements.add(INCREMENTAL_DOM_PUSH_MANUAL_KEY.call(key).asStatement());
    }
    Expression openTagExpr = getOpenCall(node);
    statements.add(openTagExpr.asStatement());
    statements.add(getAttributes(node));
    if (node.getTaggedPairs().isEmpty()
        && node.getKeyNode() != null
        && node.getTagName().isDefinitelyVoid()) {
      statements.add(INCREMENTAL_DOM_POP_MANUAL_KEY.call().asStatement());
    }
    getClose(node).ifPresent(statements::add);

    return Statements.of(statements);
  }

  private String scriptOutputVar;

  private Optional<Statement> getClose(HtmlOpenTagNode node) {
    Expression close = node.isElementRoot() ? INCREMENTAL_DOM_ELEMENT_CLOSE : INCREMENTAL_DOM_CLOSE;
    boolean isSelfClosing = node.isSelfClosing() || node.getTagName().isDefinitelyVoid();
    if (isSelfClosing) {
      return Optional.of(close.call().asStatement());
    }
    return Optional.empty();
  }

  private Statement getAttributes(HtmlOpenTagNode node) {
    List<Statement> statements = new ArrayList<>();
    var staticAttributes = getStaticAttributes(node);
    Optional<Expression> maybeApplyStatics = getApplyStaticAttributes(staticAttributes);
    if (maybeApplyStatics.isPresent()) {
      statements.add(maybeApplyStatics.get().asStatement());
    }
    statements.add(getApplyAttrs(node).asStatement());
    return Statements.of(statements);
  }

  /**
   * Visits an {@link HtmlCloseTagNode}, which occurs when an HTML tag is closed. For example:
   *
   * <pre>
   * &lt;/div&gt;
   * </pre>
   *
   * generates
   *
   * <pre>
   * incrementalDom.close('div');
   * </pre>
   */
  @Override
  protected Statement visitHtmlCloseTagNode(HtmlCloseTagNode node) {
    // This case occurs in the case where we encounter the end of a keyed element.
    List<Statement> statements = new ArrayList<>();
    statements.add(LineComment.create(node.getSourceLocation().toString()).asStatement());
    String tagName = node.getTagName().getTagString();
    if (tagName != null && Ascii.equalsIgnoreCase(tagName, "script")) {
      outputVars.popOutputVar();
      Expression ordainer = id("soy").dotAccess("VERY_UNSAFE").dotAccess("ordainSanitizedJs");
      Expression safeScript = ordainer.call(id(scriptOutputVar)).dotAccess("toSafeScript").call();
      GoogRequire require = GoogRequire.create("safevalues");
      Expression unwrapped = require.dotAccess("unwrapScript").call(safeScript);
      Expression currentElement = INCREMENTAL_DOM.dotAccess("currentElement").call();
      Expression textContentAssignment = currentElement.dotAccess("textContent").assign(unwrapped);
      ConditionalBuilder ifCurrentElementExists =
          Statements.ifStatement(currentElement, textContentAssignment.asStatement());
      statements.add(ifCurrentElementExists.build());
      statements.add(INCREMENTAL_DOM.dotAccess("skipNode").call().asStatement());
      // We could be in some other content kind (like JS in a <script> tag) or still in HTML. Either
      // way, a closing HTML tag means we're back in HTML, so pop if needed for HTML.
      if (!contentKind.peek().isHtml()) {
        contentKind.pop();
        checkState(contentKind.peek().isHtml(), "unexpected contentKind: %s", contentKind);
      }
    }
    if (node.getTaggedPairs().size() == 1) {
      HtmlOpenTagNode openTag = (HtmlOpenTagNode) node.getTaggedPairs().get(0);
      if (openTag.getKeyNode() != null && !(openTag.getParent() instanceof SkipNode)) {
        statements.add(INCREMENTAL_DOM_POP_MANUAL_KEY.call().asStatement());
      }
    }

    Expression close = INCREMENTAL_DOM_CLOSE;
    if (node.getTaggedPairs().size() == 1
        && ((HtmlOpenTagNode) node.getTaggedPairs().get(0)).isElementRoot()) {
      close = INCREMENTAL_DOM_ELEMENT_CLOSE;
    }

    if (!node.getTagName().isDefinitelyVoid()) {
      statements.add(close.call().asStatement());
    }
    return Statements.of(statements);
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
  protected Statement visitRawTextNode(RawTextNode node) {
    Expression textArg = stringLiteral(node.getRawText());
    switch (node.getHtmlContext()) {
      case CSS:
      case HTML_RCDATA:
      case HTML_PCDATA:
        // Note - we don't use generateTextCall since this text can never be null.
        return INCREMENTAL_DOM_TEXT.call(textArg).asStatement();
      case HTML_TAG:
        return INCREMENTAL_DOM_ATTR.call(textArg, stringLiteral("")).asStatement();
      default:
        return outputVars.addChunkToOutputVar(textArg);
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
  protected Statement visitPrintNode(PrintNode node) {
    List<Expression> chunks = genJsExprsVisitor.exec(node);
    switch (node.getHtmlContext()) {
      case HTML_TAG:
        return SOY_IDOM_PRINT_DYNAMIC_ATTR
            .call(INCREMENTAL_DOM, Expressions.concat(chunks))
            .asStatement();
      case JS:
        return outputVars.addChunkToOutputVar(Expressions.concat(chunks));
      case CSS:
      // fall through
      case HTML_PCDATA:
        if (node.numChildren() > 0
            && node.getChild(node.numChildren() - 1).getPrintDirective()
                instanceof SanitizedContentOperator
            && ((SanitizedContentOperator)
                        node.getChild(node.numChildren() - 1).getPrintDirective())
                    .getContentKind()
                == SanitizedContent.ContentKind.HTML) {
          return INCREMENTAL_DOM_PRINT
              .call(Expressions.concat(chunks), Expressions.LITERAL_TRUE)
              .asStatement();
        } else {
          return INCREMENTAL_DOM_PRINT.call(Expressions.concat(chunks)).asStatement();
        }
      case HTML_RCDATA:
        return INCREMENTAL_DOM_TEXT
            .call(id("String").call(Expressions.concat(chunks)))
            .asStatement();
      default:
        return super.visitPrintNode(node);
    }
  }

  @Override
  protected Statement visitSkipNode(SkipNode node) {
    var statements = new ImmutableList.Builder<Statement>();
    HtmlOpenTagNode openTag = (HtmlOpenTagNode) node.getChild(0);
    statements.add(getOpenCall(openTag).asStatement());
    Statement attributes = getAttributes(openTag);
    if (node.skipOnlyChildren()) {
      statements.add(attributes);
    }
    Optional<Statement> maybeClose = getClose(openTag);

    Statement childStatements =
        maybeClose.isPresent()
            ? Statements.of(Statements.of(visitChildren(node)), maybeClose.get())
            : Statements.of(visitChildren(node));
    Expression maybeSkip =
        getMaybeSkip(
            openTag,
            node.skipOnlyChildren() ? childStatements : Statements.of(attributes, childStatements));
    statements.add(maybeSkip.asStatement());
    return Statements.of(statements.build());
  }

  @Override
  protected Statement visitVeLogNode(VeLogNode node) {
    List<Statement> statements = new ArrayList<>();
    statements.add(openVeLogNode(node));
    statements.addAll(visitChildren(node));
    statements.add(exitVeLogNode(node));
    return Statements.of(statements);
  }

  Statement openVeLogNode(VeLogNode node) {
    Expression veData = createExprTranslator().exec(node.getVeDataExpression());
    if (node.getLogonlyExpression() != null) {
      return Statements.assign(
          INCREMENTAL_DOM,
          INCREMENTAL_DOM_ENTER_VELOG.call(
              veData, createExprTranslator().exec(node.getLogonlyExpression())));
    }
    return INCREMENTAL_DOM_ENTER_VELOG.call(veData).asStatement();
  }

  Statement exitVeLogNode(VeLogNode node) {
    var exit = INCREMENTAL_DOM_EXIT_VELOG.call();
    if (node.getLogonlyExpression() != null) {
      return Statements.assign(INCREMENTAL_DOM, exit);
    }
    return exit.asStatement();
  }

  @Override
  protected Statement visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    Expression msgExpression;
    switch (node.getHtmlContext()) {
      case HTML_PCDATA:
        String id = "_msg_" + alias + "_" + staticsCounter++;
        VariableDeclaration staticDecl =
            VariableDeclaration.builder(id)
                .setRhs(Expressions.objectLiteral(ImmutableMap.of()))
                .build();
        staticVarDeclarations.add(staticDecl);
        return new AssistantForHtmlMsgs(
                /* idomTemplateBodyVisitor= */ this,
                jsSrcOptions,
                genCallCodeUtils,
                isComputableAsJsExprsVisitor,
                genJsExprsVisitor,
                templateTranslationContext,
                errorReporter,
                id,
                outputVars)
            .generateMsgGroupCode(node);
      // Messages in attribute values are plain text. However, since the translated content
      // includes entities (because other Soy backends treat these messages as HTML source), we
      // must unescape the translations before passing them to the idom APIs.
      case HTML_NORMAL_ATTR_VALUE:
        msgExpression =
            new AssistantForAttributeMsgs(
                    this,
                    jsSrcOptions,
                    genCallCodeUtils,
                    isComputableAsJsExprsVisitor,
                    genJsExprsVisitor,
                    templateTranslationContext,
                    errorReporter,
                    outputVars)
                .generateMsgGroupVariable(node);
        return outputVars.addChunkToOutputVar(GOOG_STRING_UNESCAPE_ENTITIES.call(msgExpression));
      case HTML_RCDATA:
      case CSS:
        msgExpression = getAssistantForMsgs().generateMsgGroupVariable(node);
        return INCREMENTAL_DOM_TEXT.call(id("String").call(msgExpression)).asStatement();
      default:
        msgExpression = getAssistantForMsgs().generateMsgGroupVariable(node);
        return outputVars.addChunkToOutputVar(msgExpression);
    }
  }

  @Override
  protected Statement visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    return Statements.of(visitChildren(node));
  }

  private Expression getTagNameCodeChunk(TagName tagName) {
    // No need to check if is computable as js expr because tag names can only be
    // print nodes and constants.
    return genJsExprsVisitor.exec(tagName.getNode()).get(0);
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
        GenIdomTemplateBodyVisitor master,
        SoyJsSrcOptions jsSrcOptions,
        GenCallCodeUtils genCallCodeUtils,
        IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
        GenJsExprsVisitor genJsExprsVisitor,
        TranslationContext translationContext,
        ErrorReporter errorReporter,
        OutputVarHandler outputVars) {
      super(
          master,
          jsSrcOptions,
          genCallCodeUtils,
          isComputableAsJsExprsVisitor,
          genJsExprsVisitor,
          translationContext,
          errorReporter,
          outputVars);
    }

    @Override
    protected Expression genGoogMsgPlaceholder(MsgPlaceholderNode msgPhNode) {
      Expression toEscape = super.genGoogMsgPlaceholder(msgPhNode);
      return SOY_ESCAPE_HTML.call(toEscape);
    }
  }
}
