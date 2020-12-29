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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.jbcsrc.SyntheticVarName.foreachLoopIndex;
import static com.google.template.soy.jbcsrc.SyntheticVarName.foreachLoopLength;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.LIST_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.NULL_POINTER_EXCEPTION_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.compare;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.firstNonNull;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.logicalNot;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.ternary;

import com.google.common.collect.Iterables;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListComprehensionNode.ComprehensionVarDefn;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.jbcsrc.ExpressionDetacher.BasicDetacher;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.ConstructorRef;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.shared.ClassLoaderFallbackCallFactory;
import com.google.template.soy.jbcsrc.shared.LegacyFunctionAdapter;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
import com.google.template.soy.plugin.internal.JavaPluginExecContext;
import com.google.template.soy.plugin.java.internal.PluginAnalyzer;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Compiles an {@link ExprNode} to a {@link SoyExpression}.
 *
 * <p>A note on how we use soy types. We generally try to limit the places where we read type
 * information from the AST. This is because it tends to be not very accurate. Specifically, the
 * places where we rely on type information from the AST are:
 *
 * <ul>
 *   <li>{@link VarRefNode}
 *   <li>{@link PrimitiveNode}
 *   <li>{@link DataAccessNode}
 * </ul>
 *
 * <p>This is because these are the points that are most likely to be in direct control of the user.
 * All other type information is derived directly from operations on SoyExpression objects.
 */
final class ExpressionCompiler {

  static final class BasicExpressionCompiler {
    private final CompilerVisitor compilerVisitor;

    private BasicExpressionCompiler(
        TemplateAnalysis analysis,
        TemplateParameterLookup parameters,
        LocalVariableManager varManager,
        FieldManager fields,
        ErrorReporter reporter,
        SoyTypeRegistry registry,
        CompiledTemplateRegistry compiledTemplateRegistry) {
      this.compilerVisitor =
          new CompilerVisitor(
              analysis,
              parameters,
              varManager,
              fields,
              BasicDetacher.INSTANCE,
              reporter,
              registry,
              compiledTemplateRegistry);
    }

    private BasicExpressionCompiler(CompilerVisitor visitor) {
      this.compilerVisitor = visitor;
    }

    /** Compile an expression. */
    SoyExpression compile(ExprNode expr) {
      return compilerVisitor.exec(expr);
    }

    /**
     * Returns an expression that evaluates to a {@code List<SoyValue>} containing all the children.
     */
    List<SoyExpression> compileToList(List<? extends ExprNode> children) {
      List<SoyExpression> soyExprs = new ArrayList<>(children.size());
      for (ExprNode expr : children) {
        soyExprs.add(compile(expr));
      }
      return soyExprs;
    }
  }

  /**
   * Create an expression compiler that can implement complex detaching logic with the given {@link
   * ExpressionDetacher.Factory}
   */
  static ExpressionCompiler create(
      TemplateAnalysis analysis,
      TemplateParameterLookup parameters,
      LocalVariableManager varManager,
      FieldManager fields,
      ErrorReporter reporter,
      SoyTypeRegistry registry,
      CompiledTemplateRegistry compiledTemplateRegistry) {
    return new ExpressionCompiler(
        analysis,
        checkNotNull(parameters),
        varManager,
        fields,
        reporter,
        registry,
        compiledTemplateRegistry);
  }

  static BasicExpressionCompiler createConstantCompiler(
      TemplateAnalysis analysis,
      LocalVariableManager varManager,
      FieldManager fields,
      ErrorReporter reporter,
      SoyTypeRegistry registry,
      CompiledTemplateRegistry compiledTemplateRegistry) {
    return new BasicExpressionCompiler(
        new CompilerVisitor(
            analysis,
            new AbstractTemplateParameterLookup() {
              UnsupportedOperationException unsupported() {
                return new UnsupportedOperationException(
                    "This method isn't supported in constant context");
              }

              @Override
              FieldRef getParamField(TemplateParam param) {
                throw unsupported();
              }

              @Override
              FieldRef getParamsRecordField() {
                throw unsupported();
              }

              @Override
              FieldRef getIjRecordField() {
                throw unsupported();
              }

              @Override
              Expression getCompiledTemplate() {
                throw unsupported();
              }

              @Override
              public Expression getLocal(AbstractLocalVarDefn<?> local) {
                return varManager.getVariable(local.name());
              }

              @Override
              public Expression getLocal(SyntheticVarName varName) {
                throw unsupported();
              }

              @Override
              public RenderContextExpression getRenderContext() {
                throw unsupported();
              }
            },
            varManager,
            fields,
            ExpressionDetacher.NullDetatcher.INSTANCE,
            reporter,
            registry,
            compiledTemplateRegistry));
  }

  /**
   * Create a basic compiler with trivial detaching logic.
   *
   * <p>All generated detach points are implemented as {@code return} statements and the returned
   * value is boxed, so it is only valid for use by the {@link LazyClosureCompiler}.
   */
  static BasicExpressionCompiler createBasicCompiler(
      TemplateAnalysis analysis,
      TemplateParameterLookup parameters,
      LocalVariableManager varManager,
      FieldManager fields,
      ErrorReporter reporter,
      SoyTypeRegistry registry,
      CompiledTemplateRegistry compiledTemplateRegistry) {
    return new BasicExpressionCompiler(
        analysis, parameters, varManager, fields, reporter, registry, compiledTemplateRegistry);
  }

  /**
   * Returns {@code true} if the value can be compiled to a constant expression in a static
   * initializer.
   */
  static boolean canCompileToConstant(ExprRootNode expr) {
    return CanCompileToConstantVisitor.INSTANCE.exec(expr);
  }

  private final TemplateAnalysis analysis;
  private final TemplateParameterLookup parameters;
  private final LocalVariableManager varManager;
  private final FieldManager fields;
  private final ErrorReporter reporter;
  private final SoyTypeRegistry registry;
  private final CompiledTemplateRegistry compiledTemplateRegistry;

  private ExpressionCompiler(
      TemplateAnalysis analysis,
      TemplateParameterLookup parameters,
      LocalVariableManager varManager,
      FieldManager fields,
      ErrorReporter reporter,
      SoyTypeRegistry registry,
      CompiledTemplateRegistry compiledTemplateRegistry) {
    this.analysis = analysis;
    this.parameters = checkNotNull(parameters);
    this.varManager = checkNotNull(varManager);
    this.fields = checkNotNull(fields);
    this.reporter = reporter;
    this.registry = registry;
    this.compiledTemplateRegistry = compiledTemplateRegistry;
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode.
   *
   * <p>If the stack will not be empty when the expression is compiled, this method should be used
   * instead of {@link compileRootExpression}, and you should configure the {@code detacher} to
   * return to a {@code Label} where the stack will be empty.
   */
  SoyExpression compileSubExpression(ExprNode node, ExpressionDetacher detacher) {
    return asBasicCompiler(detacher).compile(node);
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode in the current method visitor.
   *
   * <p>The generated bytecode expects that the evaluation stack is empty when this method is called
   * and it will generate code such that the stack contains a single SoyValue when it returns. The
   * SoyValue object will have a runtime type equal to {@code node.getType().javaType()}.
   *
   * <p>If the stack will not be empty when this method is called, {@link compileSubExpression}
   * should be used instead so that you can manually configure a detacher that will return to the
   * correct label (where the stack is empty).
   */
  SoyExpression compileRootExpression(ExprNode node, ExpressionDetacher.Factory detacherFactory) {
    Label reattachPoint = new Label();
    final SoyExpression exec =
        compileSubExpression(node, detacherFactory.createExpressionDetacher(reattachPoint));
    return exec.withSource(exec.labelStart(reattachPoint));
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode if it can be done without
   * generating any detach operations.
   */
  Optional<SoyExpression> compileWithNoDetaches(ExprNode node) {
    checkNotNull(node);
    if (new RequiresDetachVisitor(analysis).exec(node)) {
      return Optional.empty();
    }
    return Optional.of(
        new CompilerVisitor(
                analysis,
                parameters,
                varManager,
                fields,
                /* detacher=*/ null,
                reporter,
                registry,
                compiledTemplateRegistry)
            .exec(node));
  }

  /**
   * Returns a {@link BasicExpressionCompiler} that can be used to compile multiple expressions all
   * with the same detach logic.
   */
  BasicExpressionCompiler asBasicCompiler(ExpressionDetacher detacher) {
    return new BasicExpressionCompiler(
        new CompilerVisitor(
            analysis,
            parameters,
            varManager,
            fields,
            detacher,
            reporter,
            registry,
            compiledTemplateRegistry));
  }

  private static final class CompilerVisitor
      extends EnhancedAbstractExprNodeVisitor<SoyExpression> {
    // is null when we are generating code with no detaches.
    @Nullable final ExpressionDetacher detacher;
    final TemplateAnalysis analysis;
    final TemplateParameterLookup parameters;
    final LocalVariableManager varManager;
    final FieldManager fields;
    final ErrorReporter reporter;
    final SoyTypeRegistry registry;
    final CompiledTemplateRegistry compiledTemplateRegistry;

    CompilerVisitor(
        TemplateAnalysis analysis,
        TemplateParameterLookup parameters,
        LocalVariableManager varManager,
        FieldManager fields,
        ExpressionDetacher detacher,
        ErrorReporter reporter,
        SoyTypeRegistry registry,
        CompiledTemplateRegistry compiledTemplateRegistry) {
      this.analysis = analysis;
      this.detacher = detacher;
      this.parameters = parameters;
      this.varManager = varManager;
      this.fields = fields;
      this.reporter = reporter;
      this.registry = registry;
      this.compiledTemplateRegistry = compiledTemplateRegistry;
    }

    @Override
    protected final SoyExpression visit(ExprNode node) {
      return super.visit(node).withSourceLocation(node.getSourceLocation());
    }

    @Override
    protected final SoyExpression visitExprRootNode(ExprRootNode node) {
      return visit(node.getRoot());
    }

    // Primitive value constants

    @Override
    protected final SoyExpression visitNullNode(NullNode node) {
      return SoyExpression.NULL;
    }

    @Override
    protected final SoyExpression visitFloatNode(FloatNode node) {
      return SoyExpression.forFloat(constant(node.getValue()));
    }

    @Override
    protected final SoyExpression visitStringNode(StringNode node) {
      return SoyExpression.forString(constant(node.getValue(), fields));
    }

    @Override
    protected SoyExpression visitProtoEnumValueNode(ProtoEnumValueNode node) {
      return SoyExpression.forInt(BytecodeUtils.constant(node.getValue()));
    }

    @Override
    protected final SoyExpression visitBooleanNode(BooleanNode node) {
      return node.getValue() ? SoyExpression.TRUE : SoyExpression.FALSE;
    }

    @Override
    protected final SoyExpression visitIntegerNode(IntegerNode node) {
      return SoyExpression.forInt(BytecodeUtils.constant(node.getValue()));
    }

    @Override
    protected final SoyExpression visitGlobalNode(GlobalNode node) {
      return visit(node.getValue());
    }

    // Collection literals

    @Override
    protected final SoyExpression visitListLiteralNode(ListLiteralNode node) {
      // TODO(lukes): this should really box the children as SoyValueProviders, we are boxing them
      // anyway and could additionally delay detach generation. Ditto for RecordLiteralNode.
      return SoyExpression.forList(
          (ListType) node.getType(), SoyExpression.asBoxedValueProviderList(visitChildren(node)));
    }

    @Override
    protected final SoyExpression visitListComprehensionNode(ListComprehensionNode node) {
      // TODO(lukes): consider adding a special case for when the listExpr is a range() function
      // invocation, as we do for regular loops.
      ExprNode listExpr = node.getListExpr();
      SoyExpression soyList = visit(listExpr);
      SoyExpression javaList = soyList.unboxAsList();
      ExprNode mapExpr = node.getListItemTransformExpr();
      ExprNode filterExpr = node.getFilterExpr();

      String varName = node.getListIterVar().name();
      LocalVariableManager.Scope scope = varManager.enterScope();
      LocalVariable listVar = scope.createTemporary(varName + "_input_list", LIST_TYPE);
      Statement listVarInitializer = listVar.store(javaList, listVar.start());
      LocalVariable resultVar = scope.createTemporary(varName + "_output_list", LIST_TYPE);
      Statement resultVarInitializer =
          resultVar.store(ConstructorRef.ARRAY_LIST.construct(), resultVar.start());
      LocalVariable sizeVar = scope.createTemporary(varName + "_input_list_size", Type.INT_TYPE);
      Statement sizeVarInitializer =
          sizeVar.store(listVar.invoke(MethodRef.LIST_SIZE), sizeVar.start());
      LocalVariable indexVar = scope.createTemporary(varName + "_index", Type.INT_TYPE);
      Statement indexVarInitializer = indexVar.store(constant(0), indexVar.start());
      LocalVariable itemVar =
          scope.createNamedLocal(node.getListIterVar().name(), SOY_VALUE_PROVIDER_TYPE);
      Statement itemVarInitializer =
          itemVar.store(
              listVar.invoke(MethodRef.LIST_GET, indexVar).checkedCast(SOY_VALUE_PROVIDER_TYPE),
              itemVar.start());
      LocalVariable userIndexVar =
          node.getIndexVar() == null
              ? null
              : scope.createNamedLocal(node.getIndexVar().name(), SOY_VALUE_PROVIDER_TYPE);
      Statement userIndexVarInitializer =
          userIndexVar == null
              ? null
              : userIndexVar.store(
                  SoyExpression.forInt(BytecodeUtils.numericConversion(indexVar, Type.LONG_TYPE))
                      .boxAsSoyValueProvider()
                      .checkedCast(SOY_VALUE_PROVIDER_TYPE),
                  userIndexVar.start());

      // TODO: Consider compiling to a SoyValueProvider instead of boxing.
      Expression visitedMap = visit(mapExpr).boxAsSoyValueProvider();

      SoyExpression visitedFilter = filterExpr != null ? visit(filterExpr).coerceToBoolean() : null;

      Statement exitScope = scope.exitScope();

      /*

      Generates byte code for a for loop that looks more or less like:

      List<?> a_list = unwrap(...);
      List<?> a_result = new ArrayList<>();
      int a_length = a_list.size();
      for (int a_i = 0; a_i < a_length; a_i++) {
        Object a = a_list.get(a_i);
        if (userIndexVar != null) {
          int i = a_i;
        }
        if (filterPredicate != null && !filterPredicate.test(a,i)) {
          continue;
        }
        a_result.add(mapFunction.apply(a,i));
      }
      return a_result;

      */
      return SoyExpression.forList(
              (ListType) node.getType(),
              new Expression(BytecodeUtils.LIST_TYPE, Feature.NON_NULLABLE) {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  listVarInitializer.gen(adapter); //   List<?> a_list = ...;
                  resultVarInitializer.gen(adapter); // List<?> a_result = new ArrayList<>();
                  sizeVarInitializer.gen(adapter); //   int a_length = a_list.size();
                  indexVarInitializer.gen(adapter); //  int a_i = 0;

                  Label loopStart = new Label();
                  Label loopContinue = new Label();
                  Label loopEnd = new Label();

                  adapter.mark(loopStart);

                  indexVar.gen(adapter);
                  sizeVar.gen(adapter);
                  adapter.ifICmp(Opcodes.IFGE, loopEnd); // if (a_i >= a_length) break;

                  itemVarInitializer.gen(adapter); // Object a = a_list.get(a_i);

                  if (userIndexVar != null) {
                    userIndexVarInitializer.gen(adapter); // int i = a_i;
                  }

                  if (visitedFilter != null) {
                    visitedFilter.gen(adapter);
                    adapter.ifZCmp(Opcodes.IFEQ, loopContinue); // if (!filter.test(a,i)) continue;
                  }

                  resultVar.gen(adapter);
                  visitedMap.gen(adapter);
                  MethodRef.ARRAY_LIST_ADD.invokeUnchecked(
                      adapter); // a_result.add(map.apply(a,i));
                  adapter.pop(); // pop boolean return value of List.add()

                  adapter.mark(loopContinue);

                  adapter.iinc(indexVar.index(), 1); // a_i++
                  adapter.goTo(loopStart);

                  adapter.mark(loopEnd);

                  resultVar.gen(adapter); // "return" a_result;
                  // exit the loop
                  exitScope.gen(adapter);
                }
              })
          .box();
    }

    @Override
    protected final SoyExpression visitRecordLiteralNode(RecordLiteralNode node) {
      final int numItems = node.numChildren();
      List<Expression> keys = new ArrayList<>(numItems);
      List<Expression> values = new ArrayList<>(numItems);
      for (int i = 0; i < numItems; i++) {
        // Keys are strings and values are boxed SoyValues.
        keys.add(BytecodeUtils.constant(node.getKey(i).identifier()));
        values.add(visit(node.getChild(i)).box());
      }
      Expression soyDict =
          MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invoke(
              BytecodeUtils.newLinkedHashMap(keys, values),
              FieldRef.enumReference(RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD)
                  .accessor());
      return SoyExpression.forSoyValue(node.getType(), soyDict);
    }

    @Override
    protected final SoyExpression visitMapLiteralNode(MapLiteralNode node) {
      final int numItems = node.numChildren() / 2;
      if (numItems == 0) {
        return SoyExpression.forSoyValue(node.getType(), FieldRef.EMPTY_MAP.accessor());
      }
      List<Expression> keys = new ArrayList<>(numItems);
      List<Expression> values = new ArrayList<>(numItems);
      for (int i = 0; i < numItems; i++) {
        // Keys and values are boxed SoyValues.
        keys.add(visit(node.getChild(2 * i)).box());
        values.add(visit(node.getChild(2 * i + 1)).box());
      }
      Expression soyDict =
          MethodRef.MAP_IMPL_FOR_PROVIDER_MAP.invoke(BytecodeUtils.newHashMap(keys, values));
      return SoyExpression.forSoyValue(node.getType(), soyDict);
    }

    // Comparison operators

    @Override
    protected final SoyExpression visitEqualOpNode(EqualOpNode node) {
      return SoyExpression.forBool(
          BytecodeUtils.compareSoyEquals(visit(node.getChild(0)), visit(node.getChild(1))));
    }

    @Override
    protected final SoyExpression visitNotEqualOpNode(NotEqualOpNode node) {
      return SoyExpression.forBool(
          logicalNot(
              BytecodeUtils.compareSoyEquals(visit(node.getChild(0)), visit(node.getChild(1)))));
    }

    // binary comparison operators.  N.B. it is ok to coerce 'number' values to floats because that
    // coercion preserves ordering

    @Override
    protected final SoyExpression visitLessThanOpNode(LessThanOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFLT, left.unboxAsLong(), right.unboxAsLong()));
      }
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFLT, left.coerceToDouble(), right.coerceToDouble()));
      }
      if (left.assignableToNullableString() && right.assignableToNullableString()) {
        return createStringComparisonOperator(Opcodes.IFLT, left, right);
      }
      return SoyExpression.forBool(MethodRef.RUNTIME_LESS_THAN.invoke(left.box(), right.box()));
    }

    @Override
    protected final SoyExpression visitGreaterThanOpNode(GreaterThanOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFGT, left.unboxAsLong(), right.unboxAsLong()));
      }
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFGT, left.coerceToDouble(), right.coerceToDouble()));
      }
      if (left.assignableToNullableString() && right.assignableToNullableString()) {
        return createStringComparisonOperator(Opcodes.IFGT, left, right);
      }
      // Note the argument reversal
      return SoyExpression.forBool(MethodRef.RUNTIME_LESS_THAN.invoke(right.box(), left.box()));
    }

    @Override
    protected final SoyExpression visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFLE, left.unboxAsLong(), right.unboxAsLong()));
      }
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFLE, left.coerceToDouble(), right.coerceToDouble()));
      }
      if (left.assignableToNullableString() && right.assignableToNullableString()) {
        return createStringComparisonOperator(Opcodes.IFLE, left, right);
      }
      return SoyExpression.forBool(
          MethodRef.RUNTIME_LESS_THAN_OR_EQUAL.invoke(left.box(), right.box()));
    }

    @Override
    protected final SoyExpression visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFGE, left.unboxAsLong(), right.unboxAsLong()));
      }
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFGE, left.coerceToDouble(), right.coerceToDouble()));
      }
      if (left.assignableToNullableString() && right.assignableToNullableString()) {
        return createStringComparisonOperator(Opcodes.IFGE, left, right);
      }
      // Note the reversal of the arguments.
      return SoyExpression.forBool(
          MethodRef.RUNTIME_LESS_THAN_OR_EQUAL.invoke(right.box(), left.box()));
    }

    private SoyExpression createStringComparisonOperator(
        int operator, SoyExpression left, SoyExpression right) {
      return SoyExpression.forBool(
          compare(
              operator,
              left.coerceToString().invoke(MethodRef.STRING_COMPARE_TO, right.coerceToString()),
              BytecodeUtils.constant(0)));
    }

    // Binary operators
    // For the binary math operators we try to do unboxed arithmetic as much as possible.
    // If both args are definitely ints -> do int math
    // If both args are definitely numbers and at least one is definitely a float -> do float math
    // otherwise use our boxed runtime methods.

    @Override
    protected final SoyExpression visitPlusOpNode(PlusOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyRuntimeType leftRuntimeType = left.soyRuntimeType();
      SoyExpression right = visit(node.getChild(1));
      SoyRuntimeType rightRuntimeType = right.soyRuntimeType();
      // They are both definitely numbers
      if (leftRuntimeType.assignableToNullableNumber()
          && rightRuntimeType.assignableToNullableNumber()) {
        if (leftRuntimeType.assignableToNullableInt()
            && rightRuntimeType.assignableToNullableInt()) {
          return applyBinaryIntOperator(Opcodes.LADD, left, right);
        }
        // if either is definitely a float, then we are definitely coercing so just do it now
        if (leftRuntimeType.assignableToNullableFloat()
            || rightRuntimeType.assignableToNullableFloat()) {
          return applyBinaryFloatOperator(Opcodes.DADD, left, right);
        }
      }
      // '+' is overloaded for string arguments to mean concatenation.
      if (leftRuntimeType.isKnownString() || rightRuntimeType.isKnownString()) {
        SoyExpression leftString = left.coerceToString();
        SoyExpression rightString = right.coerceToString();
        return SoyExpression.forString(leftString.invoke(MethodRef.STRING_CONCAT, rightString));
      }
      return SoyExpression.forSoyValue(
          SoyTypes.NUMBER_TYPE, MethodRef.RUNTIME_PLUS.invoke(left.box(), right.box()));
    }

    @Override
    protected final SoyExpression visitMinusOpNode(MinusOpNode node) {
      final SoyExpression left = visit(node.getChild(0));
      final SoyExpression right = visit(node.getChild(1));
      // They are both definitely numbers
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
          return applyBinaryIntOperator(Opcodes.LSUB, left, right);
        }
        // if either is definitely a float, then we are definitely coercing so just do it now
        if (left.assignableToNullableFloat() || right.assignableToNullableFloat()) {
          return applyBinaryFloatOperator(Opcodes.DSUB, left, right);
        }
      }
      return SoyExpression.forSoyValue(
          SoyTypes.NUMBER_TYPE, MethodRef.RUNTIME_MINUS.invoke(left.box(), right.box()));
    }

    @Override
    protected final SoyExpression visitTimesOpNode(TimesOpNode node) {
      final SoyExpression left = visit(node.getChild(0));
      final SoyExpression right = visit(node.getChild(1));
      // They are both definitely numbers
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
          return applyBinaryIntOperator(Opcodes.LMUL, left, right);
        }
        // if either is definitely a float, then we are definitely coercing so just do it now
        if (left.assignableToNullableFloat() || right.assignableToNullableFloat()) {
          return applyBinaryFloatOperator(Opcodes.DMUL, left, right);
        }
      }
      return SoyExpression.forSoyValue(
          SoyTypes.NUMBER_TYPE, MethodRef.RUNTIME_TIMES.invoke(left.box(), right.box()));
    }

    @Override
    protected final SoyExpression visitDivideByOpNode(DivideByOpNode node) {
      // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
      // Note that this *will* lose precision for longs.
      return applyBinaryFloatOperator(
          Opcodes.DDIV, visit(node.getChild(0)), visit(node.getChild(1)));
    }

    @Override
    protected final SoyExpression visitModOpNode(ModOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      // They are both definitely numbers
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
          return applyBinaryIntOperator(Opcodes.LREM, left, right);
        }
        // if either is definitely a float, then we are definitely coercing so just do it now
        if (left.assignableToNullableFloat() || right.assignableToNullableFloat()) {
          return applyBinaryFloatOperator(Opcodes.DREM, left, right);
        }
      }
      return SoyExpression.forSoyValue(
          SoyTypes.NUMBER_TYPE, MethodRef.RUNTIME_MOD.invoke(left.box(), right.box()));
    }

    private static SoyExpression applyBinaryIntOperator(
        final int operator, SoyExpression left, SoyExpression right) {
      final SoyExpression leftInt = left.unboxAsLong();
      final SoyExpression rightInt = right.unboxAsLong();
      return SoyExpression.forInt(
          new Expression(Type.LONG_TYPE) {
            @Override
            protected void doGen(CodeBuilder mv) {
              leftInt.gen(mv);
              rightInt.gen(mv);
              mv.visitInsn(operator);
            }
          });
    }

    private static SoyExpression applyBinaryFloatOperator(
        final int operator, SoyExpression left, SoyExpression right) {
      final SoyExpression leftFloat = left.coerceToDouble();
      final SoyExpression rightFloat = right.coerceToDouble();
      return SoyExpression.forFloat(
          new Expression(Type.DOUBLE_TYPE) {
            @Override
            protected void doGen(CodeBuilder mv) {
              leftFloat.gen(mv);
              rightFloat.gen(mv);
              mv.visitInsn(operator);
            }
          });
    }

    // Unary negation

    @Override
    protected final SoyExpression visitNegativeOpNode(NegativeOpNode node) {
      final SoyExpression child = visit(node.getChild(0));
      if (child.assignableToNullableInt()) {
        final SoyExpression intExpr = child.unboxAsLong();
        return SoyExpression.forInt(
            new Expression(Type.LONG_TYPE, child.features()) {
              @Override
              protected void doGen(CodeBuilder mv) {
                intExpr.gen(mv);
                mv.visitInsn(Opcodes.LNEG);
              }
            });
      }
      if (child.assignableToNullableFloat()) {
        final SoyExpression floatExpr = child.unboxAsDouble();
        return SoyExpression.forFloat(
            new Expression(Type.DOUBLE_TYPE, child.features()) {
              @Override
              protected void doGen(CodeBuilder mv) {
                floatExpr.gen(mv);
                mv.visitInsn(Opcodes.DNEG);
              }
            });
      }
      return SoyExpression.forSoyValue(
          SoyTypes.NUMBER_TYPE, MethodRef.RUNTIME_NEGATIVE.invoke(child.box()));
    }

    // Boolean operators

    @Override
    protected final SoyExpression visitNotOpNode(NotOpNode node) {
      // All values are convertible to boolean
      return SoyExpression.forBool(logicalNot(visit(node.getChild(0)).coerceToBoolean()));
    }

    @Override
    protected final SoyExpression visitAndOpNode(AndOpNode node) {
      SoyExpression left = visit(node.getChild(0)).coerceToBoolean();
      SoyExpression right = visit(node.getChild(1)).coerceToBoolean();
      return SoyExpression.forBool(BytecodeUtils.logicalAnd(left, right));
    }

    @Override
    protected final SoyExpression visitOrOpNode(OrOpNode node) {
      SoyExpression left = visit(node.getChild(0)).coerceToBoolean();
      SoyExpression right = visit(node.getChild(1)).coerceToBoolean();
      return SoyExpression.forBool(BytecodeUtils.logicalOr(left, right));
    }

    // Null coalescing operator

    @Override
    protected SoyExpression visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      final SoyExpression left = visit(node.getLeftChild());
      if (left.isNonNullable()) {
        // This would be for when someone writes '1 ?: 2', we just compile that to '1'
        // This case is insane and should potentially be a compiler error, for now we just assume
        // it is dead code.
        return left;
      }
      // It is extremely common for a user to write '<complex-expression> ?: <primitive-expression>
      // so try to generate code that doesn't involve unconditionally boxing the right hand side.
      final SoyExpression right = visit(node.getRightChild());
      if (SoyTypes.removeNull(left.soyType()).equals(right.soyType())) {
        SoyExpression result;
        if (left.isBoxed() == right.isBoxed()) {
          // no conversions!
          result = right.withSource(firstNonNull(left, right));
        } else {
          SoyExpression boxedRight = right.box();
          result = boxedRight.withSource(firstNonNull(left.box(), boxedRight));
        }
        if (Expression.areAllCheap(left, right)) {
          result = result.asCheap();
        }
        return result;
      }
      // Now we need to do some boxing conversions.  soy expression boxes null -> null so this is
      // safe (and I assume that the jit can eliminate the resulting redundant branches)
      Type runtimeType = SoyRuntimeType.getBoxedType(node.getType()).runtimeType();
      return SoyExpression.forSoyValue(
          node.getType(),
          firstNonNull(left.box().checkedCast(runtimeType), right.box().checkedCast(runtimeType)));
    }

    // Ternary operator

    @Override
    protected final SoyExpression visitConditionalOpNode(ConditionalOpNode node) {
      final SoyExpression condition = visit(node.getChild(0)).coerceToBoolean();
      SoyExpression trueBranch = visit(node.getChild(1));
      SoyExpression falseBranch = visit(node.getChild(2));
      // If types are == and they are both boxed (or both not boxed) then we can just use them
      // directly.
      // Otherwise we need to do boxing conversions.
      // In the past there have been several attempts to eliminate unnecessary boxing operations
      // in these conditions however it is too difficult given the type information we have
      // available to us and the primitive operations available on SoyExpression.  For example, the
      // expressions may have non-nullable types and yet take on null values at runtime, if we were
      // to introduce aggressive unboxing operations it could result in unexpected
      // NullPointerExceptions at runtime.  To fix these issues we would need to have a better
      // notion of what expressions are nullable (or really, non-nullable) at runtime.
      // TODO(lukes): Simple ideas that could help the above:
      // 1. expose the 'non-null' prover from ResolveExpressionTypesPass, this can in fact be
      //    relied on.  However it is currently mixed in with other parts of the type system which
      //    cannot be trusted
      boolean typesEqual = trueBranch.soyType().equals(falseBranch.soyType());
      if (typesEqual) {
        if (trueBranch.isBoxed() == falseBranch.isBoxed()) {
          return trueBranch.withSource(ternary(condition, trueBranch, falseBranch));
        }
        SoyExpression boxedTrue = trueBranch.box();
        return boxedTrue.withSource(ternary(condition, boxedTrue, falseBranch.box()));
      }
      Type boxedRuntimeType = SoyRuntimeType.getBoxedType(node.getType()).runtimeType();
      return SoyExpression.forSoyValue(
          node.getType(),
          ternary(
              condition,
              trueBranch.box().checkedCast(boxedRuntimeType),
              falseBranch.box().checkedCast(boxedRuntimeType)));
    }

    @Override
    SoyExpression visitForLoopVar(VarRefNode varRef, LocalVar local) {
      Expression expression = parameters.getLocal(local);
      if (expression.resultType().equals(Type.LONG_TYPE)) {
        // it can be an unboxed long when executing a foreach over a range
        return SoyExpression.forInt(expression);
      } else if (!analysis.isResolved(varRef)) {
        // otherwise it must be a SoyValueProvider, resolve and cast
        expression = detacher.resolveSoyValueProvider(expression);
        return SoyExpression.forSoyValue(
            varRef.getType(),
            expression.checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
      } else {
        return SoyExpression.forSoyValue(
            varRef.getType(),
            expression
                .invoke(MethodRef.SOY_VALUE_PROVIDER_RESOLVE)
                .checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
      }
    }

    // Params

    @Override
    SoyExpression visitParam(VarRefNode varRef, TemplateParam param) {
      // This inserts a CHECKCAST instruction (aka runtime type checking).  However, it is limited
      // since we do not have good checking for unions (or nullability)
      // TODO(lukes): Where/how should we implement type checking.  For the time being type errors
      // will show up here, and in the unboxing conversions performed during expression
      // manipulation. And, presumably, in NullPointerExceptions.
      return SoyExpression.forSoyValue(
          varRef.getType(),
          resolveVarRefNode(varRef, parameters.getParam(param))
              .checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
    }

    // Let vars

    @Override
    SoyExpression visitLetNodeVar(VarRefNode varRef, LocalVar local) {
      return SoyExpression.forSoyValue(
          varRef.getType(),
          resolveVarRefNode(varRef, parameters.getLocal(local))
              .checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
    }

    @Override
    SoyExpression visitListComprehensionVar(VarRefNode varRef, ComprehensionVarDefn var) {
      return SoyExpression.forSoyValue(
          varRef.getType(),
          resolveVarRefNode(varRef, parameters.getLocal(var))
              .checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
    }

    /**
     * Returns either an expression to detach or a direct SOY_VALUE_PROVIDER_RESOLVE invocation.
     *
     * @param varRef The variable node that we want to generate an expression for.
     * @param unresolvedExpression The expression corresponding to the unresolved variable.
     */
    private Expression resolveVarRefNode(VarRefNode varRef, Expression unresolvedExpression) {
      if (!analysis.isResolved(varRef)) {
        return detacher.resolveSoyValueProvider(unresolvedExpression);
      } else {
        return unresolvedExpression.invoke(MethodRef.SOY_VALUE_PROVIDER_RESOLVE);
      }
    }

    // Data access

    @Override
    protected SoyExpression visitDataAccessNode(DataAccessNode node) {
      return visitDataAccessNodeRecurse(node);
    }

    private SoyExpression visitDataAccessNodeRecurse(ExprNode node) {
      switch (node.getKind()) {
        case FIELD_ACCESS_NODE:
        case ITEM_ACCESS_NODE:
        case METHOD_CALL_NODE:
          SoyExpression baseExpr =
              visitDataAccessNodeRecurse(((DataAccessNode) node).getBaseExprChild());
          // Mark non nullable.
          // Dereferencing for access below may require unboxing and there is no point in adding
          // null safety checks to the unboxing code.  So we just mark non nullable. In otherwords,
          // if we are going to hit an NPE while dereferencing this expression, it makes no
          // difference if it is due to the unboxing or the actual dereference.
          if (baseExpr.soyType() != NullType.getInstance()) {
            baseExpr = baseExpr.asNonNullable();
          } else {
            // Unless, the type actually is 'null'.  In this case the code is bugged, but this
            // can happen due to inlining+@state desugaring.  The code we generate will always
            // fail, so performance isn't a concern.
            // Consider:
            // {@state foo: Bar|null = null}
            // ...
            // {$foo.field}
            // State desugaring will rewrite this to
            // {let $foo: null /}
            // and the inliner will inline it to
            // {null.field}
            // This code will always fail with an NPE, so do that here.
            return SoyExpression.forSoyValue(
                node.getType(),
                new Expression(
                    SoyRuntimeType.getBoxedType(node.getType()).runtimeType(), Feature.CHEAP) {
                  @Override
                  protected void doGen(CodeBuilder cb) {
                    String accessType;
                    switch (node.getKind()) {
                      case FIELD_ACCESS_NODE:
                        accessType = "field " + ((FieldAccessNode) node).getFieldName();
                        break;
                      case ITEM_ACCESS_NODE:
                        accessType = "element " + ((ItemAccessNode) node).getSourceStringSuffix();
                        break;
                      case METHOD_CALL_NODE:
                        accessType =
                            "method " + ((MethodCallNode) node).getMethodName().identifier();
                        break;
                      default:
                        throw new AssertionError();
                    }
                    cb.throwException(
                        NULL_POINTER_EXCEPTION_TYPE,
                        String.format("Attempted to access %s of null", accessType));
                  }
                });
          }
          return visitDataAccess((DataAccessNode) node, baseExpr, /* hasAssertNonNull= */ false);
        default:
          return visit(node);
      }
    }

    private SoyExpression visitDataAccess(
        DataAccessNode node, SoyExpression baseExpr, boolean hasAssertNonNull) {
      SoyExpression result;
      switch (node.getKind()) {
        case FIELD_ACCESS_NODE:
          result =
              visitFieldAccess(baseExpr, (FieldAccessNode) node)
                  .withSourceLocation(node.getSourceLocation());
          break;
        case ITEM_ACCESS_NODE:
          result =
              visitItemAccess(baseExpr, (ItemAccessNode) node)
                  .withSourceLocation(node.getSourceLocation());
          break;
        case METHOD_CALL_NODE:
          result =
              visitMethodCall(baseExpr, (MethodCallNode) node)
                  .withSourceLocation(node.getSourceLocation());
          break;
        default:
          throw new AssertionError();
      }
      if (hasAssertNonNull) {
        result = assertNonNull(result, node);
      }
      return result;
    }

    private SoyExpression visitFieldAccess(SoyExpression baseExpr, FieldAccessNode node) {
      // All null safe accesses should've already been converted to NullSafeAccessNodes.
      checkArgument(!node.isNullSafe());
      if (baseExpr.soyRuntimeType().isKnownProtoOrUnionOfProtos()) {
        if (baseExpr.soyType().getKind() == Kind.PROTO) {
          // It is a single known proto field.  Generate code to call the getter directly
          SoyProtoType protoType = (SoyProtoType) baseExpr.soyType();
          return ProtoUtils.accessField(protoType, baseExpr, node.getFieldName(), node.getType());
        } else {
          return ProtoUtils.accessProtoUnionField(baseExpr, node, varManager);
        }
      }
      // Otherwise this must be a vanilla SoyRecord.  Box, call getField or getFieldProvider
      // depending on the resolution status.
      Expression fieldAccess;
      Expression baseExprAsRecord = baseExpr.box().checkedCast(SoyRecord.class);
      if (analysis.isResolved(node)) {
        fieldAccess =
            MethodRef.RUNTIME_GET_FIELD.invoke(baseExprAsRecord, constant(node.getFieldName()));
      } else {
        Expression fieldProvider =
            MethodRef.RUNTIME_GET_FIELD_PROVIDER.invoke(
                baseExprAsRecord, constant(node.getFieldName()));
        fieldAccess = detacher.resolveSoyValueProvider(fieldProvider);
      }
      return SoyExpression.forSoyValue(
          node.getType(),
          fieldAccess.checkedCast(SoyRuntimeType.getBoxedType(node.getType()).runtimeType()));
    }

    private SoyExpression visitItemAccess(SoyExpression baseExpr, ItemAccessNode node) {
      // All null safe accesses should've already been converted to NullSafeAccessNodes.
      checkArgument(!node.isNullSafe());
      // KeyExprs never participate in the current null access chain.
      SoyExpression keyExpr = visit(node.getKeyExprChild());

      Expression soyValueProvider;
      // Special case index lookups on lists to avoid boxing the int key.  Maps cannot be
      // optimized the same way because there is no real way to 'unbox' a SoyMap.
      if (baseExpr.soyRuntimeType().isKnownListOrUnionOfLists()) {
        SoyExpression list = baseExpr.unboxAsList();
        SoyExpression index = keyExpr.unboxAsLong();
        if (analysis.isResolved(node)) {
          soyValueProvider = MethodRef.RUNTIME_GET_LIST_ITEM.invoke(list, index);
        } else {
          soyValueProvider =
              detacher.resolveSoyValueProvider(
                  MethodRef.RUNTIME_GET_LIST_ITEM_PROVIDER.invoke(list, index));
        }
      } else if (baseExpr.soyRuntimeType().isKnownMapOrUnionOfMaps()) {
        Expression map = baseExpr.box().checkedCast(SoyMap.class);
        SoyExpression index = keyExpr.box();
        if (analysis.isResolved(node)) {
          soyValueProvider = MethodRef.RUNTIME_GET_MAP_ITEM.invoke(map, index);
        } else {
          soyValueProvider =
              detacher.resolveSoyValueProvider(
                  MethodRef.RUNTIME_GET_MAP_ITEM_PROVIDER.invoke(map, index));
        }
      } else {
        Expression map = baseExpr.box().checkedCast(SoyLegacyObjectMap.class);
        SoyExpression index = keyExpr.box();
        if (analysis.isResolved(node)) {
          soyValueProvider = MethodRef.RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM.invoke(map, index);
        } else {
          soyValueProvider =
              detacher.resolveSoyValueProvider(
                  MethodRef.RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM_PROVIDER.invoke(map, index));
        }
      }
      return SoyExpression.forSoyValue(
          node.getType(),
          soyValueProvider.checkedCast(SoyRuntimeType.getBoxedType(node.getType()).runtimeType()));
    }

    private SoyExpression visitMethodCall(SoyExpression baseExpr, MethodCallNode node) {
      // All null safe accesses should've already been converted to NullSafeAccessNodes.
      checkArgument(!node.isNullSafe());
      checkArgument(node.isMethodResolved());

      // Never allow a null method receiver.
      if (!BytecodeUtils.isPrimitive(baseExpr.resultType())) {
        baseExpr = assertNonNull(baseExpr, node.getBaseExprChild());
      }

      SoyMethod function = node.getSoyMethod();
      if (function instanceof BuiltinMethod) {
        BuiltinMethod builtinMethod = (BuiltinMethod) function;
        switch (builtinMethod) {
          case GET_EXTENSION:
            return ProtoUtils.accessExtensionField(
                baseExpr,
                node,
                BuiltinMethod.getProtoExtensionIdFromMethodCall(node),
                /* useBrokenSemantics= */ true);
          case HAS_PROTO_FIELD:
            return ProtoUtils.hasserField(
                baseExpr, BuiltinMethod.getProtoFieldNameFromMethodCall(node));
          case BIND:
            return SoyExpression.forSoyValue(
                node.getType(),
                MethodRef.RUNTIME_BIND_TEMPLATE_PARAMS.invoke(baseExpr, visit(node.getChild(1))));
        }
      } else if (function instanceof SoySourceFunctionMethod) {
        SoySourceFunctionMethod sourceMethod = (SoySourceFunctionMethod) function;
        List<SoyExpression> args = new ArrayList<>(node.numParams() + 1);
        args.add(baseExpr);
        node.getParams().forEach(n -> args.add(visit(n)));
        return visitSoyJavaSourceFunction(
            JavaPluginExecContext.forMethodCallNode(node, sourceMethod), args);
      }
      throw new AssertionError(function.getClass());
    }

    @Override
    protected SoyExpression visitNullSafeAccessNode(NullSafeAccessNode nullSafeAccessNode) {
      // A null safe access {@code $foo?.bar?.baz} is syntactic sugar for {@code $foo == null ?
      // null : $foo.bar == null ? null : $foo.bar.baz)}. So to generate code for it we need to have
      // a way to 'exit' the full access chain as soon as we observe a failed null safety check.
      Label nullSafeExit = new Label();
      SoyExpression accumulator = visit(nullSafeAccessNode.getBase());
      ExprNode dataAccess = nullSafeAccessNode.getDataAccess();
      while (dataAccess.getKind() == ExprNode.Kind.NULL_SAFE_ACCESS_NODE) {
        NullSafeAccessNode node = (NullSafeAccessNode) dataAccess;
        accumulator =
            accumulateNullSafeDataAccess(
                (DataAccessNode) node.getBase(),
                accumulator,
                nullSafeExit,
                /* hasAssertNonNull= */ false);
        dataAccess = node.getDataAccess();
      }
      accumulator =
          accumulateNullSafeDataAccessTail(
              (AccessChainComponentNode) dataAccess, accumulator, nullSafeExit);
      if (BytecodeUtils.isPrimitive(accumulator.resultType())) {
        // proto accessors will return primitives, so in order to allow it to be compatible with
        // a nullable expression we need to box.
        accumulator = accumulator.box();
      }
      return accumulator.asNullable().labelEnd(nullSafeExit);
    }

    private static SoyExpression addNullSafetyCheck(
        final SoyExpression baseExpr, Label nullSafeExit) {
      // need to check if baseExpr == null
      return baseExpr
          .withSource(
              new Expression(baseExpr.resultType(), baseExpr.features()) {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  baseExpr.gen(adapter);
                  BytecodeUtils.nullCoalesce(adapter, nullSafeExit);
                }
              })
          .asNonNullable();
    }

    private SoyExpression accumulateNullSafeDataAccessTail(
        AccessChainComponentNode dataAccessNode, SoyExpression baseExpr, Label nullSafeExit) {
      boolean hasAssertNonNull = false;
      if (dataAccessNode.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        AssertNonNullOpNode assertNonNull = (AssertNonNullOpNode) dataAccessNode;
        dataAccessNode = (AccessChainComponentNode) assertNonNull.getChild(0);
        hasAssertNonNull = true;
      }
      return accumulateNullSafeDataAccess(
          (DataAccessNode) dataAccessNode, baseExpr, nullSafeExit, hasAssertNonNull);
    }

    private SoyExpression accumulateNullSafeDataAccess(
        DataAccessNode dataAccessNode,
        SoyExpression baseExpr,
        Label nullSafeExit,
        boolean hasAssertNonNull) {
      baseExpr = addNullSafetyCheck(baseExpr, nullSafeExit);
      return accumulateDataAccess(dataAccessNode, baseExpr, hasAssertNonNull);
    }

    private SoyExpression accumulateDataAccess(
        DataAccessNode dataAccessNode, SoyExpression baseExpr, boolean hasAssertNonNull) {
      if (dataAccessNode.getBaseExprChild() instanceof DataAccessNode) {
        baseExpr =
            accumulateDataAccess(
                    (DataAccessNode) dataAccessNode.getBaseExprChild(),
                    baseExpr,
                    /* hasAssertNonNull= */ false)
                // Mark non nullable.
                // Dereferencing for access below may require unboxing and there is no point in
                // adding null safety checks to the unboxing code.  So we just mark non nullable.
                // In otherwords, if we are going to hit an NPE while dereferencing this
                // expression, it makes no difference if it is due to the unboxing or the actual
                // dereference.
                .asNonNullable();
      }
      return visitDataAccess(dataAccessNode, baseExpr, hasAssertNonNull);
    }

    // Builtin functions

    @Override
    SoyExpression visitIsFirstFunction(FunctionNode node) {
      VarRefNode varRef = (VarRefNode) node.getChild(0);
      LocalVarNode foreach = ((LocalVar) varRef.getDefnDecl()).declaringNode();
      SyntheticVarName indexVar = foreachLoopIndex((ForNonemptyNode) foreach);
      final Expression expr = parameters.getLocal(indexVar);

      return SoyExpression.forBool(
          new Expression(Type.BOOLEAN_TYPE) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              // implements index == 0 ? true : false
              expr.gen(adapter);
              Label ifFirst = new Label();
              adapter.ifZCmp(Opcodes.IFEQ, ifFirst);
              adapter.pushBoolean(false);
              Label end = new Label();
              adapter.goTo(end);
              adapter.mark(ifFirst);
              adapter.pushBoolean(true);
              adapter.mark(end);
            }
          });
    }

    @Override
    SoyExpression visitIsLastFunction(FunctionNode node) {
      VarRefNode varRef = (VarRefNode) node.getChild(0);
      LocalVarNode foreach = ((LocalVar) varRef.getDefnDecl()).declaringNode();
      SyntheticVarName indexVar = foreachLoopIndex((ForNonemptyNode) foreach);
      SyntheticVarName lengthVar = foreachLoopLength((ForNonemptyNode) foreach);

      final Expression index = parameters.getLocal(indexVar);
      final Expression length = parameters.getLocal(lengthVar);
      // basically 'index + 1 == length'
      return SoyExpression.forBool(
          new Expression(Type.BOOLEAN_TYPE) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              // 'index + 1 == length ? true : false'
              index.gen(adapter);
              adapter.pushInt(1);
              adapter.visitInsn(Opcodes.IADD);
              length.gen(adapter);
              Label ifLast = new Label();
              adapter.ifICmp(Opcodes.IFEQ, ifLast);
              adapter.pushBoolean(false);
              Label end = new Label();
              adapter.goTo(end);
              adapter.mark(ifLast);
              adapter.pushBoolean(true);
              adapter.mark(end);
            }
          });
    }

    @Override
    SoyExpression visitIndexFunction(FunctionNode node) {
      VarRefNode varRef = (VarRefNode) node.getChild(0);
      LocalVarNode foreach = ((LocalVar) varRef.getDefnDecl()).declaringNode();
      SyntheticVarName indexVar = foreachLoopIndex((ForNonemptyNode) foreach);

      // '(long) index'
      return SoyExpression.forInt(
          BytecodeUtils.numericConversion(parameters.getLocal(indexVar), Type.LONG_TYPE));
    }

    @Override
    protected SoyExpression visitAssertNonNullOpNode(AssertNonNullOpNode node) {
      return assertNonNull(Iterables.getOnlyElement(node.getChildren()));
    }

    @Override
    SoyExpression visitCheckNotNullFunction(FunctionNode node) {
      // there is only ever a single child
      return assertNonNull(Iterables.getOnlyElement(node.getChildren()));
    }

    private SoyExpression assertNonNull(ExprNode node) {
      return assertNonNull(visit(node), node);
    }

    private static SoyExpression assertNonNull(SoyExpression expr, ExprNode node) {
      return expr.withSource(
              new Expression(expr.resultType(), expr.features()) {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  expr.gen(adapter);
                  adapter.dup();
                  Label end = new Label();
                  adapter.ifNonNull(end);
                  adapter.throwException(
                      NULL_POINTER_EXCEPTION_TYPE,
                      "'" + node.toSourceString() + "' evaluates to null");
                  adapter.mark(end);
                }
              })
          .asNonNullable();
    }

    // TODO(lukes):  The RenderVisitor optimizes css/xid renaming by stashing a one element cache in
    // the CSS node itself (keyed off the identity of the renaming map).  We could easily add such
    // an optimization via a static field in the Template class. Though im not sure it makes sense
    // as an optimization... this should just be an immutable map lookup keyed off of a constant
    // string. If we cared a lot, we could employ a simpler (and more compact) optimization by
    // assigning each selector a unique integer id and then instead of hashing we can just reference
    // an array (aka perfect hashing).  This could be part of our runtime library and ids could be
    // assigned at startup.

    @Override
    SoyExpression visitCssFunction(FunctionNode node) {
      StringNode selector = (StringNode) Iterables.getLast(node.getChildren());
      Expression renamedSelector = parameters.getRenderContext().renameCss(selector.getValue());

      if (node.numChildren() == 1) {
        return SoyExpression.forString(renamedSelector);
      } else {
        SoyExpression base = visit(node.getChild(0)).coerceToString();
        Expression fullSelector =
            base.invoke(MethodRef.STRING_CONCAT, constant("-"))
                .invoke(MethodRef.STRING_CONCAT, renamedSelector);
        return SoyExpression.forString(fullSelector);
      }
    }

    @Override
    SoyExpression visitXidFunction(FunctionNode node) {
      StringNode xid = (StringNode) Iterables.getOnlyElement(node.getChildren());
      Expression renamedXid = parameters.getRenderContext().renameXid(xid.getValue());
      return SoyExpression.forString(renamedXid);
    }

    @Override
    SoyExpression visitSoyServerKeyFunction(FunctionNode node) {
      ExprNode child = Iterables.getOnlyElement(node.getChildren());
      return SoyExpression.forString(MethodRef.SOY_SERVER_KEY.invoke(visit(child).box()));
    }

    @Override
    SoyExpression visitIsPrimaryMsgInUse(FunctionNode node) {
      return SoyExpression.forBool(
          parameters
              .getRenderContext()
              .usePrimaryMsgIfFallback(
                  ((IntegerNode) node.getChild(1)).getValue(),
                  ((IntegerNode) node.getChild(2)).getValue()));
    }

    @Override
    SoyExpression visitToFloatFunction(FunctionNode node) {
      SoyExpression arg = visit(node.getChild(0));
      return SoyExpression.forFloat(
          BytecodeUtils.numericConversion(arg.unboxAsLong(), Type.DOUBLE_TYPE));
    }

    @Override
    SoyExpression visitDebugSoyTemplateInfoFunction(FunctionNode node) {
      return SoyExpression.forBool(parameters.getRenderContext().getDebugSoyTemplateInfo());
    }

    @Override
    SoyExpression visitVeDataFunction(FunctionNode node) {
      SoyExpression ve = visit(node.getChild(0));
      Expression data = visit(node.getChild(1)).unboxAsMessage();
      return SoyExpression.forSoyValue(
          node.getType(), MethodRef.SOY_VISUAL_ELEMENT_DATA_CREATE.invoke(ve, data));
    }

    // Non-builtin functions

    @Override
    SoyExpression visitPluginFunction(FunctionNode node) {
      Object fn = node.getSoyFunction();
      List<SoyExpression> args = visitChildren(node);
      if (fn instanceof SoyJavaSourceFunction) {
        return visitSoyJavaSourceFunction(
            JavaPluginExecContext.forFunctionNode(node, (SoyJavaSourceFunction) fn), args);
      }

      // Functions that are not a SoyJavaSourceFunction
      // are registered with a LegacyFunctionAdapter by SoySauceImpl.
      Expression legacyFunctionRuntimeExpr =
          parameters
              .getRenderContext()
              .getPluginInstance(node.getStaticFunctionName())
              .checkedCast(LegacyFunctionAdapter.class);
      Expression list = SoyExpression.asBoxedList(args);
      // Most soy functions don't have return types, but if they do we should enforce it
      return SoyExpression.forSoyValue(
          node.getType(),
          MethodRef.RUNTIME_CALL_LEGACY_FUNCTION
              .invoke(legacyFunctionRuntimeExpr, list)
              .checkedCast(SoyRuntimeType.getBoxedType(node.getType()).runtimeType()));
    }

    SoyExpression visitSoyJavaSourceFunction(
        JavaPluginExecContext context, List<SoyExpression> args) {
      return new JbcSrcValueFactory(
              context,
              // parameters is null when we are in a constant context.
              parameters == null
                  ? new JbcSrcPluginContext() {
                    private Expression error() {
                      throw new UnsupportedOperationException(
                          "Cannot access contextual data from a pure context");
                    }

                    @Override
                    public Expression getBidiGlobalDir() {
                      return error();
                    }

                    @Override
                    public Expression getAllRequiredCssNamespaces(SoyExpression template) {
                      return error();
                    }

                    @Override
                    public Expression getULocale() {
                      return error();
                    }
                  }
                  : parameters.getPluginContext(),
              pluginName -> {
                if (parameters == null) {
                  throw new UnsupportedOperationException("Pure functions cannot have instances");
                }
                return parameters.getRenderContext().getPluginInstance(pluginName);
              },
              reporter,
              registry)
          .computeForJavaSource(args);
    }

    // Proto initialization calls

    @Override
    protected SoyExpression visitProtoInitFunction(FunctionNode node) {
      return ProtoUtils.createProto(node, this::visit, detacher, varManager);
    }

    private static final Handle GET_VE_WITH_METADATA_HANDLE =
        MethodRef.create(
                ClassLoaderFallbackCallFactory.class,
                "bootstrapVeWithMetadata",
                MethodHandles.Lookup.class,
                String.class,
                MethodType.class,
                String.class)
            .asHandle();

    private static final String VE_WITH_METADATA_SIGNATURE =
        Type.getMethodDescriptor(
            BytecodeUtils.SOY_VISUAL_ELEMENT_TYPE,
            Type.LONG_TYPE,
            BytecodeUtils.STRING_TYPE,
            BytecodeUtils.RENDER_CONTEXT_TYPE,
            Type.LONG_TYPE);

    @Override
    protected SoyExpression visitVeLiteralNode(VeLiteralNode node) {
      Expression visualElement;
      ValidatedLoggableElement element = node.getLoggableElement();
      if (element.hasMetadata()) {
        Expression renderContext = parameters.getRenderContext();
        visualElement =
            new Expression(BytecodeUtils.SOY_VISUAL_ELEMENT_TYPE) {
              @Override
              protected void doGen(CodeBuilder adapter) {
                adapter.pushLong(node.getId());
                adapter.pushString(node.getName().identifier());
                renderContext.gen(adapter);
                adapter.pushLong(node.getId());
                adapter.visitInvokeDynamicInsn(
                    "getVeWithMetadata",
                    VE_WITH_METADATA_SIGNATURE,
                    GET_VE_WITH_METADATA_HANDLE,
                    String.format("%s.%s", element.getJavaPackage(), element.getClassName()));
              }
            };
      } else {
        visualElement =
            MethodRef.SOY_VISUAL_ELEMENT_CREATE.invoke(
                constant(node.getId()), constant(node.getName().identifier()));
      }
      return SoyExpression.forSoyValue(node.getType(), visualElement);
    }

    private static final Handle GETFACTORY_HANDLE =
        MethodRef.create(
                ClassLoaderFallbackCallFactory.class,
                "bootstrapFactoryValueLookup",
                MethodHandles.Lookup.class,
                String.class,
                MethodType.class,
                String.class)
            .asHandle();

    private static final String TEMPLATE_FACTORY_SIGNATURE =
        Type.getMethodDescriptor(
            BytecodeUtils.COMPILED_TEMPLATE_FACTORY_VALUE_TYPE, BytecodeUtils.RENDER_CONTEXT_TYPE);

    @Override
    protected SoyExpression visitTemplateLiteralNode(TemplateLiteralNode node) {
      Expression renderContext = parameters.getRenderContext();
      return SoyExpression.forSoyValue(
          node.getType(),
          new Expression(BytecodeUtils.COMPILED_TEMPLATE_FACTORY_VALUE_TYPE) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              renderContext.gen(adapter);
              adapter.visitInvokeDynamicInsn(
                  "create", TEMPLATE_FACTORY_SIGNATURE, GETFACTORY_HANDLE, node.getResolvedName());
            }
          });
    }

    // Catch-all for unimplemented nodes

    @Override
    protected final SoyExpression visitExprNode(ExprNode node) {
      throw new UnsupportedOperationException(
          "Support for " + node.getKind() + " has not been added yet");
    }
  }

  private static final class CanCompileToConstantVisitor
      extends AbstractReturningExprNodeVisitor<Boolean> {
    static final CanCompileToConstantVisitor INSTANCE = new CanCompileToConstantVisitor();

    @Override
    protected Boolean visitExprRootNode(ExprRootNode node) {
      return areAllChildrenConstant(node);
    }

    @Override
    protected Boolean visitVarRefNode(VarRefNode node) {
      // no variable references are allowed in constant context, except for those defined in the
      // same context which at this point is simply list comprehensions.
      // NOTE: this logic is only valid if the whole expression is being compiled to a constant. If
      // we ever try compiling subexpressions to constants we will need to check that the
      // declaringNode is in the same subexpression.
      switch (node.getDefnDecl().kind()) {
        case COMPREHENSION_VAR:
          return true;
        case PARAM:
        case LOCAL_VAR:
        case STATE:
          return false;
        case UNDECLARED:
        case IMPORT_VAR:
        case TEMPLATE:
          break;
      }
      throw new AssertionError();
    }

    @Override
    protected Boolean visitPrimitiveNode(PrimitiveNode node) {
      // primitives are fine
      return true;
    }

    @Override
    protected Boolean visitVeLiteralNode(VeLiteralNode node) {
      // VE metadata needs a RenderContext to resolve.
      return !node.getLoggableElement().hasMetadata();
    }

    @Override
    protected Boolean visitTemplateLiteralNode(TemplateLiteralNode node) {
      // This requires a RenderContext object to look up the template.
      // Technically this could be conditional on whether or not the callee is in a SRC file, but
      // this would require wiring through the CompiledTemplateRegistry to this class to calculate
      // and probably isn't a big deal.
      return false;
    }

    @Override
    protected Boolean visitGlobalNode(GlobalNode node) {
      // this is essentially a primitive
      return true;
    }

    // collection literals are fine if their contents are
    @Override
    protected Boolean visitListLiteralNode(ListLiteralNode node) {
      return areAllChildrenConstant(node);
    }

    @Override
    protected Boolean visitListComprehensionNode(ListComprehensionNode node) {
      return areAllChildrenConstant(node);
    }

    @Override
    protected Boolean visitRecordLiteralNode(RecordLiteralNode node) {
      return areAllChildrenConstant(node);
    }

    @Override
    protected Boolean visitMapLiteralNode(MapLiteralNode node) {
      return areAllChildrenConstant(node);
    }

    @Override
    protected Boolean visitMethodCallNode(MethodCallNode node) {
      if (node.getMethodName().toString().equals("bind")) {
        return areAllChildrenConstant(node);
      }
      return false;
    }

    @Override
    protected Boolean visitDataAccessNode(DataAccessNode node) {
      // If this could be compiled to a constant expression, then the optimizer should have already
      // evaluated it.  So don't bother.
      return false;
    }

    @Override
    protected Boolean visitNullSafeAccessNode(NullSafeAccessNode node) {
      return visit(node.getBase()) && areAllChildrenConstant(node);
    }

    @Override
    protected Boolean visitOperatorNode(OperatorNode node) {
      return areAllChildrenConstant(node);
    }

    @Override
    protected Boolean visitFunctionNode(FunctionNode node) {
      if (!areAllChildrenConstant(node)) {
        return false;
      }
      if (node.getSoyFunction() == BuiltinFunction.PROTO_INIT) {
        return true;
      }
      if (!node.isPure()) {
        return false;
      }
      // We can evaluate the function if
      // all the parameters are constants and we have an implementation that doesn't depend on the
      // render context.
      // TODO(lukes): if the plugin is annotated as @SoyPureFunction, but it accesses the context,
      // then it isn't pure.  add logic in the vallidator?
      if (node.getSoyFunction() instanceof SoyJavaSourceFunction) {
        try {
          PluginAnalyzer.PluginMetadata metadata =
              PluginAnalyzer.analyze((SoyJavaSourceFunction) node.getSoyFunction());
          // the plugin can be generated as a constant expression if it doesn't access the context
          // or require an instance function.
          return metadata.pluginInstanceNames().isEmpty() && !metadata.accessesContext();
        } catch (Throwable ignored) {
          // sort of lame but this just means that we will report the error when we try to generate
          // actual code.
          return false;
        }
      }
      // legacy functions are not OK.
      return false;
    }

    private boolean areAllChildrenConstant(ParentExprNode node) {
      for (ExprNode child : node.getChildren()) {
        if (!visit(child)) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * A visitor that scans an expression to see if it has any subexpression that may require detach
   * operations. Should be kept in sync with {@link CompilerVisitor}.
   */
  private static final class RequiresDetachVisitor
      extends EnhancedAbstractExprNodeVisitor<Boolean> {
    final TemplateAnalysis analysis;

    RequiresDetachVisitor(TemplateAnalysis analysis) {
      this.analysis = analysis;
    }

    @Override
    Boolean visitForLoopVar(VarRefNode varRef, LocalVar local) {
      return !analysis.isResolved(varRef);
    }

    @Override
    protected Boolean visitListComprehensionNode(ListComprehensionNode node) {
      return true;
    }

    @Override
    Boolean visitParam(VarRefNode varRef, TemplateParam param) {
      return !analysis.isResolved(varRef);
    }

    @Override
    Boolean visitLetNodeVar(VarRefNode node, LocalVar local) {
      return !analysis.isResolved(node);
    }

    @Override
    protected Boolean visitMethodCallNode(MethodCallNode node) {
      if (node.getMethodName().toString().equals("bind")) {
        for (Boolean childRequiresDetach : visitChildren(node)) {
          if (childRequiresDetach) {
            return true;
          }
        }
        return false;
      }
      return true;
    }

    @Override
    protected Boolean visitDataAccessNode(DataAccessNode node) {
      if (!analysis.isResolved(node)) {
        return true;
      }
      for (ExprNode child : node.getChildren()) {
        if (visit(child)) {
          return true;
        }
      }
      return false;
    }

    @Override
    protected Boolean visitProtoInitFunction(FunctionNode node) {
      for (Boolean i : visitChildren(node)) {
        if (i) {
          return true;
        }
      }

      // Proto init calls require detach if any of the specified fields are repeated.
      SoyProtoType protoType = (SoyProtoType) node.getType();
      for (Identifier paramName : node.getParamNames()) {
        if (protoType.getFieldDescriptor(paramName.identifier()).isRepeated()) {
          return true;
        }
      }

      return false;
    }

    @Override
    protected Boolean visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        for (Boolean i : visitChildren((ParentExprNode) node)) {
          if (i) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
