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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.template.soy.jbcsrc.PrintDirectives.applyStreamingEscapingDirectives;
import static com.google.template.soy.jbcsrc.PrintDirectives.applyStreamingPrintDirectives;
import static com.google.template.soy.jbcsrc.PrintDirectives.areAllPrintDirectivesStreamable;
import static com.google.template.soy.jbcsrc.TemplateVariableManager.SaveStrategy.STORE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STACK_FRAME_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.compareSoySwitchCaseEquals;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.newLabel;
import static java.util.function.Function.identity;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.NumberNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.UndefinedNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.ControlFlow.IfBlock;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.LazyClosureCompiler.LazyClosure;
import com.google.template.soy.jbcsrc.MsgCompiler.PlaceholderCompiler;
import com.google.template.soy.jbcsrc.TemplateVariableManager.AbstractVariable;
import com.google.template.soy.jbcsrc.TemplateVariableManager.Scope;
import com.google.template.soy.jbcsrc.TemplateVariableManager.Variable;
import com.google.template.soy.jbcsrc.internal.InnerMethods;
import com.google.template.soy.jbcsrc.restricted.Branch;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.ClassLoaderFallbackCallFactory;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.SwitchFactory;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.passes.IndirectParamsCalculator;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.AssignmentNode;
import com.google.template.soy.soytree.AutoImplNode;
import com.google.template.soy.soytree.BreakNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.ContinueNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.EvalNode;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.HtmlContext;
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
import com.google.template.soy.soytree.ReturnNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SoyTreeUtils.VisitDirective;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.WhileNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnknownType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
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
   * @param appendableVar An expression that returns the current AdvisingAppendable that we are
   *     rendering into
   * @param variables The variable set for generating locals and fields
   * @param parameterLookup The variable lookup table for reading locals.
   */
  static SoyNodeCompiler create(
      SoyNode context,
      TypeInfo typeInfo,
      TemplateAnalysis analysis,
      InnerMethods innerMethods,
      AppendableExpression appendableVar,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      FieldManager fields,
      BasicExpressionCompiler constantCompiler,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler,
      FileSetMetadata fileSetMetadata) {
    return create(
        context,
        typeInfo,
        analysis,
        innerMethods,
        appendableVar,
        variables,
        parameterLookup,
        fields,
        constantCompiler,
        javaSourceFunctionCompiler,
        fileSetMetadata,
        null);
  }

  private static SoyNodeCompiler create(
      SoyNode context,
      TypeInfo typeInfo,
      TemplateAnalysis analysis,
      InnerMethods innerMethods,
      AppendableExpression appendableVar,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      FieldManager fields,
      BasicExpressionCompiler constantCompiler,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler,
      FileSetMetadata fileSetMetadata,
      Function<SoyExpression, SoyExpression> returnMapper) {
    // We pass a lazy supplier of render context so that lazy closure compiler classes that don't
    // generate detach logic don't trigger capturing this value into a field.
    ExpressionCompiler expressionCompiler =
        ExpressionCompiler.create(
            context,
            analysis,
            parameterLookup,
            variables,
            javaSourceFunctionCompiler,
            fileSetMetadata);
    ExpressionToSoyValueProviderCompiler soyValueProviderCompiler =
        ExpressionToSoyValueProviderCompiler.create(analysis, expressionCompiler, parameterLookup);
    return new SoyNodeCompiler(
        typeInfo,
        analysis,
        innerMethods,
        null,
        variables,
        parameterLookup,
        fields,
        appendableVar,
        expressionCompiler,
        soyValueProviderCompiler,
        constantCompiler,
        javaSourceFunctionCompiler,
        fileSetMetadata,
        returnMapper);
  }

  static SoyNodeCompiler createForExtern(
      SoyNode context,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      BasicExpressionCompiler constantCompiler,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler,
      FileSetMetadata fileSetMetadata,
      Function<SoyExpression, SoyExpression> returnMapper) {
    return create(
        context,
        null,
        ExternCompiler.EXTERN_CONTEXT,
        null,
        null,
        variables,
        parameterLookup,
        null,
        constantCompiler,
        javaSourceFunctionCompiler,
        fileSetMetadata,
        checkNotNull(returnMapper));
  }

  @Nullable final TypeInfo typeInfo;
  final TemplateAnalysis analysis;
  @Nullable final InnerMethods innerMethods;
  @Nullable private DetachState detachState;
  final TemplateVariableManager variables;
  final TemplateParameterLookup parameterLookup;
  @Nullable final FieldManager fields;
  @Nullable final AppendableExpression appendableExpression;
  final ExpressionCompiler exprCompiler;
  final ExpressionToSoyValueProviderCompiler expressionToSoyValueProviderCompiler;
  final BasicExpressionCompiler constantCompiler;
  final JavaSourceFunctionCompiler javaSourceFunctionCompiler;
  final FileSetMetadata fileSetMetadata;
  @Nullable private final Function<SoyExpression, SoyExpression> returnMapper;
  private Scope currentScope;
  private final Deque<LoopContext> loopStack = new ArrayDeque<>();

  private SoyNodeCompiler(
      @Nullable TypeInfo typeInfo,
      TemplateAnalysis analysis,
      @Nullable InnerMethods innerMethods,
      @Nullable DetachState detachState,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      @Nullable FieldManager fields,
      @Nullable AppendableExpression appendableExpression,
      ExpressionCompiler exprCompiler,
      ExpressionToSoyValueProviderCompiler expressionToSoyValueProviderCompiler,
      BasicExpressionCompiler constantCompiler,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler,
      FileSetMetadata fileSetMetadata,
      Function<SoyExpression, SoyExpression> returnMapper) {
    this.typeInfo = typeInfo;
    this.analysis = checkNotNull(analysis);
    this.innerMethods = innerMethods;
    this.detachState = detachState;
    this.variables = checkNotNull(variables);
    this.parameterLookup = checkNotNull(parameterLookup);
    this.fields = fields;
    this.appendableExpression = appendableExpression;
    this.exprCompiler = checkNotNull(exprCompiler);
    this.expressionToSoyValueProviderCompiler = checkNotNull(expressionToSoyValueProviderCompiler);
    this.constantCompiler = checkNotNull(constantCompiler);
    this.javaSourceFunctionCompiler = checkNotNull(javaSourceFunctionCompiler);
    this.fileSetMetadata = checkNotNull(fileSetMetadata);
    this.returnMapper = returnMapper;
  }

  Statement compile(RenderUnitNode node, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {
    List<Statement> statements = new ArrayList<>();
    if (shouldCheckForSoftLimit(node)) {
      getDetachState().detachLimited(getAppendableExpression()).ifPresent(statements::add);
    }
    statements.add(trackRequiredCssPathStatements(node));
    statements.add(doCompile(node, prefix, suffix));
    statements.add(
        // needs to go at the beginning but can only be generated after the whole method body.
        0, getDetachState().generateReattachTable());
    return AppendableExpression.concat(statements);
  }

  Statement compile(AutoImplNode node) {
    return visit(node);
  }

  Statement compileWithoutDetaches(
      RenderUnitNode node, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {
    try (DetachState.NoNewDetaches noNewDetaches = getDetachState().expectNoNewDetaches()) {
      return doCompile(node, prefix, suffix);
    }
  }

  private AppendableExpression getAppendableExpression() {
    return Preconditions.checkNotNull(appendableExpression);
  }

  /**
   * Returns whether we are compiling a function ({autoimpl}) block, as opposed to a template block.
   */
  private boolean isFunctionBlock() {
    return appendableExpression == null;
  }

  DetachState getDetachState() {
    if (detachState == null) {
      detachState = new DetachState(variables, parameterLookup.getStackFrame());
    }
    return detachState;
  }

  @Nullable
  DetachState getNullableDetachState() {
    return detachState;
  }

  public Statement trackRequiredCssPathStatements(SoyFileNode fileNode) {
    if (!(fileNode.getAllRequiredCssPaths().isEmpty()
        && fileNode.getRequiredCssNamespaces().isEmpty())) {
      var cssPaths =
          fileNode.getAllRequiredCssPaths().stream()
              .map(css -> css.resolvedPath().orElseThrow())
              .collect(toImmutableList());
      var cssNamespaces = fileNode.getRequiredCssNamespaces();
      return parameterLookup.getRenderContext().trackRequiredCss(cssPaths, cssNamespaces);
    }
    return Statement.NULL_STATEMENT;
  }

  private Statement trackRequiredCssPathStatements(RenderUnitNode node) {
    SoyFileNode fileNode = node.getNearestAncestor(SoyFileNode.class);
    if (node instanceof TemplateNode
        && (((TemplateNode) node).getVisibility() == Visibility.PUBLIC
            || (node instanceof TemplateBasicNode
                && ((TemplateBasicNode) node).getModifiesExpr() != null))
        && !definitelyCallsPublicTemplateInSameFile((TemplateNode) node)) {
      return trackRequiredCssPathStatements(fileNode);
    }
    return Statement.NULL_STATEMENT;
  }

  /**
   * returns whether the node unconditionally calls another public template in the same file. When
   * that's the case for a TemplateNode, we don't need to track that template's required CSS paths,
   * as the same paths will already be reported by the inner template call.
   */
  private boolean definitelyCallsPublicTemplateInSameFile(TemplateNode node) {
    ImmutableSet<String> publicTemplateNames =
        node.getNearestAncestor(SoyFileNode.class).getTemplates().stream()
            .filter(t -> t.getVisibility() == Visibility.PUBLIC)
            .map(TemplateNode::getTemplateName)
            .collect(toImmutableSet());

    return ((ParentNode<? extends SoyNode>) node)
        .getChildren().stream()
            .anyMatch(
                child ->
                    child.getKind() == SoyNode.Kind.CALL_BASIC_NODE
                        && ((CallBasicNode) child).isStaticCall()
                        && publicTemplateNames.contains(((CallBasicNode) child).getCalleeName()));
  }

  private Statement doCompile(
      RenderUnitNode node, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {
    return AppendableExpression.concat(
        prefix.compile(exprCompiler, getAppendableExpression(), getDetachState()),
        visitChildrenInNewScope(node),
        suffix.compile(exprCompiler, getAppendableExpression(), getDetachState()));
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

  @Override
  protected Statement visitConstNode(ConstNode node) {
    // constant nodes are directly handled by compile()
    throw new AssertionError("should not be called");
  }

  @Override
  protected Statement visitAutoImplNode(AutoImplNode node) {
    return visitChildrenInNewScope(node);
  }

  @Override
  protected Statement visitReturnNode(ReturnNode node) {
    SoyExpression value = compileRootExpression(node.getExpr());
    return Statement.returnExpression(returnMapper.apply(value));
  }

  private Statement visitChildrenInNewScope(BlockNode node) {
    Scope prev = currentScope;
    currentScope = variables.enterScope();
    List<Statement> children = visitChildren(node);
    var leave = currentScope.exitScopeMarker();
    currentScope = prev;
    return AppendableExpression.concat(children).labelEnd(leave);
  }

  @Override
  protected Statement visitIfNode(IfNode node) {
    List<IfBlock> ifs = new ArrayList<>();
    Optional<Statement> elseBlock = Optional.empty();
    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        Label reattachPoint = newLabel();
        Branch cond = compileIfCondNode(icn, reattachPoint, node.getHtmlContext());
        Statement block = visitChildrenInNewScope(icn);
        // TODO: b/375681066 - tag the label here instead of on the expression since the compiler
        // occasionally prove the condition to be trivial and remove the branch expression.
        ifs.add(IfBlock.create(reattachPoint, cond, block));
      } else {
        IfElseNode ien = (IfElseNode) child;
        elseBlock = Optional.of(visitChildrenInNewScope(ien));
      }
    }
    return ControlFlow.ifElseChain(ifs, elseBlock);
  }

  /**
   * To preserve streaming in certain document contexts we generate optimized truthiness checks that
   * use a secondary `SoyValueProvider` generated by calling `coerceToBooleanProvider()` and then
   * coerce that to boolean. This provider has special logic for some subclasses of
   * `SoyValueProvider` that avoid fully evaluating some parameters.
   */
  private Branch compileIfCondNode(IfCondNode node, Label reattachPoint, HtmlContext htmlContext) {
    var expressionDetacher = getDetachState().createExpressionDetacher(reattachPoint);
    if (DetachState.ifCondNodeDetachableContext(htmlContext)
        && exprCompiler.requiresDetach(node.getExpr())) {
      var asSoyValueProvider =
          expressionToSoyValueProviderCompiler.compileToSoyValueProviderIfUsefulToPreserveStreaming(
              node.getExpr(), expressionDetacher);
      if (asSoyValueProvider.isPresent()) {
        var booleanProviderExpression =
            MethodRefs.SOY_VALUE_PROVIDER_COERCE_TO_BOOLEAN_PROVIDER.invoke(
                asSoyValueProvider.get());
        return SoyExpression.forSoyValue(
                BoolType.getInstance(),
                expressionDetacher.resolveSoyValueProvider(booleanProviderExpression))
            .compileToBranch();
      }
    }
    return exprCompiler.compileSubExpression(node.getExpr(), expressionDetacher).compileToBranch();
  }

  /** Returns an integer that is not in the set of sorted integers. */
  private static int getUnusedKey(NavigableSet<Integer> sortedKeys) {
    Integer min = sortedKeys.higher(Integer.MIN_VALUE);
    if (min != null) {
      return min - 1;
    }
    Integer max = sortedKeys.lower(Integer.MAX_VALUE);
    if (max != null) {
      return max + 1;
    }
    // unusual, but there must be some gap between the keys since there are twice as many integers
    // as valid array indexes, so by the pigeon hole principle, there must be an unused slot.
    int candidate = min + 1;
    for (var i : sortedKeys) {
      if (candidate < i) {
        break;
      }
      candidate = i + 1;
    }
    return candidate;
  }

  /**
   * Maps the SoyExpression to an `int` Expression that can be used to evaluate a switch for the
   * given keys.
   */
  private static Expression asSwitchableInt(
      SoyExpression switchExpr, NavigableSet<Integer> switchKeys) {
    int unusedKey = getUnusedKey(switchKeys);
    // We need to coerce the switchExpr to an int value. Use some runtime helpers to map non-int
    // values to some value that is out of range of this switch.
    // NOTE: we don't unbox here to avoid triggering null pointer exceptions.  All such cases imply
    // that there is a latent bug in the template but for backwards compatibility we avoid
    // triggering it.
    // TODO(b/295895863): leverage type information to unbox, this should be more efficient and
    // improve JS/Java compatibility. Right now there are several Soy templates that fail as a
    // result, in each case it is a clear bug in the template.
    if (switchExpr.resultType().equals(Type.LONG_TYPE)) {
      return MethodRefs.AS_SWITCHABLE_VALUE_LONG.invoke(switchExpr, constant(unusedKey));
    } else if (switchExpr.resultType().equals(Type.DOUBLE_TYPE)) {
      return MethodRefs.AS_SWITCHABLE_VALUE_DOUBLE.invoke(switchExpr, constant(unusedKey));
    } else {
      return MethodRefs.AS_SWITCHABLE_VALUE_SOY_VALUE.invoke(switchExpr.box(), constant(unusedKey));
    }
  }

  /**
   * Maps the SoyExpression to an `int` Expression that can be used to evaluate a switch for the
   * given keys.
   */
  private static Expression asSwitchableInt(SoyExpression switchExpr, Object[] switchKeys) {
    // We need to coerce the switchExpr to an int value. Use an invokedynamic bootstrap to manage a
    // hash of string to case
    SoyExpression stringKey =
        switchExpr.soyRuntimeType().assignableToNullableString()
            ? switchExpr.unboxAsStringOrJavaNull()
            : switchExpr.box();
    return new Expression(Type.INT_TYPE) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        stringKey.gen(adapter);
        adapter.visitInvokeDynamicInsn(
            "switchCase",
            (stringKey.isBoxed()
                    ? STRING_SWITCH_DESCRIPTOR_SOY_VALUE
                    : STRING_SWITCH_DESCRIPTOR_OBJECT)
                .getDescriptor(),
            STRING_SWITCH_FACTORY_HANDLE,
            switchKeys);
      }
    };
  }

  private static final Handle STRING_SWITCH_FACTORY_HANDLE =
      MethodRef.createPure(
              SwitchFactory.class,
              "bootstrapSwitch",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              Object[].class)
          .asHandle();
  private static final Type STRING_SWITCH_DESCRIPTOR_OBJECT =
      Type.getMethodType(Type.INT_TYPE, BytecodeUtils.OBJECT.type());
  private static final Type STRING_SWITCH_DESCRIPTOR_SOY_VALUE =
      Type.getMethodType(Type.INT_TYPE, BytecodeUtils.SOY_VALUE_TYPE);

  final class StatementAndStartLabel {
    final Statement statement;
    final Label startLabel;

    StatementAndStartLabel(SwitchCaseNode caseNode) {
      startLabel = newLabel();
      this.statement = visitChildrenInNewScope(caseNode).labelStart(startLabel);
    }
  }

  private static class LoopContext {
    private final Label continueToLabel;
    private final Label breakToLabel;

    LoopContext(Label continueToLabel, Label breakToLabel) {
      this.continueToLabel = continueToLabel;
      this.breakToLabel = breakToLabel;
    }

    /** Gets the label to jump to for a 'continue' statement within a loop. */
    Label getContinueToLabel() {
      return continueToLabel;
    }

    /** Gets the label to jump to for a 'break' statement within a loop. */
    Label getBreakToLabel() {
      return breakToLabel;
    }
  }

  /**
   * Special case switches against literal primitives to use java switch instructions.
   *
   * <p>This is both faster and smaller than the default cascading if-statement.
   *
   * <p>We support 2 distinct cases
   *
   * <ul>
   *   <li>All cases are expressible as java `int` constants. This is relatively common since many
   *       switches are dispatching on proto enums which meet this criteria. In this scenario we can
   *       directly target a java switch instruction.
   *   <li>All cases are soy primitive literals (boolean, int, float, string, null, proto enum). In
   *       this case we use the `SwitchFactory` to manage the mapping from value to case label. This
   *       saves use from generating complex conditional logic in side the cases.
   * </ul>
   *
   * Otherwise we bail out.
   */
  private Optional<Statement> tryCompileSwitchToSwitchInstruction(
      SoyExpression switchExpr, SwitchNode node) {
    // First make sure all cases are representable as constants bail out if this isn't true.
    // Key is one of Integer|Double|Long|String|ConstantDynamic where the ConstantDynamic values are
    // for bool|null|undefined
    Map<Object, SwitchCaseNode> cases = new LinkedHashMap<>();
    SwitchDefaultNode dfltNode = null;
    for (SoyNode child : node.getChildren()) {
      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode caseNode = (SwitchCaseNode) child;
        for (ExprRootNode caseExpr : caseNode.getExprList()) {
          var root = caseExpr.getRoot();
          if (root instanceof NumberNode) {
            NumberNode numberNode = (NumberNode) root;
            if (numberNode.isInteger()) {
              long intValue = numberNode.longValue();
              // If a case expression is used multiple times, only use the first occurrence
              if (intValue == (int) intValue) {
                cases.putIfAbsent((int) intValue, caseNode);
              } else {
                cases.putIfAbsent(intValue, caseNode);
              }
            } else {
              double floatValue = numberNode.doubleValue();
              if (floatValue == (int) floatValue) {
                cases.putIfAbsent((int) floatValue, caseNode);
              } else if (floatValue == (long) floatValue) {
                cases.putIfAbsent((long) floatValue, caseNode);
              } else {
                cases.putIfAbsent(floatValue, caseNode);
              }
            }
          } else if (root instanceof ProtoEnumValueNode) {
            cases.putIfAbsent(((ProtoEnumValueNode) root).getValueAsInt(), caseNode);
          } else if (root instanceof BooleanNode) {
            cases.putIfAbsent(
                constant(((BooleanNode) root).getValue()).constantBytecodeValue(), caseNode);
          } else if (root instanceof StringNode) {
            cases.putIfAbsent(((StringNode) root).getValue(), caseNode);
          } else if (root instanceof NullNode) {
            cases.putIfAbsent(BytecodeUtils.soyNull().constantBytecodeValue(), caseNode);
          } else if (root instanceof UndefinedNode) {
            cases.putIfAbsent(BytecodeUtils.soyUndefined().constantBytecodeValue(), caseNode);
          } else {
            return Optional.empty();
          }
        }
      } else {
        dfltNode = (SwitchDefaultNode) child;
      }
    }

    // If we get here we can generate a switch instruction
    // So generate code for each of the children.
    // Use this to ensure we generate each case exactly once
    Map<SwitchCaseNode, StatementAndStartLabel> caseToStatement = new LinkedHashMap<>();
    Statement defaultBlock = dfltNode == null ? null : visitChildrenInNewScope(dfltNode);
    TreeMap<Integer, StatementAndStartLabel> casesByKey = new TreeMap<>();
    // If all cases are an int we can directly generate a switch with the actual values
    if (cases.keySet().stream().allMatch(key -> key instanceof Integer)) {
      for (var entry : cases.entrySet()) {
        casesByKey.put(
            ((Integer) entry.getKey()).intValue(),
            caseToStatement.computeIfAbsent(entry.getValue(), StatementAndStartLabel::new));
      }
      // We need to coerce the switchExpr to an int value.
      return Optional.of(
          asNativeSwitch(
              asSwitchableInt(switchExpr, casesByKey.navigableKeySet()), casesByKey, defaultBlock));

    } else {
      // Otherwise we need more complex matching logic that we outsource to an invoke dynamic
      // bootstrap.  Create a fake key for each case and then rely on the bootstrap to figure it
      // out.
      // update the map with the pseudo keys, so that the loops below can find them

      var keys = cases.keySet().toArray();
      int i = 0;
      for (SwitchCaseNode caseNode : cases.values()) {
        casesByKey.put(i, caseToStatement.computeIfAbsent(caseNode, StatementAndStartLabel::new));
        i++;
      }
      return Optional.of(
          asNativeSwitch(asSwitchableInt(switchExpr, keys), casesByKey, defaultBlock));
    }
  }

  private static Statement asNativeSwitch(
      Expression switchExpr,
      TreeMap<Integer, StatementAndStartLabel> casesByKey,
      Statement defaultBlock) {
    int min = casesByKey.firstKey();
    int max = casesByKey.lastKey();
    int range = max - min + 1;
    // If more than 50% of the slots between min and max or full, use a tableswitch otherwise a
    // lookup switch
    boolean isDense = ((float) casesByKey.size() / range) >= 0.5f;
    List<Statement> cases = new ArrayList<>();
    boolean isTerminal = defaultBlock != null;
    // We need to dedupe since multiple keys may point at the same case
    for (var entry : new LinkedHashSet<>(casesByKey.values())) {
      cases.add(entry.statement);
      isTerminal = isTerminal && entry.statement.isTerminal();
    }
    Label end = newLabel();
    Label dflt = defaultBlock == null ? end : newLabel();
    if (defaultBlock != null) {
      isTerminal = isTerminal && defaultBlock.isTerminal();
      cases.add(defaultBlock.labelStart(dflt));
    }
    return new Statement(isTerminal ? Statement.Kind.TERMINAL : Statement.Kind.NON_TERMINAL) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        switchExpr.gen(adapter); // stack: I
        if (isDense) {
          // For dense table switches we need a label for everything in the range
          // for things in the range that don't map to a known case we just jump to dflt
          Label[] labels = new Label[range];
          Arrays.fill(labels, dflt);
          for (Map.Entry<Integer, SoyNodeCompiler.StatementAndStartLabel> entry :
              casesByKey.entrySet()) {
            Integer key = entry.getKey();
            int labelIndex = key - min;
            labels[labelIndex] = entry.getValue().startLabel;
          }
          adapter.visitTableSwitchInsn(
              /* min= */ min, /* max= */ max, /* dflt= */ dflt, /* labels...= */ labels);
        } else {
          // for lookup switches we need a label for each key
          adapter.visitLookupSwitchInsn(
              /* dflt= */ dflt,
              /* keys= */ casesByKey.keySet().stream().mapToInt(Integer::intValue).toArray(),
              /* labels= */ casesByKey.values().stream()
                  .map(s -> s.startLabel)
                  .toArray(Label[]::new));
        }

        for (int i = 0; i < cases.size(); i++) {
          var caseStatement = cases.get(i);
          caseStatement.gen(adapter);
          // If it is terminal (e.g. a return or throw) or it is the last case we don't need to
          // jump to the end of the switch.
          if (!caseStatement.isTerminal() && (i < cases.size() - 1)) {
            // we need to jump to the end of the switch if this isn't the last case and it is not
            // terminal.
            adapter.goTo(end); // jump from the last case past default
          }
        }
        adapter.mark(end);
      }
    };
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
    SoyExpression switchVar = compileRootExpression(node.getExpr());

    // if all switch cases are literals we can use a switch instruction.
    var maybeNativeSwitch = tryCompileSwitchToSwitchInstruction(switchVar, node);
    if (maybeNativeSwitch.isPresent()) {
      return maybeNativeSwitch.get();
    }
    // Otherwise the case expressions are complex and we need to compile them to full expressions
    // and use a cascading if.  Per
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/switch an
    // iterative execution of cases is the spec on switch so this is compliant with JavaScript.
    // Case expressions are only evaluated if no prior case has matched.
    Scope scope = variables.enterScope();
    Variable variable = scope.createSynthetic(SyntheticVarName.forSwitch(node), switchVar, STORE);
    Statement initializer = variable.initializer();
    switchVar = switchVar.withSource(variable.local());
    // Soy allows arbitrary expressions to appear in {case} statements within a {switch}.
    // Java/C, by contrast, only allow some constant expressions in cases.
    List<IfBlock> cases = new ArrayList<>();
    Optional<Statement> defaultBlock = Optional.empty();
    for (SoyNode child : children) {
      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode caseNode = (SwitchCaseNode) child;
        Label reattachPoint = null;

        List<Branch> comparisons = new ArrayList<>();
        for (ExprRootNode caseExpr : caseNode.getExprList()) {
          boolean isFirst = reattachPoint == null;
          if (isFirst) {
            reattachPoint = newLabel();
          }
          Expression compiledCase =
              compareSoySwitchCaseEquals(
                  switchVar,
                  exprCompiler.compileSubExpression(
                      caseExpr, getDetachState().createExpressionDetacher(reattachPoint)));

          if (isFirst) {
            compiledCase = compiledCase.labelStart(reattachPoint);
          }
          comparisons.add(Branch.ifTrue(compiledCase));
        }
        Statement block = visitChildrenInNewScope(caseNode);
        cases.add(IfBlock.create(Branch.or(comparisons), block));
      } else {
        SwitchDefaultNode defaultNode = (SwitchDefaultNode) child;
        defaultBlock = Optional.of(visitChildrenInNewScope(defaultNode));
      }
    }
    var exitScope = scope.exitScopeMarker();
    return Statement.concat(initializer, ControlFlow.ifElseChain(cases, defaultBlock))
        .labelEnd(exitScope);
  }

  @Override
  protected Statement visitForNode(ForNode node) {
    ForNonemptyNode nonEmptyNode = (ForNonemptyNode) node.getChild(0);
    Scope scope = variables.enterScope();

    Expression iteratorExpr = compileRootExpression(node.getExpr()).unboxAsIteratorUnchecked();
    Variable iteratorVar =
        scope.createSynthetic(
            SyntheticVarName.foreachLoopIterator(nonEmptyNode), iteratorExpr, STORE);
    Variable userIndexVar =
        nonEmptyNode.getIndexVar() == null
            ? null
            : scope.create(nonEmptyNode.getIndexVarName(), constant(0), STORE);
    Variable itemVar =
        scope.create(
            nonEmptyNode.getVarName(),
            iteratorVar
                .local()
                .invoke(MethodRefs.ITERATOR_NEXT)
                .checkedCast(SOY_VALUE_PROVIDER_TYPE),
            STORE);

    Label loopStart = newLabel();
    Label loopEnd = newLabel();

    LoopContext context = new LoopContext(loopStart, loopEnd);
    loopStack.push(context);

    Expression hasNext = MethodRefs.ITERATOR_HAS_NEXT.invoke(iteratorVar.local());
    Statement loopBody = visitChildrenInNewScope(nonEmptyNode);

    loopStack.pop();

    var exitScope = scope.exitScopeMarker();

    return new Statement(
        loopBody.isTerminal() ? Statement.Kind.TERMINAL : Statement.Kind.NON_TERMINAL) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        iteratorVar.initializer().gen(adapter); // Iterator it = ...;
        if (userIndexVar != null) {
          userIndexVar.initializer().gen(adapter); // int index = 0;
        }

        adapter.mark(loopStart);

        hasNext.gen(adapter);
        adapter.ifZCmp(Opcodes.IFEQ, loopEnd); // while (it.hasNext()) {
        itemVar.initializer().gen(adapter); // SoyValueProvider item = i.next();
        loopBody.gen(adapter);

        if (userIndexVar != null) {
          adapter.iinc(userIndexVar.local().index(), 1); // index++
        }
        adapter.goTo(loopStart);
        adapter.mark(exitScope);
        adapter.mark(loopEnd);
      }
    };
  }

  @Override
  protected Statement visitWhileNode(WhileNode node) {
    Scope scope = variables.enterScope();
    Expression boolExpr = compileRootExpression(node.getExpr()).coerceToBoolean();

    Label loopStart = newLabel();
    Label loopEnd = newLabel();

    LoopContext context = new LoopContext(loopStart, loopEnd);
    loopStack.push(context);

    Statement loopBody = AppendableExpression.concat(visitChildren(node));

    loopStack.pop();

    var exitScope = scope.exitScopeMarker();

    return new Statement(
        loopBody.isTerminal() ? Statement.Kind.TERMINAL : Statement.Kind.NON_TERMINAL) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(loopStart);

        boolExpr.gen(adapter);
        adapter.ifZCmp(Opcodes.IFEQ, loopEnd); // while (booleanProvider == true) {
        loopBody.gen(adapter);

        adapter.goTo(loopStart);
        adapter.mark(exitScope);
        adapter.mark(loopEnd);
      }
    };
  }

  @Override
  protected Statement visitBreakNode(BreakNode node) {
    if (loopStack.isEmpty()) {
      throw new IllegalStateException("{break /} found outside of a loop structure.");
    }
    Label breakToLabel = loopStack.peek().getBreakToLabel();
    return new Statement(Statement.Kind.TERMINAL) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.goTo(breakToLabel);
      }
    };
  }

  @Override
  protected Statement visitContinueNode(ContinueNode node) {
    if (loopStack.isEmpty()) {
      throw new IllegalStateException("{continue /} found outside of a loop structure.");
    }
    Label continueToLabel = loopStack.peek().getContinueToLabel();
    return new Statement(
        Statement.Kind.TERMINAL) { // 'continue' terminates the current block iteration
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.goTo(continueToLabel);
      }
    };
  }

  private SoyExpression compileRootExpression(ExprRootNode expr) {
    DetachState detach = getNullableDetachState();
    return detach != null
        ? exprCompiler.compileRootExpression(expr, detach)
        : exprCompiler.asBasicCompiler(null).compile(expr);
  }

  @Override
  protected Statement visitPrintNode(PrintNode node) {
    if (node.getExpr().getRoot() instanceof FunctionNode) {
      FunctionNode fn = (FunctionNode) node.getExpr().getRoot();
      if (fn.getSoyFunction() instanceof LoggingFunction) {
        return visitLoggingFunction(node, fn, (LoggingFunction) fn.getSoyFunction());
      }
      if (fn.getSoyFunction() == BuiltinFunction.FLUSH_PENDING_LOGGING_ATTRIBUTES) {
        return getAppendableExpression()
            .flushPendingLoggingAttributes(((BooleanNode) fn.getParams().get(0)).getValue())
            .toStatement();
      }
    }
    // First check our special case where all print directives are streamable and an expression that
    // evaluates to a SoyValueProvider.  This will allow us to render incrementally.
    if (areAllPrintDirectivesStreamable(node)) {
      Label reattachPoint = newLabel();
      ExprRootNode expr = node.getExpr();
      Optional<Expression> asSoyValueProvider =
          expressionToSoyValueProviderCompiler.compileToSoyValueProviderIfUsefulToPreserveStreaming(
              expr, getDetachState().createExpressionDetacher(reattachPoint));
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
    Label reattachPoint = newLabel();
    var detacher = getDetachState().createExpressionDetacher(reattachPoint);
    SoyExpression value = compilePrintNodeAsExpression(node, detacher);
    if (value.isBoxed()) {
      return value
          .invokeVoid(MethodRefs.SOY_VALUE_RENDER, getAppendableExpression())
          .labelStart(reattachPoint);
    }
    // handle trivial unboxed values like numbers and strings.
    Expression renderSoyValue = getAppendableExpression().appendString(value.coerceToString());
    if (detacher.hasDetaches()) {
      renderSoyValue = renderSoyValue.labelStart(reattachPoint);
    }

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
    Label reattachPoint = newLabel();
    var detacher = getDetachState().createExpressionDetacher(reattachPoint);
    SoyFunctionSignature functionSignature =
        loggingFunction.getClass().getAnnotation(SoyFunctionSignature.class);
    checkNotNull(
        functionSignature,
        "LoggingFunction %s must be annotated with @SoyFunctionSignature",
        loggingFunction.getClass().getName());
    Expression appendLoggingFunction =
        getAppendableExpression()
            .appendLoggingFunctionInvocation(
                functionSignature.name(),
                loggingFunction.getPlaceholder(),
                exprCompiler.asBasicCompiler(detacher).compileToList(fn.getParams()),
                printDirectives);
    if (detacher.hasDetaches()) {
      appendLoggingFunction = appendLoggingFunction.labelStart(reattachPoint);
    }
    return appendLoggingFunction.toStatement();
  }

  private SoyExpression compilePrintNodeAsExpression(PrintNode node, ExpressionDetacher detacher) {
    BasicExpressionCompiler basic = exprCompiler.asBasicCompiler(detacher);
    SoyExpression value = basic.compile(node.getExpr());
    // We may have print directives, that means we need to pass the render value through a bunch of
    // SoyJavaPrintDirective.apply methods.  This means lots and lots of boxing.
    for (PrintDirectiveNode printDirective : node.getChildren()) {
      var directive = printDirective.getPrintDirective();
      if (directive instanceof SoyJbcSrcPrintDirective) {
        value =
            ((SoyJbcSrcPrintDirective) directive)
                .applyForJbcSrc(
                    parameterLookup.getPluginContext(),
                    value,
                    basic.compileToList(printDirective.getArgs()));
      } else {
        value =
            SoyExpression.forSoyValue(
                UnknownType.getInstance(),
                MethodRefs.SOY_JAVA_PRINT_DIRECTIVE_APPLY_FOR_JAVA.invoke(
                    parameterLookup.getRenderContext().getPrintDirective(directive.getName()),
                    value.box(),
                    SoyExpression.asBoxedValueProviderList(
                        basic.compileToList(printDirective.getArgs()))));
      }
    }
    return value;
  }

  /**
   * Renders a {@link com.google.template.soy.data.SoyValueProvider} incrementally via {@link
   * com.google.template.soy.data.SoyValueProvider#renderAndResolve}
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
   *   <li>Invoke {@link com.google.template.soy.data.SoyValueProvider#renderAndResolve} with the
   *       standard detach logic.
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
    AppendableExpression appendable = getAppendableExpression();
    if (!directives.isEmpty()) {
      Label printDirectiveArgumentReattachPoint = newLabel();
      PrintDirectives.AppendableAndFlushBuffersDepth wrappedAppendable =
          applyStreamingPrintDirectives(
              directives,
              appendable,
              exprCompiler.asBasicCompiler(
                  getDetachState().createExpressionDetacher(printDirectiveArgumentReattachPoint)),
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
    // TODO(b/289390227): there are some cases where we statically know that this will not require a
    // detach despite our static analysis saying otherwise.  Remove references to the analyzer and
    // instead type test the expression.  If the ExpressionCompiler doesn't require a detach we
    // should get something statically typed as a SoyValue subtype.
    Expression callRenderAndResolve =
        soyValueProvider.invoke(MethodRefs.SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE, appendable);
    Statement doCall =
        requiresDetachLogic
            ? getDetachState().detachForRender(callRenderAndResolve)
            : getDetachState().assertFullyRenderered(callRenderAndResolve);
    return AppendableExpression.concat(initRenderee, initAppendable, doCall, clearAppendable)
        .labelEnd(renderScope.exitScopeMarker());
  }

  @Override
  protected Statement visitRawTextNode(RawTextNode node) {
    AppendableExpression render;
    if (node.getRawText().length() == 1) {
      render = getAppendableExpression().appendChar(constant(node.getRawText().charAt(0)));
    } else {
      render = getAppendableExpression().appendString(constant(node.getRawText()));
    }
    return render.toStatement();
  }

  @Override
  protected Statement visitDebuggerNode(DebuggerNode node) {
    // Call JbcSrcRuntime.debugger.  This logs a stack trace by default and is an obvious place to
    // put a breakpoint.
    return MethodRefs.RUNTIME_DEBUGGER.invokeVoid(
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
    ImmutableList<SoyJbcSrcPrintDirective> escapingDirectives =
        PrintDirectives.asJbcSrcDirectives(node.getEscapingDirectives());
    Statement renderDefault =
        getMsgCompiler()
            .compileMessage(idAndParts, msg, escapingDirectives, /* isFallback= */ false);
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
      IfBlock ifAvailableRenderDefault = IfBlock.create(Branch.ifTrue(cond), renderDefault);
      return ControlFlow.ifElseChain(
          ImmutableList.of(ifAvailableRenderDefault),
          Optional.of(
              getMsgCompiler()
                  .compileMessage(
                      fallbackIdAndParts, fallback, escapingDirectives, /* isFallback= */ true)));
    } else {
      return renderDefault;
    }
  }

  @Override
  protected Statement visitVeLogNode(VeLogNode node) {
    Label restartPoint = newLabel();
    var detacher = getDetachState().createExpressionDetacher(restartPoint);
    Statement enterStatement;
    if (node.getLogonlyExpression() != null) {
      Expression logonlyExpression =
          exprCompiler.compileSubExpression(node.getLogonlyExpression(), detacher).unboxAsBoolean();
      Expression veData =
          exprCompiler.compileSubExpression(node.getVeDataExpression(), detacher).toMaybeConstant();
      enterStatement =
          getAppendableExpression()
              .enterLoggableElement(
                  MethodRefs.CREATE_LOG_STATEMENT
                      .invoke(logonlyExpression, veData)
                      .toMaybeConstant())
              .toStatement();
    } else {
      enterStatement =
          getAppendableExpression()
              .enterLoggableElement(
                  MethodRefs.CREATE_LOG_STATEMENT_NOT_LOGONLY
                      .invoke(
                          exprCompiler
                              .compileSubExpression(node.getVeDataExpression(), detacher)
                              .toMaybeConstant())
                      .toMaybeConstant())
              .toStatement();
    }
    return AppendableExpression.concat(
            enterStatement,
            visitChildrenInNewScope(node),
            getAppendableExpression().exitLoggableElement().toStatement())
        .labelStart(restartPoint);
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
        Expression frame, AppendableExpression appendable, RenderContextExpression renderContext);
  }

  @FunctionalInterface
  private interface DirectCallGenerator {
    Expression call(
        Expression stackFrame,
        Expression params,
        AppendableExpression appendable,
        RenderContextExpression renderContext);
  }

  private interface DirectPositionalCallGenerator {
    Expression call(
        Expression stackFrame,
        List<Expression> params,
        AppendableExpression appendable,
        RenderContextExpression renderContext);

    TemplateType calleeType();
  }

  /**
   * Given this delcall: {@code {delcall foo.bar variant="$expr"}}
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
   * variant} and the fact that we have to invoke the {@link
   * com.google.template.soy.jbcsrc.shared.RenderContext} runtime to do the deltemplate lookup.
   */
  @Override
  protected Statement visitCallDelegateNode(CallDelegateNode node) {
    return renderCallNode(
        node,
        () -> {
          Label reattachPoint = newLabel();
          Expression variantExpr;
          if (node.getDelCalleeVariantExpr() == null) {
            variantExpr = constant("");
          } else {
            variantExpr =
                exprCompiler
                    .compileSubExpression(
                        node.getDelCalleeVariantExpr(),
                        getDetachState().createExpressionDetacher(reattachPoint))
                    .coerceToString();
          }
          return parameterLookup
              .getRenderContext()
              .getDeltemplate(node.getDelCalleeName(), variantExpr)
              .labelStart(reattachPoint);
        });
  }

  private static final Handle STATIC_CALL_HANDLE =
      MethodRef.createPure(
              ClassLoaderFallbackCallFactory.class,
              "bootstrapCall",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              String.class)
          .asHandle();

  private static final Handle STATIC_NODEBUILDER_HANDLE =
      MethodRef.createPure(
              ClassLoaderFallbackCallFactory.class,
              "bootstrapNodeBuilder",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              String.class,
              String.class)
          .asHandle();

  private static final String CREATE_NODE_BUILDER_SIGNATURE =
      "(Lcom/google/template/soy/jbcsrc/shared/StackFrame;"
          + "[Ljava/lang/Object;"
          + "Ljava/lang/Object;)"
          + "Lcom/google/template/soy/data/NodeBuilder;";

  private static final Handle STATIC_TEMPLATE_HANDLE =
      MethodRef.createPure(
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
      CompiledTemplateMetadata metadata =
          CompiledTemplateMetadata.create(node, checkNotNull(fileSetMetadata));
      // For private calls we can directly dispatch, but in order to support test stubbing we need
      // to dispatch all calls to public templates through our invokedyanmic infrastructure
      boolean isPrivateCall = CompiledTemplateMetadata.isPrivateCall(node);

      return renderCallNode(
          node,
          new CallGenerator() {
            @Override
            public Expression asCompiledTemplate() {
              if (isPrivateCall) {
                return metadata.templateMethod().invoke();
              }
              Expression renderContext = parameterLookup.getRenderContext();
              return new Expression(
                  BytecodeUtils.COMPILED_TEMPLATE_TYPE, Feature.NON_JAVA_NULLABLE.asFeatures()) {
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
                var positionalRenderMethod = metadata.positionalRenderMethod().get();
                return Optional.of(
                    new DirectPositionalCallGenerator() {
                      @Override
                      public TemplateType calleeType() {
                        return metadata.templateType();
                      }

                      @Override
                      public Expression call(
                          Expression stackFrame,
                          List<Expression> params,
                          AppendableExpression appendable,
                          RenderContextExpression renderContext) {
                        if (node.isLazy()) {
                          Expression nbParams =
                              BytecodeUtils.asArray(
                                  Type.getType(Object[].class), ImmutableList.copyOf(params));
                          Expression nodeBuilder =
                              new Expression(BytecodeUtils.NODE_BUILDER_TYPE) {
                                @Override
                                protected void doGen(CodeBuilder adapter) {
                                  stackFrame.gen(adapter);
                                  nbParams.gen(adapter);
                                  renderContext.gen(adapter);
                                  adapter.visitInvokeDynamicInsn(
                                      "bootstrapNodeBuilder",
                                      CREATE_NODE_BUILDER_SIGNATURE,
                                      STATIC_NODEBUILDER_HANDLE,
                                      node.getCalleeName(),
                                      positionalRenderMethod.method().getDescriptor());
                                }
                              };
                          return appendableExpression.appendNodeBuilder(nodeBuilder);
                        }
                        if (isPrivateCall) {
                          return positionalRenderMethod.invoke(
                              ImmutableList.<Expression>builder()
                                  .add(stackFrame)
                                  .addAll(params)
                                  .add(appendable)
                                  .add(renderContext)
                                  .build());
                        }
                        return new Expression(STACK_FRAME_TYPE) {
                          @Override
                          protected void doGen(CodeBuilder adapter) {
                            stackFrame.gen(adapter);
                            params.forEach(p -> p.gen(adapter));
                            appendable.gen(adapter);
                            renderContext.gen(adapter);
                            adapter.visitInvokeDynamicInsn(
                                "call",
                                positionalRenderMethod.method().getDescriptor(),
                                STATIC_CALL_HANDLE,
                                node.getCalleeName());
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
                  (stackFrame, params, appendable, renderContext) -> {
                    if (node.isLazy()) {
                      Expression nbParams =
                          BytecodeUtils.asArray(
                              Type.getType(Object[].class), ImmutableList.of(params));
                      Expression nodeBuilder =
                          new Expression(BytecodeUtils.NODE_BUILDER_TYPE) {
                            @Override
                            protected void doGen(CodeBuilder adapter) {
                              stackFrame.gen(adapter);
                              nbParams.gen(adapter);
                              renderContext.gen(adapter);
                              adapter.visitInvokeDynamicInsn(
                                  "bootstrapNodeBuilder",
                                  CREATE_NODE_BUILDER_SIGNATURE,
                                  STATIC_NODEBUILDER_HANDLE,
                                  node.getCalleeName(),
                                  metadata.renderMethod().method().getDescriptor());
                            }
                          };
                      return appendableExpression.appendNodeBuilder(nodeBuilder);
                    }
                    if (isPrivateCall) {
                      return metadata
                          .renderMethod()
                          .invoke(stackFrame, params, appendable, renderContext);
                    }
                    return new Expression(STACK_FRAME_TYPE) {
                      @Override
                      protected void doGen(CodeBuilder adapter) {
                        stackFrame.gen(adapter);
                        params.gen(adapter);
                        appendable.gen(adapter);
                        renderContext.gen(adapter);
                        adapter.visitInvokeDynamicInsn(
                            "call",
                            metadata.renderMethod().method().getDescriptor(),
                            STATIC_CALL_HANDLE,
                            node.getCalleeName());
                      }
                    };
                  });
            }
          });
    } else {
      return renderCallNode(
          node,
          () ->
              MethodRefs.GET_COMPILED_TEMPLATE_FROM_VALUE.invoke(
                  exprCompiler
                      .compileRootExpression(node.getCalleeExpr(), getDetachState())
                      .checkedCast(BytecodeUtils.TEMPLATE_VALUE_TYPE)));
    }
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
   *       the {@code $currentAppendable} field for the same reasons as above.
   *   <li>Invoke {@link com.google.template.soy.jbcsrc.shared.CompiledTemplate#render} with the
   *       standard detach logic.
   *   <li>Clear the two fields once rendering is complete.
   * </ul>
   *
   * @param node The call node
   * @return A statement rendering the template.
   */
  private Statement renderCallNode(CallNode node, CallGenerator callGenerator) {
    Statement initAppendable = Statement.NULL_STATEMENT;
    Statement flushAppendable = Statement.NULL_STATEMENT;
    AppendableExpression appendable = getAppendableExpression();

    BoundCallGenerator boundCall;
    Statement initParams;

    TemplateVariableManager.Scope renderScope = variables.enterScope();
    Statement initCallee = Statement.NULL_STATEMENT;
    boolean allPrintDirectivesStreamable = areAllPrintDirectivesStreamable(node);
    boolean isLazy = (node instanceof CallBasicNode) && ((CallBasicNode) node).isLazy();
    if (!allPrintDirectivesStreamable || node.isErrorFallbackSkip()) {
      // in this case we need to wrap a CompiledTemplate to buffer to catch exceptions or to
      // apply non-streaming escaping directives.
      ExpressionAndInitializer expressionAndInitializer =
          compileParamStoreParams(node, renderScope);
      initParams = expressionAndInitializer.initializer();
      Expression calleeExpression =
          MethodRefs.BUFFER_TEMPLATE.invoke(
              callGenerator.asCompiledTemplate(),
              BytecodeUtils.constant(node.isErrorFallbackSkip()),
              !allPrintDirectivesStreamable
                  ? MethodRefs.ESCAPING_BUFFERED_RENDER_DONE_FN.invoke(
                      getEscapingDirectivesList(node))
                  // In this case the appendable is wrapped below.
                  : FieldRef.REPLAYING_BUFFERED_RENDER_DONE_FN.accessor());
      TemplateVariableManager.Variable calleeVariable =
          renderScope.createSynthetic(
              SyntheticVarName.renderee(),
              calleeExpression,
              TemplateVariableManager.SaveStrategy.STORE);
      initCallee = calleeVariable.initializer();
      boundCall =
          (frame, output, context) -> {
            if (isLazy) {
              Expression nodeBuilder =
                  MethodRefs.CREATE_NODE_BUILDER.invoke(
                      calleeVariable.accessor(),
                      frame,
                      expressionAndInitializer.expression(),
                      context);
              return output.appendNodeBuilder(nodeBuilder);
            } else {
              return calleeVariable
                  .accessor()
                  .invoke(
                      MethodRefs.COMPILED_TEMPLATE_RENDER,
                      frame,
                      expressionAndInitializer.expression(),
                      output,
                      context);
            }
          };
    } else {
      Optional<DirectPositionalCallGenerator> asDirectPositionalCall =
          callGenerator.asDirectPositionalCall();
      if (asDirectPositionalCall.isPresent()) {
        var positional =
            compilePositionalParams(node, renderScope, asDirectPositionalCall.get().calleeType());
        initParams = positional.initializer();
        boundCall =
            (frame, output, context) ->
                asDirectPositionalCall
                    .get()
                    .call(
                        frame,
                        positional.expressions(),
                        maybeSetKindAndDirectionalityForCall(output, node),
                        context);
      } else {
        Optional<DirectCallGenerator> asDirectCall = callGenerator.asDirectCall();
        ExpressionAndInitializer expressionAndInitializer =
            compileParamStoreParams(node, renderScope);
        initParams = expressionAndInitializer.initializer();
        if (asDirectCall.isPresent()) {
          boundCall =
              (frame, output, context) ->
                  asDirectCall
                      .get()
                      .call(
                          frame,
                          expressionAndInitializer.expression(),
                          maybeSetKindAndDirectionalityForCall(output, node),
                          context);
        } else {
          TemplateVariableManager.Variable calleeVariable =
              renderScope.createSynthetic(
                  SyntheticVarName.renderee(),
                  callGenerator.asCompiledTemplate(),
                  TemplateVariableManager.SaveStrategy.STORE);
          initCallee = calleeVariable.initializer();
          boundCall =
              (frame, output, context) -> {
                if (isLazy) {
                  Expression nodeBuilder =
                      MethodRefs.CREATE_NODE_BUILDER.invoke(
                          calleeVariable.accessor(),
                          frame,
                          expressionAndInitializer.expression(),
                          context);
                  return output.appendNodeBuilder(nodeBuilder);
                } else {
                  return calleeVariable
                      .accessor()
                      .invoke(
                          MethodRefs.COMPILED_TEMPLATE_RENDER,
                          frame,
                          expressionAndInitializer.expression(),
                          output,
                          context);
                }
              };
        }
      }
    }

    if (!node.getEscapingDirectives().isEmpty() && allPrintDirectivesStreamable) {
      PrintDirectives.AppendableAndFlushBuffersDepth wrappedAppendable =
          applyStreamingEscapingDirectives(
              node.getEscapingDirectives(), appendable, parameterLookup.getPluginContext());
      TemplateVariableManager.Variable variable =
          renderScope.createSynthetic(
              SyntheticVarName.appendable(),
              wrappedAppendable.appendable(),
              TemplateVariableManager.SaveStrategy.STORE);
      initAppendable = variable.initializer();
      appendable = AppendableExpression.forExpression(variable.accessor());
      if (wrappedAppendable.flushBuffersDepth() >= 0) {
        flushAppendable = appendable.flushBuffers(wrappedAppendable.flushBuffersDepth());
      }
    }

    Expression callRender =
        boundCall
            .call(parameterLookup.getStackFrame(), appendable, parameterLookup.getRenderContext())
            // make sure to tag this expression with the source location to ensure stack traces are
            // accurate.
            .withSourceLocation(node.getSourceLocation());
    Statement callCallee = getDetachState().detachForRender(callRender);
    // We need to init the appendable after the parmas because initializing the params may require
    // rendering params into temporary buffers which may themselves use the currentAppendable local.
    return AppendableExpression.concat(
            initParams, initCallee, initAppendable, callCallee, flushAppendable)
        .labelEnd(renderScope.exitScopeMarker());
  }

  /**
   * Calls `setKindAndDirectionality` on the appendable iff it is not identical to the current
   * appendable for this renderUnit.
   */
  private AppendableExpression maybeSetKindAndDirectionalityForCall(
      AppendableExpression appendable, CallNode node) {
    if (!appendable.equals(this.getAppendableExpression())) {
      var kind = ((CallBasicNode) node).getStaticType().getContentKind().getSanitizedContentKind();
      return appendable.setSanitizedContentKindAndDirectionality(kind);
    }
    return appendable;
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

  private ExpressionAndInitializer compileParamStoreParams(
      CallNode node, TemplateVariableManager.Scope scope) {
    // params will only be 'cheap' if they are something trivial like the empty constant
    // or data="all", in those cases we don't need to save/restore anything.
    Label reattachPoint = newLabel();
    Expression record = getParamStoreExpression(node, compileExplicitParams(node, reattachPoint));
    Statement initialize = Statement.NULL_STATEMENT;
    if (!record.isCheap()) {
      TemplateVariableManager.Variable paramsVariable =
          scope.createSynthetic(
              SyntheticVarName.params(), record, TemplateVariableManager.SaveStrategy.STORE);
      record = paramsVariable.accessor();
      initialize = paramsVariable.initializer();
    }
    return ExpressionAndInitializer.create(record, initialize.labelStart(reattachPoint));
  }

  private ListOfExpressionsAndInitializer compilePositionalParams(
      CallNode node, TemplateVariableManager.Scope scope, TemplateType calleeType) {
    List<Statement> initStatements = new ArrayList<>();
    // For positional calls we support looking up non-explicitly passed parameters from the data=
    // expression.
    Function<TemplateType.Parameter, Expression> paramFallback;
    if (node.isPassingData()) {
      if (node.isPassingAllData()) {
        ImmutableMap<String, TemplateParam> callerParams =
            node.getNearestAncestor(TemplateNode.class).getParams().stream()
                .collect(toImmutableMap(TemplateParam::name, identity()));
        paramFallback =
            calleeParam -> {
              // If it matches a param on our signature, just access it directly. This supports the
              // case where the callee template has a positional signature.
              TemplateParam param = callerParams.get(calleeParam.getName());
              if (param != null) {
                return parameterLookup.getParam(param).asCheap();
              }
              // For indirect parameters we must have a params record, unless it is an implicit
              // parameter.
              if (calleeParam.isImplicit()) {
                return FieldRef.UNDEFINED_DATA.accessor();
              }
              // Otherwise, we must have a params record just unpack the parameter from it.
              return parameterLookup
                  .getParamsRecord()
                  .orElseThrow(
                      () -> new IllegalStateException("no local param found for " + calleeParam))
                  .invoke(
                      MethodRefs.PARAM_STORE_GET_PARAMETER,
                      BytecodeUtils.constantRecordProperty(calleeParam.getName()))
                  .asCheap();
            };
      } else {
        Label reattachPoint = newLabel();
        Expression data = getDataRecordExpression(node, reattachPoint);
        TemplateVariableManager.Variable variable =
            scope.createSynthetic(
                SyntheticVarName.dataExpr(), data, TemplateVariableManager.SaveStrategy.STORE);

        var value = variable.accessor();
        initStatements.add(variable.initializer().labelStart(reattachPoint));
        paramFallback =
            calleeParam ->
                calleeParam.isImplicit()
                    ? FieldRef.UNDEFINED_DATA.accessor()
                    : value
                        .invoke(
                            MethodRefs.PARAM_STORE_GET_PARAMETER,
                            BytecodeUtils.constantRecordProperty(calleeParam.getName()))
                        .asCheap();
      }
    } else {
      paramFallback = name -> FieldRef.UNDEFINED_DATA.accessor();
    }

    ImmutableList.Builder<Expression> builder = ImmutableList.builder();
    Label reattachPoint = newLabel();
    Map<String, Supplier<Expression>> explicit =
        new HashMap<>(compileExplicitParams(node, reattachPoint));
    ImmutableMap<String, CallParamNode> keyToParam = null;
    for (TemplateType.Parameter param : calleeType.getActualParameters()) {
      Supplier<Expression> supplier = explicit.remove(param.getName());
      Expression value = supplier == null ? paramFallback.apply(param) : supplier.get();
      if (!value.isCheap()) {
        if (keyToParam == null) {
          keyToParam =
              node.getChildren().stream()
                  .collect(toImmutableMap(n -> n.getKey().identifier(), child -> child));
        }
        TemplateVariableManager.Variable variable =
            scope.createSynthetic(
                SyntheticVarName.forParam(keyToParam.get(param.getName())),
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
    return ListOfExpressionsAndInitializer.create(
        builder.build(), Statement.concat(initStatements).labelStart(reattachPoint));
  }

  private ImmutableMap<String, Supplier<Expression>> compileExplicitParams(
      CallNode node, Label reattachPoint) {
    ImmutableMap.Builder<String, Supplier<Expression>> builder = ImmutableMap.builder();

    if (node instanceof CallBasicNode && ((CallBasicNode) node).getVariantExpr() != null) {
      CallBasicNode callBasicNode = (CallBasicNode) node;
      builder.put(
          Names.VARIANT_VAR_NAME,
          Suppliers.ofInstance(
              exprCompiler
                  .compileSubExpression(
                      callBasicNode.getVariantExpr(),
                      getDetachState().createExpressionDetacher(reattachPoint))
                  .box()));
    }
    for (CallParamNode child : node.getChildren()) {
      String paramKey = child.getKey().identifier();
      Supplier<Expression> valueExpr;
      if (child instanceof CallParamContentNode) {
        valueExpr =
            () ->
                new LazyClosureCompiler(this)
                    .compileLazyContent((CallParamContentNode) child, paramKey)
                    .soyValueProvider();
      } else {
        valueExpr =
            () ->
                new LazyClosureCompiler(this)
                    .compileLazyExpression(child, paramKey, ((CallParamValueNode) child).getExpr())
                    .soyValueProvider();
      }
      builder.put(child.getKey().identifier(), valueExpr);
    }
    return builder.buildOrThrow();
  }

  /**
   * Returns an expression that creates a new {@link
   * com.google.template.soy.data.internal.ParamStore} suitable for holding all the parameters.
   */
  private Expression getParamStoreExpression(
      CallNode node, Map<String, Supplier<Expression>> params) {
    Map<String, Expression> paramsMap = new LinkedHashMap<>();
    params.forEach((k, v) -> paramsMap.put(k, v.get()));
    Optional<Expression> baseRecord;
    ;
    Label reattachDataLabel = newLabel();
    if (node.isPassingAllData()) {
      maybeAddCallerParametersForDataAllCall(node, paramsMap);
      baseRecord = parameterLookup.getParamsRecord();
    } else {
      baseRecord =
          node.isPassingData()
              ? Optional.of(getDataRecordExpression(node, reattachDataLabel))
              : Optional.empty();
    }

    return BytecodeUtils.newParamStore(baseRecord, paramsMap).labelStart(reattachDataLabel);
  }

  /**
   * Augment the set of explicit parameters with values from the local template signature.
   *
   * <p>We need to do this in 2 cases:
   *
   * <ul>
   *   <li>The caller has a default value for the parameter. In this case we need to pass the
   *       default when otherwise unset.
   *   <li>The caller is passing data="all" but has a positional signature, now we need to copy
   *       local parameters to the new record.
   * </ul>
   *
   * <p>Finally, we should avoid doing this for 'all' caller parameters and instead only do it for
   * those parameters that satisfy a callee parameter.
   */
  private void maybeAddCallerParametersForDataAllCall(
      CallNode node, Map<String, Expression> paramsMap) {
    Predicate<String> isCalleeParameter = s -> true;

    if (node instanceof CallBasicNode) {
      CallBasicNode callBasicNode = (CallBasicNode) node;
      if (callBasicNode.isStaticCall()) {
        var indirectParams =
            new IndirectParamsCalculator(checkNotNull(fileSetMetadata))
                .calculateIndirectParams(callBasicNode.getStaticType());
        if (!indirectParams.mayHaveExternalParams()) {
          var directParams =
              callBasicNode.getStaticType().getActualParameters().stream()
                  .map(p -> p.getName())
                  .collect(toImmutableSet());
          isCalleeParameter =
              param ->
                  directParams.contains(param) || indirectParams.indirectParams.containsKey(param);
        }
      }
    }
    for (TemplateParam param : node.getNearestAncestor(TemplateNode.class).getParams()) {
      if (!paramsMap.containsKey(param.name()) && isCalleeParameter.test(param.name())) {
        if (param.hasDefault() || parameterLookup.getParamsRecord().isEmpty()) {
          paramsMap.put(param.name(), parameterLookup.getParam(param));
        }
      }
    }
  }

  private Expression getDataRecordExpression(CallNode node, Label reattachPoint) {
    return MethodRefs.PARAM_STORE_FROM_RECORD.invoke(
        exprCompiler
            .compileSubExpression(
                node.getDataExpr(), getDetachState().createExpressionDetacher(reattachPoint))
            .box()
            .toMaybeConstant());
  }

  @Override
  protected Statement visitLogNode(LogNode node) {
    return compilerWithNewAppendable(AppendableExpression.logger()).visitChildrenInNewScope(node);
  }

  @Override
  protected Statement visitLetValueNode(LetValueNode node) {
    if (isFunctionBlock()) {
      LocalVar ref = node.getVar();
      return currentScope
          .create(
              ref.name(),
              compileRootExpression(node.getExpr()),
              TemplateVariableManager.SaveStrategy.STORE)
          .initializer();
    } else {
      return storeClosure(
          new LazyClosureCompiler(this)
              .compileLazyExpression(node, node.getVarName(), node.getExpr()));
    }
  }

  @Override
  protected Statement visitLetContentNode(LetContentNode node) {
    return storeClosure(new LazyClosureCompiler(this).compileLazyContent(node, node.getVarName()));
  }

  @Override
  protected Statement visitAssignmentNode(AssignmentNode node) {
    VarRefNode ref = (VarRefNode) node.getLhs().getRoot();
    String varName = ref.getDefnDecl().name();
    AbstractVariable letOrParam = currentScope.get(varName);
    SoyExpression newValue = exprCompiler.compileWithNoDetaches(node.getRhs()).get();

    // ASM has no common type for object v. primitive representations. So we need to coerce the
    // new value to fix within the bounds of the old value. This is mostly boxed v. unboxed and
    // numeric conversions among primitives.
    newValue = newValue.coerceTo(letOrParam.accessor().resultType());

    if (letOrParam instanceof Variable) {
      // This is assignment on a let.
      return ((Variable) letOrParam).local().store(newValue);
    } else {
      // This is assignment on a param. Param is from parent scope.
      return currentScope
          .create(varName, newValue, TemplateVariableManager.SaveStrategy.STORE)
          .initializer();
    }
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
    return AppendableExpression.concat(visitChildren(node));
  }

  @Override
  protected Statement visitEvalNode(EvalNode node) {
    Label reattachPoint = newLabel();
    ExpressionDetacher detacher = getDetachState().createExpressionDetacher(reattachPoint);
    BasicExpressionCompiler basic = exprCompiler.asBasicCompiler(detacher);
    SoyExpression value = basic.compile(node.getExpr());
    return value.labelStart(reattachPoint).toStatement();
  }

  @Override
  protected Statement visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException(
        "The jbcsrc backend doesn't support: " + node.getKind() + " nodes yet.");
  }

  private MsgCompiler getMsgCompiler() {
    return new MsgCompiler(
        getDetachState(),
        parameterLookup,
        variables,
        getAppendableExpression(),
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
                    /* id= */ -1,
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
        typeInfo,
        analysis,
        innerMethods,
        detachState,
        variables,
        parameterLookup,
        fields,
        appendable,
        exprCompiler,
        expressionToSoyValueProviderCompiler,
        constantCompiler,
        javaSourceFunctionCompiler,
        fileSetMetadata,
        returnMapper);
  }

  /** Returns a {@link SoyNodeCompiler} for compiling the new child node in a new context. */
  SoyNodeCompiler compilerForChildNode(
      SoyNode node,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      AppendableExpression appendable) {
    return create(
        node,
        typeInfo,
        analysis,
        innerMethods,
        appendable,
        variables,
        parameterLookup,
        fields,
        constantCompiler,
        javaSourceFunctionCompiler,
        fileSetMetadata);
  }
}
