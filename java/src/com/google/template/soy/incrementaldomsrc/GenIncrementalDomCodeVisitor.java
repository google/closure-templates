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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_LIB;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_PARAM_NAME;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_TYPE_ATTRIBUTE;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_TYPE_HTML;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.STATE_PREFIX;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.STATE_VAR_PREFIX;
import static com.google.template.soy.jssrc.dsl.Expressions.EMPTY_OBJECT_LITERAL;
import static com.google.template.soy.jssrc.dsl.Expressions.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Statements.returnValue;
import static com.google.template.soy.jssrc.internal.JsRuntime.ELEMENT_LIB_IDOM;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_ALIAS;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_DATA;
import static com.google.template.soy.soytree.SoyTreeUtils.isConstantExpr;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.incrementaldomsrc.GenIncrementalDomExprsVisitor.GenIncrementalDomExprsVisitorFactory;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.ClassExpression;
import com.google.template.soy.jssrc.dsl.ClassExpression.MethodDeclaration;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.Statements;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.jssrc.internal.CanInitOutputVarVisitor;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.jssrc.internal.JsCodeBuilder;
import com.google.template.soy.jssrc.internal.JsRuntime;
import com.google.template.soy.jssrc.internal.JsType;
import com.google.template.soy.jssrc.internal.StandardNames;
import com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor;
import com.google.template.soy.passes.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TemplateType;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Generates a series of JavaScript control statements and function calls for rendering one or more
 * templates as HTML. This heavily leverages {@link GenJsCodeVisitor}, adding logic to print the
 * function calls and changing how statements are combined.
 */
public final class GenIncrementalDomCodeVisitor extends GenJsCodeVisitor {

  private static final String NAMESPACE_EXTENSION = ".incrementaldom";

  private final Deque<SanitizedContentKind> contentKind;

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
    contentKind = new ArrayDeque<>();
  }

  @Override
  protected JsType getJsTypeForParamForDeclaration(SoyType paramType) {
    return JsType.forIncrementalDomDeclarations(paramType);
  }

  @Override
  protected JsType getJsTypeForParam(SoyType paramType) {
    return JsType.forIncrementalDom(paramType);
  }

  @Override
  protected JsType getJsTypeForParamTypeCheck(SoyType paramType) {
    return JsType.forIncrementalDomTypeChecks(paramType);
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
    SanitizedContentKind kind = node.getContentKind();
    contentKind.push(kind);
    String alias;
    if (node instanceof TemplateDelegateNode) {
      alias = node.getPartialTemplateName();
    } else {
      alias = templateAliases.get(node.getTemplateName());
    }

    if (node instanceof TemplateElementNode) {
      hasNonConstantState = calcHasNonConstantState((TemplateElementNode) node);
    }

    super.visitTemplateNode(node);
    if ((node instanceof TemplateDelegateNode && node.getChildren().isEmpty())) {
      contentKind.pop();
      return;
    }
    if (kind.isHtml() || kind == SanitizedContentKind.ATTRIBUTES) {
      Expression type;
      if (kind.isHtml()) {
        type = SOY_IDOM_TYPE_HTML;
      } else {
        type = SOY_IDOM_TYPE_ATTRIBUTE;
      }
      getJsCodeBuilder()
          .append(
              Statements.assign(
                  id(alias)
                      .castAs(
                          "!" + ELEMENT_LIB_IDOM.alias() + ".IdomFunction",
                          ImmutableSet.of(ELEMENT_LIB_IDOM))
                      .dotAccess("contentKind"),
                  type));
      if (isModifiable(node) && !node.getChildren().isEmpty()) {
        getJsCodeBuilder()
            .append(
                Statements.assign(
                    id(alias + modifiableDefaultImplSuffix)
                        .castAs(
                            "!" + ELEMENT_LIB_IDOM.alias() + ".IdomFunction",
                            ImmutableSet.of(ELEMENT_LIB_IDOM))
                        .dotAccess("contentKind"),
                    type));
      }
    }

    if (node instanceof TemplateElementNode) {
      TemplateElementNode element = (TemplateElementNode) node;
      String elementName = this.getSoyElementClassName(alias);
      String elementAccessor = elementName + "Interface";
      getJsCodeBuilder().appendLine();
      getJsCodeBuilder().append(generateAccessorInterface(elementAccessor, element));
      getJsCodeBuilder().append(generateExportsForSoyElement(elementAccessor));
      getJsCodeBuilder().append(generateRenderInternal(element, alias));
      getJsCodeBuilder().appendNullable(generateSyncInternal(element, alias));
      getJsCodeBuilder()
          .append(generateClassForSoyElement(elementName, elementAccessor, element, alias));
      getJsCodeBuilder().append(generateExportsForSoyElement(elementName));
    }
    contentKind.pop();
  }

  private Statement generateRenderInternal(TemplateElementNode node, String alias) {
    String paramsType = hasOnlyImplicitParams(node) ? "null" : "!" + alias + ".Params";
    String soyElementClassName = this.getSoyElementClassName(alias);
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
        Expressions.function(
            jsDoc,
            // Various parts of the js codegen expects these values to be in the local
            // scope.
            Statements.of(
                VariableDeclaration.builder(StandardNames.DOLLAR_IJDATA)
                    .setRhs(Expressions.THIS.dotAccess("ijData"))
                    .build(),
                Statements.of(
                    node.getStateVars().stream()
                        .map(
                            stateVar ->
                                VariableDeclaration.builder(
                                        STATE_VAR_PREFIX + STATE_PREFIX + stateVar.name())
                                    .setRhs(getStateVarWithCasts(stateVar))
                                    .build())
                        .collect(toImmutableList())),
                generateIncrementalDomRenderCalls(node, alias, /* isPositionalStyle= */ false)));
    return VariableDeclaration.builder(soyElementClassName + "Render")
        .setJsDoc(jsDoc)
        .setRhs(fn)
        .build();
  }

  private Statement generateInitInternal(TemplateElementNode node) {
    ImmutableList.Builder<Statement> stateVarInitializations = ImmutableList.builder();
    JsCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    for (TemplateStateVar stateVar : node.getStateVars()) {
      JsType jsType = JsType.forIncrementalDomState(stateVar.type());
      for (GoogRequire require : jsType.getGoogRequires()) {
        jsCodeBuilder.addGoogRequire(require);
      }

      Expression rhsValue;
      if (isConstantExpr(stateVar.defaultValue())) {
        rhsValue = translateExpr(stateVar.defaultValue());
        if (!rhsValue.hasOuterCast()) {
          rhsValue = rhsValue.castAs(jsType.typeExpr(), jsType.getGoogRequires());
        }
      } else {
        rhsValue = Expressions.LITERAL_UNDEFINED.castAsUnknown();
      }

      JsDoc stateVarJsdoc =
          JsDoc.builder().addParameterizedAnnotation("private", jsType.typeExpr()).build();
      stateVarInitializations.add(
          Statements.assign(
              Expressions.THIS.dotAccess(STATE_PREFIX + stateVar.name()), rhsValue, stateVarJsdoc));
    }
    return Statements.of(stateVarInitializations.build());
  }

  @Nullable
  private Statement generateSyncInternal(TemplateElementNode node, String alias) {
    String soyElementClassName = this.getSoyElementClassName(alias);
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
            Expressions.function(
                jsDoc,
                Statements.of(
                    // Various parts of the js codegen expects these parameters to be in the local
                    // scope.
                    VariableDeclaration.builder(StandardNames.DOLLAR_IJDATA)
                        .setRhs(Expressions.THIS.dotAccess("ijData"))
                        .build(),
                    genSyncStateCalls(node, alias))))
        .build();
  }

  @Override
  protected JsDoc generatePositionalFunctionJsDoc(TemplateNode node, boolean addVariantParam) {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    addInternalCallerParam(jsDocBuilder);
    addIjDataParam(jsDocBuilder, /* forPositionalSignature= */ true);
    maybeAddRenderer(jsDocBuilder, node);
    for (TemplateParam param : paramsInOrder(node)) {
      JsType jsType = getJsTypeForParamForDeclaration(param.type());
      jsDocBuilder.addParam(
          GenJsCodeVisitor.getPositionalParamName(param),
          jsType.typeExpr() + (param.isRequired() ? "" : "="));
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
    addIjDataParam(jsDocBuilder, /* forPositionalSignature= */ false);
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
          Statements.assign(
              OPT_DATA,
              OPT_DATA.or(
                  EMPTY_OBJECT_LITERAL.castAsNoRequire(objectParamName),
                  templateTranslationContext.codeGenerator())));
    }
    if (isPositionalStyle && node instanceof TemplateElementNode) {
      throw new IllegalStateException("elements cannot be compiled into positional style.");
    }
    bodyStatements.add(
        node instanceof TemplateElementNode
            ? this.generateFunctionBodyForSoyElement((TemplateElementNode) node, alias)
            : this.generateIncrementalDomRenderCalls(node, alias, isPositionalStyle));
    return Statements.of(bodyStatements.build());
  }

  private boolean calcHasNonConstantState(TemplateElementNode node) {
    return node.getStateVars().stream().anyMatch(v -> !isConstantExpr(v.defaultValue()));
  }

  private Statement genSyncStateCalls(TemplateElementNode node, String alias) {
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
            Statements.assign(
                Expressions.THIS.dotAccess(prefix + headerVar.name()),
                translateExpr(headerVar.defaultValue())));
      }
      haveVisitedLastNonConstVar = haveVisitedLastNonConstVar || headerVar.equals(lastNonConstVar);
      // Only need to alias if there's another non-constant var to come that may reference this.
      if (!haveVisitedLastNonConstVar) {
        stateReassignmentBuilder.add(
            VariableDeclaration.builder(STATE_VAR_PREFIX + STATE_PREFIX + headerVar.name())
                .setRhs(Expressions.THIS.dotAccess(prefix + headerVar.name()))
                .build());
      }
    }
    List<Statement> assignments = stateReassignmentBuilder.build();
    Statement stateReassignments = Statements.of(assignments);
    return Statements.of(typeChecks, stateReassignments);
  }

  /** Generates idom#elementOpen, idom#elementClose, etc. function calls for the given node. */
  private Statement generateIncrementalDomRenderCalls(
      TemplateNode node, String alias, boolean isPositionalStyle) {
    JsCodeBuilder jsCodeBuilder = getJsCodeBuilder();
    boolean isTextTemplate = isTextContent(node.getContentKind());

    Statement typeChecks = genParamTypeChecks(node, alias, isPositionalStyle);
    // Note: we do not try to combine this into a single return statement if the content is
    // computable as a JsExpr. A JavaScript compiler, such as Closure Compiler, is able to perform
    // the transformation.
    if (isTextTemplate) {
      // We do our own initialization below, so mark it as such.
      jsCodeBuilder.pushOutputVar("output").setOutputVarInited();
    }
    Statement body =
        Statements.of(
            new GenIncrementalDomTemplateBodyVisitor(
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
                    contentKind,
                    staticVarDeclarations,
                    generatePositionalParamsSignature,
                    fileSetMetadata,
                    alias)
                .visitChildren(node));

    if (isTextTemplate) {
      VariableDeclaration declare =
          VariableDeclaration.builder("output").setMutable().setRhs(LITERAL_EMPTY_STRING).build();
      jsCodeBuilder.popOutputVar();
      body =
          Statements.of(declare, body, returnValue(sanitize(declare.ref(), node.getContentKind())));
    }
    return Statements.of(typeChecks, body);
  }

  /**
   * Generates main template function body for Soy elements. Specifically, generates code to create
   * a new instance of the element class and invoke its #render method.
   */
  private Statement generateFunctionBodyForSoyElement(TemplateElementNode node, String alias) {
    String soyElementClassName = this.getSoyElementClassName(alias);
    String tplName =
        node.getHtmlElementMetadata().getFinalCallee().isEmpty()
            ? node.getTemplateName()
            : node.getHtmlElementMetadata().getFinalCallee();
    Expression firstElementKey =
        // Since Soy element roots cannot have manual keys (see go/soy-element-keyed-roots),
        // this will always be the first element key.
        JsRuntime.XID.call(Expressions.stringLiteral(tplName + "-root"));
    List<Expression> params =
        Arrays.asList(
            INCREMENTAL_DOM,
            id(soyElementClassName),
            firstElementKey,
            Expressions.stringLiteral(node.getHtmlElementMetadata().getTag()),
            OPT_DATA,
            JsRuntime.IJ_DATA,
            id(soyElementClassName + "Render"));
    return Statements.of(
        VariableDeclaration.builder("soyEl")
            .setRhs(SOY_IDOM.dotAccess("$$handleSoyElement").call(params))
            .build(),
        Statements.ifStatement(
                id("soyEl"),
                Statements.of(
                    id("soyEl")
                        .dotAccess("renderInternal")
                        .call(INCREMENTAL_DOM, OPT_DATA)
                        .asStatement()))
            .build());
  }

  /** Generates class expression for the given template node, provided it is a Soy element. */
  private VariableDeclaration generateClassForSoyElement(
      String soyElementClassName,
      String soyElementAccessorName,
      TemplateElementNode node,
      String alias) {

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
          Statements.assign(
              Expressions.THIS.dotAccess("syncStateFromData"),
              id(soyElementClassName + "SyncInternal")));
    }
    // Build constructor method.
    Statement ctorBody =
        Statements.of(
            id("super").call().asStatement(), Statements.of(stateVarInitializations.build()));
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
    return Statements.assign(
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
    ImmutableList.Builder<MethodDeclaration> methods = ImmutableList.builder();

    // Generate getters.
    JsType typeForState = JsType.forIncrementalDomDeclarations(stateVar.type());
    JsType typeForGetters = JsType.forIncrementalDomGetters(stateVar.type());
    String stateAccessorSuffix =
        Ascii.toUpperCase(stateVar.name().substring(0, 1)) + stateVar.name().substring(1);
    Expression getterStateValue =
        maybeCastAs(
            id("this").dotAccess(STATE_PREFIX + stateVar.name()), typeForState, typeForGetters);
    methods.add(
        MethodDeclaration.create(
            "get" + stateAccessorSuffix,
            JsDoc.builder().addParameterizedAnnotation("return", typeForGetters.typeExpr()).build(),
            Statements.returnValue(getterStateValue)));

    // Generate setters.
    ImmutableList.Builder<Statement> setStateMethodStatements = ImmutableList.builder();
    JsType typeForSetters = JsType.forIncrementalDomSetters(stateVar.type());
    Optional<Expression> typeAssertion =
        typeForSetters.getSoyParamTypeAssertion(
            id(stateVar.name()),
            stateVar.name(),
            /* paramKind= */ "@state",
            templateTranslationContext.codeGenerator());
    if (typeAssertion.isPresent()) {
      setStateMethodStatements.add(typeAssertion.get().asStatement());
    }
    Expression setterStateValue = id("this").dotAccess(STATE_PREFIX + stateVar.name());
    // TODO(b/230911572): remove this cast when types are always aligned.
    Expression setterParam = maybeCastAs(id(stateVar.name()), typeForSetters, typeForState);
    setStateMethodStatements.add(
        setterStateValue.assign(setterParam).asStatement(), Statements.returnValue(id("this")));
    methods.add(
        MethodDeclaration.create(
            "set" + stateAccessorSuffix,
            JsDoc.builder()
                .addParam(stateVar.name(), typeForSetters.typeExpr())
                .addParameterizedAnnotation("return", "!" + soyElementClassName)
                .build(),
            Statements.of(setStateMethodStatements.build())));

    return methods.build();
  }

  /** Generates `get[X]` for a given parameter value. */
  private MethodDeclaration generateGetParamMethodForSoyElementClass(
      TemplateParam param, boolean isAbstract, boolean isInjected) {
    JsType jsType = JsType.forIncrementalDomGetters(param.type());
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
          Statements.of(ImmutableList.of()));
    }
    // TODO(b/230911572): remove this cast when types are always aligned.
    Expression value =
        maybeCastAs(
            id("this").dotAccess(isInjected ? "ijData" : "data").dotAccess(param.name()),
            JsType.forIncrementalDomState(param.type()),
            jsType);
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
        Statements.returnValue(typeAssertion.orElse(value)));
  }

  /** Constructs template class name, e.g. converts template `ns.foo` => `$FooElement`. */
  private String getSoyElementClassName(String alias) {
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
   * Determines if a given type of content represents text or some sort of HTML.
   *
   * @param contentKind The kind of content to check.
   * @return True if the content represents text, false otherwise.
   */
  private static boolean isTextContent(SanitizedContentKind contentKind) {
    return !contentKind.isHtml() && contentKind != SanitizedContentKind.ATTRIBUTES;
  }

  private static Expression getStateVarWithCasts(TemplateStateVar stateVar) {
    // Access the state variable and cast if the declared type is
    // different from what we would expect it to be inline in a template,
    // as described in the -TypeChecks accessors.
    SoyType stateVarType = stateVar.typeOrDefault(null);
    return maybeCastAs(
        Expressions.THIS.dotAccess(STATE_PREFIX + stateVar.name()),
        JsType.forIncrementalDomState(stateVarType),
        JsType.forIncrementalDomTypeChecks(stateVarType));
  }

  private static Expression maybeCastAs(
      Expression expression, JsType currentType, JsType desiredType) {
    if (!currentType.typeExpr().equals(desiredType.typeExpr())) {
      expression = expression.castAs(desiredType.typeExpr(), desiredType.getGoogRequires());
    }
    return expression;
  }

  @Override
  protected TranslateExprNodeVisitor getExprTranslator() {
    return new IncrementalDomTranslateExprNodeVisitor(
        javaScriptValueFactory,
        templateTranslationContext,
        templateAliases,
        errorReporter,
        OPT_DATA);
  }
}
