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

package com.google.template.soy.jssrc.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.template.soy.jssrc.dsl.Expressions.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Expressions.number;
import static com.google.template.soy.jssrc.dsl.Statements.forLoop;
import static com.google.template.soy.jssrc.dsl.Statements.ifStatement;
import static com.google.template.soy.jssrc.dsl.Statements.switchValue;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_IS_OBJECT;
import static com.google.template.soy.jssrc.internal.JsRuntime.WINDOW_CONSOLE_LOG;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentOrdainerFunction;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.ConditionalBuilder;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.Id;
import com.google.template.soy.jssrc.dsl.SourceMapHelper;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.Statements;
import com.google.template.soy.jssrc.dsl.SwitchBuilder;
import com.google.template.soy.jssrc.dsl.TryCatch;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor.ScopedJsTypeRegistry;
import com.google.template.soy.shared.RangeArgs;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.AssignmentNode;
import com.google.template.soy.soytree.BreakNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.ContinueNode;
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
import com.google.template.soy.soytree.ReturnNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.WhileNode;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnknownType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Visitor for generating the full JS code (i.e. statements) for a template body.
 *
 * <p>Precondition: MsgNode should not exist in the tree.
 */
public class GenJsTemplateBodyVisitor extends AbstractReturningSoyNodeVisitor<Statement> {

  protected final VisitorsState state;

  protected final OutputVarHandler outputVars;

  /** The options for generating JS source code. */
  protected final SoyJsSrcOptions jsSrcOptions;

  protected final JavaScriptValueFactoryImpl javaScriptValueFactory;

  /** Instance of GenCallCodeUtils to use. */
  protected final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  protected final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** The CanInitOutputVarVisitor used by this instance. */
  private final CanInitOutputVarVisitor canInitOutputVarVisitor;

  /** The GenJsExprsVisitor used for the current template. */
  protected final GenJsExprsVisitor genJsExprsVisitor;

  /** The assistant visitor for msgs used for the current template (lazily initialized). */
  private GenJsCodeVisitorAssistantForMsgs assistantForMsgs;

  protected final ErrorReporter errorReporter;
  protected final TranslationContext templateTranslationContext;

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name. This is created on a per {@link SoyFileNode} basis.
   */
  protected final TemplateAliases templateAliases;

  protected final ScopedJsTypeRegistry jsTypeRegistry;

  protected final SourceMapHelper sourceMapHelper;

  protected final boolean mutableLets;

  protected GenJsTemplateBodyVisitor(
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
      ScopedJsTypeRegistry jsTypeRegistry,
      SourceMapHelper sourceMapHelper,
      boolean mutableLets) {
    this.state = checkNotNull(state);
    this.outputVars = checkNotNull(outputVars);
    this.jsSrcOptions = checkNotNull(jsSrcOptions);
    this.javaScriptValueFactory = checkNotNull(javaScriptValueFactory);
    this.genCallCodeUtils = checkNotNull(genCallCodeUtils);
    this.isComputableAsJsExprsVisitor = checkNotNull(isComputableAsJsExprsVisitor);
    this.canInitOutputVarVisitor = checkNotNull(canInitOutputVarVisitor);
    this.genJsExprsVisitor = checkNotNull(genJsExprsVisitor);
    this.errorReporter = checkNotNull(errorReporter);
    this.templateTranslationContext = checkNotNull(templateTranslationContext);
    this.templateAliases = checkNotNull(templateAliases);
    this.jsTypeRegistry = checkNotNull(jsTypeRegistry);
    this.sourceMapHelper = sourceMapHelper;
    this.mutableLets = mutableLets;
  }

  @Override
  public Statement visit(SoyNode node) {
    Statement rv = super.visit(node);
    sourceMapHelper.setPrimaryLocation(rv, node.getSourceLocation());
    return rv;
  }

  protected List<Statement> visitChildren(ParentSoyNode<?> node) {
    List<Statement> statements = new ArrayList<>();

    // If the block is empty or if the first child cannot initialize the output var, we must
    // initialize the output var.
    if (node.numChildren() == 0 || !canInitOutputVarVisitor.exec(node.getChild(0))) {
      outputVars.initOutputVarIfNecessary().ifPresent(statements::add);
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
          statements.add(outputVars.addChunksToOutputVar(consecChunks));
          consecChunks.clear();
        }
        statements.add(visit(child));
      }
    }

    if (!consecChunks.isEmpty()) {
      statements.add(outputVars.addChunksToOutputVar(consecChunks));
      consecChunks.clear();
    }

    return statements;
  }

  /**
   * Visits the children in a new variable scope.
   *
   * <p>Use when the Soy variable scoping rules create a new lexical scope but the JS output does
   * not.
   */
  protected final List<Statement> visitChildrenInNewSoyScope(ParentSoyNode<?> node) {
    try (var scope = templateTranslationContext.enterSoyScope()) {
      return visitChildren(node);
    }
  }

  /**
   * Visits the children in a new variable scope.
   *
   * <p>Use when the Soy variable scoping rules create a new lexical scope and we are creating a JS
   * block scope.
   */
  protected final List<Statement> visitChildrenInNewSoyAndJsScope(ParentSoyNode<?> node) {
    try (var scope = templateTranslationContext.enterSoyAndJsScope()) {
      return visitChildren(node);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  protected GenJsCodeVisitorAssistantForMsgs getAssistantForMsgs() {
    if (assistantForMsgs == null) {
      assistantForMsgs = state.createVisitorAssistantForMsgs(this, genJsExprsVisitor);
    }
    return assistantForMsgs;
  }

  @Override
  protected Statement visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    // TODO(b/33382980): This is not ideal and leads to less than optimal code generation.
    // ideally genJsExprsVisitor could be used here, but it doesn't work due to the way we need
    // to handle placeholder generation.
    return outputVars.addChunkToOutputVar(getAssistantForMsgs().generateMsgGroupVariable(node));
  }

  @Override
  protected Statement visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    throw new AssertionError();
  }

  @Override
  protected Statement visitPrintNode(PrintNode node) {
    return outputVars.addChunksToOutputVar(genJsExprsVisitor.exec(node));
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
  protected Statement visitLetValueNode(LetValueNode node) {
    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    Expression value = translateExpr(node.getExpr());
    if (value.equals(Expressions.LITERAL_NULL)) {
      JsType type = jsTypeRegistry.getWithDelegate(JsType.forJsSrc(), node.getVar().authoredType());
      value =
          value.castAs(
              type.typeExpr(),
              type.googRequires().stream()
                  .map(GoogRequire::toRequireType)
                  .collect(toImmutableSet()));
    }

    // Add a mapping for generating future references to this local var.
    templateTranslationContext.soyToJsVariableMappings().put(node.getVar(), id(generatedVarName));

    return VariableDeclaration.builder(Id.create(generatedVarName))
        .setRhs(value)
        .setIsMutable(mutableLets)
        .build();
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
  protected Statement visitLetContentNode(LetContentNode node) {
    String generatedVarName = node.getUniqueVarName();
    Expression generatedVar = id(generatedVarName);

    // Generate code to define the local var.
    outputVars.pushOutputVar(generatedVarName);

    List<Statement> statements = visitChildrenInNewSoyScope(node);

    outputVars.popOutputVar();

    if (node.getContentKind() != SanitizedContentKind.TEXT) {
      // If the let node had a content kind specified, it was autoescaped in the corresponding
      // context. Hence the result of evaluating the let block is wrapped in a SanitizedContent
      // instance of the appropriate kind.

      // The expression for the constructor of SanitizedContent of the appropriate kind (e.g.,
      // "soydata.VERY_UNSAFE.ordainSanitizedHtml"), or null if the node has no 'kind' attribute.
      // Introduce a new variable for this value since it has a different type from the output
      // variable (SanitizedContent vs String) and this will enable optimizations in the jscompiler
      String wrappedVarName = node.getVarName() + "__wrapped" + node.getId();
      statements.add(
          VariableDeclaration.builder(Id.create(wrappedVarName))
              .setRhs(sanitizedContentOrdainerFunction(node.getContentKind()).call(generatedVar))
              .build());
      generatedVar = id(wrappedVarName);
    }

    // Add a mapping for generating future references to this local var.
    templateTranslationContext.soyToJsVariableMappings().put(node.getVar(), generatedVar);

    return Statements.of(statements);
  }

  @Override
  protected Statement visitAssignmentNode(AssignmentNode node) {
    return Statements.assign(translateExpr(node.getLhs()), translateExpr(node.getRhs()));
  }

  @Override
  protected Statement visitReturnNode(ReturnNode node) {
    return Statements.returnValue(translateExpr(node.getExpr()));
  }

  @Override
  protected Statement visitBreakNode(BreakNode node) {
    return Statements.breakStatement();
  }

  @Override
  protected Statement visitContinueNode(ContinueNode node) {
    return Statements.continueStatement();
  }

  @Override
  protected Statement visitWhileNode(WhileNode node) {
    Statement body = Statements.of(visitChildren(node));
    return Statements.whileLoop(translateExpr(node.getExpr()), body);
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
  protected Statement visitIfNode(IfNode node) {

    if (isComputableAsJsExprsVisitor.exec(node)) {
      return outputVars.addChunksToOutputVar(state.createJsExprsVisitor().exec(node));
    } else {
      return generateNonExpressionIfNode(node);
    }
  }

  /** Generates the JavaScript code for an {if} block that cannot be done as an expression. */
  protected Statement generateNonExpressionIfNode(IfNode node) {
    ConditionalBuilder conditional = null;

    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode condNode = (IfCondNode) child;

        // Convert predicate.
        Expression predicate =
            createExprTranslator()
                .maybeCoerceToBoolean(
                    condNode.getExpr().getType(), translateExpr(condNode.getExpr()), false);
        // Convert body.
        Statement consequent = Statements.of(visitChildrenInNewSoyAndJsScope(condNode));
        // Add if-block to conditional.
        if (conditional == null) {
          conditional = ifStatement(predicate, consequent);
        } else {
          conditional.addElseIf(predicate, consequent);
        }

      } else if (child instanceof IfElseNode) {
        // Convert body.
        Statement trailingElse = Statements.of(visitChildrenInNewSoyAndJsScope((IfElseNode) child));
        // Add else-block to conditional.
        conditional.setElse(trailingElse);
      } else {
        throw new AssertionError();
      }
    }

    return conditional.build();
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
  protected Statement visitSwitchNode(SwitchNode node) {

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
        Statement body = Statements.of(visitChildrenInNewSoyScope(scn));
        switchBuilder.addCase(caseChunks.build(), body);
      } else if (child instanceof SwitchDefaultNode) {
        Statement body = visitSwitchDefaultNode((SwitchDefaultNode) child);
        switchBuilder.setDefault(body);
      } else {
        throw new AssertionError();
      }
    }
    return switchBuilder.build();
  }

  @Override
  protected Statement visitSwitchDefaultNode(SwitchDefaultNode node) {
    return Statements.of(visitChildrenInNewSoyScope(node));
  }

  // js switch statements use === for comparing the switch expr to the cases.  In order to preserve
  // soy equality semantics for sanitized content objects we need to coerce cases and switch exprs
  // to strings.
  private Expression coerceTypeForSwitchComparison(ExprRootNode expr) {
    Expression switchOn = translateExpr(expr);
    SoyType type = expr.getType();
    // If the type is possibly a sanitized content type then we need to toString it.
    // TODO(lukes): this condition is wrong. it should be if is unknown, any or sanitized (or union
    // of sanitized)
    if (SoyTypes.makeNullish(StringType.getInstance()).isAssignableFromStrict(type)
        || type.equals(AnyType.getInstance())
        || type.equals(UnknownType.getInstance())) {
      CodeChunk.Generator codeGenerator = templateTranslationContext.codeGenerator();
      Expression tmp = codeGenerator.declarationBuilder().setRhs(switchOn).build().ref();
      return Expressions.ifExpression(GOOG_IS_OBJECT.call(tmp), tmp.dotAccess("toString").call())
          .setElse(tmp)
          .build(codeGenerator);
    }
    // For everything else just pass through.  switching on objects/collections is unlikely to
    // have reasonably defined behavior.
    return switchOn;
  }

  protected TranslateExprNodeVisitor createExprTranslator() {
    return state.createTranslateExprNodeVisitor();
  }

  protected Expression translateExpr(ExprNode expr) {
    return createExprTranslator().exec(expr);
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
   * <pre>{@code
   * var foo2List = opt_data.boo.foos;
   * var foo2ListLen = foo2List.length;
   * if (foo2ListLen > 0) {
   *   ...
   * }
   * }</pre>
   */
  @Override
  protected Statement visitForNode(ForNode node) {
    // Build some local variable names.
    ForNonemptyNode nonEmptyNode = (ForNonemptyNode) node.getChild(0);
    String varPrefix = nonEmptyNode.getVarName() + node.getId();

    Expression limitInitializer;
    Optional<RangeArgs> args = RangeArgs.createFromNode(node);
    Function<Expression, Expression> getDataItemFunction;
    if (args.isPresent()) {
      RangeArgs range = args.get();
      // if any of the expressions are too expensive, allocate local variables for them
      Expression start =
          maybeStashInLocal(
              range.start().isPresent()
                  ? translateExpr(range.start().get())
                  : Expressions.number(0),
              varPrefix + "_RangeStart");
      Expression end = maybeStashInLocal(translateExpr(range.limit()), varPrefix + "_RangeEnd");
      Expression step =
          maybeStashInLocal(
              range.increment().isPresent()
                  ? translateExpr(range.increment().get())
                  : Expressions.number(1),
              varPrefix + "_RangeStep");
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
      String listVarName = varPrefix + "List";
      Expression listVar =
          VariableDeclaration.builder(Id.create(listVarName))
              .setRhs(
                  // We must cast as readonly since if this ends up being a type union, the value
                  // will be typed as `?` and we can have disambiguation errors.
                  //
                  // TODO(b/242196577): remove this.
                  JsRuntime.SOY_AS_READONLY.call(dataRef))
              .build()
              .ref();
      // does it make sense to store this in a variable?
      limitInitializer = listVar.dotAccess("length");
      getDataItemFunction = listVar::bracketAccess;
    }

    // Generate the foreach body as a CodeChunk.
    Expression limit =
        VariableDeclaration.builder(varPrefix + "ListLen").setRhs(limitInitializer).build().ref();
    Statement foreachBody = handleForeachLoop(nonEmptyNode, limit, getDataItemFunction);

    return foreachBody;
  }

  private Expression maybeStashInLocal(Expression expr, String varName) {
    if (expr.isCheap()) {
      return expr;
    }
    return VariableDeclaration.builder(varName).setRhs(expr).build().ref();
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
    String refPrefix = node.getVarRefName();
    String jsLetPrefix = node.getVarName() + node.getForNodeId();

    // TODO(b/32224284): A more consistent pattern for local variable management.
    String loopIndexName = jsLetPrefix + "Index";
    String dataName = jsLetPrefix + "Data";

    // TODO(b/32224284): This could be a ref() and CodeChunk could handle adding this (if it's used)
    // but if this is used by both branches of an if, it would get separately declared in each. So
    // keep this delcared at the top-level (which is bad in the case this isn't used at all), but we
    // could potentially make the declaration generator code smarter and put this at the top-level.
    VariableDeclaration data =
        VariableDeclaration.builder(dataName)
            .setRhs(getDataItemFunction.apply(id(loopIndexName)))
            .build();

    try (var scope = templateTranslationContext.enterSoyAndJsScope()) {

      // Populate the local var translations with the translations from this node.
      templateTranslationContext.soyToJsVariableMappings().put(refPrefix, id(dataName));

      Id loopIndexId = Id.create(loopIndexName);
      if (node.getIndexVar() != null) {
        templateTranslationContext.soyToJsVariableMappings().put(node.getIndexVar(), loopIndexId);
      }

      // Generate the loop body.
      Statement foreachBody = Statements.of(data, Statements.of(visitChildren(node)));

      // Create the entire for block.
      return forLoop(loopIndexId, limit, foreachBody);
    }
  }

  @Override
  protected Statement visitForNonemptyNode(ForNonemptyNode node) {
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
  protected Statement visitCallNode(CallNode node) {

    List<Statement> statements = new ArrayList<>();

    // If this node has any CallParamContentNode children those contents are not computable as JS
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJsExprsVisitor.exec(child)) {
        statements.add(visit(child));
      }
    }

    // Add the call's result to the current output var.
    Expression call = genCallCodeUtils.gen(node, createExprTranslator());
    if (node.isErrorFallbackSkip()) {
      VariableDeclaration callResult =
          VariableDeclaration.builder(Id.create("call_" + node.getId())).setRhs(call).build();
      return Statements.of(
          outputVars.initOutputVarIfNecessary().orElse(Statements.EMPTY),
          TryCatch.create(
              Statements.of(callResult, outputVars.addChunkToOutputVar(callResult.ref()))));
    } else {
      return outputVars.addChunkToOutputVar(call.withInitialStatements(statements));
    }
  }

  @Override
  protected Statement visitCallParamContentNode(CallParamContentNode node) {

    // This node should only be visited when it's not computable as JS expressions, because this
    // method just generates the code to define the temporary 'param<n>' variable.
    if (isComputableAsJsExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'param<n>' when not computable as JS expressions.");
    }

    outputVars.pushOutputVar("param" + node.getId());

    List<Statement> content = visitChildrenInNewSoyScope(node);

    outputVars.popOutputVar();

    return Statements.of(content);
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
  protected Statement visitLogNode(LogNode node) {

    if (isComputableAsJsExprsVisitor.execOnChildren(node)) {
      List<Expression> logMsgChunks = genJsExprsVisitor.execOnChildren(node);

      return WINDOW_CONSOLE_LOG.call(Expressions.concat(logMsgChunks)).asStatement();
    } else {
      // Must build log msg in a local var logMsg_s##.
      outputVars.pushOutputVar("logMsg_s" + node.getId());

      List<Statement> statements = visitChildren(node);

      Expression outputVar = outputVars.popOutputVar();

      statements.add(WINDOW_CONSOLE_LOG.call(outputVar).asStatement());

      return Statements.of(statements);
    }
  }

  @Override
  protected Statement visitKeyNode(KeyNode node) {
    // Do nothing. Outside of incremental dom, key nodes are a no-op.
    return Statements.of(ImmutableList.of());
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
  protected Statement visitDebuggerNode(DebuggerNode node) {
    return Statements.debugger();
  }

  @Override
  protected Statement visitVeLogNode(VeLogNode node) {
    // no need to do anything, the VeLogInstrumentationVisitor has already handled these.
    if (!node.needsSyntheticVelogNode()) {
      return Statements.of(visitChildren(node));
    }
    // Create synthetic velog nodes. These will be removed in JS.
    FunctionNode funcNode =
        FunctionNode.newPositional(
            Identifier.create(VeLogFunction.NAME, node.getSourceLocation()),
            VeLogFunction.INSTANCE,
            node.getSourceLocation());
    funcNode.addChild(node.getVeDataExpression().copy(new CopyState()));
    if (node.getLogonlyExpression() != null) {
      funcNode.addChild(node.getLogonlyExpression().copy(new CopyState()));
    }

    List<Statement> statements = new ArrayList<>();
    statements.add(
        outputVars.addChunksToOutputVar(
            ImmutableList.of(
                Expressions.stringLiteral("<velog"),
                createExprTranslator().exec(funcNode),
                Expressions.stringLiteral(">"))));
    statements.addAll(visitChildren(node));
    statements.add(outputVars.addChunkToOutputVar(Expressions.stringLiteral("</velog>")));
    return Statements.of(statements);
  }

  @Override
  protected Statement visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    // PlaceholderNodes just wrap other nodes with placeholder metadata which is processed by the
    // GenJsCodeVisitorAssistentForMsgs
    return Statements.of(visitChildren(node));
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected Statement visitSoyNode(SoyNode node) {
    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Simply generate JS expressions for this node and add them to the current output var.
      return outputVars.addChunksToOutputVar(genJsExprsVisitor.exec(node));

    } else {
      // Need to implement visit*Node() for the specific case.
      throw new UnsupportedOperationException(
          "implement visit*Node for " + node.getKind() + " at " + node.getSourceLocation());
    }
  }
}
