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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.PrintDirectives.applyStreamingEscapingDirectives;
import static com.google.template.soy.jbcsrc.PrintDirectives.applyStreamingPrintDirectives;
import static com.google.template.soy.jbcsrc.PrintDirectives.areAllPrintDirectivesStreamable;
import static com.google.template.soy.jbcsrc.TemplateVariableManager.SaveStrategy.DERIVED;
import static com.google.template.soy.jbcsrc.TemplateVariableManager.SaveStrategy.STORE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.RENDER_RESULT_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.compareSoyEquals;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static java.util.stream.Collectors.toMap;
import static org.objectweb.asm.commons.GeneratorAdapter.EQ;

import com.google.auto.value.AutoValue;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.jbcsrc.ControlFlow.IfBlock;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.LazyClosureCompiler.LazyClosure;
import com.google.template.soy.jbcsrc.MsgCompiler.PlaceholderCompiler;
import com.google.template.soy.jbcsrc.TemplateVariableManager.Scope;
import com.google.template.soy.jbcsrc.TemplateVariableManager.Variable;
import com.google.template.soy.jbcsrc.internal.InnerClasses;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.ConstructorRef;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime;
import com.google.template.soy.jbcsrc.shared.ClassLoaderFallbackCallFactory;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.shared.RangeArgs;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
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
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SoyTreeUtils.VisitDirective;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.TemplateType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Compiles {@link SoyNode soy nodes} into {@link Statement statements}.
 *
 * <p>The normal contract for {@link Statement statements} is that they leave the state of the
 * runtime stack unchanged before and after execution. The SoyNodeCompiler requires that the runtime
 * stack be <em>empty</em> prior to any of the code produced.
 */
final class SoyNodeCompiler extends AbstractReturningSoyNodeVisitor<Statement> {
  // TODO(lukes): consider introducing a Builder or a non-static Factory.

  /**
   * Creates a SoyNodeCompiler
   *
   * @param innerClasses The current set of inner classes
   * @param appendableVar An expression that returns the current AdvisingAppendable that we are
   *     rendering into
   * @param variables The variable set for generating locals and fields
   * @param parameterLookup The variable lookup table for reading locals.
   */
  static SoyNodeCompiler create(
      TemplateAnalysis analysis,
      InnerClasses innerClasses,
      AppendableExpression appendableVar,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      FieldManager fields,
      BasicExpressionCompiler constantCompiler,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler) {
    // We pass a lazy supplier of render context so that lazy closure compiler classes that don't
    // generate detach logic don't trigger capturing this value into a field.
    DetachState detachState = new DetachState(variables, parameterLookup::getRenderContext);
    ExpressionCompiler expressionCompiler =
        ExpressionCompiler.create(analysis, parameterLookup, variables, javaSourceFunctionCompiler);
    ExpressionToSoyValueProviderCompiler soyValueProviderCompiler =
        ExpressionToSoyValueProviderCompiler.create(analysis, expressionCompiler, parameterLookup);
    return new SoyNodeCompiler(
        analysis,
        innerClasses,
        detachState,
        variables,
        parameterLookup,
        fields,
        appendableVar,
        expressionCompiler,
        soyValueProviderCompiler,
        constantCompiler,
        javaSourceFunctionCompiler);
  }

  final TemplateAnalysis analysis;
  final InnerClasses innerClasses;
  final DetachState detachState;
  final TemplateVariableManager variables;
  final TemplateParameterLookup parameterLookup;
  final FieldManager fields;
  final AppendableExpression appendableExpression;
  final ExpressionCompiler exprCompiler;
  final ExpressionToSoyValueProviderCompiler expressionToSoyValueProviderCompiler;
  final BasicExpressionCompiler constantCompiler;
  final JavaSourceFunctionCompiler javaSourceFunctionCompiler;
  private Scope currentScope;

  SoyNodeCompiler(
      TemplateAnalysis analysis,
      InnerClasses innerClasses,
      DetachState detachState,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      FieldManager fields,
      AppendableExpression appendableExpression,
      ExpressionCompiler exprCompiler,
      ExpressionToSoyValueProviderCompiler expressionToSoyValueProviderCompiler,
      BasicExpressionCompiler constantCompiler,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler) {
    this.analysis = checkNotNull(analysis);
    this.innerClasses = innerClasses;
    this.detachState = checkNotNull(detachState);
    this.variables = checkNotNull(variables);
    this.parameterLookup = checkNotNull(parameterLookup);
    this.fields = checkNotNull(fields);
    this.appendableExpression = checkNotNull(appendableExpression);
    this.exprCompiler = checkNotNull(exprCompiler);
    this.expressionToSoyValueProviderCompiler = checkNotNull(expressionToSoyValueProviderCompiler);
    this.constantCompiler = constantCompiler;
    this.javaSourceFunctionCompiler = javaSourceFunctionCompiler;
  }

  @AutoValue
  abstract static class CompiledMethodBody {
    static CompiledMethodBody create(Statement body, int numDetaches) {
      return new AutoValue_SoyNodeCompiler_CompiledMethodBody(body, numDetaches);
    }

    abstract Statement body();

    abstract int numberOfDetachStates();
  }

  CompiledMethodBody compile(
      RenderUnitNode node, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {
    List<Statement> statements = new ArrayList<>();
    if (shouldCheckForSoftLimit(node)) {
      statements.add(detachState.detachLimited(appendableExpression));
    }
    statements.add(doCompile(node, prefix, suffix));
    statements.add(
        // needs to go at the beginning but can only be generated after the whole method body.
        0, detachState.generateReattachTable());
    return CompiledMethodBody.create(
        Statement.concat(statements), detachState.getNumberOfDetaches());
  }

  Statement compileWithoutDetaches(
      RenderUnitNode node, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {
    try (DetachState.NoNewDetaches noNewDetaches = detachState.expectNoNewDetaches()) {
      return doCompile(node, prefix, suffix);
    }
  }

  private Statement doCompile(
      RenderUnitNode node, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {
    return Statement.concat(
        // Tag the content with the kind
        // TODO(lukes): directionality is always the default, do we need to set it?
        appendableExpression
            .setSanitizedContentKindAndDirectionality(node.getContentKind())
            .toStatement(),
        prefix.compile(exprCompiler, appendableExpression, detachState),
        visitChildrenInNewScope(node),
        suffix.compile(exprCompiler, appendableExpression, detachState));
  }

  @Override
  protected Statement visit(SoyNode node) {
    try {
      return super.visit(node).withSourceLocation(node.getSourceLocation());
    } catch (UnexpectedCompilerFailureException e) {
      e.addLocation(node);
      throw e;
    } catch (Throwable t) {
      throw new UnexpectedCompilerFailureException(node, t);
    }
  }

  /**
   * Certain content kinds are limited in size by their nature. Skip detach logic in those cases.
   */
  private static boolean kindRequiresDetach(SanitizedContentKind kind) {
    switch (kind) {
      case TEXT:
      case HTML:
      case HTML_ELEMENT:
      case CSS: // Sometimes templates include very large JS and CSS documents.
      case JS:
        return true;
      case TRUSTED_RESOURCE_URI: // uris are naturally small (>2K is unlikely to work in practice)
      case URI:
      case ATTRIBUTES:
        // attributes are generally small as well, though don't really share a tight bound.
        return false;
    }
    throw new AssertionError("invalid kind: " + kind);
  }

  private static boolean directlyPrintingNode(Node node) {
    if (node instanceof SoyNode) {
      SoyNode.Kind kind = ((SoyNode) node).getKind();
      return kind == SoyNode.Kind.RAW_TEXT_NODE || kind == SoyNode.Kind.PRINT_NODE;
    }
    return false;
  }

  /**
   * Returns true if we should add logic for checking if we have exceeded the soft limit to the
   * beginning of the code generated for the given node.
   *
   */
  private static boolean shouldCheckForSoftLimit(RenderUnitNode node) {
    // Only check templates
    if (!(node instanceof TemplateNode)) {
      return false;
    }
    // Only for certain content kinds
    if (!kindRequiresDetach(node.getContentKind())) {
      return false;
    }
    // Only if it contains a print node directly.  If it is just a set of call nodes (possibly with
    // control flow) we can assume that buffer checks will be handled by our callees.
    return SoyTreeUtils.allNodes(
            node,
            n -> {
              // Don't explore expr nodes or render unit nodes.  let/param nodes may contain
              // printing nodes but it is only relevant if they themselves are printed, in which
              // case our later check will find them.
              if (!(n instanceof SoyNode)
                  || n instanceof LetContentNode
                  || n instanceof CallParamContentNode) {
                return VisitDirective.SKIP_CHILDREN;
              }
              return VisitDirective.CONTINUE;
            })
        .anyMatch(SoyNodeCompiler::directlyPrintingNode);
  }

  @Override
  protected Statement visitTemplateNode(TemplateNode node) {
    // template nodes are directly handled by compile()
    throw new AssertionError("should not be called");
  }

  private Statement visitChildrenInNewScope(BlockNode node) {
    Scope prev = currentScope;
    currentScope = variables.enterScope();
    List<Statement> children = visitChildren(node);
    Statement leave = currentScope.exitScope();
    children.add(leave);
    currentScope = prev;
    return Statement.concat(children);
  }

  @Override
  protected Statement visitIfNode(IfNode node) {
    List<IfBlock> ifs = new ArrayList<>();
    Optional<Statement> elseBlock = Optional.empty();
    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        SoyExpression cond =
            exprCompiler.compileRootExpression(icn.getExpr(), detachState).coerceToBoolean();
        Statement block = visitChildrenInNewScope(icn);
        ifs.add(IfBlock.create(cond, block));
      } else {
        IfElseNode ien = (IfElseNode) child;
        elseBlock = Optional.of(visitChildrenInNewScope(ien));
      }
    }
    return ControlFlow.ifElseChain(ifs, elseBlock);
  }

  @Override
  protected Statement visitSwitchNode(SwitchNode node) {
    // A few special cases:
    // 1. only a {default} block.  In this case we can skip all the switch logic and temporaries
    // 2. no children.  Just return the empty statement
    // Note that in both of these cases we do not evalutate (or generate code) for the switch
    // expression.
    List<CaseOrDefaultNode> children = node.getChildren();
    if (children.isEmpty()) {
      return Statement.NULL_STATEMENT;
    }
    if (children.size() == 1 && children.get(0) instanceof SwitchDefaultNode) {
      return visitChildrenInNewScope(children.get(0));
    }

    // otherwise we need to evaluate the switch variable and generate dispatching logic.
    SoyExpression switchVar = exprCompiler.compileRootExpression(node.getExpr(), detachState);

    Scope scope = variables.enterScope();
    Variable variable = scope.createSynthetic(SyntheticVarName.forSwitch(node), switchVar, STORE);
    Statement initializer = variable.initializer();
    switchVar = switchVar.withSource(variable.local());

    List<IfBlock> cases = new ArrayList<>();
    Optional<Statement> defaultBlock = Optional.empty();
    for (SoyNode child : children) {
      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode caseNode = (SwitchCaseNode) child;
        Label reattachPoint = new Label();
        List<Expression> comparisons = new ArrayList<>();
        for (ExprRootNode caseExpr : caseNode.getExprList()) {
          comparisons.add(
              compareSoyEquals(
                  switchVar,
                  exprCompiler.compileSubExpression(
                      caseExpr, detachState.createExpressionDetacher(reattachPoint))));
        }
        Expression condition = BytecodeUtils.logicalOr(comparisons).labelStart(reattachPoint);
        Statement block = visitChildrenInNewScope(caseNode);
        cases.add(IfBlock.create(condition, block));
      } else {
        SwitchDefaultNode defaultNode = (SwitchDefaultNode) child;
        defaultBlock = Optional.of(visitChildrenInNewScope(defaultNode));
      }
    }
    Statement exitScope = scope.exitScope();

    // Soy allows arbitrary expressions to appear in {case} statements within a {switch}.
    // Java/C, by contrast, only allow some constant expressions in cases.
    // TODO(lukes): in practice the case statements are often constant strings/ints.  If everything
    // is typed to int/string we should consider implementing via the tableswitch/lookupswitch
    // instruction which would be way way way faster.  cglib has some helpers for string switch
    // generation that we could maybe use
    return Statement.concat(initializer, ControlFlow.ifElseChain(cases, defaultBlock), exitScope);
  }

  @Override
  protected Statement visitForNode(ForNode node) {
    ForNonemptyNode nonEmptyNode = (ForNonemptyNode) node.getChild(0);
    Optional<RangeArgs> exprAsRangeArgs = RangeArgs.createFromNode(node);
    Scope scope = variables.enterScope();
    final Variable indexVar;
    final List<Statement> initializers = new ArrayList<>();
    final Variable sizeVar;
    final Variable itemVar;
    final Variable userIndexVar;
    if (exprAsRangeArgs.isPresent()) {
      final CompiledForeachRangeArgs compiledArgs = calculateRangeArgs(node, scope);
      initializers.addAll(compiledArgs.initStatements());
      // The size is just the number of items in the range.  The logic is a little tricky so we
      // implement it in a runtime function: JbcsrcRuntime.rangeLoopLength
      sizeVar =
          scope.createSynthetic(
              SyntheticVarName.foreachLoopLength(nonEmptyNode),
              MethodRef.RUNTIME_RANGE_LOOP_LENGTH.invoke(
                  compiledArgs.start(), compiledArgs.end(), compiledArgs.step()),
              DERIVED);
      indexVar =
          scope.createSynthetic(
              SyntheticVarName.foreachLoopIndex(nonEmptyNode), constant(0), STORE);
      userIndexVar =
          nonEmptyNode.getIndexVar() == null
              ? null
              : scope.create(
                  nonEmptyNode.getIndexVarName(),
                  SoyExpression.forInt(
                          BytecodeUtils.numericConversion(indexVar.local(), Type.LONG_TYPE))
                      .boxAsSoyValueProvider()
                      .checkedCast(SOY_VALUE_PROVIDER_TYPE),
                  DERIVED);
      itemVar =
          scope.create(
              nonEmptyNode.getVarName(),
              new Expression(Type.LONG_TYPE, Feature.CHEAP) {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  // executes ((long) start + index * step)
                  compiledArgs.start().gen(adapter);
                  compiledArgs.step().gen(adapter);
                  indexVar.local().gen(adapter);
                  adapter.visitInsn(Opcodes.IMUL);
                  adapter.visitInsn(Opcodes.IADD);
                  adapter.cast(Type.INT_TYPE, Type.LONG_TYPE);
                }
              },
              DERIVED);
    } else {
      SoyExpression expr =
          exprCompiler.compileRootExpression(node.getExpr(), detachState).unboxAsList();
      Variable listVar =
          scope.createSynthetic(SyntheticVarName.foreachLoopList(nonEmptyNode), expr, STORE);
      initializers.add(listVar.initializer());
      sizeVar =
          scope.createSynthetic(
              SyntheticVarName.foreachLoopLength(nonEmptyNode),
              MethodRef.LIST_SIZE.invoke(listVar.local()),
              DERIVED);
      indexVar =
          scope.createSynthetic(
              SyntheticVarName.foreachLoopIndex(nonEmptyNode), constant(0), STORE);
      userIndexVar =
          nonEmptyNode.getIndexVar() == null
              ? null
              : scope.create(
                  nonEmptyNode.getIndexVarName(),
                  SoyExpression.forInt(
                          BytecodeUtils.numericConversion(indexVar.local(), Type.LONG_TYPE))
                      .boxAsSoyValueProvider()
                      .checkedCast(SOY_VALUE_PROVIDER_TYPE),
                  DERIVED);
      itemVar =
          scope.create(
              nonEmptyNode.getVarName(),
              MethodRef.LIST_GET
                  .invoke(listVar.local(), indexVar.local())
                  .checkedCast(SOY_VALUE_PROVIDER_TYPE),
              DERIVED);
    }
    initializers.add(sizeVar.initializer());
    final Statement loopBody = visitChildrenInNewScope(nonEmptyNode);
    final Statement exitScope = scope.exitScope();

    // it important for this to be generated after exitScope is called (or before enterScope)
    final Statement emptyBlock =
        node.numChildren() == 2 ? visitChildrenInNewScope(node.getChild(1)) : null;
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        for (Statement initializer : initializers) {
          initializer.gen(adapter);
        }
        sizeVar.local().gen(adapter);
        Label emptyListLabel = new Label();
        adapter.ifZCmp(Opcodes.IFEQ, emptyListLabel);
        indexVar.initializer().gen(adapter);
        Label loopStart = adapter.mark();
        itemVar.initializer().gen(adapter);
        if (userIndexVar != null) {
          userIndexVar.initializer().gen(adapter);
        }

        loopBody.gen(adapter);

        adapter.iinc(indexVar.local().index(), 1); // index++
        indexVar.local().gen(adapter);
        sizeVar.local().gen(adapter);
        adapter.ifICmp(Opcodes.IFLT, loopStart); // if index < list.size(), goto loopstart
        // exit the loop
        exitScope.gen(adapter);

        if (emptyBlock != null) {
          Label skipIfEmptyBlock = new Label();
          adapter.goTo(skipIfEmptyBlock);
          adapter.mark(emptyListLabel);
          emptyBlock.gen(adapter);
          adapter.mark(skipIfEmptyBlock);
        } else {
          adapter.mark(emptyListLabel);
        }
      }
    };
  }

  @AutoValue
  abstract static class CompiledForeachRangeArgs {
    /** Current loop index. */
    abstract Expression start();

    /** Where to end loop iteration, defaults to {@code 0}. */
    abstract Expression end();

    /** This statement will increment the index by the loop stride. */
    abstract Expression step();

    /** Statements that must have been run prior to using any of the above expressions. */
    abstract ImmutableList<Statement> initStatements();
  }

  /**
   * Interprets the given expressions as the arguments of a {@code range(...)} expression in a
   * {@code foreach} loop.
   */
  private CompiledForeachRangeArgs calculateRangeArgs(ForNode forNode, Scope scope) {
    RangeArgs rangeArgs = RangeArgs.createFromNode(forNode).get();
    ForNonemptyNode nonEmptyNode = (ForNonemptyNode) forNode.getChild(0);
    ImmutableList.Builder<Statement> initStatements = ImmutableList.builder();
    Expression startExpression =
        computeRangeValue(
            SyntheticVarName.foreachLoopRangeStart(nonEmptyNode),
            rangeArgs.start(),
            0,
            scope,
            initStatements);
    Expression stepExpression =
        computeRangeValue(
            SyntheticVarName.foreachLoopRangeStep(nonEmptyNode),
            rangeArgs.increment(),
            1,
            scope,
            initStatements);
    Expression endExpression =
        computeRangeValue(
            SyntheticVarName.foreachLoopRangeEnd(nonEmptyNode),
            Optional.of(rangeArgs.limit()),
            Integer.MAX_VALUE,
            scope,
            initStatements);

    return new AutoValue_SoyNodeCompiler_CompiledForeachRangeArgs(
        startExpression, endExpression, stepExpression, initStatements.build());
  }

  /**
   * Computes a single range argument.
   *
   * @param varName The variable name to use if this value should be stored in a local
   * @param expression The expression
   * @param defaultValue The value to use if there is no expression
   * @param scope The current variable scope to add variables to
   * @param initStatements Initializing statements, if any.
   */
  private Expression computeRangeValue(
      SyntheticVarName varName,
      Optional<ExprNode> expression,
      int defaultValue,
      Scope scope,
      final ImmutableList.Builder<Statement> initStatements) {
    if (!expression.isPresent()) {
      return constant(defaultValue);
    } else if (expression.get() instanceof IntegerNode
        && ((IntegerNode) expression.get()).isInt()) {
      int value = Ints.checkedCast(((IntegerNode) expression.get()).getValue());
      return constant(value);
    } else {
      Label startDetachPoint = new Label();
      // Note: If the value of rangeArgs.start() is above 32 bits, Ints.checkedCast() will fail at
      // runtime with IllegalArgumentException.
      SoyExpression compiledExpression =
          exprCompiler.compileSubExpression(
              expression.get(), detachState.createExpressionDetacher(startDetachPoint));
      Expression rangeValue;
      SoyRuntimeType type = compiledExpression.soyRuntimeType();
      if (!type.isKnownInt()) {
        // The type checker allows for numeric types, but we only support ints in the runtime.
        // simply unbox to a double and cast to an int
        rangeValue =
            MethodRef.INTS_CHECKED_CAST.invoke(
                BytecodeUtils.numericConversion(
                    type.isKnownFloat()
                        ? compiledExpression.unboxAsDouble()
                        : compiledExpression.coerceToDouble(),
                    Type.LONG_TYPE));
      } else {
        rangeValue = MethodRef.INTS_CHECKED_CAST.invoke(compiledExpression.unboxAsLong());
      }
      if (!rangeValue.isCheap()) {
        // bounce it into a local variable
        Variable startVar = scope.createSynthetic(varName, rangeValue, STORE);
        initStatements.add(startVar.initializer().labelStart(startDetachPoint));
        rangeValue = startVar.local();
      }
      return rangeValue;
    }
  }

  @Override
  protected Statement visitPrintNode(PrintNode node) {
    if (node.getExpr().getRoot() instanceof FunctionNode) {
      FunctionNode fn = (FunctionNode) node.getExpr().getRoot();
      if (fn.getSoyFunction() instanceof LoggingFunction) {
        return visitLoggingFunction(node, fn, (LoggingFunction) fn.getSoyFunction());
      }
    }
    // First check our special case where all print directives are streamable and an expression that
    // evaluates to a SoyValueProvider.  This will allow us to render incrementally.
    if (areAllPrintDirectivesStreamable(node)) {
      Label reattachPoint = new Label();
      ExprRootNode expr = node.getExpr();
      Optional<Expression> asSoyValueProvider =
          expressionToSoyValueProviderCompiler.compileToSoyValueProviderIfUsefulToPreserveStreaming(
              expr, detachState.createExpressionDetacher(reattachPoint));
      if (asSoyValueProvider.isPresent()) {
        boolean requiresDetachLogic =
            exprCompiler.requiresDetach(expr)
                || node.getChildren().stream()
                    .flatMap(pdn -> pdn.getExprList().stream())
                    .anyMatch(exprCompiler::requiresDetach);
        return renderIncrementally(
            asSoyValueProvider.get(), node.getChildren(), reattachPoint, requiresDetachLogic);
      }
    }

    // otherwise we need to apply some non-streaming print directives, or the expression would
    // require boxing to be a print directive (which usually means it is quite trivial).
    Label reattachPoint = new Label();
    SoyExpression value = compilePrintNodeAsExpression(node, reattachPoint);
    // TODO(lukes): call value.render?
    AppendableExpression renderSoyValue =
        appendableExpression.appendString(value.coerceToString()).labelStart(reattachPoint);

    return renderSoyValue.toStatement();
  }

  private Statement visitLoggingFunction(
      PrintNode node, FunctionNode fn, LoggingFunction loggingFunction) {
    List<Expression> printDirectives = new ArrayList<>(node.numChildren());
    for (PrintDirectiveNode child : node.getChildren()) {
      checkState(child.getArgs().isEmpty()); // sanity
      printDirectives.add(
          parameterLookup.getRenderContext().getEscapingDirectiveAsFunction(child.getName()));
    }
    Label reattachPoint = new Label();
    SoyFunctionSignature functionSignature =
        loggingFunction.getClass().getAnnotation(SoyFunctionSignature.class);
    checkNotNull(
        functionSignature,
        "LoggingFunction %s must be annotated with @SoyFunctionSignature",
        loggingFunction.getClass().getName());
    return appendableExpression
        .appendLoggingFunctionInvocation(
            functionSignature.name(),
            loggingFunction.getPlaceholder(),
            exprCompiler
                .asBasicCompiler(detachState.createExpressionDetacher(reattachPoint))
                .compileToList(fn.getChildren()),
            printDirectives)
        .labelStart(reattachPoint)
        .toStatement();
  }

  private SoyExpression compilePrintNodeAsExpression(PrintNode node, Label reattachPoint) {
    BasicExpressionCompiler basic =
        exprCompiler.asBasicCompiler(detachState.createExpressionDetacher(reattachPoint));
    SoyExpression value = basic.compile(node.getExpr());
    // We may have print directives, that means we need to pass the render value through a bunch of
    // SoyJavaPrintDirective.apply methods.  This means lots and lots of boxing.
    for (PrintDirectiveNode printDirective : node.getChildren()) {
      value =
          parameterLookup
              .getRenderContext()
              .applyPrintDirective(
                  printDirective.getPrintDirective(),
                  value,
                  basic.compileToList(printDirective.getArgs()));
    }
    return value;
  }

  /**
   * Renders a {@link SoyValueProvider} incrementally via {@link SoyValueProvider#renderAndResolve}
   *
   * <p>The strategy is to:
   *
   * <ul>
   *   <li>Stash the SoyValueProvider in a field {@code $currentRenderee}, so that if we detach
   *       halfway through rendering we don't lose the value. Note, we could use the scope/variable
   *       system of {@link TemplateVariableManager} to manage this value, but we know there will
   *       only ever be 1 live at a time, so we can just manage the single special field ourselves.
   *   <li>Apply all the streaming autoescapers to the current appendable. Also, stash it in the
   *       {@code $currentAppendable} field for the same reasons as above.
   *   <li>Invoke {@link SoyValueProvider#renderAndResolve} with the standard detach logic.
   *   <li>Clear the two fields once rendering is complete.
   * </ul>
   *
   * <p>TODO(lukes): if the expression is a param, then this is kind of silly since it looks like
   *
   * <pre>{@code
   * SoyValueProvider localParam = this.param;
   * this.currentRenderee = localParam;
   * SoyValueProvider localRenderee = this.currentRenderee;
   * localRenderee.renderAndResolve();
   * }</pre>
   *
   * <p>In this case we could elide the currentRenderee altogether if we knew the soyValueProvider
   * expression was just a field read... And this is the _common_case for .renderAndResolve calls.
   * to actually do this we could add a mechanism similar to the SaveStrategy enum for expressions,
   * kind of like {@link Expression#isCheap()} which isn't that useful in practice.
   *
   * @param soyValueProvider The value to render incrementally
   * @param directives The streaming print directives applied to the expression
   * @param reattachPoint The point where execution should resume if the soyValueProvider detaches
   *     while being evaluated.
   * @return a statement for the full render.
   */
  private Statement renderIncrementally(
      Expression soyValueProvider,
      List<PrintDirectiveNode> directives,
      Label reattachPoint,
      boolean requiresDetachLogic) {
    // In this case we want to render the SoyValueProvider via renderAndResolve which will
    // enable incremental rendering of parameters for lazy transclusions!
    // This actually ends up looking a lot like how calls work so we use the same strategy.
    Statement initRenderee = Statement.NULL_STATEMENT;

    TemplateVariableManager.Scope renderScope = variables.enterScope();
    if (!soyValueProvider.isCheap()) {
      TemplateVariableManager.Variable variable =
          renderScope.createSynthetic(
              SyntheticVarName.renderee(),
              soyValueProvider,
              TemplateVariableManager.SaveStrategy.STORE);
      initRenderee = variable.initializer();
      soyValueProvider = variable.accessor();
    }
    initRenderee = initRenderee.labelStart(reattachPoint);

    Statement initAppendable = Statement.NULL_STATEMENT;
    Statement clearAppendable = Statement.NULL_STATEMENT;
    AppendableExpression appendable = appendableExpression;
    if (!directives.isEmpty()) {
      Label printDirectiveArgumentReattachPoint = new Label();
      PrintDirectives.AppendableAndFlushBuffersDepth wrappedAppendable =
          applyStreamingPrintDirectives(
              directives,
              appendable,
              exprCompiler.asBasicCompiler(
                  detachState.createExpressionDetacher(printDirectiveArgumentReattachPoint)),
              parameterLookup.getPluginContext());
      TemplateVariableManager.Variable variable =
          renderScope.createSynthetic(
              SyntheticVarName.appendable(),
              wrappedAppendable.appendable(),
              TemplateVariableManager.SaveStrategy.STORE);
      initAppendable = variable.initializer().labelStart(printDirectiveArgumentReattachPoint);
      appendable = AppendableExpression.forExpression(variable.accessor());
      if (wrappedAppendable.flushBuffersDepth() >= 0) {
        // make sure to call close before clearing
        clearAppendable = appendable.flushBuffers(wrappedAppendable.flushBuffersDepth());
      }
    }
    Expression callRenderAndResolve =
        soyValueProvider.invoke(
            MethodRef.SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE,
            appendable,
            // the isLast param
            // TODO(lukes): pass a real value here when we have expression use analysis.
            constant(false));
    Statement doCall =
        requiresDetachLogic
            ? detachState.detachForRender(callRenderAndResolve)
            : detachState.assertFullyRenderered(callRenderAndResolve);
    return Statement.concat(
        initRenderee, initAppendable, doCall, clearAppendable, renderScope.exitScope());
  }

  @Override
  protected Statement visitRawTextNode(RawTextNode node) {
    AppendableExpression render;
    if (node.getRawText().length() == 1) {
      render = appendableExpression.appendChar(constant(node.getRawText().charAt(0)));
    } else {
      render = appendableExpression.appendString(constant(node.getRawText()));
    }
    return render.toStatement();
  }

  @Override
  protected Statement visitDebuggerNode(DebuggerNode node) {
    // Call JbcSrcRuntime.debuggger.  This logs a stack trace by default and is an obvious place to
    // put a breakpoint.
    return MethodRef.RUNTIME_DEBUGGER.invokeVoid(
        constant(node.getSourceLocation().getFilePath().path()),
        constant(node.getSourceLocation().getBeginLine()));
  }

  @Override
  protected Statement visitKeyNode(KeyNode node) {
    // Outside of incremental dom, key nodes are a no-op.
    return Statement.NULL_STATEMENT;
  }

  /**
   * MsgFallbackGroupNodes have either one or two children. In the 2 child case the second child is
   * the {@code {fallbackmsg}} entry. For this we generate code that looks like:
   *
   * <pre>{@code
   * if (renderContext.hasMsg(primaryId)) {
   *   <render primary msg>
   * } else {
   *   <render fallback msg>
   * }
   * }</pre>
   *
   * <p>All of the logic for actually rendering {@code msg} nodes is handled by the {@link
   * MsgCompiler}.
   */
  @Override
  protected Statement visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    MsgNode msg = node.getMsg();
    MsgPartsAndIds idAndParts = MsgUtils.buildMsgPartsAndComputeMsgIdForDualFormat(msg);
    ImmutableList<SoyPrintDirective> escapingDirectives = node.getEscapingDirectives();
    Statement renderDefault = getMsgCompiler().compileMessage(idAndParts, msg, escapingDirectives);
    // fallback groups have 1 or 2 children.  if there are 2 then the second is a fallback and we
    // need to check for presence.
    if (node.hasFallbackMsg()) {
      MsgNode fallback = node.getFallbackMsg();
      MsgPartsAndIds fallbackIdAndParts =
          MsgUtils.buildMsgPartsAndComputeMsgIdForDualFormat(fallback);
      // TODO(lukes): consider changing the control flow here by 'inlining' the usePrimaryMsg logic
      // it would save some lookups.  Right now we will do to 2- 3 calls to
      // SoyMsgBundle.getMsgParts (each of which requires a binary search).  We could reduce that
      // to 1-2 in the worse case by inlining and storing the lists in local variables.
      Expression cond =
          msg.getAlternateId().isPresent()
              ? (fallback.getAlternateId().isPresent()
                  // msg > alternate > fallback > fb alternate
                  ? parameterLookup
                      .getRenderContext()
                      .usePrimaryOrAlternateIfFallbackOrFallbackAlternate(
                          idAndParts.id,
                          msg.getAlternateId().getAsLong(),
                          fallbackIdAndParts.id,
                          fallback.getAlternateId().getAsLong())
                  // msg > alternate > fallback
                  : parameterLookup
                      .getRenderContext()
                      .usePrimaryOrAlternateIfFallback(
                          idAndParts.id, msg.getAlternateId().getAsLong(), fallbackIdAndParts.id))
              : (fallback.getAlternateId().isPresent()
                  // msg > fallback > fb alternate
                  ? parameterLookup
                      .getRenderContext()
                      .usePrimaryIfFallbackOrFallbackAlternate(
                          idAndParts.id,
                          fallbackIdAndParts.id,
                          fallback.getAlternateId().getAsLong())
                  // msg > fallback
                  : parameterLookup
                      .getRenderContext()
                      .usePrimaryMsgIfFallback(idAndParts.id, fallbackIdAndParts.id));
      IfBlock ifAvailableRenderDefault = IfBlock.create(cond, renderDefault);
      return ControlFlow.ifElseChain(
          ImmutableList.of(ifAvailableRenderDefault),
          Optional.of(
              getMsgCompiler().compileMessage(fallbackIdAndParts, fallback, escapingDirectives)));
    } else {
      return renderDefault;
    }
  }

  @Override
  protected Statement visitVeLogNode(final VeLogNode node) {
    final Label restartPoint = new Label();
    final Expression veData =
        exprCompiler.compileSubExpression(
            node.getVeDataExpression(), detachState.createExpressionDetacher(restartPoint));
    final Expression hasLogger = parameterLookup.getRenderContext().hasLogger();
    final Statement exitStatement =
        ControlFlow.IfBlock.create(
                hasLogger, appendableExpression.exitLoggableElement().toStatement())
            .asStatement();
    if (node.getLogonlyExpression() != null) {
      final Expression logonlyExpression =
          exprCompiler
              .compileSubExpression(
                  node.getLogonlyExpression(), detachState.createExpressionDetacher(restartPoint))
              .unboxAsBoolean();
      // needs to be called after evaluating the logonly expression so variables defined in the
      // block aren't part of the save restore state for the logonly expression.
      final Statement body = Statement.concat(visitChildrenInNewScope(node));
      return new Statement() {
        @Override
        protected void doGen(CodeBuilder cb) {
          // Key
          // LO: logonly
          // HL: hasLogger
          // veData: SoyVisualElementData
          // LS: LogStatement
          // A: appendable
          // RC: RenderContext
          //
          // Each en end of line comments represents the state of the stack  _after_ the instruction
          // is executed, the top of the stack is on the left.
          // These shenanigans are necessary to ensure that
          // 1. we only generate/evaluate the logonly code once
          // 2. the arguments are put into the correct order for the LogStatement constructor
          cb.mark(restartPoint);
          logonlyExpression.gen(cb); // LO
          Label noLogger = new Label();
          hasLogger.gen(cb); // HL, LO
          cb.ifZCmp(EQ, noLogger); // LO
          veData.gen(cb); // veData, LO
          MethodRef.CREATE_LOG_STATEMENT.invokeUnchecked(cb); // LS
          appendableExpression.gen(cb); // A, LS
          cb.swap(); // LS, A
          AppendableExpression.ENTER_LOGGABLE_STATEMENT.invokeUnchecked(cb); // A
          cb.pop();
          Label bodyLabel = new Label();
          cb.goTo(bodyLabel);
          cb.mark(noLogger); // LO
          cb.ifZCmp(EQ, bodyLabel);
          cb.throwException(
              BytecodeUtils.ILLEGAL_STATE_EXCEPTION_TYPE,
              "Cannot set logonly=\"true\" unless there is a logger configured");
          cb.mark(bodyLabel);

          body.gen(cb);
          exitStatement.gen(cb);
        }
      };
    } else {
      final Statement enterStatement =
          ControlFlow.IfBlock.create(
                  hasLogger,
                  appendableExpression
                      .enterLoggableElement(
                          MethodRef.CREATE_LOG_STATEMENT.invoke(
                              BytecodeUtils.constant(false), veData))
                      .toStatement()
                      .labelStart(restartPoint))
              .asStatement();
      return Statement.concat(
          enterStatement, Statement.concat(visitChildrenInNewScope(node)), exitStatement);
    }
  }

  /** Helper interface for generating templates calls. */
  @FunctionalInterface
  private interface CallGenerator {
    Expression asCompiledTemplate();

    default Optional<DirectCallGenerator> asDirectCall() {
      return Optional.empty();
    }

    default Optional<DirectPositionalCallGenerator> asDirectPositionalCall() {
      return Optional.empty();
    }
  }

  @FunctionalInterface
  private interface BoundCallGenerator {
    Expression call(
        Expression ijParams,
        AppendableExpression appendable,
        RenderContextExpression renderContext);
  }

  @FunctionalInterface
  private interface DirectCallGenerator {
    Expression call(
        Expression params,
        Expression ijParams,
        AppendableExpression appendable,
        RenderContextExpression renderContext);
  }

  private interface DirectPositionalCallGenerator {
    Expression call(
        List<Expression> params,
        Expression ijParams,
        AppendableExpression appendable,
        RenderContextExpression renderContext);

    TemplateType calleeType();
  }

  /**
   * Given this delcall: {@code {delcall foo.bar variant="$expr" allowemptydefault="true"}}
   *
   * <p>Generate code that looks like:
   *
   * <pre>{@code
   * renderContext.getDeltemplate("foo.bar", <variant-expression>, true)
   *     .create(<prepareParameters>, ijParams)
   *     .render(appendable, renderContext)
   *
   * }</pre>
   *
   * <p>We share logic with {@link #visitCallBasicNode(CallBasicNode)} around the actual calling
   * convention (setting up detaches, storing the template in a field). As well as the logic for
   * preparing the data record. The only interesting part of delcalls is calculating the {@code
   * variant} and the fact that we have to invoke the {@link RenderContext} runtime to do the
   * deltemplate lookup.
   */
  @Override
  protected Statement visitCallDelegateNode(CallDelegateNode node) {
    return renderCallNode(
        node,
        () -> {
          Label reattachPoint = new Label();
          Expression variantExpr;
          if (node.getDelCalleeVariantExpr() == null) {
            variantExpr = constant("");
          } else {
            variantExpr =
                exprCompiler
                    .compileSubExpression(
                        node.getDelCalleeVariantExpr(),
                        detachState.createExpressionDetacher(reattachPoint))
                    .coerceToString();
          }
          return parameterLookup
              .getRenderContext()
              .getDeltemplate(node.getDelCalleeName(), variantExpr, node.allowEmptyDefault())
              .labelStart(reattachPoint);
        });
  }

  private static final Handle STATIC_CALL_HANDLE =
      MethodRef.create(
              ClassLoaderFallbackCallFactory.class,
              "bootstrapCall",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              String.class,
              String[].class)
          .asHandle();

  private static final Handle STATIC_TEMPLATE_HANDLE =
      MethodRef.create(
              ClassLoaderFallbackCallFactory.class,
              "bootstrapTemplateLookup",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              String.class)
          .asHandle();
  private static final String TEMPLATE_METHOD_DESCRIPTOR =
      Type.getMethodDescriptor(
          BytecodeUtils.COMPILED_TEMPLATE_TYPE, BytecodeUtils.RENDER_CONTEXT_TYPE);

  @Override
  protected Statement visitCallBasicNode(CallBasicNode node) {
    if (node.isStaticCall()) {
      // Use invokedynamic to bind to the method.  This allows applications using complex
      // classloader setups to have {call} commands cross classloader boundaries.  It also enables
      // our stubbing library to intercept all calls.
      CompiledTemplateMetadata metadata = CompiledTemplateMetadata.create(node);
      return renderCallNode(
          node,
          new CallGenerator() {
            @Override
            public Expression asCompiledTemplate() {
              Expression renderContext = parameterLookup.getRenderContext();
              return new Expression(BytecodeUtils.COMPILED_TEMPLATE_TYPE, Feature.NON_NULLABLE) {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  renderContext.gen(adapter);
                  adapter.visitInvokeDynamicInsn(
                      "template",
                      TEMPLATE_METHOD_DESCRIPTOR,
                      STATIC_TEMPLATE_HANDLE,
                      node.getCalleeName());
                }
              };
            }

            @Override
            public Optional<DirectPositionalCallGenerator> asDirectPositionalCall() {
              if (metadata.hasPositionalSignature()) {
                return Optional.of(
                    new DirectPositionalCallGenerator() {
                      @Override
                      public TemplateType calleeType() {
                        return metadata.templateType();
                      }

                      @Override
                      public Expression call(
                          List<Expression> params,
                          Expression ij,
                          AppendableExpression appendable,
                          RenderContextExpression renderContext) {
                        return new Expression(RENDER_RESULT_TYPE) {
                          @Override
                          protected void doGen(CodeBuilder adapter) {
                            params.forEach(p -> p.gen(adapter));
                            ij.gen(adapter);
                            appendable.gen(adapter);
                            renderContext.gen(adapter);
                            adapter.visitInvokeDynamicInsn(
                                "call",
                                metadata.positionalRenderMethod().get().method().getDescriptor(),
                                STATIC_CALL_HANDLE,
                                Stream.concat(
                                        Stream.of(node.getCalleeName()),
                                        metadata.templateType().getActualParameters().stream()
                                            .map(p -> p.getName()))
                                    .toArray(Object[]::new));
                          }
                        };
                      }
                    });
              }
              return Optional.empty();
            }

            @Override
            public Optional<DirectCallGenerator> asDirectCall() {
              return Optional.of(
                  (params, ij, appendable, renderContext) ->
                      new Expression(RENDER_RESULT_TYPE) {
                        @Override
                        protected void doGen(CodeBuilder adapter) {
                          params.gen(adapter);
                          ij.gen(adapter);
                          appendable.gen(adapter);
                          renderContext.gen(adapter);
                          adapter.visitInvokeDynamicInsn(
                              "call",
                              metadata.renderMethod().method().getDescriptor(),
                              STATIC_CALL_HANDLE,
                              node.getCalleeName());
                        }
                      });
            }
          });
    } else {
      return renderCallNode(
          node,
          () ->
              exprCompiler
                  .compileRootExpression(node.getCalleeExpr(), detachState)
                  .checkedCast(BytecodeUtils.COMPILED_TEMPLATE_TEMPLATE_VALUE_TYPE)
                  .invoke(MethodRef.COMPILED_TEMPLATE_GET_TEMPLATE));
    }
  }

  private static DirectCallGenerator directCallFromTemplateExpression(
      Expression compiledTemplateExpression) {
    return (params, ij, output, context) ->
        compiledTemplateExpression.invoke(
            MethodRef.COMPILED_TEMPLATE_RENDER, params, ij, output, context);
  }

  private static BoundCallGenerator simpleCall(
      Expression compiledTemplateExpression, Expression params) {
    return simpleCall(directCallFromTemplateExpression(compiledTemplateExpression), params);
  }

  private static BoundCallGenerator simpleCall(
      DirectCallGenerator callGenerator, Expression params) {
    return (ij, output, context) -> callGenerator.call(params, ij, output, context);
  }

  /**
   * Renders a {@link com.google.template.soy.jbcsrc.shared.CompiledTemplate} incrementally.
   *
   * <p>Similar to {@link #renderIncrementally(Expression, List, Label, boolean)}, we need to:
   *
   * <ul>
   *   <li>Stash the CompiledTemplate in a field {@code $currentCallee}, so that if we detach
   *       halfway through rendering we don't lose the value. Note, we could use the scope/variable
   *       system of {@link TemplateVariableManager} to manage this value, but we know there will
   *       only ever be 1 live at a time, so we can just manage the single special field ourselves.
   *   <li>Either apply all the streaming autoescapers to the current appendable and, stash it in
   *       the {@code $currentAppendable} field for the same reasons as above, or call {@link
   *       JbcSrcRuntime#applyEscapers} to apply non-streaming print directives.
   *   <li>Invoke {@link com.google.template.soy.jbcsrc.shared.CompiledTemplate#render} with the
   *       standard detach logic.
   *   <li>Clear the two fields once rendering is complete.
   * </ul>
   *
   * @param parametersReattachPoint The label where execution should resume if we need to detach
   *     while calculating parameters.
   * @param node The call node
   * @param calleeExpression The expression that resolves to a constructed instance of the template
   * @return A statement rendering the template.
   */
  private Statement renderCallNode(CallNode node, CallGenerator callGenerator) {
    Statement initAppendable = Statement.NULL_STATEMENT;
    Statement flushAppendable = Statement.NULL_STATEMENT;
    AppendableExpression appendable = appendableExpression;

    final BoundCallGenerator boundCall;
    final Statement initParams;

    TemplateVariableManager.Scope renderScope = variables.enterScope();
    RecordOrPositional paramsExpression = prepareParamsHelper(node);
    Statement initCallee = Statement.NULL_STATEMENT;
    if (!areAllPrintDirectivesStreamable(node)) {
      // in this case we need to wrap a CompiledTemplate to apply escaping directives, so we
      // definitely need a record style call.
      ExpressionAndInitializer expressionAndInitializer = paramsExpression.asRecord(renderScope);
      initParams = expressionAndInitializer.initializer();
      Expression calleeExpression =
          MethodRef.RUNTIME_APPLY_ESCAPERS.invoke(
              callGenerator.asCompiledTemplate(), getEscapingDirectivesList(node));
      TemplateVariableManager.Variable calleeVariable =
          renderScope.createSynthetic(
              SyntheticVarName.renderee(),
              calleeExpression,
              TemplateVariableManager.SaveStrategy.STORE);
      initCallee = calleeVariable.initializer();
      boundCall = simpleCall(calleeVariable.accessor(), expressionAndInitializer.expression());
    } else {
      Optional<DirectPositionalCallGenerator> asDirectPositionalCall =
          callGenerator.asDirectPositionalCall();
      Optional<ListOfExpressionsAndInitializer> explicitParams;
      if (asDirectPositionalCall.isPresent()
          && (explicitParams =
                  paramsExpression.asPositionalParams(
                      renderScope, asDirectPositionalCall.get().calleeType()))
              .isPresent()) {
        initParams = explicitParams.get().initializer();
        boundCall =
            (ij, output, context) ->
                asDirectPositionalCall
                    .get()
                    .call(explicitParams.get().expressions(), ij, output, context);
      } else {
        Optional<DirectCallGenerator> asDirectCall = callGenerator.asDirectCall();
        ExpressionAndInitializer expressionAndInitializer = paramsExpression.asRecord(renderScope);
        initParams = expressionAndInitializer.initializer();
        if (asDirectCall.isPresent()) {
          boundCall = simpleCall(asDirectCall.get(), expressionAndInitializer.expression());
        } else {
          TemplateVariableManager.Variable calleeVariable =
              renderScope.createSynthetic(
                  SyntheticVarName.renderee(),
                  callGenerator.asCompiledTemplate(),
                  TemplateVariableManager.SaveStrategy.STORE);
          initCallee = calleeVariable.initializer();
          boundCall = simpleCall(calleeVariable.accessor(), expressionAndInitializer.expression());
        }
      }
      if (!node.getEscapingDirectives().isEmpty()) {
        PrintDirectives.AppendableAndFlushBuffersDepth wrappedAppendable =
            applyStreamingEscapingDirectives(
                node.getEscapingDirectives(), appendable, parameterLookup.getPluginContext());
        TemplateVariableManager.Variable variable =
            renderScope.createSynthetic(
                SyntheticVarName.appendable(),
                wrappedAppendable.appendable(),
                // TODO(lukes): this could be STORE or derive depending on whether or not flush
                // logic is required.
                TemplateVariableManager.SaveStrategy.STORE);
        initAppendable = variable.initializer();
        appendable = AppendableExpression.forExpression(variable.accessor());
        if (wrappedAppendable.flushBuffersDepth() >= 0) {
          flushAppendable = appendable.flushBuffers(wrappedAppendable.flushBuffersDepth());
        }
      }
    }

    Expression callRender =
        boundCall
            .call(parameterLookup.getIjRecord(), appendable, parameterLookup.getRenderContext())
            // make sure to tag this expression with the source location to ensure stack traces are
            // accurate.
            .withSourceLocation(node.getSourceLocation());
    Statement callCallee = detachState.detachForRender(callRender);
    // We need to init the appendable after the parmas because initializing the params may require
    // rendering params into temporary buffers which may themselves use the currentAppendable local.
    return Statement.concat(
        initParams,
        initCallee,
        initAppendable,
        callCallee,
        flushAppendable,
        renderScope.exitScope());
  }

  private Expression getEscapingDirectivesList(CallNode node) {
    ImmutableList<SoyPrintDirective> escapingDirectives = node.getEscapingDirectives();
    List<Expression> directiveExprs = new ArrayList<>(escapingDirectives.size());
    for (SoyPrintDirective directive : escapingDirectives) {
      directiveExprs.add(parameterLookup.getRenderContext().getPrintDirective(directive.getName()));
    }
    return BytecodeUtils.asImmutableList(directiveExprs);
  }

  @AutoValue
  abstract static class ExpressionAndInitializer {
    static ExpressionAndInitializer create(Expression expression, Statement initializer) {
      return new AutoValue_SoyNodeCompiler_ExpressionAndInitializer(expression, initializer);
    }

    abstract Expression expression();

    abstract Statement initializer();
  }

  @AutoValue
  abstract static class ListOfExpressionsAndInitializer {
    static ListOfExpressionsAndInitializer create(
        ImmutableList<Expression> expressions, Statement initializer) {
      return new AutoValue_SoyNodeCompiler_ListOfExpressionsAndInitializer(
          expressions, initializer);
    }

    abstract ImmutableList<Expression> expressions();

    abstract Statement initializer();
  }

  @AutoValue
  abstract static class RecordOrPositional {
    static RecordOrPositional create(Expression record) {
      return create(Suppliers.ofInstance(record), Optional.empty());
    }

    static RecordOrPositional create(
        Supplier<Expression> record,
        Optional<ImmutableMap<CallParamNode, Supplier<Expression>>> explicit) {
      return new AutoValue_SoyNodeCompiler_RecordOrPositional(record, explicit);
    }

    ExpressionAndInitializer asRecord(TemplateVariableManager.Scope scope) {
      // params will only be 'cheap' if they are something trivial like the empty constant
      // or data="all", in those cases we don't need to save/restore anything.
      Expression record = record().get();
      Statement initialize = Statement.NULL_STATEMENT;
      if (!record.isCheap()) {
        TemplateVariableManager.Variable paramsVariable =
            scope.createSynthetic(
                SyntheticVarName.params(), record, TemplateVariableManager.SaveStrategy.STORE);
        record = paramsVariable.accessor();
        initialize = paramsVariable.initializer();
      }
      return ExpressionAndInitializer.create(record, initialize);
    }

    Optional<ListOfExpressionsAndInitializer> asPositionalParams(
        TemplateVariableManager.Scope scope, TemplateType calleeType) {
      if (!explicit().isPresent()) {
        return Optional.empty();
      }

      List<Statement> initStatements = new ArrayList<>();
      ImmutableList.Builder<Expression> builder = ImmutableList.builder();
      Map<String, CallParamNode> nameToNode =
          explicit().get().keySet().stream().collect(toMap(n -> n.getKey().identifier(), n -> n));
      Map<CallParamNode, Supplier<Expression>> explicit = new HashMap<>(explicit().get());
      for (TemplateType.Parameter param : calleeType.getActualParameters()) {
        CallParamNode paramNode = nameToNode.get(param.getName());
        Supplier<Expression> supplier = explicit.remove(paramNode);
        Expression value =
            supplier == null ? BytecodeUtils.constantNull(SOY_VALUE_PROVIDER_TYPE) : supplier.get();
        if (!value.isCheap()) {
          TemplateVariableManager.Variable variable =
              scope.createSynthetic(
                  SyntheticVarName.forParam(paramNode),
                  value,
                  TemplateVariableManager.SaveStrategy.STORE);
          value = variable.accessor();
          initStatements.add(variable.initializer());
        }
        builder.add(value);
      }
      if (!explicit.isEmpty()) {
        // sanity check
        throw new AssertionError("failed to use: " + explicit);
      }
      return Optional.of(
          ListOfExpressionsAndInitializer.create(
              builder.build(), Statement.concat(initStatements)));
    }

    abstract Supplier<Expression> record();

    abstract Optional<ImmutableMap<CallParamNode, Supplier<Expression>>> explicit();
  }

  private RecordOrPositional prepareParamsHelper(CallNode node) {
    if (node.numChildren() == 0) {
      if (!node.isPassingData()) {
        return RecordOrPositional.create(
            Suppliers.ofInstance(FieldRef.EMPTY_PARAMS.accessor()), Optional.of(ImmutableMap.of()));
      } else if (!node.isPassingAllData()) {
        Label reattachLabel = new Label();
        return RecordOrPositional.create(
            getDataRecordExpression(node, reattachLabel).labelStart(reattachLabel));
      }

      Expression paramsRecord = parameterLookup.getParamsRecord();
      return RecordOrPositional.create(
          maybeAddDefaultParams(
                  node,
                  ConstructorRef.PARAM_STORE_AUGMENT.construct(
                      paramsRecord, constant(node.numChildren())))
              .orElse(paramsRecord));
    }

    ImmutableMap<CallParamNode, Supplier<Expression>> explicitParams = compileExplicitParams(node);
    Supplier<Expression> recordExpression =
        () -> {
          Expression paramStoreExpression = getParamStoreExpression(node);
          for (Map.Entry<CallParamNode, Supplier<Expression>> explicit :
              explicitParams.entrySet()) {
            String paramKey = explicit.getKey().getKey().identifier();
            // ParamStore.setField return 'this' so we can just chain the invocations together.
            paramStoreExpression =
                MethodRef.PARAM_STORE_SET_FIELD.invoke(
                    paramStoreExpression,
                    BytecodeUtils.constant(paramKey),
                    explicit.getValue().get());
          }
          return paramStoreExpression;
        };
    return RecordOrPositional.create(
        recordExpression, node.isPassingData() ? Optional.empty() : Optional.of(explicitParams));
  }

  private ImmutableMap<CallParamNode, Supplier<Expression>> compileExplicitParams(CallNode node) {
    ImmutableMap.Builder<CallParamNode, Supplier<Expression>> builder = ImmutableMap.builder();
    for (CallParamNode child : node.getChildren()) {
      String paramKey = child.getKey().identifier();
      Supplier<Expression> valueExpr;
      if (child instanceof CallParamContentNode) {
        valueExpr =
            () ->
                new LazyClosureCompiler(this)
                    .compileLazyContent("param", (CallParamContentNode) child, paramKey)
                    .soyValueProvider();
      } else {
        valueExpr =
            () ->
                new LazyClosureCompiler(this)
                    .compileLazyExpression(
                        "param", child, paramKey, ((CallParamValueNode) child).getExpr())
                    .soyValueProvider();
      }
      builder.put(child, valueExpr);
    }
    return builder.build();
  }

  /**
   * Returns an expression that creates a new {@link ParamStore} suitable for holding all the
   * parameters.
   */
  private Expression getParamStoreExpression(CallNode node) {
    if (!node.isPassingData()) {
      return ConstructorRef.PARAM_STORE_SIZE.construct(constant(node.numChildren()));
    }

    Label reattachDataLabel = new Label();
    Expression dataExpression;
    if (node.isPassingAllData()) {
      dataExpression = parameterLookup.getParamsRecord();
    } else {
      dataExpression = getDataRecordExpression(node, reattachDataLabel);
    }
    Expression paramStoreExpression =
        ConstructorRef.PARAM_STORE_AUGMENT
            .construct(dataExpression, constant(node.numChildren()))
            .labelStart(reattachDataLabel);
    if (node.isPassingAllData()) {
      paramStoreExpression =
          maybeAddDefaultParams(node, paramStoreExpression).orElse(paramStoreExpression);
    }
    return paramStoreExpression;
  }

  private Optional<Expression> maybeAddDefaultParams(
      CallNode node, Expression paramStoreExpression) {
    boolean foundDefaultParams = false;
    // If this is a data="all" call and the caller has default parameters we need to augment the
    // params record to make sure any unset default parameters are set to the default in the
    // params record. It's not worth it to determine if we're using the default value or not
    // here, so just augment all default parameters with whatever value they ended up with.
    for (TemplateParam param : node.getNearestAncestor(TemplateNode.class).getParams()) {
      if (param.hasDefault()) {
        foundDefaultParams = true;
        paramStoreExpression =
            MethodRef.PARAM_STORE_SET_FIELD.invoke(
                paramStoreExpression,
                BytecodeUtils.constant(param.name()),
                parameterLookup.getParam(param));
      }
    }
    return foundDefaultParams ? Optional.of(paramStoreExpression) : Optional.empty();
  }

  private Expression getDataRecordExpression(CallNode node, Label reattachPoint) {
    return exprCompiler
        .compileSubExpression(
            node.getDataExpr(), detachState.createExpressionDetacher(reattachPoint))
        .box()
        .checkedCast(SoyRecord.class);
  }

  @Override
  protected Statement visitLogNode(LogNode node) {
    return compilerWithNewAppendable(AppendableExpression.logger()).visitChildrenInNewScope(node);
  }

  @Override
  protected Statement visitLetValueNode(LetValueNode node) {
    return storeClosure(
        new LazyClosureCompiler(this)
            .compileLazyExpression("let", node, node.getVarName(), node.getExpr()));
  }

  @Override
  protected Statement visitLetContentNode(LetContentNode node) {
    return storeClosure(
        new LazyClosureCompiler(this).compileLazyContent("let", node, node.getVarName()));
  }

  Statement storeClosure(LazyClosure newLetValue) {
    if (newLetValue.isTrivial()) {
      currentScope.createTrivial(newLetValue.name(), newLetValue.soyValueProvider());
      return Statement.NULL_STATEMENT;
    } else {
      return currentScope
          .create(
              newLetValue.name(),
              newLetValue.soyValueProvider(),
              TemplateVariableManager.SaveStrategy.STORE)
          .initializer();
    }
  }

  @Override
  protected Statement visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    // trivial node that is just a number of children surrounded by raw text nodes.
    return Statement.concat(visitChildren(node));
  }

  @Override
  protected Statement visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException(
        "The jbcsrc backend doesn't support: " + node.getKind() + " nodes yet.");
  }

  private MsgCompiler getMsgCompiler() {
    return new MsgCompiler(
        detachState,
        parameterLookup,
        variables,
        appendableExpression,
        new PlaceholderCompiler() {
          @Override
          public Placeholder compile(ExprRootNode node, ExpressionDetacher expressionDetatcher) {
            return Placeholder.create(
                expressionToSoyValueProviderCompiler.compile(node, expressionDetatcher),
                exprCompiler.requiresDetach(node));
          }

          @Override
          public Placeholder compile(
              String phname,
              StandaloneNode node,
              ExtraCodeCompiler prefix,
              ExtraCodeCompiler suffix) {
            // We want to use the LazyClosureCompiler to optionally produce a new class for this
            // node.  To do this we create a synthetic `let` variable.
            // We need to take `node` and reparent it as the child of the `let`, we also need to
            // insert this let into the AST in the original location.  This is because the
            // LazyClosureCompiler makes code generation decisions by querying ancestors, so it
            // needs to be part of the main tree.
            LetContentNode fakeLet =
                LetContentNode.forVariable(
                    /*id=*/ -1,
                    node.getSourceLocation(),
                    "$" + phname,
                    node.getSourceLocation(),
                    SanitizedContentKind.TEXT);
            MsgPlaceholderNode placeholderParent = (MsgPlaceholderNode) node.getParent();
            checkState(
                placeholderParent.numChildren() == 1,
                "expected placeholder %s (%s) to be the only child of our parent: %s",
                phname,
                node,
                placeholderParent);
            fakeLet.addChild(node); // NOTE: this removes node from placeholderParent
            placeholderParent.addChild(fakeLet);

            LazyClosureCompiler.LazyClosure closure =
                new LazyClosureCompiler(SoyNodeCompiler.this)
                    .compileLazyContent("ph", fakeLet, phname, prefix, suffix);
            placeholderParent.removeChild(fakeLet);
            placeholderParent.addChild(node); // Restore the tree to the prior state.
            return Placeholder.create(
                closure.soyValueProvider(), closure.requiresDetachLogicToResolve());
          }
        });
  }

  /** Returns a {@link SoyNodeCompiler} identical to this one but with an alternate appendable. */
  SoyNodeCompiler compilerWithNewAppendable(AppendableExpression appendable) {
    return new SoyNodeCompiler(
        analysis,
        innerClasses,
        detachState,
        variables,
        parameterLookup,
        fields,
        appendable,
        exprCompiler,
        expressionToSoyValueProviderCompiler,
        constantCompiler,
        javaSourceFunctionCompiler);
  }
}
