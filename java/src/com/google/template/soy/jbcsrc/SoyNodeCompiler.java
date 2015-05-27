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
import static com.google.template.soy.jbcsrc.BytecodeUtils.compareSoyEquals;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constantNull;
import static com.google.template.soy.jbcsrc.Statement.NULL_STATEMENT;
import static com.google.template.soy.jbcsrc.VariableSet.SaveStrategy.DERIVED;
import static com.google.template.soy.jbcsrc.VariableSet.SaveStrategy.STORE;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.ControlFlow.IfBlock;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.VariableSet.SaveStrategy;
import com.google.template.soy.jbcsrc.VariableSet.Scope;
import com.google.template.soy.jbcsrc.VariableSet.Variable;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallNode.DataAttribute;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNode.RangeArgs;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.XidNode;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Compiles {@link SoyNode soy nodes} into {@link Statement statements}.
 *
 * <p>The normal contract for {@link Statement statements} is that they leave the state of the
 * runtime stack unchanged before and after execution.  The SoyNodeCompiler requires that the
 * runtime stack be <em>empty</em> prior to any of the code produced.
 */
final class SoyNodeCompiler extends AbstractReturningSoyNodeVisitor<Statement> {
   // TODO(lukes): consider introducing a Builder or a non-static Factory.

   /**
    * Creates a SoyNodeCompiler
    *
    * @param innerClasses The current set of inner classes
    * @param stateField The field on the current class that holds the state variable
    * @param thisVar An expression that returns 'this'
    * @param appendableVar An expression that returns the current AdvisingAppendable that we are
    *     rendering into
    * @param variableSet The variable set for generating locals and fields
    * @param variables The variable lookup table for reading locals.
    */
  static SoyNodeCompiler create(
      CompiledTemplateRegistry registry,
      InnerClasses innerClasses,
      FieldRef stateField,
      Expression thisVar,
      Expression appendableVar,
      VariableSet variableSet,
      VariableLookup variables,
      ErrorReporter errorReporter) {
    DetachState detachState = new DetachState(variableSet, thisVar, stateField);
    ExpressionCompiler expressionCompiler =
        ExpressionCompiler.create(detachState, variables, errorReporter);
    ExpressionToSoyValueProviderCompiler soyValueProviderCompiler =
        ExpressionToSoyValueProviderCompiler.create(expressionCompiler, variables, errorReporter);
    return new SoyNodeCompiler(
        thisVar,
        registry,
        detachState,
        variableSet,
        variables,
        appendableVar,
        expressionCompiler,
        soyValueProviderCompiler,
        new LazyClosureCompiler(
            registry, innerClasses, variables, errorReporter, soyValueProviderCompiler),
        errorReporter);
  }

  private final Expression thisVar;
  private final CompiledTemplateRegistry registry;
  private final DetachState detachState;
  private final VariableSet variables;
  private final VariableLookup variableLookup;
  private final Expression appendableExpression;
  private final ExpressionCompiler exprCompiler;
  private final ExpressionToSoyValueProviderCompiler expressionToSoyValueProviderCompiler;
  private final LazyClosureCompiler lazyClosureCompiler;
  private Scope currentScope;

  SoyNodeCompiler(
      Expression thisVar,
      CompiledTemplateRegistry registry,
      DetachState detachState,
      VariableSet variables,
      VariableLookup variableLookup,
      Expression appendableExpression,
      ExpressionCompiler exprCompiler,
      ExpressionToSoyValueProviderCompiler expressionToSoyValueProviderCompiler,
      LazyClosureCompiler lazyClosureCompiler,
      ErrorReporter errorReporter) {
    super(errorReporter);
    // TODO(lukes): consider extracting a special subtype of Expression for the appendable, we could
    // then associate additional metadata with it (like, does it support detaching).
    appendableExpression.checkAssignableTo(Type.getType(AdvisingAppendable.class));
    this.thisVar = checkNotNull(thisVar);
    this.registry = checkNotNull(registry);
    this.detachState = checkNotNull(detachState);
    this.variables = checkNotNull(variables);
    this.variableLookup = checkNotNull(variableLookup);
    this.appendableExpression = checkNotNull(appendableExpression);
    this.exprCompiler = checkNotNull(exprCompiler);
    this.expressionToSoyValueProviderCompiler = checkNotNull(expressionToSoyValueProviderCompiler);
    this.lazyClosureCompiler = checkNotNull(lazyClosureCompiler);
  }

  Statement compile(TemplateNode node) {
    Statement templateBody = visit(node);
    Statement jumpTable = detachState.generateReattachTable();
    return Statement.concat(jumpTable, templateBody);
  }

  Statement compileChildren(RenderUnitNode node) {
    Statement templateBody = visitChildrenInNewScope(node);
    Statement jumpTable = detachState.generateReattachTable();
    return Statement.concat(jumpTable, templateBody);
  }

  @Override protected Statement visitTemplateNode(TemplateNode node) {
    return visitChildrenInNewScope(node);
  }

  private Statement visitChildrenInNewScope(BlockNode node) {
    Scope prev = currentScope;
    currentScope = variables.enterScope();
    List<Statement> children = visitChildren(node);
    Statement leave = currentScope.exitScope();
    children.add(leave);
    currentScope = prev;
    return Statement.concat(children).withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitIfNode(IfNode node) {
    List<IfBlock> ifs = new ArrayList<>();
    Optional<Statement> elseBlock = Optional.absent();
    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        SoyExpression cond =
            exprCompiler.compile(icn.getExprUnion().getExpr()).convert(boolean.class);
        Statement block = visitChildrenInNewScope(icn);
        ifs.add(IfBlock.create(cond, block));
      } else {
        IfElseNode ien = (IfElseNode) child;
        elseBlock = Optional.of(visitChildrenInNewScope(ien));
      }
    }
    return ControlFlow.ifElseChain(ifs, elseBlock).withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitSwitchNode(SwitchNode node) {
    SoyExpression expression = exprCompiler.compile(node.getExpr());
    Statement init;
    List<IfBlock> cases = new ArrayList<>();
    Optional<Statement> defaultBlock = Optional.absent();
    Scope scope = variables.enterScope();
    Variable variable = scope.createSynthetic(SyntheticVarName.forSwitch(node), expression, STORE);
    init = variable.initializer();
    expression = expression.withSource(variable.local());

    for (SoyNode child : node.getChildren()) {
      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode caseNode = (SwitchCaseNode) child;
        Label reattachPoint = new Label();
        List<Expression> comparisons = new ArrayList<>();
        for (ExprRootNode caseExpr : caseNode.getExprList()) {
          comparisons.add(compareSoyEquals(expression,
              exprCompiler.compile(caseExpr, reattachPoint)));
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
    return Statement.concat(init, ControlFlow.ifElseChain(cases, defaultBlock), exitScope)
        .withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitForNode(ForNode node) {
    // Despite appearances, range() is not a soy function, it is essentially a keyword that only
    // works in for loops, there are 3 forms.
    // {for $i in range(3)}{$i}{/for} -> 0 1 2
    // {for $i in range(2, 5)} ... {/for} -> 2 3 4
    // {for $i in range(2, 8, 2)} ... {/for} -> 2 4 6

    Scope scope = variables.enterScope();
    final CompiledRangeArgs rangeArgs = calculateRangeArgs(node, scope);
    // The currentIndex variable has a user defined name and we always need a local for it because
    // we mutate it across loop iterations.
    final Variable currentIndex = scope.create(node.getVarName(), rangeArgs.startIndex(), STORE);
    final Statement incrementCurrentIndex = incrementInt(currentIndex, rangeArgs.increment());

    final Statement loopBody = visitChildrenInNewScope(node);

    // Note it is important that exitScope is called _after_ the children are visited.
    // TODO(lukes): this is somewhat error-prone... we could maybe manage it by have the scope
    // maintain a sequence of statements and then all statements would be added to Scope which would
    // return a statement for the whole thing at the end... would that be clearer?
    final Statement exitScope = scope.exitScope();
    return new Statement(node.getSourceLocation()) {
      @Override void doGen(CodeBuilder adapter) {
        for (Statement initializer : rangeArgs.initStatements()) {
          initializer.gen(adapter);
        }
        currentIndex.initializer().gen(adapter);
        // We need to check for an empty loop by doing an entry test
        Label loopStart = adapter.mark();

        // If current >= limit we are done
        currentIndex.local().gen(adapter);
        rangeArgs.limit().gen(adapter);
        Label end = new Label();
        adapter.ifCmp(Type.INT_TYPE, Opcodes.IFGE, end);

        loopBody.gen(adapter);

        // at the end of the loop we need to increment and jump back.
        incrementCurrentIndex.gen(adapter);
        adapter.goTo(loopStart);
        adapter.mark(end);
        exitScope.gen(adapter);
      }
    };
  }

  @AutoValue abstract static class CompiledRangeArgs {
    /** How much to increment the loop index by, defaults to {@code 1}. */
    abstract Expression increment();

    /** Where to start loop iteration, defaults to {@code 0}. */
    abstract Expression startIndex();

    /** Where to end loop iteration, defaults to {@code 0}. */
    abstract Expression limit();

    /** Statements that must have been run prior to using any of the above expressions. */
    abstract ImmutableList<Statement> initStatements();
  }

  /**
   * Interprets the given expressions as the arguments of a {@code range(...)} expression in a
   * {@code for} loop.
   */
  private CompiledRangeArgs calculateRangeArgs(ForNode forNode, Scope scope) {
    RangeArgs rangeArgs = forNode.getRangeArgs();
    final ImmutableList.Builder<Statement> initStatements = ImmutableList.builder();
    Expression increment;
    if (rangeArgs.increment().isPresent()) {
      Label detachPoint = new Label();
      increment = MethodRef.INTS_CHECKED_CAST.invoke(
          exprCompiler.compile(rangeArgs.increment().get(), detachPoint).convert(long.class));
      // If the expression is non-trivial, make sure to save it to a field.
      Variable variable = scope.createSynthetic(
          SyntheticVarName.forLoopIncrement(forNode),
          increment,
          increment.isConstant() ? DERIVED : STORE);
      initStatements.add(variable.initializer().labelStart(detachPoint));
      increment = variable.local();
    } else {
      increment = constant(1);
    }
    Expression startIndex;
    if (rangeArgs.start().isPresent()) {
      startIndex = MethodRef.INTS_CHECKED_CAST.invoke(
          exprCompiler.compile(rangeArgs.start().get()).convert(long.class));
    } else {
      startIndex = constant(0);
    }
    Label detachPoint = new Label();
    Expression limit = MethodRef.INTS_CHECKED_CAST.invoke(
        exprCompiler.compile(rangeArgs.limit(), detachPoint).convert(long.class));
    // If the expression is non-trivial we should cache it in a local variable
    Variable variable = scope.createSynthetic(
        SyntheticVarName.forLoopLimit(forNode),
        limit,
        limit.isConstant() ? DERIVED : STORE);
    initStatements.add(variable.initializer().labelStart(detachPoint));
    limit = variable.local();
    return new AutoValue_SoyNodeCompiler_CompiledRangeArgs(
        increment, startIndex, limit, initStatements.build());
  }

  private Statement incrementInt(final Variable variable, final Expression increment) {
    Expression nextIndex = new Expression.SimpleExpression(Type.INT_TYPE, false) {
      @Override void doGen(CodeBuilder adapter) {
        variable.local().gen(adapter);
        increment.gen(adapter);
        adapter.visitInsn(Opcodes.IADD);
      }
    };
    return variable.local().store(nextIndex);
  }

  @Override protected Statement visitForeachNode(ForeachNode node) {
    ForeachNonemptyNode nonEmptyNode = (ForeachNonemptyNode) node.getChild(0);
    SoyExpression expr = exprCompiler.compile(node.getExpr()).convert(List.class);
    Scope scope = variables.enterScope();
    final Variable listVar =
        scope.createSynthetic(SyntheticVarName.foreachLoopList(nonEmptyNode), expr, STORE);
    final Variable indexVar =
        scope.createSynthetic(SyntheticVarName.foreachLoopIndex(nonEmptyNode), constant(0), STORE);
    final Variable listSizeVar =
        scope.createSynthetic(SyntheticVarName.foreachLoopLength(nonEmptyNode),
            MethodRef.LIST_SIZE.invoke(listVar.local()), DERIVED);
    final Variable itemVar = scope.create(nonEmptyNode.getVarName(),
        MethodRef.LIST_GET.invoke(listVar.local(),
            indexVar.local()).cast(Type.getType(SoyValueProvider.class)), SaveStrategy.DERIVED);
    final Statement loopBody = visitChildrenInNewScope(nonEmptyNode);
    final Statement exitScope = scope.exitScope();

    // it important for this to be generated after exitScope is called (or before enterScope)
    final Statement emptyBlock = node.numChildren() == 2
        ? visitChildrenInNewScope((ForeachIfemptyNode) node.getChild(1))
        : null;
    return new Statement() {
      @Override void doGen(CodeBuilder adapter) {
        listVar.initializer().gen(adapter);
        listSizeVar.initializer().gen(adapter);
        listSizeVar.local().gen(adapter);
        Label emptyListLabel = new Label();
        adapter.ifZCmp(Opcodes.IFEQ, emptyListLabel);
        indexVar.initializer().gen(adapter);
        Label loopStart = adapter.mark();
        itemVar.initializer().gen(adapter);

        loopBody.gen(adapter);

        adapter.iinc(indexVar.local().index(), 1);  // index++
        indexVar.local().gen(adapter);
        listSizeVar.local().gen(adapter);
        adapter.ifICmp(Opcodes.IFLT, loopStart);  // if index < list.size(), goto loopstart
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

  @Override protected Statement visitPrintNode(PrintNode node) {
    // First check our special case for compatible content types (no print directives) and an
    // expression that evaluates to a SoyValueProvider.  This will allow us to render incrementally
    if (node.getChildren().isEmpty()) {
      Label reattachPoint = new Label();
      ExprRootNode expr = node.getExprUnion().getExpr();
      Optional<Expression> asSoyValueProvider =
          expressionToSoyValueProviderCompiler.compileAvoidingBoxing(expr, reattachPoint);
      if (asSoyValueProvider.isPresent()) {
        return renderIncrementally(node, asSoyValueProvider.get(), reattachPoint);
      }
    }
    // otherwise we need to do some escapes or simply cannot do incremental rendering
    Label reattachPoint = new Label();
    SoyExpression value = compilePrintNodeAsExpression(node, reattachPoint);
    Expression renderSoyValue =
        appendableExpression.invoke(
            MethodRef.ADVISING_APPENDABLE_APPEND,
            value.convert(String.class))
                .labelStart(reattachPoint);
    return detachState.detachLimited(renderSoyValue)
        .withSourceLocation(node.getSourceLocation());
  }

  private SoyExpression compilePrintNodeAsExpression(PrintNode node, Label reattachPoint) {
    BasicExpressionCompiler basic = exprCompiler.asBasicCompiler(reattachPoint);
    SoyExpression value = basic.compile(node.getExprUnion().getExpr());
    // We may have print directives, that means we need to pass the render value through a bunch of
    // SoyJavaPrintDirective.apply methods.  This means lots and lots of boxing.
    // TODO(user): tracks adding streaming print directives which would help with this,
    // because instead of wrapping the soy value, we would just wrap the appendable.
    for (PrintDirectiveNode printDirective : node.getChildren()) {
      value = value.applyPrintDirective(
          variableLookup.getRenderContext(),
          printDirective.getName(),
          basic.compileToList(printDirective.getArgs()));
    }
    return value;
  }

  /**
   * TODO(lukes): if the expression is a param, then this is kind of silly since it looks like
   * <pre>{@code
   *   SoyValueProvider localParam = this.param;
   *   this.currentRenderee = localParam;
   *   SoyValueProvider localRenderee = this.currentRenderee;
   *   localRenderee.renderAndResolve();
   * }</pre>
   *
   * <p>In this case we could elide the currentRenderee altogether if we knew the soyValueProvider
   * expression was just a field read... And this is the _common_case for .renderAndResolve calls.
   * to actually do this we could add a mechanism similar to the SaveStrategy enum for expressions,
   * kind of like {@link Expression#isConstant()} which isn't that useful in practice.
   */
  private Statement renderIncrementally(PrintNode node, Expression soyValueProvider,
      Label reattachPoint) {
    // In this case we want to render the SoyValueProvider via renderAndResolve which will
    // enable incremental rendering of parameters for lazy transclusions!
    // This actually ends up looking a lot like how calls work so we use the same strategy.
    FieldRef currentRendereeField = variables.getCurrentRenderee();
    Statement initRenderee =
        currentRendereeField.putInstanceField(thisVar, soyValueProvider)
            .labelStart(reattachPoint);

    // This cast will always succeed.
    Expression callRenderAndResolve = currentRendereeField.accessor(thisVar)
        .invoke(MethodRef.SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE,
            appendableExpression,
            // the isLast param
            // TODO(lukes): pass a real value here when we have expression use analysis.
            constant(false));
    Statement doCall = detachState.detachForRender(callRenderAndResolve);
    Statement clearRenderee =
        currentRendereeField.putInstanceField(thisVar, constantNull(SoyValueProvider.class));
    return Statement.concat(initRenderee, doCall, clearRenderee)
        .withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitRawTextNode(RawTextNode node) {
    Expression render = MethodRef.ADVISING_APPENDABLE_APPEND
        .invoke(appendableExpression, constant(node.getRawText()));
    // TODO(lukes): add some heuristics about when to add this
    // ideas:
    // * never try to detach in certain 'contexts' (e.g. attribute context)
    // * never detach after rendering small chunks (< 128 bytes?)
    return detachState.detachLimited(render).withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitDebuggerNode(DebuggerNode node) {
    // intentional no-op.  java has no 'breakpoint' equivalent.  But we can add a label + line
    // number.  Which may be useful for debugging :)
    return NULL_STATEMENT.withSourceLocation(node.getSourceLocation());
  }

  // Note: xid and css translations are expected to be very short, so we do _not_ generate detaches
  // for them, even though they write to the output.

  @Override protected Statement visitXidNode(XidNode node) {
    return appendableExpression
        .invoke(
            MethodRef.ADVISING_APPENDABLE_APPEND,
            variableLookup.getRenderContext()
                .invoke(MethodRef.RENDER_CONTEXT_RENAME_XID, constant(node.getText())))
        .toStatement()
        .withSourceLocation(node.getSourceLocation());
  }

  // TODO(lukes):  The RenderVisitor optimizes css/xid renaming by stashing a one element cache in
  // the CSS node itself (keyed off the identity of the renaming map).  We could easily add such
  // an optimization via a static field in the Template class. Though im not sure it makes sense
  // as an optimization... this should just be an immutable map lookup keyed off of a constant
  // string. If we cared a lot, we could employ a simpler (and more compact) optimization by
  // assigning each selector a unique integer id and then instead of hashing we can just reference
  // an array (aka perfect hashing).  This could be part of our runtime library and ids could be
  // assigned at startup.

  @Override protected Statement visitCssNode(CssNode node) {
    Expression renamedSelector =
        variableLookup.getRenderContext()
            .invoke(MethodRef.RENDER_CONTEXT_RENAME_CSS_SELECTOR, constant(node.getSelectorText()));

    if (node.getComponentNameExpr() != null) {
      Label reattachPoint = new Label();
      SoyExpression compiledComponent =
          exprCompiler.compile(node.getComponentNameExpr(), reattachPoint).convert(String.class);
      return appendableExpression.invoke(MethodRef.ADVISING_APPENDABLE_APPEND, compiledComponent)
          .invoke(MethodRef.ADVISING_APPENDABLE_APPEND_CHAR, constant('-'))
          .invoke(MethodRef.ADVISING_APPENDABLE_APPEND, renamedSelector)
          .labelStart(reattachPoint)
          .toStatement()
          .withSourceLocation(node.getSourceLocation());
    }
    return appendableExpression.invoke(MethodRef.ADVISING_APPENDABLE_APPEND, renamedSelector)
        .toStatement()
        .withSourceLocation(node.getSourceLocation());
  }

  /**
   * Given this delcall:
   * {@code {delcall foo.bar variant="$expr" allowemptydefault="true"}}
   *
   * Generate code that looks like:
   * <pre> {@code
   *   renderContext.getDeltemplate("foo.bar", <variant-expression>, true)
   *       .create(<prepareParameters>, ijParams)
   *       .render(appendable, renderContext)
   *
   * }</pre>
   *
   * <p>We share logic with {@link #visitCallBasicNode(CallBasicNode)} around the actual calling
   * convention (setting up detaches, storing the template in a field). As well as the logic for
   * preparing the data record.  The only interesting part of delcalls is calculating the
   * {@code variant} and the fact that we have to invoke the {@link RenderContext} runtime to do
   * the deltemplate lookup.
   */
  @Override protected Statement visitCallDelegateNode(CallDelegateNode node) {
    Label reattachPoint = new Label();
    Expression variantExpr;
    if (node.getDelCalleeVariantExpr() == null) {
      variantExpr = constant("");
    } else {
      variantExpr =
          exprCompiler.compile(node.getDelCalleeVariantExpr(), reattachPoint).convert(String.class);
    }
    Expression calleeExpression =
        variableLookup.getRenderContext()
            .invoke(MethodRef.RENDER_CONTEXT_GET_DELTEMPLATE,
                constant(node.getDelCalleeName()),
                variantExpr,
                constant(node.allowsEmptyDefault()),
                prepareParamsHelper(node, reattachPoint),
                variableLookup.getIjRecord());

    return visitCallNodeHelper(
        node,
        registry.getDelTemplateContentKind(node.getDelCalleeName()),
        MethodRef.COMPILED_TEMPLATE_RENDER,
        reattachPoint,
        calleeExpression);
  }

  @Override protected Statement visitCallBasicNode(CallBasicNode node) {
    // Basic nodes are basic! We can just call the node directly.
    CompiledTemplateMetadata callee = registry.getTemplateInfo(node.getCalleeName());
    Label reattachPoint = new Label();
    Expression calleeExpression =
        callee.constructor()
            .construct(prepareParamsHelper(node, reattachPoint), variableLookup.getIjRecord());
    return visitCallNodeHelper(
        node,
        callee.node().getContentKind(),
        callee.renderMethod(),
        reattachPoint,
        calleeExpression);
  }

  private Statement visitCallNodeHelper(CallNode node,
      @Nullable ContentKind calleeContentKind,
      MethodRef renderMethod,
      Label reattachPoint,
      Expression calleeExpression) {
    FieldRef currentCalleeField = variables.getCurrentCalleeField();
    // Apply escaping directives if any
    if (!node.getEscapingDirectiveNames().isEmpty()) {
      List<Expression> directiveExprs = new ArrayList<>(node.getEscapingDirectiveNames().size());
      for (String directive : node.getEscapingDirectiveNames()) {
        directiveExprs.add(
            variableLookup.getRenderContext()
                .invoke(MethodRef.RENDER_CONTEXT_GET_PRINT_DIRECTIVE, constant(directive)));
      }
      calleeExpression =
          MethodRef.RUNTIME_APPLY_ESCAPERS.invoke(
              calleeExpression,
              BytecodeUtils.asList(directiveExprs),
              calleeContentKind == null
                  ? BytecodeUtils.constantNull(ContentKind.class)
                  : FieldRef.enumReference(calleeContentKind).accessor());
    }
    Statement initCallee =
        currentCalleeField.putInstanceField(thisVar, calleeExpression).labelStart(reattachPoint);

    // This cast will always succeed.
    Expression typedCallee = currentCalleeField.accessor(thisVar)
        .cast(calleeExpression.resultType());
    Expression callRender =
        typedCallee.invoke(renderMethod, appendableExpression, variableLookup.getRenderContext());
    Statement callCallee = detachState.detachForRender(callRender);
    Statement clearCallee = currentCalleeField.putInstanceField(thisVar,
        BytecodeUtils.constantNull(CompiledTemplate.class));
    return Statement.concat(initCallee, callCallee, clearCallee)
        .withSourceLocation(node.getSourceLocation());
  }

  private Expression prepareParamsHelper(CallNode node, Label reattachPoint) {
    DataAttribute dataAttribute = node.dataAttribute();
    if (node.numChildren() == 0) {
      // Easy, just use the data attribute
      return getDataExpression(dataAttribute, reattachPoint);
    } else {
      // Otherwise we need to build a dictionary from {param} statements.
      Expression paramStoreExpression = getParamStoreExpression(node, dataAttribute, reattachPoint);
      for (CallParamNode child : node.getChildren()) {
        String paramKey = child.getKey();
        Expression valueExpr;
        if (child instanceof CallParamContentNode) {
          valueExpr = lazyClosureCompiler.compileLazyContent(
              (CallParamContentNode) child, paramKey);
        } else {
          valueExpr = lazyClosureCompiler.compileLazyExpression(
              child, paramKey, ((CallParamValueNode) child).getValueExprUnion().getExpr());
        }
        // ParamStore.setField return 'this' so we can just chain the invocations together.
        paramStoreExpression =
            MethodRef.PARAM_STORE_SET_FIELD
                .invoke(paramStoreExpression, BytecodeUtils.constant(paramKey), valueExpr);
      }
      return paramStoreExpression;
    }
  }

  /**
   * Returns an expression that creates a new {@link ParamStore} suitable for holding all the
   */
  private Expression getParamStoreExpression(CallNode node, DataAttribute dataAttribute,
      Label reattachPoint) {
    Expression paramStoreExpression;
    if (dataAttribute.isPassingData()) {
      paramStoreExpression = ConstructorRef.AUGMENTED_PARAM_STORE.construct(
          getDataExpression(dataAttribute, reattachPoint), constant(node.numChildren()));
    } else {
      paramStoreExpression = ConstructorRef.BASIC_PARAM_STORE.construct(
          constant(node.numChildren()));
    }
    return paramStoreExpression;
  }

  private Expression getDataExpression(DataAttribute dataAttribute, Label reattachPoint) {
    if (dataAttribute.isPassingData()) {
      if (dataAttribute.isPassingAllData()) {
        return variableLookup.getParamsRecord();
      } else {
        return exprCompiler.compile(dataAttribute.dataExpr(), reattachPoint).box()
            .cast(SoyRecord.class);
      }
    } else {
      return FieldRef.EMPTY_DICT.accessor();
    }
  }

  @Override protected Statement visitLogNode(LogNode node) {
    return compilerWithNewAppendable(MethodRef.RUNTIME_LOGGER.invoke())
        .visitChildrenInNewScope(node);
  }

  @Override protected Statement visitLetValueNode(LetValueNode node) {
    Expression newLetValue =
        lazyClosureCompiler.compileLazyExpression(node, node.getVarName(), node.getValueExpr());
    return currentScope.create(node.getVarName(), newLetValue, STORE).initializer();
  }

  @Override protected Statement visitLetContentNode(LetContentNode node) {
    Expression newLetValue =
        lazyClosureCompiler.compileLazyContent(node, node.getVarName());
    return currentScope.create(node.getVarName(), newLetValue, STORE).initializer();
  }

  @Override protected Statement visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException(
        "The jbcsrc backend doesn't support: " + node.getKind() + " nodes yet.");
  }

  /** Returns a {@link SoyNodeCompiler} identical to this one but with an alternate appendable. */
  private SoyNodeCompiler compilerWithNewAppendable(Expression appendable) {
    return new SoyNodeCompiler(
        thisVar,
        registry,
        detachState,
        variables,
        variableLookup,
        appendable,
        exprCompiler,
        expressionToSoyValueProviderCompiler,
        lazyClosureCompiler,
        errorReporter);
  }
}
