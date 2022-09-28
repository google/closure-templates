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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_APPLY_ATTRS;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_APPLY_STATICS;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_ATTR;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_CLOSE;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_ELEMENT_CLOSE;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_ENTER;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_EXIT;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_LIB;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_OPEN;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_OPEN_SSR;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_PARAM_NAME;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_POP_KEY;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_POP_MANUAL_KEY;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_PUSH_KEY;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_PUSH_MANUAL_KEY;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_TEXT;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_TODEFAULT;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_TONULL;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_VERIFY_LOGONLY;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_CALL_DYNAMIC_ATTRIBUTES;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_CALL_DYNAMIC_CSS;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_CALL_DYNAMIC_HTML;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_CALL_DYNAMIC_JS;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_CALL_DYNAMIC_TEXT;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_MAKE_ATTRIBUTES;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_MAKE_HTML;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_PRINT;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_PRINT_DYNAMIC_ATTR;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_TYPE_ATTRIBUTE;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_TYPE_HTML;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_VISIT_HTML_COMMENT;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.STATE_PREFIX;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.STATE_VAR_PREFIX;
import static com.google.template.soy.jssrc.dsl.Expression.EMPTY_OBJECT_LITERAL;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;
import static com.google.template.soy.jssrc.dsl.Statement.returnValue;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_ALIAS;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_STRING_UNESCAPE_ENTITIES;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_ESCAPE_HTML;
import static com.google.template.soy.soytree.SoyTreeUtils.isConstantExpr;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.incrementaldomsrc.GenIncrementalDomExprsVisitor.GenIncrementalDomExprsVisitorFactory;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.ClassExpression;
import com.google.template.soy.jssrc.dsl.ClassExpression.MethodDeclaration;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.ConditionalBuilder;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.jssrc.internal.CanInitOutputVarVisitor;
import com.google.template.soy.jssrc.internal.GenCallCodeUtils;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitorAssistantForMsgs;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor;
import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.jssrc.internal.JsCodeBuilder;
import com.google.template.soy.jssrc.internal.JsRuntime;
import com.google.template.soy.jssrc.internal.JsType;
import com.google.template.soy.jssrc.internal.StandardNames;
import com.google.template.soy.jssrc.internal.TemplateAliases;
import com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor;
import com.google.template.soy.jssrc.internal.TranslationContext;
import com.google.template.soy.passes.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
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
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TemplateType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Generates a series of JavaScript control statements and function calls for rendering one or more
 * templates as HTML. This heavily leverages {@link GenJsCodeVisitor}, adding logic to print the
 * function calls and changing how statements are combined.
 */
public final class GenIncrementalDomCodeVisitor extends GenJsCodeVisitor {

  private static final String NAMESPACE_EXTENSION = ".incrementaldom";

  /**
   * Class that contains the state generated from visiting the beginning of a velogging statement.
   * This allows one to generate code such as
   *
   * <pre>
   *   var velog_1 = foobar;
   *   if (velog_1) {
   *     idom = idom.toNullRenderer();
   *   }
   *   idom.enter(new Metadata(...));
   *   ...
   *   if (velog_1) {
   *     idom = idom.toDefaultRenderer();
   *   }
   * </pre>
   */
  static class VeLogStateHolder {
    Expression logOnlyConditional; // Holds the variable reference to velog_1
    Statement enterStatement; // Contains the idom.enter(...) statement

    public VeLogStateHolder(Expression logOnlyConditional, Statement enterStatement) {
      this.logOnlyConditional = logOnlyConditional;
      this.enterStatement = enterStatement;
    }
  }

  // Counter for static variables that are declared at the global scope.
  private int staticsCounter = 0;
  private String alias = "";
  private boolean hasNonConstantState;

  GenIncrementalDomCodeVisitor(
      SoyJsSrcOptions jsSrcOptions,
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      IncrementalDomDelTemplateNamer incrementalDomDelTemplateNamer,
      IncrementalDomGenCallCodeUtils genCallCodeUtils,
      IsComputableAsIncrementalDomExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenIncrementalDomExprsVisitorFactory genIncrementalDomExprsVisitorFactory,
      SoyTypeRegistry typeRegistry) {
    super(
        jsSrcOptions,
        javaScriptValueFactory,
        incrementalDomDelTemplateNamer,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        canInitOutputVarVisitor,
        genIncrementalDomExprsVisitorFactory,
        typeRegistry);
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

  @Override
  protected JsType getJsTypeForParamForDeclaration(SoyType paramType) {
    return JsType.forIncrementalDomState(paramType);
  }

  @Override
  protected JsType getJsTypeForParamTypeCheck(SoyType paramType) {
    return JsType.forIncrementalDom(paramType);
  }

  @Override
  protected void visit(SoyNode node) {
    try {
      super.visit(node);
    } catch (RuntimeException e) {
      throw new Error("error from : " + node.getKind() + " @ " + node.getSourceLocation(), e);
    }
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
    if (isTextContent(node.getContentKind())) {
      return super.getTemplateReturnType(node);
    }

    // This template does not return any content but rather contains Incremental DOM
    // instructions.
    return "void";
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    staticsCounter = 0;
    SanitizedContentKind kind = node.getContentKind();
    getJsCodeBuilder().setContentKind(kind);
    if (node instanceof TemplateDelegateNode) {
      alias = node.getLocalTemplateSymbol();
    } else {
      alias = templateAliases.get(node.getTemplateName());
    }

    if (node instanceof TemplateElementNode) {
      hasNonConstantState = calcHasNonConstantState((TemplateElementNode) node);
    }

    super.visitTemplateNode(node);
    if ((node instanceof TemplateDelegateNode
            && ((TemplateDelegateNode) node).getChildren().isEmpty())
        || (isModifiable(node) && node.getChildren().isEmpty())) {
      return;
    }
    if (kind.isHtml() || kind == SanitizedContentKind.ATTRIBUTES) {
      Expression type;
      if (kind.isHtml()) {
        type = SOY_IDOM_TYPE_HTML;
      } else {
        type = SOY_IDOM_TYPE_ATTRIBUTE;
      }
      getJsCodeBuilder().append(Statement.assign(id(alias).dotAccess("contentKind"), type));
    }

    if (node instanceof TemplateElementNode) {
      TemplateElementNode element = (TemplateElementNode) node;
      String elementName = this.getSoyElementClassName();
      String elementAccessor = elementName + "Interface";
      getJsCodeBuilder().appendLine();
      getJsCodeBuilder().append(generateAccessorInterface(elementAccessor, element));
      getJsCodeBuilder().append(generateExportsForSoyElement(elementAccessor));
      getJsCodeBuilder().append(generateRenderInternal(element));
      getJsCodeBuilder().appendNullable(generateSyncInternal(element));
      getJsCodeBuilder().append(generateClassForSoyElement(elementName, elementAccessor, element));
      getJsCodeBuilder().append(generateExportsForSoyElement(elementName));
    }
  }

  private Statement generateRenderInternal(TemplateElementNode node) {
    String paramsType = hasOnlyImplicitParams(node) ? "null" : "!" + alias + ".Params";
    String soyElementClassName = this.getSoyElementClassName();
    JsDoc jsDoc =
        JsDoc.builder()
            .addParam(INCREMENTAL_DOM_PARAM_NAME, "!incrementaldomlib.IncrementalDomRenderer")
            .addParam(StandardNames.OPT_DATA, paramsType)
            .addAnnotation("public")
            .addAnnotation("override")
            .addParameterizedAnnotation("this", soyElementClassName)
            .addParameterizedAnnotation("suppress", "checkTypes")
            .build();
    // Build `renderInternal` method.
    Expression fn =
        Expression.function(
            jsDoc,
            // Various parts of the js codegen expects these values to be in the local
            // scope.
            Statement.of(
                VariableDeclaration.builder(StandardNames.DOLLAR_IJDATA)
                    .setRhs(Expression.THIS.dotAccess("ijData"))
                    .build(),
                Statement.of(
                    node.getStateVars().stream()
                        .map(
                            stateVar ->
                                VariableDeclaration.builder(
                                        STATE_VAR_PREFIX + STATE_PREFIX + stateVar.name())
                                    .setRhs(
                                        Expression.THIS.dotAccess(STATE_PREFIX + stateVar.name()))
                                    .build())
                        .collect(toImmutableList())),
                generateIncrementalDomRenderCalls(node, alias, /*isPositionalStyle=*/ false)));
    return VariableDeclaration.builder(soyElementClassName + "Render")
        .setJsDoc(jsDoc)
        .setRhs(fn)
        .build();
  }

  private Statement generateInitInternal(TemplateElementNode node) {
    ImmutableList.Builder<Statement> stateVarInitializations = ImmutableList.builder();
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    for (TemplateStateVar stateVar : node.getStateVars()) {
      JsType jsType = JsType.forIncrementalDomState(stateVar.type());
      for (GoogRequire require : jsType.getGoogRequires()) {
        jsCodeBuilder.addGoogRequire(require);
      }
      JsDoc stateVarJsdoc =
          JsDoc.builder().addParameterizedAnnotation("private", jsType.typeExpr()).build();
      Expression rhsValue;
      if (isConstantExpr(stateVar.defaultValue())) {
        rhsValue = translateExpr(stateVar.defaultValue());
      } else {
        rhsValue = Expression.LITERAL_UNDEFINED.castAs("?");
      }
      stateVarInitializations.add(
          Statement.assign(
              Expression.THIS.dotAccess(STATE_PREFIX + stateVar.name()), rhsValue, stateVarJsdoc));
    }
    return Statement.of(stateVarInitializations.build());
  }

  @Nullable
  private Statement generateSyncInternal(TemplateElementNode node) {
    String soyElementClassName = this.getSoyElementClassName();
    String paramsType = hasOnlyImplicitParams(node) ? "null" : "!" + alias + ".Params";
    JsDoc jsDoc =
        JsDoc.builder()
            .addParam(StandardNames.OPT_DATA, paramsType)
            .addAnnotation("public")
            .addAnnotation("override")
            .addParameterizedAnnotation("this", soyElementClassName)
            .addParameterizedAnnotation("suppress", "checkTypes")
            .addParam("syncOnlyData", "boolean=")
            .build();
    return VariableDeclaration.builder(soyElementClassName + "SyncInternal")
        .setJsDoc(jsDoc)
        .setRhs(
            Expression.function(
                jsDoc,
                Statement.of(
                    // Various parts of the js codegen expects these parameters to be in the local
                    // scope.
                    VariableDeclaration.builder(StandardNames.DOLLAR_IJDATA)
                        .setRhs(Expression.THIS.dotAccess("ijData"))
                        .build(),
                    genSyncStateCalls(node, alias))))
        .build();
  }

  @Override
  protected JsDoc generatePositionalFunctionJsDoc(TemplateNode node, boolean addVariantParam) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    addInternalCallerParam(jsDocBuilder);
    addIjDataParam(jsDocBuilder, /*forPositionalSignature=*/ true);
    maybeAddRenderer(jsDocBuilder, node);
    for (TemplateParam param : paramsInOrder(node)) {
      JsType jsType = getJsTypeForParamForDeclaration(param.type());
      jsDocBuilder.addParam(
          genParamAlias(param.name()), jsType.typeExpr() + (param.isRequired() ? "" : "="));
    }
    if (addVariantParam) {
      jsDocBuilder.addParam(StandardNames.OPT_VARIANT, "string=");
    }
    addReturnTypeAndAnnotations(node, jsDocBuilder);
    // TODO(b/11787791): make the checkTypes suppression more fine grained.
    jsDocBuilder.addParameterizedAnnotation("suppress", "checkTypes");
    return jsDocBuilder.build();
  }

  @Override
  protected JsDoc generateEmptyFunctionJsDoc(TemplateNode node) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    String ijDataTypeExpression = ijDataTypeExpression(jsDocBuilder);
    jsDocBuilder.addAnnotation(
        "type",
        String.format("{function(?Object<string, *>=, ?%s=):string}", ijDataTypeExpression));
    jsDocBuilder.addParameterizedAnnotation("suppress", "checkTypes");
    return jsDocBuilder.build();
  }

  @Override
  protected JsDoc generateFunctionJsDoc(
      TemplateNode node, String alias, boolean suppressCheckTypes, boolean addVariantParam) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    maybeAddRenderer(jsDocBuilder, node);
    // This is true if there are any calls with data="all" (which implicitly add optional parameters
    // from those template) or if all parameters are optional (but there are some parameters).
    boolean noRequiredParams = new ShouldEnsureDataIsDefinedVisitor().exec(node);
    if (hasOnlyImplicitParams(node)) {
      // If there are indirect parameters, allow an arbitrary object.
      // Either way, allow null, since the caller may not pass parameters.
      jsDocBuilder.addParam(
          StandardNames.OPT_DATA, noRequiredParams ? "?Object<string, *>=" : "null=");
    } else if (noRequiredParams) {
      // All parameters are optional or only owned by an indirect callee; caller doesn't need to
      // pass an object.
      jsDocBuilder.addParam(StandardNames.OPT_DATA, "?" + alias + ".Params=");
    } else {
      jsDocBuilder.addParam(StandardNames.OPT_DATA, "!" + alias + ".Params");
    }
    addIjDataParam(jsDocBuilder, /*forPositionalSignature=*/ false);
    if (addVariantParam) {
      jsDocBuilder.addParam(StandardNames.OPT_VARIANT, "string=");
    }
    addReturnTypeAndAnnotations(node, jsDocBuilder);
    if (suppressCheckTypes) {
      // TODO(b/11787791): make the checkTypes suppression more fine grained.
      jsDocBuilder.addParameterizedAnnotation("suppress", "checkTypes");
    } else {
      if (fileSetMetadata.getTemplate(node).getTemplateType().getActualParameters().stream()
          .anyMatch(TemplateType.Parameter::isImplicit)) {
        jsDocBuilder.addParameterizedAnnotation("suppress", "missingProperties");
      }
    }
    return jsDocBuilder.build();
  }

  private static void maybeAddRenderer(JsDoc.Builder jsDocBuilder, TemplateNode node) {
    SanitizedContentKind kind = node.getContentKind();
    if (kind.isHtml() || kind == SanitizedContentKind.ATTRIBUTES) {
      jsDocBuilder.addGoogRequire(INCREMENTAL_DOM_LIB);
      jsDocBuilder.addParam(
          INCREMENTAL_DOM_PARAM_NAME, "!incrementaldomlib.IncrementalDomRenderer");
    }
  }

  /** Returns the simple type of IjData, adding requires as necessary. */
  @Override
  protected String ijDataTypeExpression(JsDoc.Builder jsDocBuilder) {
    jsDocBuilder.addGoogRequire(GOOG_SOY_ALIAS);
    return GOOG_SOY_ALIAS.alias() + ".IjData";
  }

  @Override
  protected void addIjDataParam(JsDoc.Builder jsDocBuilder, boolean forPositionalSignature) {
    String ijDataTypeExpression = ijDataTypeExpression(jsDocBuilder);
    if (forPositionalSignature) {
      jsDocBuilder.addParam(StandardNames.DOLLAR_IJDATA, "!" + ijDataTypeExpression);
    } else {
      jsDocBuilder.addParam(StandardNames.OPT_IJDATA, "?" + ijDataTypeExpression + "=");
    }
  }

  /** Return the parameters always present in positional calls. */
  @Override
  protected ImmutableList<Expression> getFixedParamsToPositionalCall(TemplateNode node) {
    SanitizedContentKind kind = node.getContentKind();
    ImmutableList.Builder<Expression> params = ImmutableList.builder();
    params.addAll(super.getFixedParamsToPositionalCall(node));
    if (kind.isHtml() || kind == SanitizedContentKind.ATTRIBUTES) {
      params.add(id(INCREMENTAL_DOM_PARAM_NAME));
    }

    return params.build();
  }

  /** Return the parameters always present in non-positional calls. */
  @Override
  protected ImmutableList<Expression> getFixedParamsForNonPositionalCall(TemplateNode node) {
    SanitizedContentKind kind = node.getContentKind();
    ImmutableList.Builder<Expression> params = ImmutableList.builder();
    if (kind.isHtml() || kind == SanitizedContentKind.ATTRIBUTES) {
      params.add(id(INCREMENTAL_DOM_PARAM_NAME));
    }
    params.addAll(super.getFixedParamsForNonPositionalCall(node));

    return params.build();
  }

  @Override
  protected ImmutableList<Expression> templateArguments(
      TemplateNode node, boolean isPositionalStyle) {
    ImmutableList<Expression> arguments = super.templateArguments(node, isPositionalStyle);
    SanitizedContentKind kind = node.getContentKind();
    if (kind.isHtml() || kind == SanitizedContentKind.ATTRIBUTES) {
      return ImmutableList.<Expression>builder().add(INCREMENTAL_DOM).addAll(arguments).build();
    }
    return arguments;
  }

  @Override
  protected Statement generateFunctionBody(
      TemplateNode node, String alias, @Nullable String objectParamName, boolean addStubMapLogic) {
    ImmutableList.Builder<Statement> bodyStatements = ImmutableList.builder();
    boolean isPositionalStyle = objectParamName == null;
    if (!isPositionalStyle) {
      bodyStatements.add(redeclareIjData());
    } else {
      bodyStatements.add(
          JsRuntime.SOY_ARE_YOU_AN_INTERNAL_CALLER
              .call(id(StandardNames.ARE_YOU_AN_INTERNAL_CALLER))
              .asStatement());
    }
    if (addStubMapLogic) {
      bodyStatements.add(generateStubbingTest(node, alias, isPositionalStyle));
    }
    // Generate statement to ensure data is defined, if necessary.
    if (!isPositionalStyle && new ShouldEnsureDataIsDefinedVisitor().exec(node)) {
      bodyStatements.add(
          Statement.assign(
              JsRuntime.OPT_DATA,
              JsRuntime.OPT_DATA.or(
                  EMPTY_OBJECT_LITERAL.castAs(objectParamName),
                  templateTranslationContext.codeGenerator())));
    }
    if (isPositionalStyle && node instanceof TemplateElementNode) {
      throw new IllegalStateException("elements cannot be compiled into positional style.");
    }
    bodyStatements.add(
        node instanceof TemplateElementNode
            ? this.generateFunctionBodyForSoyElement((TemplateElementNode) node)
            : this.generateIncrementalDomRenderCalls(node, alias, isPositionalStyle));
    return Statement.of(bodyStatements.build());
  }

  private boolean calcHasNonConstantState(TemplateElementNode node) {
    return node.getStateVars().stream().anyMatch(v -> !isConstantExpr(v.defaultValue()));
  }

  private Statement genSyncStateCalls(TemplateElementNode node, String alias) {
    Expression originalDataSource = dataSource;
    dataSource = JsRuntime.OPT_DATA;
    Statement typeChecks = genParamTypeChecks(node, alias, false);
    ImmutableList<TemplateStateVar> headerVars = node.getStateVars();
    ImmutableMap<TemplateStateVar, Boolean> isNonConst =
        headerVars.stream().collect(toImmutableMap(v -> v, v -> !isConstantExpr(v.defaultValue())));

    ImmutableList.Builder<Statement> stateReassignmentBuilder = ImmutableList.builder();
    TemplateStateVar lastNonConstVar =
        headerVars.reverse().stream().filter(isNonConst::get).findFirst().orElse(null);
    boolean haveVisitedLastNonConstVar = lastNonConstVar == null;

    String prefix = STATE_PREFIX;
    for (TemplateStateVar headerVar : headerVars) {
      if (isNonConst.get(headerVar)) {
        stateReassignmentBuilder.add(
            Statement.assign(
                Expression.THIS.dotAccess(prefix + headerVar.name()),
                translateExpr(headerVar.defaultValue())));
      }
      haveVisitedLastNonConstVar = haveVisitedLastNonConstVar || headerVar.equals(lastNonConstVar);
      // Only need to alias if there's another non-constant var to come that may reference this.
      if (!haveVisitedLastNonConstVar) {
        stateReassignmentBuilder.add(
            VariableDeclaration.builder(STATE_VAR_PREFIX + STATE_PREFIX + headerVar.name())
                .setRhs(Expression.THIS.dotAccess(prefix + headerVar.name()))
                .build());
      }
    }
    List<Statement> assignments = stateReassignmentBuilder.build();
    Statement stateReassignments = Statement.of(assignments);
    dataSource = originalDataSource;
    return Statement.of(typeChecks, stateReassignments);
  }

  /** Generates idom#elementOpen, idom#elementClose, etc. function calls for the given node. */
  private Statement generateIncrementalDomRenderCalls(
      TemplateNode node, String alias, boolean isPositionalStyle) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    boolean isTextTemplate = isTextContent(node.getContentKind());

    Statement typeChecks = genParamTypeChecks(node, alias, isPositionalStyle);
    // Note: we do not try to combine this into a single return statement if the content is
    // computable as a JsExpr. A JavaScript compiler, such as Closure Compiler, is able to perform
    // the transformation.
    if (isTextTemplate) {
      // We do our own initialization below, so mark it as such.
      jsCodeBuilder.pushOutputVar("output").setOutputVarInited();
    }
    Statement body = visitChildrenReturningCodeChunk(node);

    if (isTextTemplate) {
      VariableDeclaration declare =
          VariableDeclaration.builder("output").setMutable().setRhs(LITERAL_EMPTY_STRING).build();
      jsCodeBuilder.popOutputVar();
      body =
          Statement.of(declare, body, returnValue(sanitize(declare.ref(), node.getContentKind())));
    }
    return Statement.of(typeChecks, body);
  }

  /**
   * Generates main template function body for Soy elements. Specifically, generates code to create
   * a new instance of the element class and invoke its #render method.
   */
  private Statement generateFunctionBodyForSoyElement(TemplateElementNode node) {
    String soyElementClassName = this.getSoyElementClassName();
    String tplName =
        node.getHtmlElementMetadata().getFinalCallee().isEmpty()
            ? node.getTemplateName()
            : node.getHtmlElementMetadata().getFinalCallee();
    Expression firstElementKey =
        // Since Soy element roots cannot have manual keys (see go/soy-element-keyed-roots),
        // this will always be the first element key.
        JsRuntime.XID.call(Expression.stringLiteral(tplName + "-root"));
    List<Expression> params =
        Arrays.asList(
            INCREMENTAL_DOM,
            id(soyElementClassName),
            firstElementKey,
            Expression.stringLiteral(node.getHtmlElementMetadata().getTag()),
            JsRuntime.OPT_DATA,
            JsRuntime.IJ_DATA,
            id(soyElementClassName + "Render"));
    return Statement.of(
        VariableDeclaration.builder("soyEl")
            .setRhs(SOY_IDOM.dotAccess("$$handleSoyElement").call(params))
            .build(),
        Statement.ifStatement(
                id("soyEl"),
                Statement.of(
                    id("soyEl")
                        .dotAccess("renderInternal")
                        .call(INCREMENTAL_DOM, JsRuntime.OPT_DATA)
                        .asStatement()))
            .build());
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

  /** Generates class expression for the given template node, provided it is a Soy element. */
  private VariableDeclaration generateClassForSoyElement(
      String soyElementClassName, String soyElementAccessorName, TemplateElementNode node) {

    String paramsType = hasOnlyImplicitParams(node) ? "null" : "!" + alias + ".Params";

    ImmutableList.Builder<MethodDeclaration> stateMethods = ImmutableList.builder();
    for (TemplateStateVar stateVar : node.getStateVars()) {
      stateMethods.addAll(
          this.generateStateMethodsForSoyElementClass(soyElementClassName, stateVar));
    }
    ImmutableList.Builder<MethodDeclaration> parameterMethods = ImmutableList.builder();
    for (TemplateParam param : node.getParams()) {
      if (param.isImplicit()) {
        continue;
      }
      parameterMethods.add(
          this.generateGetParamMethodForSoyElementClass(
              param, /* isAbstract= */ false, /* isInjected= */ false));
    }
    ImmutableList.Builder<MethodDeclaration> injectedParameterMethods = ImmutableList.builder();
    for (TemplateParam injectedParam : node.getInjectedParams()) {
      injectedParameterMethods.add(
          this.generateGetParamMethodForSoyElementClass(
              injectedParam, /* isAbstract= */ false, /* isInjected= */ true));
    }

    ImmutableList.Builder<Statement> stateVarInitializations = ImmutableList.builder();
    stateVarInitializations.add(generateInitInternal(node));
    if (hasNonConstantState) {
      stateVarInitializations.add(
          Statement.assign(
              Expression.THIS.dotAccess("syncStateFromData"),
              id(soyElementClassName + "SyncInternal")));
    }
    // Build constructor method.
    Statement ctorBody =
        Statement.of(
            id("super").call().asStatement(), Statement.of(stateVarInitializations.build()));
    MethodDeclaration constructorMethod =
        MethodDeclaration.create("constructor", JsDoc.builder().build(), ctorBody);
    ImmutableList.Builder<MethodDeclaration> builder = ImmutableList.builder();
    builder.add(constructorMethod);

    ClassExpression soyElementClass =
        ClassExpression.create(
            SOY_IDOM.dotAccess("$SoyElement"),
            builder
                .addAll(stateMethods.build())
                .addAll(parameterMethods.build())
                .addAll(injectedParameterMethods.build())
                .build());
    String elementAccessor = soyElementClassName + "Interface";
    return VariableDeclaration.builder(soyElementClassName)
        .setJsDoc(
            JsDoc.builder()
                .addAnnotation(
                    "extends", "{soyIdom.$SoyElement<" + paramsType + ",!" + elementAccessor + ">}")
                .addParameterizedAnnotation("implements", soyElementAccessorName)
                .build())
        .setRhs(soyElementClass)
        .build();
  }

  /** Generates class expression for the given template node, provided it is a Soy element. */
  private VariableDeclaration generateAccessorInterface(
      String className, TemplateElementNode node) {
    ImmutableList.Builder<MethodDeclaration> parameterMethods = ImmutableList.builder();
    for (TemplateParam param : node.getParams()) {
      if (param.isImplicit()) {
        continue;
      }
      parameterMethods.add(
          this.generateGetParamMethodForSoyElementClass(
              param, /* isAbstract= */ true, /* isInjected= */ false));
    }
    ImmutableList.Builder<MethodDeclaration> injectedParameterMethods = ImmutableList.builder();
    for (TemplateParam injectedParam : node.getInjectedParams()) {
      injectedParameterMethods.add(
          this.generateGetParamMethodForSoyElementClass(
              injectedParam, /* isAbstract= */ true, /* isInjected= */ true));
    }

    ClassExpression soyElementClass =
        ClassExpression.create(
            ImmutableList.<MethodDeclaration>builder()
                .addAll(parameterMethods.build())
                .addAll(injectedParameterMethods.build())
                .build());
    return VariableDeclaration.builder(className)
        .setJsDoc(JsDoc.builder().addAnnotation("interface").build())
        .setRhs(soyElementClass)
        .build();
  }

  private static Statement generateExportsForSoyElement(String soyElementClassName) {
    return Statement.assign(
        // Idom only supports goog.module generation.
        JsRuntime.EXPORTS.dotAccess(
            // Drop the leading '$' from soyElementClassName.
            soyElementClassName.substring(1)),
        id(soyElementClassName));
  }

  /**
   * Generates `getFoo` (index 0) and `setFoo` (index 1) methods for a given `foo` state variable.
   */
  private ImmutableList<MethodDeclaration> generateStateMethodsForSoyElementClass(
      String soyElementClassName, TemplateStateVar stateVar) {
    JsType jsType = JsType.forIncrementalDomState(stateVar.type());
    String stateAccessorSuffix =
        Ascii.toUpperCase(stateVar.name().substring(0, 1)) + stateVar.name().substring(1);
    Expression stateValue = id("this").dotAccess(STATE_PREFIX + stateVar.name());
    MethodDeclaration getStateMethod =
        MethodDeclaration.create(
            "get" + stateAccessorSuffix,
            JsDoc.builder().addParameterizedAnnotation("return", jsType.typeExpr()).build(),
            Statement.returnValue(stateValue));

    jsType = JsType.forIncrementalDomState(stateVar.type());
    ImmutableList.Builder<Statement> setStateMethodStatements = ImmutableList.builder();
    Optional<Expression> typeAssertion =
        jsType.getSoyParamTypeAssertion(
            id(stateVar.name()),
            stateVar.name(),
            /* paramKind= */ "@state",
            templateTranslationContext.codeGenerator());
    if (typeAssertion.isPresent()) {
      setStateMethodStatements.add(typeAssertion.get().asStatement());
    }
    setStateMethodStatements.add(
        stateValue.assign(id(stateVar.name())).asStatement(), Statement.returnValue(id("this")));
    MethodDeclaration setStateMethod =
        MethodDeclaration.create(
            "set" + stateAccessorSuffix,
            JsDoc.builder()
                .addParam(stateVar.name(), jsType.typeExpr())
                .addParameterizedAnnotation("return", "!" + soyElementClassName)
                .build(),
            Statement.of(setStateMethodStatements.build()));
    return ImmutableList.of(getStateMethod, setStateMethod);
  }

  /** Generates `get[X]` for a given parameter value. */
  private MethodDeclaration generateGetParamMethodForSoyElementClass(
      TemplateParam param, boolean isAbstract, boolean isInjected) {
    JsType jsType = JsType.forJsSrcStrict(param.type());
    String accessorSuffix =
        Ascii.toUpperCase(param.name().substring(0, 1)) + param.name().substring(1);
    if (isAbstract) {
      return MethodDeclaration.create(
          "get" + accessorSuffix,
          JsDoc.builder()
              .addAnnotation("abstract")
              // Injected params are marked as optional, see:
              .addParameterizedAnnotation("return", jsType.typeExpr())
              .build(),
          Statement.of(ImmutableList.of()));
    }
    Expression value = id("this").dotAccess(isInjected ? "ijData" : "data").dotAccess(param.name());
    if (param.hasDefault()) {
      value =
          templateTranslationContext
              .codeGenerator()
              .declarationBuilder()
              .setMutable()
              .setRhs(value)
              .build()
              .ref();
      value =
          value.withInitialStatement(
              genParamDefault(param, value, jsType, templateTranslationContext.codeGenerator()));
    }
    // Injected params are marked as optional to account for unused templates, see:
    // We can assert the presence of the injected param if it being called.
    Optional<Expression> typeAssertion =
        isInjected
            ? jsType.getSoyParamTypeAssertion(
                value,
                param.name(),
                /* paramKind= */ "@inject",
                templateTranslationContext.codeGenerator())
            : Optional.empty();
    return MethodDeclaration.create(
        "get" + accessorSuffix,
        JsDoc.builder().addAnnotation("override").addAnnotation("public").build(),
        Statement.returnValue(typeAssertion.orElse(value)));
  }

  /** Constructs template class name, e.g. converts template `ns.foo` => `$FooElement`. */
  private String getSoyElementClassName() {
    Preconditions.checkState(
        alias.startsWith("$"),
        "Alias should start with '$', or template class name may be malformed.");
    return "$"
        // `alias` has '$' as the 0th char, so capitalize the 1st char.
        + Ascii.toUpperCase(alias.charAt(1))
        /// ...and concat the rest of the alias.
        + alias.substring(2)
        + "Element";
  }

  /**
   * Generates the content of a {@code let} or {@code param} statement. For HTML and attribute
   * let/param statements, the generated instructions inside the node are wrapped in a function
   * which will be optionally passed to another template and invoked in the correct location. All
   * other kinds of let statements are generated as a simple variable.
   */
  private void visitLetParamContentNode(RenderUnitNode node, String generatedVarName) {
    // The html transform step, performed by HtmlContextVisitor, ensures that
    // we always have a content kind specified.
    checkState(node.getContentKind() != null);

    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    SanitizedContentKind prevContentKind = jsCodeBuilder.getContentKind();

    jsCodeBuilder.setContentKind(node.getContentKind());

    CodeChunk definition;
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
      definition =
          builder
              .setRhs(
                  constructor.call(
                      Expression.arrowFunction(jsdoc, visitChildrenReturningCodeChunk(node))))
              .build();
    } else {
      // We do our own initialization, so mark it as such.
      String outputVarName = generatedVarName + "_output";
      jsCodeBuilder.pushOutputVar(outputVarName).setOutputVarInited();

      // TODO(b/246994962): Skip this definition for SanitizedContentKind.TEXT.
      definition =
          Statement.of(
              VariableDeclaration.builder(outputVarName)
                  .setMutable()
                  .setRhs(LITERAL_EMPTY_STRING)
                  .build(),
              visitChildrenReturningCodeChunk(node),
              builder
                  .setRhs(
                      kind == SanitizedContentKind.TEXT
                          ? id(outputVarName)
                          : JsRuntime.sanitizedContentOrdainerFunctionForInternalBlocks(
                                  node.getContentKind())
                              .call(id(outputVarName)))
                  .build());
      jsCodeBuilder.popOutputVar();
    }

    jsCodeBuilder.setContentKind(prevContentKind);
    jsCodeBuilder.append(definition);
  }

  /**
   * Generates the content of a {@code let} statement. For HTML and attribute let statements, the
   * generated instructions inside the node are wrapped in a function which will be optionally
   * passed to another template and invoked in the correct location. All other kinds of let/param
   * statements are generated as a simple variable.
   */
  @Override
  protected void visitLetContentNode(LetContentNode node) {
    String generatedVarName = node.getUniqueVarName();
    visitLetParamContentNode(node, generatedVarName);
    templateTranslationContext.soyToJsVariableMappings().put(node.getVar(), id(generatedVarName));
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

    Expression call;
    Optional<SanitizedContentKind> kind = Metadata.getCallContentKind(fileSetMetadata, node);
    GenCallCodeUtils.Callee callee =
        genCallCodeUtils.genCallee(node, templateAliases, getExprTranslator());
    Supplier<Expression> objToPass =
        () ->
            genCallCodeUtils.genObjToPass(
                node,
                templateAliases,
                templateTranslationContext,
                errorReporter,
                getExprTranslator());
    Optional<Supplier<List<Expression>>> positionalParameters = Optional.empty();
    if (genCallCodeUtils.canPerformPositionalCall(node)) {
      positionalParameters =
          Optional.of(
              () ->
                  genCallCodeUtils.getPositionalParams(
                      (CallBasicNode) node,
                      templateAliases,
                      templateTranslationContext,
                      errorReporter,
                      getExprTranslator(),
                      GenCallCodeUtils.hasVariant(node)));
    }
    boolean shouldPushKey = false;

    // TODO(lukes): instead of these helper functions callDynamicXXX for managing context mismatches
    // maybe we should create IncrementalDomRenderer subtypes that can perform the coercions, this
    // would be similar to how jbcsrc manages streaming escapers.

    switch (node.getHtmlContext()) {
      case HTML_TAG:
        if (!kind.isPresent() || kind.get() != SanitizedContentKind.ATTRIBUTES) {
          call =
              SOY_IDOM_CALL_DYNAMIC_ATTRIBUTES.call(
                  INCREMENTAL_DOM, callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
        } else {
          call =
              directCall(
                  callee,
                  node,
                  getExprTranslator(),
                  positionalParameters,
                  objToPass,
                  /*isIdomCall=*/ true);
        }
        break;
      case CSS:
        call =
            SOY_IDOM_CALL_DYNAMIC_CSS.call(
                INCREMENTAL_DOM, callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
        break;
      case JS:
        call =
            SOY_IDOM_CALL_DYNAMIC_JS.call(
                INCREMENTAL_DOM, callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
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
          textCall =
              SOY_IDOM_CALL_DYNAMIC_TEXT.call(
                  callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
        } else {
          // This is executed in the case of TEXT Context -> Text Template
          textCall =
              directCall(
                  callee,
                  node,
                  getExprTranslator(),
                  positionalParameters,
                  objToPass,
                  /*isIdomCall=*/ false);
        }
        getJsCodeBuilder()
            .addChunkToOutputVar(GenCallCodeUtils.applyEscapingDirectives(textCall, node));
        return;
      default:
        if (!kind.isPresent() || !kind.get().isHtml()) {
          call =
              SOY_IDOM_CALL_DYNAMIC_HTML.call(
                  INCREMENTAL_DOM, callee.objectStyle(), objToPass.get(), JsRuntime.IJ_DATA);
          shouldPushKey = true;
        } else {
          // This is executed in the case of HTML/ATTR -> HTML/ATTR. All other ambiguous cases are
          // passed through to runtime functions.
          call =
              directCall(
                  callee,
                  node,
                  getExprTranslator(),
                  positionalParameters,
                  objToPass,
                  /*isIdomCall=*/ true);
          shouldPushKey = true;
        }
        break;
    }

    String keyVariable = "_keyVariable" + staticsCounter++;
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
        getJsCodeBuilder()
            .append(INCREMENTAL_DOM_PUSH_MANUAL_KEY.call(translateExpr(node.getKeyExpr())));
      } else if (!delegatesToTemplate) {
        if (!delegatesToTemplate) {
          getJsCodeBuilder()
              .append(
                  VariableDeclaration.builder(keyVariable)
                      .setRhs(
                          INCREMENTAL_DOM_PUSH_KEY.call(
                              JsRuntime.XID.call(
                                  Expression.stringLiteral(node.getTemplateCallKey()))))
                      .build());
        }
      }
    }
    // TODO: In reality, the CALL_X functions are really just IDOM versions of the related
    // escaping directives. Consider doing a replace instead of not using escaping directives
    // at all.
    if (node.getHtmlContext() == HtmlContext.JS) {
      getJsCodeBuilder().addChunkToOutputVar(call);
    } else {
      getJsCodeBuilder().append(call);
    }
    if (shouldPushKey) {
      if (node.getKeyExpr() != null) {
        getJsCodeBuilder().append(INCREMENTAL_DOM_POP_MANUAL_KEY.call());
      } else if (!delegatesToTemplate) {
        getJsCodeBuilder().append(INCREMENTAL_DOM_POP_KEY.call(Expression.id(keyVariable)));
      }
    }
  }

  private static Expression directCall(
      GenCallCodeUtils.Callee callee,
      CallNode callNode,
      TranslateExprNodeVisitor exprTranslator,
      Optional<Supplier<List<Expression>>> positionalParameters,
      Supplier<Expression> paramObject,
      boolean isIdomCall) {
    List<Expression> params = new ArrayList<>();
    if (isIdomCall) {
      params.add(INCREMENTAL_DOM);
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
  protected void visitIfNode(IfNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    SanitizedContentKind currentContentKind = jsCodeBuilder.getContentKind();

    if (!isTextContent(currentContentKind)) {
      super.generateNonExpressionIfNode(node);
    } else {
      super.visitIfNode(node);
    }
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
  protected TranslateExprNodeVisitor getExprTranslator() {
    return new IncrementalDomTranslateExprNodeVisitor(
        javaScriptValueFactory,
        templateTranslationContext,
        templateAliases,
        errorReporter,
        dataSource);
  }

  @Override
  protected void visitHtmlCommentNode(HtmlCommentNode node) {
    String id = "html_comment_" + node.getId();
    getJsCodeBuilder()
        .append(
            VariableDeclaration.builder(id)
                .setMutable()
                .setRhs(Expression.LITERAL_EMPTY_STRING)
                .build());
    getJsCodeBuilder().pushOutputVar(id).setOutputVarInited();
    SanitizedContentKind prev = getJsCodeBuilder().getContentKind();
    getJsCodeBuilder().setContentKind(SanitizedContentKind.TEXT);
    for (int i = 0; i < node.numChildren(); i++) {
      visit(node.getChild(i));
    }
    getJsCodeBuilder().append(SOY_IDOM_VISIT_HTML_COMMENT.call(INCREMENTAL_DOM, id(id)));
    getJsCodeBuilder().popOutputVar();
    getJsCodeBuilder().setContentKind(prev);
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
  protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();

    if (node.hasValue()) {
      // Attribute keys can only be print statements or constants. As such, the first child
      // should be the key and the second the value.
      checkState(isComputableAsJsExprsVisitor.exec(node.getChild(0)));

      jsCodeBuilder.append(
          INCREMENTAL_DOM_ATTR.call(
              // Attributes can only be print nodes or constants
              genJsExprsVisitor.exec(node.getChild(0)).get(0),
              CodeChunkUtils.concatChunksForceString(getAttributeValues(node))));
    } else {
      visitChildren(node); // Prints raw text or attributes node.
    }
  }

  @Override
  protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    // ignore quotes since idom doesn't care about them, so we just iterate the children.
    visitChildren(node);
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
      getJsCodeBuilder().pushOutputVar(outputVar).setOutputVarInited();
      SanitizedContentKind prev = getJsCodeBuilder().getContentKind();
      getJsCodeBuilder().setContentKind(SanitizedContentKind.TEXT);
      IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
      jsCodeBuilder.append(
          VariableDeclaration.builder(outputVar)
              .setMutable()
              .setRhs(Expression.LITERAL_EMPTY_STRING)
              .build());
      visit(value);
      getJsCodeBuilder().popOutputVar();
      getJsCodeBuilder().setContentKind(prev);
      return ImmutableList.of(id(outputVar));
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
  private Expression getStaticContent(HtmlAttributeNode node) {
    if (!node.hasValue()) {
      return Expression.stringLiteral("");
    }
    // This case is some control flow like a switch, if, or for loop.
    if (!(node.getChild(1) instanceof HtmlAttributeValueNode)) {
      return null;
    }
    HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) node.getChild(1);
    if (attrValue.numChildren() == 0) {
      return Expression.stringLiteral("");
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
              && fnNode.getSoyFunction() != BuiltinFunction.CSS) {
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
    return CodeChunkUtils.concatChunksForceString(getAttributeValues(node));
  }

  private Expression getOpenSSRCall(HtmlOpenTagNode node) {
    List<Expression> args = new ArrayList<>();
    args.add(getTagNameCodeChunk(node.getTagName()));

    KeyNode keyNode = node.getKeyNode();
    if (keyNode == null) {
      args.add(JsRuntime.XID.call(Expression.stringLiteral(node.getKeyId())));
    } else {
      // Key difference between getOpen and getOpenSSR
      args.add(translateExpr(node.getKeyNode().getExpr()));
    }
    if (node.isElementRoot()) {
      Expression paramsObject;
      if (generatePositionalParamsSignature) {
        paramsObject =
            Expression.arrayLiteral(
                node.getNearestAncestor(TemplateNode.class).getParams().stream()
                    .map(p -> id(genParamAlias(p.name())))
                    .collect(toImmutableList()));
      } else {
        paramsObject = JsRuntime.OPT_DATA;
      }
      args.add(
          Expression.ifExpression(JsRuntime.GOOG_DEBUG, paramsObject)
              .setElse(Expression.LITERAL_UNDEFINED)
              .build(templateTranslationContext.codeGenerator()));
    }
    return INCREMENTAL_DOM_OPEN_SSR.call(args);
  }

  private Expression getOpenCall(HtmlOpenTagNode node) {
    List<Expression> args = new ArrayList<>();
    args.add(getTagNameCodeChunk(node.getTagName()));

    KeyNode keyNode = node.getKeyNode();
    Expression key = Expression.LITERAL_UNDEFINED;
    if (keyNode == null) {
      key = JsRuntime.XID.call(Expression.stringLiteral(node.getKeyId()));
    }
    args.add(key);

    return INCREMENTAL_DOM_OPEN.call(args);
  }

  private Optional<Expression> getApplyStaticAttributes(HtmlOpenTagNode node) {
    Map<String, Expression> staticAttributes = getStaticAttributes(node);
    ImmutableList.Builder<Expression> staticsBuilder = ImmutableList.builder();
    for (Map.Entry<String, Expression> entry : staticAttributes.entrySet()) {
      staticsBuilder.add(Expression.stringLiteral(entry.getKey()));
      staticsBuilder.add(entry.getValue());
    }
    // Instead of inlining the array, place the variable declaration in the global scope
    // and lazily initialize it in the template.
    if (!staticAttributes.isEmpty()) {
      String id = "_statics_" + staticsCounter++;
      Expression idExpr = id(alias + id);
      Expression lazyAssignment =
          // Generator can be null because we know this evaluates to an or
          // ie alias_statics_1 || alias_statics_1 = []
          idExpr.or(
              idExpr.assign(Expression.arrayLiteral(staticsBuilder.build())),
              /* codeGenerator= */ null);
      staticVarDeclarations.add(VariableDeclaration.builder(alias + id).build());
      return Optional.of(INCREMENTAL_DOM_APPLY_STATICS.call(lazyAssignment));
    }
    return Optional.empty();
  }

  private Expression getApplyAttrs(HtmlOpenTagNode node) {
    for (int i = 1; i < node.numChildren(); i++) {
      visit(node.getChild(i));
    }
    return INCREMENTAL_DOM_APPLY_ATTRS.call();
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
  protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    getJsCodeBuilder().appendLine("// " + node.getSourceLocation());
    if (!node.isSkipRoot()) {
      if (node.getKeyNode() != null) {
        // Push key BEFORE emitting `elementOpen`. Later, for `elementOpen` calls of keyed elements,
        // we do not specify any key.
        Expression key = translateExpr(node.getKeyNode().getExpr());
        getJsCodeBuilder().append(INCREMENTAL_DOM_PUSH_MANUAL_KEY.call(key));
      }
      Expression openTagExpr = getOpenCall(node);
      jsCodeBuilder.append(openTagExpr.asStatement());
    }
    jsCodeBuilder.append(getAttributeAndCloseCalls(node));
    String tagName = node.getTagName().getTagString();
    if (tagName != null && Ascii.equalsIgnoreCase(tagName, "script")) {
      scriptOutputVar = "script_" + staticsCounter++;
      jsCodeBuilder.pushOutputVar(scriptOutputVar).setOutputVarInited();
      jsCodeBuilder.appendLine("let ", scriptOutputVar, " = '';");
      jsCodeBuilder.setContentKind(SanitizedContentKind.JS);
    }
  }

  private String scriptOutputVar;

  private Statement getAttributeAndCloseCalls(HtmlOpenTagNode node) {
    List<Statement> statements = new ArrayList<>();
    Optional<Expression> maybeApplyStatics = getApplyStaticAttributes(node);
    Expression close = node.isElementRoot() ? INCREMENTAL_DOM_ELEMENT_CLOSE : INCREMENTAL_DOM_CLOSE;
    if (maybeApplyStatics.isPresent()) {
      statements.add(maybeApplyStatics.get().asStatement());
    }
    statements.add(getApplyAttrs(node).asStatement());

    // Whether or not it is valid for this tag to be self closing has already been validated by the
    // HtmlContextVisitor.  So we just need to output the close instructions if the node is self
    // closing or definitely void.
    if (node.isSelfClosing() || node.getTagName().isDefinitelyVoid()) {
      statements.add(close.call().asStatement());
    }
    return Statement.of(statements);
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
  protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
    // This case occurs in the case where we encounter the end of a keyed element.
    getJsCodeBuilder().appendLine("// " + node.getSourceLocation());
    String tagName = node.getTagName().getTagString();
    if (tagName != null && Ascii.equalsIgnoreCase(tagName, "script")) {
      getJsCodeBuilder().popOutputVar();
      Expression ordainer = id("soy").dotAccess("VERY_UNSAFE").dotAccess("ordainSanitizedJs");
      Expression safeScript = ordainer.call(id(scriptOutputVar)).dotAccess("toSafeScript").call();
      GoogRequire require = GoogRequire.createWithAlias("goog.html.SafeScript", "SafeScript");
      Expression unwrapped = require.dotAccess("unwrapTrustedScript").call(safeScript);
      Expression currentElement = INCREMENTAL_DOM.dotAccess("currentElement").call();
      Expression textContentAssignment = currentElement.dotAccess("textContent").assign(unwrapped);
      ConditionalBuilder ifCurrentElementExists =
          Statement.ifStatement(currentElement, textContentAssignment.asStatement());
      getJsCodeBuilder().append(ifCurrentElementExists.build());
      getJsCodeBuilder().append(INCREMENTAL_DOM.dotAccess("skipNode").call());
      getJsCodeBuilder().setContentKind(SanitizedContentKind.HTML);
    }
    if (node.getTaggedPairs().size() == 1) {
      HtmlOpenTagNode openTag = (HtmlOpenTagNode) node.getTaggedPairs().get(0);
      if (openTag.getKeyNode() != null && !(openTag.getParent() instanceof SkipNode)) {
        getJsCodeBuilder().append(INCREMENTAL_DOM_POP_MANUAL_KEY.call());
      }
    }

    Expression close = INCREMENTAL_DOM_CLOSE;
    if (node.getTaggedPairs().size() == 1
        && ((HtmlOpenTagNode) node.getTaggedPairs().get(0)).isElementRoot()) {
      close = INCREMENTAL_DOM_ELEMENT_CLOSE;
    }

    if (!node.getTagName().isDefinitelyVoid()) {
      getJsCodeBuilder().append(close.call().asStatement());
    }
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
    Expression textArg = stringLiteral(node.getRawText());
    JsCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    switch (node.getHtmlContext()) {
      case CSS:
      case HTML_RCDATA:
      case HTML_PCDATA:
        // Note - we don't use generateTextCall since this text can never be null.
        jsCodeBuilder.append(INCREMENTAL_DOM_TEXT.call(textArg));
        break;
      case HTML_TAG:
        jsCodeBuilder.append(INCREMENTAL_DOM_ATTR.call(textArg, stringLiteral("")));
        break;
      default:
        jsCodeBuilder.addChunkToOutputVar(textArg);
        break;
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
    List<Expression> chunks = genJsExprsVisitor.exec(node);
    switch (node.getHtmlContext()) {
      case HTML_TAG:
        getJsCodeBuilder()
            .append(
                SOY_IDOM_PRINT_DYNAMIC_ATTR.call(
                    INCREMENTAL_DOM, CodeChunkUtils.concatChunks(chunks)));
        break;
      case JS:
        getJsCodeBuilder().addChunkToOutputVar(CodeChunkUtils.concatChunks(chunks));
        break;
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
          getJsCodeBuilder()
              .append(
                  SOY_IDOM_PRINT.call(
                      INCREMENTAL_DOM,
                      CodeChunkUtils.concatChunks(chunks),
                      Expression.LITERAL_TRUE));
        } else {
          getJsCodeBuilder()
              .append(SOY_IDOM_PRINT.call(INCREMENTAL_DOM, CodeChunkUtils.concatChunks(chunks)));
        }
        break;
      case HTML_RCDATA:
        getJsCodeBuilder()
            .append(
                INCREMENTAL_DOM_TEXT.call(id("String").call(CodeChunkUtils.concatChunks(chunks))));
        break;
      default:
        super.visitPrintNode(node);
        break;
    }
  }

  @Override
  protected void visitSkipNode(SkipNode node) {
    IncrementalDomCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    HtmlOpenTagNode openTag = (HtmlOpenTagNode) node.getChild(0);
    Expression openTagExpr = getOpenSSRCall(openTag);
    Statement childStatements = visitChildrenReturningCodeChunk(node);
    jsCodeBuilder.append(Statement.ifStatement(openTagExpr, Statement.of(childStatements)).build());
  }

  @Override
  protected void visitVeLogNode(VeLogNode node) {
    VeLogStateHolder state = openVeLogNode(node);
    getJsCodeBuilder().append(state.enterStatement);
    visitChildren(node);
    getJsCodeBuilder().append(exitVeLogNode(node, state.logOnlyConditional));
  }

  VeLogStateHolder openVeLogNode(VeLogNode node) {
    Expression isLogOnly = Expression.LITERAL_FALSE;
    VariableDeclaration isLogOnlyVar = null;
    Expression isLogOnlyReference = null;
    List<Statement> stmts = new ArrayList<>();
    if (node.getLogonlyExpression() != null) {
      String idName = "velog_" + staticsCounter++;
      isLogOnlyReference = id(idName);
      isLogOnly = getExprTranslator().exec(node.getLogonlyExpression());
      isLogOnlyVar = VariableDeclaration.builder(idName).setRhs(isLogOnly).build();
      stmts.add(isLogOnlyVar);
      stmts.add(
          Statement.ifStatement(
                  INCREMENTAL_DOM_VERIFY_LOGONLY.call(isLogOnlyVar.ref()),
                  Statement.assign(INCREMENTAL_DOM, INCREMENTAL_DOM_TONULL.call()))
              .build());
    }
    Expression veData = getExprTranslator().exec(node.getVeDataExpression());
    stmts.add(INCREMENTAL_DOM_ENTER.call(veData, isLogOnly).asStatement());
    return new VeLogStateHolder(isLogOnlyReference, Statement.of(stmts));
  }

  Statement exitVeLogNode(VeLogNode node, @Nullable Expression isLogOnly) {
    Statement exit = INCREMENTAL_DOM_EXIT.call().asStatement();
    if (isLogOnly != null) {
      return Statement.of(
          exit,
          Statement.ifStatement(
                  isLogOnly, Statement.assign(INCREMENTAL_DOM, INCREMENTAL_DOM_TODEFAULT.call()))
              .build());
    }
    return exit;
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    Expression msgExpression;
    switch (node.getHtmlContext()) {
      case HTML_PCDATA:
        String id = "_msg_" + alias + "_" + staticsCounter++;
        VariableDeclaration staticDecl =
            VariableDeclaration.builder(id)
                .setRhs(Expression.objectLiteral(ImmutableMap.of()))
                .build();
        staticVarDeclarations.add(staticDecl);
        CodeChunk chunk =
            new AssistantForHtmlMsgs(
                    /* master= */ this,
                    jsSrcOptions,
                    genCallCodeUtils,
                    isComputableAsJsExprsVisitor,
                    templateAliases,
                    genJsExprsVisitor,
                    templateTranslationContext,
                    errorReporter,
                    id)
                .generateMsgGroupCode(node);
        getJsCodeBuilder().append(chunk);
        break;
        // Messages in attribute values are plain text. However, since the translated content
        // includes entities (because other Soy backends treat these messages as HTML source), we
        // must unescape the translations before passing them to the idom APIs.
      case HTML_NORMAL_ATTR_VALUE:
        msgExpression =
            new AssistantForAttributeMsgs(
                    /* master= */ this,
                    jsSrcOptions,
                    genCallCodeUtils,
                    isComputableAsJsExprsVisitor,
                    templateAliases,
                    genJsExprsVisitor,
                    templateTranslationContext,
                    errorReporter)
                .generateMsgGroupVariable(node);
        getJsCodeBuilder().addChunkToOutputVar(GOOG_STRING_UNESCAPE_ENTITIES.call(msgExpression));
        break;
      case HTML_RCDATA:
        msgExpression = getAssistantForMsgs().generateMsgGroupVariable(node);
        getJsCodeBuilder().append(INCREMENTAL_DOM_TEXT.call(id("String").call(msgExpression)));
        break;
      default:
        msgExpression = getAssistantForMsgs().generateMsgGroupVariable(node);
        getJsCodeBuilder().addChunkToOutputVar(msgExpression);
        break;
    }
  }

  @Override
  protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    visitChildren(node);
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
        GenIncrementalDomCodeVisitor master,
        SoyJsSrcOptions jsSrcOptions,
        GenCallCodeUtils genCallCodeUtils,
        IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
        TemplateAliases functionAliases,
        GenJsExprsVisitor genJsExprsVisitor,
        TranslationContext translationContext,
        ErrorReporter errorReporter) {
      super(
          master,
          jsSrcOptions,
          genCallCodeUtils,
          isComputableAsJsExprsVisitor,
          functionAliases,
          genJsExprsVisitor,
          translationContext,
          errorReporter);
    }

    @Override
    protected Expression genGoogMsgPlaceholder(MsgPlaceholderNode msgPhNode) {
      Expression toEscape = super.genGoogMsgPlaceholder(msgPhNode);
      return SOY_ESCAPE_HTML.call(toEscape);
    }
  }

  @Override
  protected boolean isIncrementalDom() {
    return true;
  }
}
