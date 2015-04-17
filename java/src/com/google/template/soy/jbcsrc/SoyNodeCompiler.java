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
import static com.google.template.soy.jbcsrc.Statement.NULL_STATEMENT;
import static com.google.template.soy.jbcsrc.Statement.concat;
import static com.google.template.soy.jbcsrc.VariableSet.SaveStrategy.DERIVED;
import static com.google.template.soy.jbcsrc.VariableSet.SaveStrategy.STORE;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.ControlFlow.IfBlock;
import com.google.template.soy.jbcsrc.VariableSet.SaveStrategy;
import com.google.template.soy.jbcsrc.VariableSet.Scope;
import com.google.template.soy.jbcsrc.VariableSet.Variable;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
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
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.XidNode;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles {@link SoyNode soy nodes} into {@link Statement statements}.
 * 
 * <p>The normal contract for {@link Statement statements} is that they leave the state of the 
 * runtime stack unchanged before and after execution.  The SoyNodeCompiler requires that the 
 * runtime stack be <em>empty</em> prior to any of the code produced.
 */
final class SoyNodeCompiler extends AbstractReturningSoyNodeVisitor<Statement> {
  private final DetachState detachState;
  private final VariableSet variables;
  private final Expression appendableExpression;
  private final Expression contextExpression;
  private final ExpressionCompiler exprCompiler;

  SoyNodeCompiler(
      DetachState detachState,
      VariableSet variables, 
      Expression appendableExpression, 
      Expression contextExpression, 
      ExpressionCompiler exprCompiler) {
    appendableExpression.checkAssignableTo(Type.getType(AdvisingAppendable.class));
    contextExpression.checkAssignableTo(Type.getType(RenderContext.class));
    this.detachState = detachState;
    this.variables = variables;
    this.appendableExpression = appendableExpression;
    this.contextExpression = contextExpression;
    this.exprCompiler = checkNotNull(exprCompiler);
  }

  Statement compile(TemplateBasicNode node) {
    Statement templateBody = visit(node);
    Statement jumpTable = detachState.generateReattachTable();
    return Statement.concat(jumpTable, templateBody);
  }

  @Override protected Statement visitTemplateBasicNode(TemplateNode node) {
    return childrenAsStatement(node);
  }

  private Statement childrenAsStatement(ParentSoyNode<? extends SoyNode> node) {
    return Statement.concat(visitChildren(node)).withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitIfNode(IfNode node) {
    List<IfBlock> ifs = new ArrayList<>();
    Optional<Statement> elseBlock = Optional.absent();
    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        SoyExpression cond = 
            exprCompiler.compile(icn.getExprUnion().getExpr()).convert(boolean.class);
        Statement block = childrenAsStatement(icn);
        ifs.add(IfBlock.create(cond, block));
      } else {
        IfElseNode ien = (IfElseNode) child;
        elseBlock = Optional.of(childrenAsStatement(ien));
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
        List<Expression> comparisons = new ArrayList<>();
        for (ExprRootNode caseExpr : caseNode.getExprList()) {
          comparisons.add(compareSoyEquals(expression, exprCompiler.compile(caseExpr)));
        }
        Statement block = childrenAsStatement(caseNode);
        cases.add(IfBlock.create(BytecodeUtils.logicalOr(comparisons), block));
      } else {
        SwitchDefaultNode defaultNode = (SwitchDefaultNode) child;
        defaultBlock = Optional.of(childrenAsStatement(defaultNode));
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

    final Statement loopBody = childrenAsStatement(node);

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
      increment = MethodRef.INTS_CHECKED_CAST.invoke(
          exprCompiler.compile(rangeArgs.increment().get()).convert(long.class));
      // If the expression is non-trivial, make sure to save it to a field.
      Variable variable = scope.createSynthetic(
          SyntheticVarName.forLoopIncrement(forNode),
          increment, 
          increment.isConstant() ? DERIVED : STORE);
      initStatements.add(variable.initializer());
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
    Expression limit = MethodRef.INTS_CHECKED_CAST.invoke(
        exprCompiler.compile(rangeArgs.limit()).convert(long.class));
    // If the expression is non-trivial we should cache it in a local variable
    Variable variable = scope.createSynthetic(
        SyntheticVarName.forLoopLimit(forNode),
        limit, 
        increment.isConstant() ? DERIVED : STORE);
    initStatements.add(variable.initializer());
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
    final Statement loopBody = childrenAsStatement(nonEmptyNode);
    final Statement exitScope = scope.exitScope();

    // it important for this to be generated after exitScope is called (or before enterScope)
    final Statement emptyBlock = node.numChildren() == 2 
        ? childrenAsStatement((ForeachIfemptyNode) node.getChild(1)) 
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
    if (!node.getChildren().isEmpty()) {
      throw new UnsupportedOperationException(
          "The jbcsrc implementation does not support print directives (yet!): "
              + node.toSourceString());
    }
    SoyExpression printExpr = exprCompiler.compile(node.getExprUnion().getExpr());
    // TODO(lukes): we should specialize the print operator for our primitives to avoid this box 
    // operator.  would only work if there were no print directives
    Statement renderSoyValue = MethodRef.SOY_VALUE_RENDER
        .invokeVoid(printExpr.box(), appendableExpression);
    return Statement.concat(renderSoyValue, detachState.detachLimited(appendableExpression))
        .withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitRawTextNode(RawTextNode node) {
    Expression render = MethodRef.ADVISING_APPENDABLE_APPEND
        .invoke(appendableExpression, constant(node.getRawText()));
    
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
    Expression rename = MethodRef.RENDER_CONTEXT_RENAME_XID
        .invoke(contextExpression, constant(node.getText()));
    return MethodRef.ADVISING_APPENDABLE_APPEND.invoke(appendableExpression, rename)
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
    Expression renameSelector = MethodRef.RENDER_CONTEXT_RENAME_CSS_SELECTOR
        .invoke(contextExpression, constant(node.getSelectorText()));
    Statement selectorStatement = MethodRef.ADVISING_APPENDABLE_APPEND
        .invoke(appendableExpression, renameSelector)
        .toStatement();

    if (node.getComponentNameExpr() != null) {
      return concat(
          MethodRef.SOY_VALUE_RENDER.invokeVoid(
              exprCompiler.compile(node.getComponentNameExpr()).box(), 
              appendableExpression), 
         MethodRef.ADVISING_APPENDABLE_APPEND_CHAR
            .invoke(appendableExpression, constant('-'))
            .toStatement(), 
         selectorStatement);
    }
    return selectorStatement;
  }

  @Override protected Statement visitLogNode(LogNode node) {
    return new SoyNodeCompiler(
            detachState,
            variables,
            MethodRef.RUNTIME_LOGGER.invoke(),
            contextExpression, 
            exprCompiler).childrenAsStatement(node);
  }

  @Override protected Statement visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException(
        "The jbcsrc backend doesn't support: " + node.getKind() + " nodes yet.");
  }
}
