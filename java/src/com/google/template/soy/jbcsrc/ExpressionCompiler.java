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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.jbcsrc.ExternCompiler.getTypeInfoForJavaImpl;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.FUNCTION_VALUE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.ITERATOR_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.LIST_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.NULL_POINTER_EXCEPTION_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.asImmutableList;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constantRecordProperty;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.firstSoyNonNullish;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isDefinitelyAssignableFrom;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.newLabel;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.numericConversion;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.asBoxedValueProviderList;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.forBool;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.TypeReference;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.AbstractOperatorNode;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;
import com.google.template.soy.exprtree.ExprNode.CallableExpr;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprNodes;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.GroupNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListComprehensionNode.ComprehensionVarDefn;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralFromListNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.NumberNode;
import com.google.template.soy.exprtree.OperatorNodes.AmpAmpOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AsOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BarBarOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseAndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseOrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseXorOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.InstanceOfOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ShiftLeftOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ShiftRightOpNode;
import com.google.template.soy.exprtree.OperatorNodes.SpreadOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleNotEqualOpNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.UndefinedNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.proto.Int64ConversionMode;
import com.google.template.soy.jbcsrc.restricted.Branch;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef.MethodPureness;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.ClassLoaderFallbackCallFactory;
import com.google.template.soy.jbcsrc.shared.ExtraConstantBootstraps;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.plugin.java.internal.PluginAnalyzer;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.FileMetadata.Extern;
import com.google.template.soy.soytree.FileMetadata.Extern.JavaImpl;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.PartialFileMetadata;
import com.google.template.soy.soytree.PartialFileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.SymbolVar;
import com.google.template.soy.soytree.defn.SymbolVar.SymbolKind;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SetType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

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

  private static final Handle STATIC_FUNCTION_HANDLE =
      MethodRef.createPure(
              ClassLoaderFallbackCallFactory.class,
              "bootstrapFunctionPointerLookup",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              String.class,
              String.class)
          .asHandle();
  private static final String FUNCTION_METHOD_DESCRIPTOR =
      Type.getMethodDescriptor(FUNCTION_VALUE_TYPE, BytecodeUtils.RENDER_CONTEXT_TYPE);

  static final class BasicExpressionCompiler {
    private final CompilerVisitor compilerVisitor;

    private BasicExpressionCompiler(
        SoyNode context,
        TemplateAnalysis analysis,
        TemplateParameterLookup parameters,
        LocalVariableManager varManager,
        JavaSourceFunctionCompiler sourceFunctionCompiler,
        PartialFileSetMetadata fileSetMetadata,
        ExpressionDetacher detacher) {
      this.compilerVisitor =
          new CompilerVisitor(
              context,
              analysis,
              parameters,
              varManager,
              detacher,
              sourceFunctionCompiler,
              fileSetMetadata,
              /* isConstantContext= */ false);
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
      SoyNode context,
      TemplateAnalysis analysis,
      TemplateParameterLookup parameters,
      LocalVariableManager varManager,
      JavaSourceFunctionCompiler sourceFunctionCompiler,
      PartialFileSetMetadata fileSetMetadata) {
    return new ExpressionCompiler(
        context, analysis, parameters, varManager, sourceFunctionCompiler, fileSetMetadata);
  }

  static BasicExpressionCompiler createConstantCompiler(
      SoyNode context,
      TemplateAnalysis analysis,
      LocalVariableManager varManager,
      JavaSourceFunctionCompiler sourceFunctionCompiler,
      PartialFileSetMetadata fileSetMetadata) {
    return new BasicExpressionCompiler(
        new CompilerVisitor(
            context,
            analysis,
            new TemplateParameterLookup() {
              UnsupportedOperationException unsupported() {
                return new UnsupportedOperationException(
                    "This method isn't supported in constant context");
              }

              @Override
              public LocalVariable getStackFrame() {
                throw unsupported();
              }

              @Override
              public Expression getParam(TemplateParam param) {
                throw unsupported();
              }

              @Override
              public Optional<Expression> getParamsRecord() {
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
            ExpressionDetacher.NullDetatcher.INSTANCE,
            sourceFunctionCompiler,
            fileSetMetadata,
            /* isConstantContext= */ true));
  }

  /**
   * Create a basic compiler with trivial detaching logic.
   *
   * <p>All generated detach points are implemented as {@code return} statements and the returned
   * value is boxed, so it is only valid for use by the {@link LazyClosureCompiler}.
   */
  static BasicExpressionCompiler createBasicCompiler(
      SoyNode context,
      TemplateAnalysis analysis,
      TemplateParameterLookup parameters,
      LocalVariableManager varManager,
      JavaSourceFunctionCompiler sourceFunctionCompiler,
      PartialFileSetMetadata fileSetMetadata,
      ExpressionDetacher detacher) {
    return new BasicExpressionCompiler(
        context,
        analysis,
        parameters,
        varManager,
        sourceFunctionCompiler,
        fileSetMetadata,
        detacher);
  }

  /**
   * Returns {@code true} if the value can be compiled to a constant expression in a static
   * initializer.
   */
  static boolean canCompileToConstant(SoyNode context, ExprNode expr) {
    return new CanCompileToConstantVisitor(context).exec(expr);
  }

  private final SoyNode context;
  private final TemplateAnalysis analysis;
  private final TemplateParameterLookup parameters;
  private final LocalVariableManager varManager;
  private final JavaSourceFunctionCompiler sourceFunctionCompiler;
  private final PartialFileSetMetadata fileSetMetadata;

  private ExpressionCompiler(
      SoyNode context,
      TemplateAnalysis analysis,
      TemplateParameterLookup parameters,
      LocalVariableManager varManager,
      JavaSourceFunctionCompiler sourceFunctionCompiler,
      PartialFileSetMetadata fileSetMetadata) {
    this.context = context;
    this.analysis = analysis;
    this.parameters = checkNotNull(parameters);
    this.varManager = checkNotNull(varManager);
    this.sourceFunctionCompiler = checkNotNull(sourceFunctionCompiler);
    this.fileSetMetadata = fileSetMetadata;
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode.
   *
   * <p>If the stack will not be empty when the expression is compiled, this method should be used
   * instead of {@link #compileRootExpression}, and you should configure the {@code detacher} to
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
   * <p>If the stack will not be empty when this method is called, {@link #compileSubExpression}
   * should be used instead so that you can manually configure a detacher that will return to the
   * correct label (where the stack is empty).
   */
  SoyExpression compileRootExpression(ExprNode node, ExpressionDetacher.Factory detacherFactory) {
    Label reattachPoint = newLabel();
    SoyExpression exec =
        compileSubExpression(node, detacherFactory.createExpressionDetacher(reattachPoint));
    return exec.labelStart(reattachPoint);
  }

  static boolean requiresDetach(TemplateAnalysis analysis, ExprNode node) {
    return new RequiresDetachVisitor(analysis).exec(node);
  }

  boolean requiresDetach(ExprNode node) {
    return requiresDetach(analysis, node);
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode if it can be done without
   * generating any detach operations.
   */
  Optional<SoyExpression> compileWithNoDetaches(ExprNode node) {
    checkNotNull(node);
    if (requiresDetach(node)) {
      return Optional.empty();
    }
    return Optional.of(
        new CompilerVisitor(
                context,
                analysis,
                parameters,
                varManager,
                /* detacher= */ null,
                sourceFunctionCompiler,
                fileSetMetadata,
                /* isConstantContext= */ false)
            .exec(node));
  }

  /**
   * Returns a {@link BasicExpressionCompiler} that can be used to compile multiple expressions all
   * with the same detach logic.
   */
  BasicExpressionCompiler asBasicCompiler(ExpressionDetacher detacher) {
    return new BasicExpressionCompiler(
        new CompilerVisitor(
            context,
            analysis,
            parameters,
            varManager,
            detacher,
            sourceFunctionCompiler,
            fileSetMetadata,
            /* isConstantContext= */ false));
  }

  private static final class CompilerVisitor
      extends EnhancedAbstractExprNodeVisitor<SoyExpression> {
    // is null when we are generating code with no detaches.
    @Nullable private final ExpressionDetacher detacher;
    private final SoyNode context;
    private final TemplateAnalysis analysis;
    private final TemplateParameterLookup parameters;
    private final LocalVariableManager varManager;
    private final JavaSourceFunctionCompiler sourceFunctionCompiler;
    private final PartialFileSetMetadata fileSetMetadata;
    private final boolean isConstantContext;

    CompilerVisitor(
        SoyNode context,
        TemplateAnalysis analysis,
        TemplateParameterLookup parameters,
        LocalVariableManager varManager,
        ExpressionDetacher detacher,
        JavaSourceFunctionCompiler sourceFunctionCompiler,
        PartialFileSetMetadata fileSetMetadata,
        boolean isConstantContext) {
      this.context = checkNotNull(context);
      this.analysis = analysis;
      this.detacher = detacher;
      this.parameters = parameters;
      this.varManager = varManager;
      this.sourceFunctionCompiler = checkNotNull(sourceFunctionCompiler);
      this.fileSetMetadata = fileSetMetadata;
      this.isConstantContext = isConstantContext;
    }

    @Override
    protected SoyExpression visit(ExprNode node) {
      return super.visit(node).withSourceLocation(node.getSourceLocation());
    }

    @Override
    protected SoyExpression visitExprRootNode(ExprRootNode node) {
      return visit(node.getRoot());
    }

    // Primitive value constants

    @Override
    protected SoyExpression visitNullNode(NullNode node) {
      return SoyExpression.SOY_NULL;
    }

    @Override
    protected SoyExpression visitUndefinedNode(UndefinedNode node) {
      return SoyExpression.SOY_UNDEFINED;
    }

    @Override
    protected SoyExpression visitNumberNode(NumberNode node) {
      if (node.isInteger()) {
        return SoyExpression.forInt(BytecodeUtils.constant(node.longValue()));
      } else {
        return SoyExpression.forFloat(constant(node.doubleValue()));
      }
    }

    @Override
    protected SoyExpression visitStringNode(StringNode node) {
      return SoyExpression.forString(constant(node.getValue()));
    }

    @Override
    protected SoyExpression visitProtoEnumValueNode(ProtoEnumValueNode node) {
      return SoyExpression.forInt(BytecodeUtils.constant(node.getValue()));
    }

    @Override
    protected SoyExpression visitBooleanNode(BooleanNode node) {
      return node.getValue() ? SoyExpression.TRUE : SoyExpression.FALSE;
    }

    @Override
    protected SoyExpression visitAsOpNode(AsOpNode node) {
      // Casting requires boxing since the JVM can't just cast one primitive to another without
      // coercing (and thus changing the underlying value).
      SoyExpression value = visit(node.getChild(0)).box();
      return SoyExpression.forSoyValue(node.getType(), value.checkedSoyCast(node.getType()));
    }

    @Override
    protected SoyExpression visitInstanceOfOpNode(InstanceOfOpNode node) {
      SoyExpression value = visit(node.getChild(0));
      SoyType operand = node.getChild(1).getType();
      return value.instanceOf(operand);
    }

    // Collection literals

    private static final Handle CONSTANT_LIST_HANDLE =
        MethodRef.createPure(
                ExtraConstantBootstraps.class,
                "constantSoyList",
                MethodHandles.Lookup.class,
                String.class,
                Class.class,
                int.class,
                Object[].class)
            .asHandle();

    @Override
    protected SoyExpression visitListLiteralNode(ListLiteralNode node) {
      if (node.containsSpreads()) {
        // spreads require a different implementation since we need to accumulate into a builder
        var builder = MethodRefs.IMMUTABLE_LIST_BUILDER.invoke();
        for (ExprNode child : node.getChildren()) {
          if (child instanceof SpreadOpNode) {
            builder =
                builder.invoke(
                    MethodRefs.IMMUTABLE_LIST_BUILDER_ADD_ALL_ITERATOR,
                    visit(((SpreadOpNode) child).getChild(0)).unboxAsIteratorUnchecked());
          } else {
            builder = builder.invoke(MethodRefs.IMMUTABLE_LIST_BUILDER_ADD, visit(child).box());
          }
        }
        return SoyExpression.forList(
            (ListType) node.getType(), builder.invoke(MethodRefs.IMMUTABLE_LIST_BUILDER_BUILD));
      }

      var compiledChildren = visitChildren(node);
      var asList = asBoxedValueProviderList(compiledChildren);
      var asListSoyExpression = SoyExpression.forList((ListType) node.getType(), asList);
      // lists show up in defaults and const expressions, special case those
      if (isConstantContext && Expression.areAllConstant(compiledChildren)) {
        Object[] constantArgs = new Object[1 + compiledChildren.size()];
        constantArgs[0] = node.getSourceLocation().hashCode();
        for (int i = 0; i < compiledChildren.size(); i++) {
          constantArgs[i + 1] = compiledChildren.get(i).constantBytecodeValue();
        }
        return asListSoyExpression.withConstantValue(
            Expression.ConstantValue.dynamic(
                new ConstantDynamic(
                    "constantList",
                    BytecodeUtils.IMMUTABLE_LIST_TYPE.getDescriptor(),
                    CONSTANT_LIST_HANDLE,
                    constantArgs),
                BytecodeUtils.IMMUTABLE_LIST_TYPE,
                /* isTrivialConstant= */ false));
      }

      return asListSoyExpression;
    }

    @Override
    protected SoyExpression visitListComprehensionNode(ListComprehensionNode node) {
      // TODO(lukes): consider adding a special case for when the listExpr is a range() function
      // invocation, as we do for regular loops.
      ExprNode listExpr = node.getListExpr();
      SoyExpression soyList = visit(listExpr);
      // Don't care about nullishness since we always dereference the list
      ExprNode mapExpr = node.getListItemTransformExpr();
      ExprNode filterExpr = node.getFilterExpr();

      String varName = node.getListIterVar().name();
      LocalVariableManager.Scope scope = varManager.enterScope();
      LocalVariable iteratorVar = scope.createTemporary(varName + "_iterator", ITERATOR_TYPE);
      Statement iteratorVarInitializer = iteratorVar.initialize(soyList.unboxAsIteratorUnchecked());
      LocalVariable resultVar = scope.createTemporary(varName + "_output_list", LIST_TYPE);
      Statement resultVarInitializer = resultVar.initialize(MethodRefs.ARRAY_LIST.invoke());
      LocalVariable itemVar =
          scope.createNamedLocal(node.getListIterVar().name(), SOY_VALUE_PROVIDER_TYPE);
      Statement itemVarInitializer =
          itemVar.initialize(
              MethodRefs.ITERATOR_NEXT.invoke(iteratorVar).checkedCast(SOY_VALUE_PROVIDER_TYPE));
      LocalVariable userIndexVar =
          node.getIndexVar() == null
              ? null
              : scope.createNamedLocal(node.getIndexVar().name(), Type.INT_TYPE);
      Statement userIndexVarInitializer =
          userIndexVar == null ? null : userIndexVar.initialize(constant(0));
      Expression hasNext = MethodRefs.ITERATOR_HAS_NEXT.invoke(iteratorVar);

      // TODO: Consider compiling to a SoyValueProvider instead of boxing.
      Expression visitedMap = visit(mapExpr).box();

      Branch visitedFilter = filterExpr != null ? visit(filterExpr).compileToBranch() : null;

      var exitScope = scope.exitScopeMarker();

      /*

      Generates byte code for a for loop that looks more or less like:

      Iterator<?> a_iterator = unwrap(...);
      List<?> a_result = new ArrayList<>();
      int i = 0;
      while (a_iterator.hasNext()) {
        Object a = a_iterator.next();
        if (filterPredicate != null && !filterPredicate.test(a,i)) {
          continue;
        }
        a_result.add(mapFunction.apply(a,i));
        i++;
      }
      return a_result;

      */
      return SoyExpression.forList(
          (ListType) node.getType(),
          new Expression(BytecodeUtils.LIST_TYPE, Features.of(Feature.NON_JAVA_NULLABLE)) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              iteratorVarInitializer.gen(adapter); //   Iterator<?> a_iterator = ...;
              resultVarInitializer.gen(adapter); // List<?> a_result = new ArrayList<>();
              if (userIndexVarInitializer != null) {
                userIndexVarInitializer.gen(adapter); //  int i = 0;
              }

              Label loopStart = newLabel();
              Label loopContinue = newLabel();
              Label loopEnd = newLabel();

              adapter.mark(loopStart);

              hasNext.gen(adapter);
              adapter.ifZCmp(Opcodes.IFEQ, loopEnd); // while (a_iterator.hasNext()) {

              itemVarInitializer.gen(adapter); // Object a = a_iterator.next();

              if (visitedFilter != null) {
                visitedFilter
                    .negate()
                    .branchTo(adapter, loopContinue); // if (!filter.test(a,i)) continue;
              }

              resultVar.gen(adapter);
              visitedMap.gen(adapter);
              MethodRefs.ARRAY_LIST_ADD.invokeUnchecked(adapter); // a_result.add(map.apply(a,i));
              adapter.pop(); // pop boolean return value of List.add()

              adapter.mark(loopContinue);

              if (userIndexVar != null) {
                adapter.iinc(userIndexVar.index(), 1); // a_i++
              }
              adapter.goTo(loopStart);

              adapter.mark(loopEnd);

              resultVar.gen(adapter); // "return" a_result;
              // exit the loop
              adapter.mark(exitScope);
            }
          });
    }

    @Override
    protected SoyExpression visitRecordLiteralNode(RecordLiteralNode node) {
      Expression paramStore;
      if (node.numChildren() == 0) {
        return SoyExpression.forSoyValue(node.getType(), FieldRef.EMPTY_RECORD.accessor());
      }
      if (node.containsSpreads()) {
        // The size here is just a guess.
        paramStore = MethodRefs.PARAM_STORE_SIZE.invoke(constant(node.numChildren()));
        for (int i = 0; i < node.numChildren(); i++) {
          var child = node.getChild(i);
          if (child instanceof SpreadOpNode) {
            paramStore =
                paramStore.invoke(
                    MethodRefs.PARAM_STORE_SET_ALL, visit(((SpreadOpNode) child).getChild(0)));
          } else {
            paramStore =
                paramStore.invoke(
                    MethodRefs.PARAM_STORE_SET_FIELD,
                    BytecodeUtils.constantRecordProperty(node.getKey(i).identifier()),
                    visit(child).box());
          }
        }
      } else {
        paramStore = recordLiteralAsParamStore(node);
      }
      SoyExpression record =
          BytecodeUtils.newRecordImplFromParamStore(
              node.getType(), node.getSourceLocation(), paramStore);
      return isConstantContext ? record.toMaybeConstant() : record;
    }

    private Expression recordLiteralAsParamStore(RecordLiteralNode node) {
      Map<String, Expression> recordMap = new LinkedHashMap<>();
      for (int i = 0; i < node.numChildren(); i++) {
        // Keys are RecordProperty objects and values are SoyValue object.
        recordMap.put(node.getKey(i).identifier(), visit(node.getChild(i)));
      }
      return BytecodeUtils.newParamStore(Optional.empty(), recordMap);
    }

    private static final Handle CONSTANT_MAP_HANDLE =
        MethodRef.createPure(
                ExtraConstantBootstraps.class,
                "constantSoyMap",
                MethodHandles.Lookup.class,
                String.class,
                Class.class,
                int.class,
                Object[].class)
            .asHandle();

    @Override
    protected SoyExpression visitMapLiteralNode(MapLiteralNode node) {
      int numItems = node.numChildren() / 2;
      List<Expression> keys = new ArrayList<>(numItems);
      List<Expression> values = new ArrayList<>(numItems);
      for (int i = 0; i < numItems; i++) {
        // Keys and values are boxed SoyValues.
        keys.add(visit(node.getChild(2 * i)).box());
        values.add(visit(node.getChild(2 * i + 1)).box());
      }
      var soyMap =
          SoyExpression.forSoyValue(
              node.getType(),
              MethodRefs.MAP_IMPL_FOR_PROVIDER_MAP_NO_NULL_KEYS.invoke(
                  BytecodeUtils.newImmutableMap(keys, values, /* allowDuplicates= */ true)));
      if (isConstantContext
          && Expression.areAllConstant(keys)
          && Expression.areAllConstant(values)) {
        Object[] constantArgs = new Object[1 + node.numChildren()];
        // We store a hash of the source location so that each distinct list authored gets a unique
        // value to preserve identity semantics even in constant settings
        constantArgs[0] = node.getSourceLocation().hashCode();
        for (int i = 0; i < keys.size(); i++) {
          constantArgs[2 * i + 1] = keys.get(i).constantBytecodeValue();
          constantArgs[2 * i + 2] = values.get(i).constantBytecodeValue();
        }
        soyMap =
            soyMap.withConstantValue(
                Expression.ConstantValue.dynamic(
                    new ConstantDynamic(
                        "constantMap",
                        // needs to exactly match the return type of
                        // MAP_IMPL_FOR_PROVIDER_MAP_NO_NULL_KEYS
                        BytecodeUtils.SOY_MAP_IMPL_TYPE.getDescriptor(),
                        CONSTANT_MAP_HANDLE,
                        constantArgs),
                    BytecodeUtils.SOY_MAP_IMPL_TYPE,
                    /* isTrivialConstant= */ false));
      }
      return soyMap;
    }

    @Override
    protected SoyExpression visitMapLiteralFromListNode(MapLiteralFromListNode node) {
      return SoyExpression.forSoyValue(
          node.getType(),
          MethodRefs.CONSTRUCT_MAP_FROM_ITERATOR.invoke(
              // constructMapFromIterator doesn't support null list params
              visit(node.getListExpr()).unboxAsIteratorUnchecked()));
    }

    // Comparison operators

    @Override
    protected SoyExpression visitEqualOpNode(EqualOpNode node) {
      if (ExprNodes.isNullishLiteral(node.getChild(0))) {
        return BytecodeUtils.isSoyNullish(visit(node.getChild(1)));
      }
      if (ExprNodes.isNullishLiteral(node.getChild(1))) {
        return BytecodeUtils.isSoyNullish(visit(node.getChild(0)));
      }
      return SoyExpression.forBool(
          BytecodeUtils.compareSoyEquals(visit(node.getChild(0)), visit(node.getChild(1))));
    }

    @Override
    protected SoyExpression visitNotEqualOpNode(NotEqualOpNode node) {
      if (ExprNodes.isNullishLiteral(node.getChild(0))) {
        return BytecodeUtils.isNonSoyNullish(visit(node.getChild(1)));
      }
      if (ExprNodes.isNullishLiteral(node.getChild(1))) {
        return BytecodeUtils.isNonSoyNullish(visit(node.getChild(0)));
      }
      return SoyExpression.forBool(
          Branch.ifTrue(
                  BytecodeUtils.compareSoyEquals(visit(node.getChild(0)), visit(node.getChild(1))))
              .negate()
              .asBoolean());
    }

    @Override
    protected SoyExpression visitTripleEqualOpNode(TripleEqualOpNode node) {
      if (node.getChild(0).getKind() == ExprNode.Kind.NULL_NODE) {
        return BytecodeUtils.isSoyNull(visit(node.getChild(1)));
      }
      if (node.getChild(0).getKind() == ExprNode.Kind.UNDEFINED_NODE) {
        return BytecodeUtils.isSoyUndefined(visit(node.getChild(1)));
      }
      if (node.getChild(1).getKind() == ExprNode.Kind.NULL_NODE) {
        return BytecodeUtils.isSoyNull(visit(node.getChild(0)));
      }
      if (node.getChild(1).getKind() == ExprNode.Kind.UNDEFINED_NODE) {
        return BytecodeUtils.isSoyUndefined(visit(node.getChild(0)));
      }
      return SoyExpression.forBool(
          BytecodeUtils.compareSoyTripleEquals(visit(node.getChild(0)), visit(node.getChild(1))));
    }

    @Override
    protected SoyExpression visitTripleNotEqualOpNode(TripleNotEqualOpNode node) {
      if (node.getChild(0).getKind() == ExprNode.Kind.NULL_NODE) {
        return BytecodeUtils.isNonSoyNull(visit(node.getChild(1)));
      }
      if (node.getChild(0).getKind() == ExprNode.Kind.UNDEFINED_NODE) {
        return BytecodeUtils.isNonSoyUndefined(visit(node.getChild(1)));
      }
      if (node.getChild(1).getKind() == ExprNode.Kind.NULL_NODE) {
        return BytecodeUtils.isNonSoyNull(visit(node.getChild(0)));
      }
      if (node.getChild(1).getKind() == ExprNode.Kind.UNDEFINED_NODE) {
        return BytecodeUtils.isNonSoyUndefined(visit(node.getChild(0)));
      }
      return SoyExpression.forBool(
          Branch.ifTrue(
                  BytecodeUtils.compareSoyTripleEquals(
                      visit(node.getChild(0)), visit(node.getChild(1))))
              .negate()
              .asBoolean());
    }

    // binary comparison operators.  N.B. it is ok to coerce 'number' values to floats because that
    // coercion preserves ordering

    @Override
    protected SoyExpression visitLessThanOpNode(LessThanOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.isRuntimeInt() && right.isRuntimeInt()) {
        return SoyExpression.forBool(
            Branch.compare(Opcodes.IFLT, left.unboxAsLong(), right.unboxAsLong()).asBoolean());
      }
      if (left.isRuntimeNumber() && right.isRuntimeNumber()) {
        return SoyExpression.forBool(
            Branch.compare(Opcodes.IFLT, left.coerceToDouble(), right.coerceToDouble())
                .asBoolean());
      }
      if (left.assignableToNullableString() && right.assignableToNullableString()) {
        return createStringComparisonOperator(Opcodes.IFLT, left, right);
      }
      return SoyExpression.forBool(MethodRefs.RUNTIME_LESS_THAN.invoke(left.box(), right.box()));
    }

    @Override
    protected SoyExpression visitGreaterThanOpNode(GreaterThanOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.isRuntimeInt() && right.isRuntimeInt()) {
        return SoyExpression.forBool(
            Branch.compare(Opcodes.IFGT, left.unboxAsLong(), right.unboxAsLong()).asBoolean());
      }
      if (left.isRuntimeNumber() && right.isRuntimeNumber()) {
        return SoyExpression.forBool(
            Branch.compare(Opcodes.IFGT, left.coerceToDouble(), right.coerceToDouble())
                .asBoolean());
      }
      if (left.assignableToNullableString() && right.assignableToNullableString()) {
        return createStringComparisonOperator(Opcodes.IFGT, left, right);
      }
      // Note the argument reversal
      return SoyExpression.forBool(MethodRefs.RUNTIME_LESS_THAN.invoke(right.box(), left.box()));
    }

    @Override
    protected SoyExpression visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.isRuntimeInt() && right.isRuntimeInt()) {
        return SoyExpression.forBool(
            Branch.compare(Opcodes.IFLE, left.unboxAsLong(), right.unboxAsLong()).asBoolean());
      }
      if (left.isRuntimeNumber() && right.isRuntimeNumber()) {
        return SoyExpression.forBool(
            Branch.compare(Opcodes.IFLE, left.coerceToDouble(), right.coerceToDouble())
                .asBoolean());
      }
      if (left.assignableToNullableString() && right.assignableToNullableString()) {
        return createStringComparisonOperator(Opcodes.IFLE, left, right);
      }
      return SoyExpression.forBool(
          MethodRefs.RUNTIME_LESS_THAN_OR_EQUAL.invoke(left.box(), right.box()));
    }

    @Override
    protected SoyExpression visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.isRuntimeInt() && right.isRuntimeInt()) {
        return SoyExpression.forBool(
            Branch.compare(Opcodes.IFGE, left.unboxAsLong(), right.unboxAsLong()).asBoolean());
      }
      if (left.isRuntimeNumber() && right.isRuntimeNumber()) {
        return SoyExpression.forBool(
            Branch.compare(Opcodes.IFGE, left.coerceToDouble(), right.coerceToDouble())
                .asBoolean());
      }
      if (left.assignableToNullableString() && right.assignableToNullableString()) {
        return createStringComparisonOperator(Opcodes.IFGE, left, right);
      }
      // Note the reversal of the arguments.
      return SoyExpression.forBool(
          MethodRefs.RUNTIME_LESS_THAN_OR_EQUAL.invoke(right.box(), left.box()));
    }

    private SoyExpression createStringComparisonOperator(
        int operator, SoyExpression left, SoyExpression right) {
      return SoyExpression.forBool(
          Branch.compare(
                  operator,
                  left.coerceToString()
                      .invoke(MethodRefs.STRING_COMPARE_TO, right.coerceToString()),
                  BytecodeUtils.constant(0))
              .asBoolean());
    }

    // Binary operators
    // For the binary math operators we try to do unboxed arithmetic as much as possible.
    // If both args are definitely ints -> do int math
    // If both args are definitely numbers and at least one is definitely a float -> do float math
    // otherwise use our boxed runtime methods.

    @Override
    protected SoyExpression visitPlusOpNode(PlusOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      // They are both definitely numbers
      if (left.isRuntimeNumber() && right.isRuntimeNumber()) {
        if (left.isRuntimeInt() && right.isRuntimeInt()) {
          return applyBinaryIntOperator(Opcodes.LADD, left, right);
        }
        // if either is definitely a float, then we are definitely coercing so just do it now
        if (left.isRuntimeFloat() || right.isRuntimeFloat()) {
          return applyBinaryFloatOperator(Opcodes.DADD, left, right);
        }
      }
      // '+' is overloaded for string arguments to mean concatenation.
      if (left.isKnownString() || right.isKnownString()) {
        SoyExpression leftString = left.coerceToString();
        SoyExpression rightString = right.coerceToString();
        return SoyExpression.forString(
            leftString.invoke(MethodRefs.STRING_CONCAT, rightString).toMaybeConstant());
      }
      return SoyExpression.forSoyValue(
          SoyTypes.NUMBER_TYPE, MethodRefs.RUNTIME_PLUS.invoke(left.box(), right.box()));
    }

    private SoyExpression visitBinaryOperator(
        AbstractOperatorNode node, int longOpcode, int doubleOpcode, MethodRef runtimeMethod) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      // They are both definitely numbers
      if (left.isRuntimeNumber() && right.isRuntimeNumber()) {
        if (left.isRuntimeInt() && right.isRuntimeInt()) {
          return applyBinaryIntOperator(longOpcode, left, right);
        }
        // if either is definitely a float, then we are definitely coercing so just do it now
        if (left.isRuntimeFloat() || right.isRuntimeFloat()) {
          return applyBinaryFloatOperator(doubleOpcode, left, right);
        }
      }
      return SoyExpression.forSoyValue(
          SoyTypes.NUMBER_TYPE, runtimeMethod.invoke(left.box(), right.box()));
    }

    @Override
    protected SoyExpression visitMinusOpNode(MinusOpNode node) {
      return visitBinaryOperator(node, Opcodes.LSUB, Opcodes.DSUB, MethodRefs.RUNTIME_MINUS);
    }

    @Override
    protected SoyExpression visitTimesOpNode(TimesOpNode node) {
      return visitBinaryOperator(node, Opcodes.LMUL, Opcodes.DMUL, MethodRefs.RUNTIME_TIMES);
    }

    @Override
    protected SoyExpression visitDivideByOpNode(DivideByOpNode node) {
      // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
      // Note that this *will* lose precision for longs.
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.isRuntimeNumber() && right.isRuntimeNumber()) {
        return applyBinaryFloatOperator(Opcodes.DDIV, left, right);
      }
      return SoyExpression.forSoyValue(
          FloatType.getInstance(), MethodRefs.RUNTIME_DIVIDED_BY.invoke(left.box(), right.box()));
    }

    @Override
    protected SoyExpression visitModOpNode(ModOpNode node) {
      return visitBinaryOperator(node, Opcodes.LREM, Opcodes.DREM, MethodRefs.RUNTIME_MOD);
    }

    @Override
    protected SoyExpression visitShiftLeftOpNode(ShiftLeftOpNode node) {
      return applyBitwiseIntOperator(
          node, Opcodes.LSHL, Type.INT_TYPE, MethodRefs.RUNTIME_SHIFT_LEFT);
    }

    @Override
    protected SoyExpression visitShiftRightOpNode(ShiftRightOpNode node) {
      return applyBitwiseIntOperator(
          node, Opcodes.LSHR, Type.INT_TYPE, MethodRefs.RUNTIME_SHIFT_RIGHT);
    }

    @Override
    protected SoyExpression visitBitwiseOrOpNode(BitwiseOrOpNode node) {
      return applyBitwiseIntOperator(
          node, Opcodes.LOR, Type.LONG_TYPE, MethodRefs.RUNTIME_BITWISE_OR);
    }

    @Override
    protected SoyExpression visitBitwiseXorOpNode(BitwiseXorOpNode node) {
      return applyBitwiseIntOperator(
          node, Opcodes.LXOR, Type.LONG_TYPE, MethodRefs.RUNTIME_BITWISE_XOR);
    }

    @Override
    protected SoyExpression visitBitwiseAndOpNode(BitwiseAndOpNode node) {
      return applyBitwiseIntOperator(
          node, Opcodes.LAND, Type.LONG_TYPE, MethodRefs.RUNTIME_BITWISE_AND);
    }

    private SoyExpression applyBitwiseIntOperator(
        AbstractOperatorNode node, int operator, Type rht, MethodRef runtimeMethod) {

      SoyExpression lhe = visit(node.getChild(0));
      SoyExpression rhe = visit(node.getChild(1));

      if (lhe.isRuntimeInt() && rhe.isRuntimeInt()) {
        Expression left = lhe.unboxAsLong();
        // Shift operators require INT on right side.
        Expression right = numericConversion(rhe.unboxAsLong(), rht);
        return SoyExpression.forInt(
            new Expression(Type.LONG_TYPE) {
              @Override
              protected void doGen(CodeBuilder mv) {
                left.gen(mv);
                right.gen(mv);
                mv.visitInsn(operator);
              }
            });
      }

      return SoyExpression.forSoyValue(
          SoyTypes.NUMBER_TYPE, runtimeMethod.invoke(lhe.box(), rhe.box()));
    }

    private static SoyExpression applyBinaryIntOperator(
        int operator, SoyExpression left, SoyExpression right) {
      SoyExpression leftInt = left.unboxAsLong();
      SoyExpression rightInt = right.unboxAsLong();
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
        int operator, SoyExpression left, SoyExpression right) {
      SoyExpression leftFloat = left.coerceToDouble();
      SoyExpression rightFloat = right.coerceToDouble();
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
    protected SoyExpression visitNegativeOpNode(NegativeOpNode node) {
      SoyExpression child = visit(node.getChild(0));
      if (child.isRuntimeInt()) {
        SoyExpression intExpr = child.unboxAsLong();
        return SoyExpression.forInt(
            new Expression(Type.LONG_TYPE, child.features()) {
              @Override
              protected void doGen(CodeBuilder mv) {
                intExpr.gen(mv);
                mv.visitInsn(Opcodes.LNEG);
              }
            });
      }
      if (child.isRuntimeFloat()) {
        SoyExpression floatExpr = child.unboxAsDouble();
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
          SoyTypes.NUMBER_TYPE, MethodRefs.RUNTIME_NEGATIVE.invoke(child.box()));
    }

    @Override
    protected SoyExpression visitSpreadOpNode(SpreadOpNode node) {
      throw new AssertionError("The parent should have handled this");
    }

    // Boolean operators

    @Override
    protected SoyExpression visitNotOpNode(NotOpNode node) {
      // All values are convertible to boolean
      return SoyExpression.forBool(visit(node.getChild(0)).compileToBranch().negate().asBoolean());
    }

    private SoyExpression doSimpleAnd(AbstractOperatorNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      return SoyExpression.forBool(
          Branch.and(left.compileToBranch(), right.compileToBranch()).asBoolean());
    }

    @Override
    protected SoyExpression visitAmpAmpOpNode(AmpAmpOpNode node) {
      if (node.getChild(0).getType().getKind() == SoyType.Kind.BOOL
          && node.getChild(1).getType().getKind() == SoyType.Kind.BOOL) {
        return doSimpleAnd(node);
      }
      return rewriteAsConditional(node);
    }

    private SoyExpression doSimpleOr(AbstractOperatorNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      return SoyExpression.forBool(
          Branch.or(left.compileToBranch(), right.compileToBranch()).asBoolean());
    }

    @Override
    protected SoyExpression visitBarBarOpNode(BarBarOpNode node) {
      if (node.getChild(0).getType().getKind() == SoyType.Kind.BOOL
          && node.getChild(1).getType().getKind() == SoyType.Kind.BOOL) {
        return doSimpleOr(node);
      }
      return rewriteAsConditional(node);
    }

    private SoyExpression rewriteAsConditional(AbstractOperatorNode node) {
      SoyExpression lhsExpr = visit(node.getChild(0)).box();
      SoyExpression rhsExpr = visit(node.getChild(1)).box();

      return SoyExpression.forSoyValue(
          node.getType(),
          new Expression(SoyRuntimeType.getBoxedType(node.getType()).runtimeType()) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              Label trueLabel = newLabel();
              lhsExpr.gen(adapter); // Stack: L
              adapter.dup(); // Stack: L, L
              MethodRefs.SOY_VALUE_COERCE_TO_BOOLEAN.invokeUnchecked(adapter); // Stack: L Z
              adapter.ifZCmp(node instanceof BarBarOpNode ? Opcodes.IFNE : Opcodes.IFEQ, trueLabel);
              adapter.pop(); // Stack:
              rhsExpr.gen(adapter); // Stack: R
              adapter.mark(trueLabel);
            }
          });
    }

    // Null coalescing operator

    @Override
    protected SoyExpression visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      SoyExpression left = visit(node.getLeftChild());
      if (left.isNonSoyNullish()) {
        // This would be for when someone writes '1 ?? 2', we just compile that to '1'
        // This case is insane and should potentially be a compiler error, for now we just assume
        // it is dead code.
        return left;
      }

      // It is extremely common for a user to write '<complex-expression> ?? <primitive-expression>
      // so try to generate code that doesn't involve unconditionally boxing the right hand side.
      SoyExpression right = visit(node.getRightChild());
      if (left.resultType().equals(right.resultType())) {
        SoyExpression result;
        if (left.isBoxed()) {
          result = SoyExpression.forSoyValue(node.getType(), firstSoyNonNullish(left, right));
        } else {
          result = right.withSource(firstSoyNonNullish(left, right));
        }
        if (Expression.areAllCheap(left, right)) {
          result = result.asCheap();
        }
        return result;
      }
      return SoyExpression.forSoyValue(node.getType(), firstSoyNonNullish(left.box(), right.box()));
    }

    // Ternary operator

    @Override
    protected SoyExpression visitConditionalOpNode(ConditionalOpNode node) {
      return processConditionalOp(
          visit(node.getChild(0)).compileToBranch(),
          visit(node.getChild(1)),
          visit(node.getChild(2)),
          node.getType());
    }

    private SoyExpression processConditionalOp(
        Branch condition, SoyExpression trueBranch, SoyExpression falseBranch, SoyType nodeType) {
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
        boolean bothUnboxed = !trueBranch.isBoxed() && !falseBranch.isBoxed();
        // Always specify the runtime type below to allow for the branches to have distinct but
        // related types like ListImpl vs LazyProtoSoyValueList.  This will drop the specialization
        // and just choose SoyList
        Type type =
            (bothUnboxed
                    ? SoyRuntimeType.getUnboxedType(trueBranch.soyType()).get()
                    : SoyRuntimeType.getBoxedType(trueBranch.soyType()))
                .runtimeType();
        if (trueBranch.isBoxed() == falseBranch.isBoxed()) {
          return trueBranch.withSource(condition.ternary(type, trueBranch, falseBranch));
        }
        SoyExpression boxedTrue = trueBranch.box();
        return boxedTrue.withSource(condition.ternary(type, boxedTrue, falseBranch.box()));
      }
      Type boxedRuntimeType = SoyRuntimeType.getBoxedType(nodeType).runtimeType();
      return SoyExpression.forSoyValue(
          nodeType, condition.ternary(boxedRuntimeType, trueBranch.box(), falseBranch.box()));
    }

    @Override
    SoyExpression visitForLoopVar(VarRefNode varRef, LocalVar local) {
      Expression expression = parameters.getLocal(local);
      if (expression.resultType().equals(Type.INT_TYPE)) {
        // The optional index var is an int.
        return SoyExpression.forInt(numericConversion(expression, Type.LONG_TYPE));
      }
      return resolveVarRefNode(varRef, expression);
    }

    // Params

    @Override
    SoyExpression visitParam(VarRefNode varRef, TemplateParam param) {
      Expression expression = parameters.getParam(param);
      return expression instanceof SoyExpression
          ? (SoyExpression) expression
          : resolveVarRefNode(varRef, expression);
    }

    // Let vars

    private static final Handle GET_CONST_HANDLE =
        MethodRef.createPure(
                ClassLoaderFallbackCallFactory.class,
                "bootstrapConstLookup",
                MethodHandles.Lookup.class,
                String.class,
                MethodType.class,
                String.class,
                String.class)
            .asHandle();
    private static final Handle CALL_EXTERN_HANDLE =
        MethodRef.createPure(
                ClassLoaderFallbackCallFactory.class,
                "bootstrapExternCall",
                MethodHandles.Lookup.class,
                String.class,
                MethodType.class,
                String.class,
                String.class)
            .asHandle();

    @Override
    SoyExpression visitSymbolVar(VarRefNode varRef, SymbolVar symbolVar) {
      PartialFileMetadata owningFile =
          fileSetMetadata.getPartialFile(symbolVar.getSourceFilePath());
      String namespace = owningFile.getNamespace();
      TypeInfo typeInfo = TypeInfo.createClass(Names.javaClassNameFromSoyNamespace(namespace));
      Expression renderContext = parameters.getRenderContext();
      if (symbolVar.getSymbolKind() == SymbolKind.CONST) {
        if (owningFile.getSoyFileKind() == SoyFileKind.SRC) {
          // Statically link consts in this compilation unit that don't need RenderContext.
          ConstNode constNode = lookupConstNode(symbolVar);
          if (constNode != null && canCompileToConstant(constNode, constNode.getExpr())) {
            var type = ConstantsCompiler.getConstantRuntimeType(symbolVar.type());
            MethodRef methodRef =
                MethodRef.createStaticMethod(
                    typeInfo,
                    ConstantsCompiler.getConstantMethodWithoutRenderContext(
                        symbolVar.name(), symbolVar.type()),
                    MethodPureness.PURE);
            // Constant fold the value into the callsite, no need to wait on the jit to inline the
            // value
            return SoyExpression.forRuntimeType(type, methodRef.invoke().toConstantExpression());
          }
        }
        Expression constExpression =
            new Expression(
                ConstantsCompiler.getConstantRuntimeType(symbolVar.type()).runtimeType()) {
              @Override
              protected void doGen(CodeBuilder adapter) {
                renderContext.gen(adapter);
                adapter.visitInvokeDynamicInsn(
                    "create",
                    ConstantsCompiler.getConstantMethodWithRenderContext(
                            symbolVar.name(), symbolVar.type())
                        .getDescriptor(),
                    GET_CONST_HANDLE,
                    typeInfo.className(),
                    symbolVar.getSymbol());
              }
            };
        return SoyExpression.forRuntimeType(
            ConstantsCompiler.getConstantRuntimeType(symbolVar.type()), constExpression);
      } else if (symbolVar.getSymbolKind() == SymbolKind.EXTERN) {
        return SoyExpression.forSoyValue(
            varRef.getType(), getFunctionValue(namespace, symbolVar.getSymbol()));
      } else {
        throw new IllegalArgumentException("" + symbolVar.getSymbolKind());
      }
    }

    private ConstNode lookupConstNode(SymbolVar symbol) {
      SoyFileNode file = context.getNearestAncestor(SoyFileNode.class);
      if (file != null && !file.getFilePath().asLogicalPath().equals(symbol.getSourceFilePath())) {
        file =
            file.getParent().getChildren().stream()
                .filter(f -> f.getFilePath().asLogicalPath().equals(symbol.getSourceFilePath()))
                .findFirst()
                .orElse(null);
      }
      if (file != null) {
        return file.getConstants().stream()
            .filter(c -> c.getVar().equals(symbol))
            .findFirst()
            .orElse(null);
      }
      return null;
    }

    private Expression getFunctionValue(String namespace, String symbol) {
      Expression renderContext = parameters.getRenderContext();
      TypeInfo fctOwner = TypeInfo.createClass(Names.javaClassNameFromSoyNamespace(namespace));

      return new Expression(FUNCTION_VALUE_TYPE, Feature.NON_JAVA_NULLABLE.asFeatures()) {
        @Override
        protected void doGen(CodeBuilder adapter) {
          renderContext.gen(adapter);
          adapter.visitInvokeDynamicInsn(
              "function",
              FUNCTION_METHOD_DESCRIPTOR,
              STATIC_FUNCTION_HANDLE,
              fctOwner.className(),
              symbol);
        }
      };
    }

    @Override
    SoyExpression visitLetNodeVar(VarRefNode varRef, LocalVar local) {
      Expression expression = parameters.getLocal(local);
      return expression instanceof SoyExpression
          ? (SoyExpression) expression
          : resolveVarRefNode(varRef, expression);
    }

    @Override
    SoyExpression visitListComprehensionVar(VarRefNode varRef, ComprehensionVarDefn var) {
      // Index vars are always simple ints
      if (var.declaringNode().getIndexVar() == var) {
        return SoyExpression.forInt(numericConversion(parameters.getLocal(var), Type.LONG_TYPE));
      }
      return resolveVarRefNode(varRef, parameters.getLocal(var));
    }

    /**
     * Returns either an expression to detach or a direct SOY_VALUE_PROVIDER_RESOLVE invocation.
     *
     * @param varRef The variable node that we want to generate an expression for.
     * @param unresolvedExpression The expression corresponding to the unresolved variable.
     */
    private SoyExpression resolveVarRefNode(VarRefNode varRef, Expression unresolvedExpression) {
      SoyExpression resolved;
      SoyType type = varRef.getType();
      if (!analysis.isResolved(varRef)) {
        resolved =
            SoyExpression.forSoyValue(
                type, detacher.resolveSoyValueProvider(unresolvedExpression).checkedSoyCast(type));
      } else {
        resolved = SoyExpression.resolveSoyValueProvider(type, unresolvedExpression);
      }
      if (unresolvedExpression.isNonSoyNullish()) {
        resolved = resolved.asNonSoyNullish();
      }
      return resolved;
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
          if (!SoyTypes.isNullOrUndefined(baseExpr.soyType())) {
            baseExpr = baseExpr.asNonJavaNullable().asNonSoyNullish();
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
                    SoyRuntimeType.getBoxedType(node.getType()).runtimeType(),
                    Features.of(Feature.CHEAP)) {
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
          return visitDataAccess((DataAccessNode) node, baseExpr);
        default:
          return visit(node);
      }
    }

    private SoyExpression visitDataAccess(DataAccessNode node, SoyExpression baseExpr) {
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
      return result;
    }

    private SoyExpression visitFieldAccess(SoyExpression baseExpr, FieldAccessNode node) {
      // All null safe accesses should've already been converted to NullSafeAccessNodes.
      checkArgument(!node.isNullSafe());

      SoySourceFunctionMethod sourceMethod = node.getSoyMethod();
      ImmutableList<SoyExpression> args = ImmutableList.of(baseExpr);
      if (sourceMethod != null) {
        Optional<Extern> externApi = ExternAdaptors.asExtern(sourceMethod, args);
        if (externApi.isPresent()) {
          return callExtern(externApi.get(), args);
        }
        return sourceFunctionCompiler.compile(node, sourceMethod, args, parameters, detacher);
      }

      // Otherwise this must be a vanilla SoyRecord.  Box, call getField or getFieldProvider
      // depending on the resolution status.
      Expression fieldAccess;
      Expression baseExprAsRecord = baseExpr.box();
      if (analysis.isResolved(node)) {
        fieldAccess =
            MethodRefs.RUNTIME_GET_FIELD.invoke(
                baseExprAsRecord, constantRecordProperty(node.getFieldName()));
      } else {
        Expression fieldProvider =
            MethodRefs.RUNTIME_GET_FIELD_PROVIDER.invoke(
                baseExprAsRecord, constantRecordProperty(node.getFieldName()));
        fieldAccess = detacher.resolveSoyValueProvider(fieldProvider);
      }
      return SoyExpression.forSoyValue(node.getType(), fieldAccess.checkedSoyCast(node.getType()));
    }

    private SoyExpression visitItemAccess(SoyExpression baseExpr, ItemAccessNode node) {
      // All null safe accesses should've already been converted to NullSafeAccessNodes.
      checkArgument(!node.isNullSafe());
      // KeyExprs never participate in the current null access chain.
      SoyExpression keyExpr = visit(node.getKeyExprChild());

      Expression soyValueProvider;
      // Special case index lookups on lists to avoid boxing the int key.  Maps cannot be
      // optimized the same way because there is no real way to 'unbox' a SoyLegacyObjectMap.
      if (baseExpr.soyRuntimeType().isKnownListOrUnionOfLists()) {
        SoyExpression list = baseExpr.unboxAsListUnchecked();
        SoyExpression index = keyExpr.coerceToIndex();
        if (analysis.isResolved(node)) {
          soyValueProvider = MethodRefs.RUNTIME_GET_LIST_ITEM.invoke(list, index);
        } else {
          soyValueProvider =
              detacher.resolveSoyValueProvider(
                  MethodRefs.RUNTIME_GET_LIST_ITEM_PROVIDER.invoke(list, index));
        }
      } else {
        Expression map = baseExpr.box();
        SoyExpression index = keyExpr.box();
        if (analysis.isResolved(node)) {
          soyValueProvider = MethodRefs.RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM.invoke(map, index);
        } else {
          soyValueProvider =
              detacher.resolveSoyValueProvider(
                  MethodRefs.RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM_PROVIDER.invoke(map, index));
        }
      }
      return SoyExpression.forSoyValue(
          node.getType(), soyValueProvider.checkedSoyCast(node.getType()));
    }

    private Expression getMapGetExpression(
        SoyExpression baseExpr, DataAccessNode node, SoyExpression keyExpr) {
      Expression soyValueProvider;
      Expression map = baseExpr.box();
      SoyExpression index = keyExpr.box();
      if (analysis.isResolved(node)) {
        soyValueProvider = MethodRefs.RUNTIME_GET_MAP_ITEM.invoke(map, index);
      } else {
        soyValueProvider =
            detacher.resolveSoyValueProvider(
                MethodRefs.RUNTIME_GET_MAP_ITEM_PROVIDER.invoke(map, index));
      }
      return soyValueProvider;
    }

    private SoyExpression visitMethodCall(SoyExpression baseExpr, MethodCallNode node) {
      // All null safe accesses should've already been converted to NullSafeAccessNodes.
      checkArgument(!node.isNullSafe());
      checkArgument(node.isMethodResolved());

      // NOTE: It is the responsibility of method implementations to null check receiver types.
      // Doing it here, generically will be a code size and performance regression.
      SoyMethod function = node.getSoyMethod();
      if (function instanceof BuiltinMethod) {
        var builtinMethod = (BuiltinMethod) function;
        switch (builtinMethod) {
          case GET_EXTENSION:
            return ProtoUtils.accessExtensionField(
                baseExpr,
                node,
                BuiltinMethod.getProtoExtensionIdFromMethodCall(node),
                ProtoUtils.SingularFieldAccessMode.DEFAULT_IF_UNSET_UNLESS_MESSAGE_VALUED);
          case GET_READONLY_EXTENSION:
            return ProtoUtils.accessExtensionField(
                baseExpr,
                node,
                BuiltinMethod.getProtoExtensionIdFromMethodCall(node),
                ProtoUtils.SingularFieldAccessMode.DEFAULT_IF_UNSET);
          case HAS_EXTENSION:
            return ProtoUtils.hasExtensionField(
                baseExpr, node, BuiltinMethod.getProtoExtensionIdFromMethodCall(node));
          case HAS_PROTO_FIELD:
            return ProtoUtils.hasserField(
                baseExpr, BuiltinMethod.getProtoFieldNameFromMethodCall(node), varManager);
          case GET_READONLY_PROTO_FIELD:
            return ProtoUtils.accessField(
                baseExpr,
                BuiltinMethod.getProtoFieldNameFromMethodCall(node),
                node.getType(),
                ProtoUtils.SingularFieldAccessMode.DEFAULT_IF_UNSET,
                varManager,
                Int64ConversionMode.FOLLOW_JS_TYPE);
          case GET_PROTO_FIELD:
            return ProtoUtils.accessField(
                baseExpr,
                BuiltinMethod.getProtoFieldNameFromMethodCall(node),
                node.getType(),
                ProtoUtils.SingularFieldAccessMode.DEFAULT_IF_UNSET_UNLESS_MESSAGE_VALUED,
                varManager,
                Int64ConversionMode.FORCE_GBIGINT);
          case GET_PROTO_FIELD_OR_UNDEFINED:
            return ProtoUtils.accessField(
                baseExpr,
                BuiltinMethod.getProtoFieldNameFromMethodCall(node),
                node.getType(),
                ProtoUtils.SingularFieldAccessMode.NULL_IF_UNSET,
                varManager,
                Int64ConversionMode.FORCE_GBIGINT);
          case MAP_GET:
            Expression expr = getMapGetExpression(baseExpr, node, visit(node.getParam(0)));
            return SoyExpression.forSoyValue(node.getType(), expr.checkedSoyCast(node.getType()));
          case FUNCTION_BIND:
            return SoyExpression.forSoyValue(
                node.getType(),
                baseExpr
                    .checkedCast(FUNCTION_VALUE_TYPE)
                    .invoke(
                        MethodRefs.FUNCTION_BIND,
                        adaptFunctionArgs(
                            (FunctionType) node.getBaseExprChild().getType(), node.getParams())));
          case BIND:
            {
              var record = (RecordLiteralNode) node.getChild(1);
              if (record.numChildren() == 0) {
                return baseExpr;
              }
              return SoyExpression.forSoyValue(
                  node.getType(),
                  MethodRefs.RUNTIME_BIND_TEMPLATE_PARAMS.invoke(
                      baseExpr.checkedCast(BytecodeUtils.TEMPLATE_VALUE_TYPE),
                      recordLiteralAsParamStore(record)));
            }
        }
      } else if (function instanceof SoySourceFunctionMethod) {
        SoySourceFunctionMethod sourceMethod = (SoySourceFunctionMethod) function;
        List<SoyExpression> args = new ArrayList<>(node.numParams() + 1);
        args.add(baseExpr);
        node.getParams().forEach(n -> args.add(visit(n)));
        Optional<Extern> externApi = ExternAdaptors.asExtern(sourceMethod, args);
        if (externApi.isPresent()) {
          return callExtern(externApi.get(), args);
        }
        return sourceFunctionCompiler.compile(node, sourceMethod, args, parameters, detacher);
      }
      throw new AssertionError(function.getClass());
    }

    @Override
    protected SoyExpression visitNullSafeAccessNode(NullSafeAccessNode nullSafeAccessNode) {
      // A null safe access {@code $foo?.bar?.baz} is syntactic sugar for {@code $foo == null ?
      // null : $foo.bar == null ? null : $foo.bar.baz)}. So to generate code for it we need to have
      // a way to 'exit' the full access chain as soon as we observe a failed null safety check.
      Label nullSafeExit = newLabel();
      SoyExpression accumulator = visit(nullSafeAccessNode.getBase());
      ExprNode dataAccess = nullSafeAccessNode.getDataAccess();
      while (dataAccess.getKind() == ExprNode.Kind.NULL_SAFE_ACCESS_NODE) {
        NullSafeAccessNode node = (NullSafeAccessNode) dataAccess;
        accumulator =
            accumulateNullSafeDataAccess(
                (DataAccessNode) node.getBase(), accumulator, nullSafeExit);
        dataAccess = node.getDataAccess();
      }
      accumulator =
          accumulateNullSafeDataAccessTail(
              (AccessChainComponentNode) dataAccess, accumulator, nullSafeExit);
      return accumulator.box().labelEnd(nullSafeExit).asSoyUndefinable();
    }

    private static SoyExpression addNullSafetyCheck(SoyExpression baseExpr, Label nullSafeExit) {
      // need to check if baseExpr == null
      return baseExpr
          .withSource(
              new Expression(baseExpr.resultType(), baseExpr.features()) {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  baseExpr.gen(adapter);
                  BytecodeUtils.coalesceSoyNullishToSoyUndefined(
                      adapter, baseExpr.resultType(), nullSafeExit);
                }
              })
          .asNonSoyNullish();
    }

    private SoyExpression accumulateNullSafeDataAccessTail(
        AccessChainComponentNode dataAccessNode, SoyExpression baseExpr, Label nullSafeExit) {
      if (dataAccessNode.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        AssertNonNullOpNode assertNonNull = (AssertNonNullOpNode) dataAccessNode;
        dataAccessNode = (AccessChainComponentNode) assertNonNull.getChild(0);
      }
      return accumulateNullSafeDataAccess((DataAccessNode) dataAccessNode, baseExpr, nullSafeExit);
    }

    private SoyExpression accumulateNullSafeDataAccess(
        DataAccessNode dataAccessNode, SoyExpression baseExpr, Label nullSafeExit) {
      if (!baseExpr.isNonSoyNullish()) {
        baseExpr = addNullSafetyCheck(baseExpr, nullSafeExit);
      }
      return accumulateDataAccess(dataAccessNode, baseExpr);
    }

    private SoyExpression accumulateDataAccess(
        DataAccessNode dataAccessNode, SoyExpression baseExpr) {
      if (dataAccessNode.getBaseExprChild() instanceof DataAccessNode) {
        baseExpr =
            accumulateDataAccess((DataAccessNode) dataAccessNode.getBaseExprChild(), baseExpr)
                // Mark non nullable.
                // Dereferencing for access below may require unboxing and there is no point in
                // adding null safety checks to the unboxing code.  So we just mark non nullable.
                // In other words, if we are going to hit an NPE while dereferencing this
                // expression, it makes no difference if it is due to the unboxing or the actual
                // dereference.
                .asNonSoyNullish();
      }
      return visitDataAccess(dataAccessNode, baseExpr);
    }

    // Builtin functions

    @Override
    protected SoyExpression visitAssertNonNullOpNode(AssertNonNullOpNode node) {
      return visit(Iterables.getOnlyElement(node.getChildren()));
    }

    @Override
    SoyExpression visitCheckNotNullFunction(FunctionNode node) {
      // there is only ever a single child
      ExprNode childNode = Iterables.getOnlyElement(node.getParams());
      SoyExpression expr = visit(childNode);
      if (expr.isNonSoyNullish()) {
        return expr;
      }
      return expr.withSource(
              MethodRefs.CHECK_NOT_NULL
                  .invoke(expr, constant(childNode.toSourceString()))
                  .checkedCast(expr.resultType()))
          .asNonSoyNullish();
    }

    // TODO(lukes):  The RenderVisitor optimizes css/xid renaming by stashing a one element cache in
    // the CSS node itself (keyed off the identity of the renaming map).  We could easily add such
    // an optimization via a static field in the Template class. Though im not sure it makes sense
    // as an optimization... this should just be an immutable map lookup keyed off of a constant
    // string. If we cared a lot, we could employ a simpler (and more compact) optimization by
    // assigning each selector a unique integer id and then instead of hashing we can just reference
    // an array (aka perfect hashing). This could be part of our runtime library and ids could be
    // assigned at startup.

    @Override
    SoyExpression visitCssFunction(FunctionNode node) {
      StringNode selector = (StringNode) Iterables.getLast(node.getParams());
      Expression renamedSelector = parameters.getRenderContext().renameCss(selector.getValue());

      if (node.numParams() == 1) {
        return SoyExpression.forString(renamedSelector);
      } else {
        SoyExpression base = visit(node.getParam(0)).coerceToString();
        Expression fullSelector =
            base.invoke(MethodRefs.STRING_CONCAT, constant("-"))
                .toMaybeConstant()
                .invoke(MethodRefs.STRING_CONCAT, renamedSelector);
        return SoyExpression.forString(fullSelector);
      }
    }

    @Override
    SoyExpression visitToggleFunction(FunctionNode node) {
      String toggleName = ((StringNode) node.getChild(1)).getValue();
      Expression toggleRewrite = parameters.getRenderContext().evalToggle(toggleName);
      return SoyExpression.forBool(toggleRewrite);
    }

    @Override
    SoyExpression visitXidFunction(FunctionNode node) {
      StringNode xid = (StringNode) Iterables.getOnlyElement(node.getParams());
      Expression renamedXid = parameters.getRenderContext().renameXid(xid.getValue());
      return SoyExpression.forString(renamedXid);
    }

    @Override
    SoyExpression visitRecordJsIdFunction(FunctionNode node) {
      Expression recordJsId;
      if ((node.getChild(0).getKind() == ExprNode.Kind.FUNCTION_NODE)
          && ((FunctionNode) node.getChild(0)).getSoyFunction().equals(BuiltinFunction.XID)) {
        recordJsId =
            parameters
                .getRenderContext()
                .renameXidAndRecordJsId(
                    visit(((FunctionNode) node.getChild(0)).getChild(0)).coerceToString());
      } else {
        recordJsId =
            parameters.getRenderContext().recordJsId(visit(node.getChild(0)).coerceToString());
      }
      return SoyExpression.forString(recordJsId);
    }

    @Override
    SoyExpression visitSoyServerKeyFunction(FunctionNode node) {
      ExprNode child = Iterables.getOnlyElement(node.getParams());
      return SoyExpression.forString(MethodRefs.SOY_SERVER_KEY.invoke(visit(child).box()));
    }

    @Override
    SoyExpression visitIsPrimaryMsgInUse(FunctionNode node) {
      return SoyExpression.forBool(
          parameters
              .getRenderContext()
              .usePrimaryMsgIfFallback(
                  Long.parseLong(((StringNode) node.getParam(1)).getValue()),
                  Long.parseLong(((StringNode) node.getParam(2)).getValue())));
    }

    @Override
    SoyExpression visitToNumberFunction(FunctionNode node) {
      SoyExpression arg = visit(node.getParam(0));
      return SoyExpression.forFloat(numericConversion(arg.unboxAsLong(), Type.DOUBLE_TYPE));
    }

    @Override
    SoyExpression visitIntToNumberFunction(FunctionNode node) {
      SoyExpression arg = visit(node.getParam(0));
      return SoyExpression.forSoyValue(node.getType(), MethodRefs.INT_TO_NUMBER.invoke(arg));
    }

    @Override
    SoyExpression visitIsNaNFunction(FunctionNode node) {
      SoyExpression arg = visit(node.getParam(0));
      if (arg.isRuntimeFloat()) {
        return forBool(MethodRefs.IS_NAN.invoke(arg));
      } else if (arg.isBoxed()) {
        return forBool(MethodRefs.IS_NAN_BOXED.invoke(arg));
      } else {
        return forBool(constant(false));
      }
    }

    @Override
    SoyExpression visitDebugSoyTemplateInfoFunction(FunctionNode node) {
      return SoyExpression.forBool(parameters.getRenderContext().getDebugSoyTemplateInfo());
    }

    @Override
    SoyExpression visitVeDataFunction(FunctionNode node) {
      SoyExpression ve = visit(node.getParam(0));
      if (node.numParams() == 1) {
        return SoyExpression.forSoyValue(
            node.getType(), MethodRefs.SOY_VISUAL_ELEMENT_DATA_CREATE_NULL_MESSAGE.invoke(ve));
      }
      Expression data =
          visit(node.getParam(1)).unboxAsMessageOrJavaNull(BytecodeUtils.MESSAGE_TYPE);
      return SoyExpression.forSoyValue(
          node.getType(), MethodRefs.SOY_VISUAL_ELEMENT_DATA_CREATE.invoke(ve, data));
    }

    @Override
    SoyExpression visitEmptyToUndefinedFunction(FunctionNode node) {
      return SoyExpression.forSoyValue(
          node.getType(),
          MethodRefs.RUNTIME_EMPTY_TO_UNDEFINED.invoke(visit(node.getParam(0)).box()));
    }

    @Override
    SoyExpression visitUndefinedToNullFunction(FunctionNode node) {
      return SoyExpression.forSoyValue(
          SoyTypes.undefinedToNull(node.getType()),
          MethodRefs.SOY_VALUE_NULLISH_TO_NULL.invoke(visit(node.getParam(0)).box()));
    }

    @Override
    SoyExpression visitBooleanFunction(FunctionNode node) {
      return SoyExpression.forBool(
          MethodRefs.SOY_VALUE_COERCE_TO_BOOLEAN.invoke(visit(node.getParam(0)).box()));
    }

    @Override
    SoyExpression visitHasContentFunction(FunctionNode node) {
      return SoyExpression.forBool(
          MethodRefs.SOY_VALUE_HAS_CONTENT.invoke(visit(node.getParam(0)).box()));
    }

    @Override
    SoyExpression visitIsTruthyNonEmptyFunction(FunctionNode node) {
      return SoyExpression.forBool(
          MethodRefs.SOY_VALUE_IS_TRUTHY_NON_EMPTY.invoke(visit(node.getParam(0)).box()));
    }

    @Override
    SoyExpression visitNewSetFunction(FunctionNode node) {
      Expression iterator = visit(node.getParam(0)).unboxAsIteratorUnchecked();
      return SoyExpression.forSet(
              (SetType) node.getType(), MethodRefs.IMMUTABLE_SET_COPY_OF.invoke(iterator))
          .asNonJavaNullable();
    }

    // Non-builtin functions

    @Override
    SoyExpression visitPluginFunction(FunctionNode node) {
      Object fn = node.getSoyFunction();
      if (fn instanceof SoyJavaSourceFunction) {
        ImmutableList<SoyExpression> args = visitAll(node.getParams());
        Optional<Extern> externApi =
            ExternAdaptors.asExtern(
                (SoyJavaSourceFunction) fn, args, node.getType(), node.getAllowedParamTypes());
        if (externApi.isPresent()) {
          return callExtern(externApi.get(), args);
        }
        return sourceFunctionCompiler.compile(
            node, (SoyJavaSourceFunction) fn, args, parameters, detacher);
      } else if (fn instanceof Extern) {
        return callExtern((Extern) fn, visitAll(node.getParams()));
      } else if (fn == FunctionNode.FUNCTION_POINTER) {
        FunctionType functionType = (FunctionType) node.getNameExpr().getType();
        SoyRuntimeType soyReturnType = ExternCompiler.getRuntimeType(functionType.getReturnType());
        Expression obj =
            visit(node.getNameExpr())
                .checkedCast(FUNCTION_VALUE_TYPE)
                .invoke(MethodRefs.FUNCTION_WITH_RENDER_CONTEXT, parameters.getRenderContext())
                .invoke(
                    MethodRefs.FUNCTION_CALL, adaptFunctionArgs(functionType, node.getParams()));
        // TODO(b/407056315): This can all be moved into another bootstrap with invokeDynamic, and
        //                    relying on MethodHandle.invokeExact.
        if (BytecodeUtils.isPrimitive(soyReturnType.runtimeType())) {
          obj = BytecodeUtils.unboxJavaPrimitive(soyReturnType.runtimeType(), obj);
        } else {
          obj = obj.checkedCast(soyReturnType.runtimeType());
        }

        return SoyExpression.forRuntimeType(soyReturnType, obj);
      }

      // Functions that are not a SoyJavaSourceFunction
      return SoyExpression.forSoyValue(
          node.getType(),
          parameters
              .getRenderContext()
              .getPluginInstance(node.getStaticFunctionName())
              .checkedCast(SoyJavaFunction.class)
              .invoke(
                  MethodRefs.SOY_JAVA_FUNCTION_COMPUTE_FOR_JAVA,
                  asBoxedValueProviderList(visitChildren(node)))
              // Most soy functions don't have return types, but if they do we should enforce it
              .checkedSoyCast(node.getType()));
    }

    private Expression adaptFunctionArgs(FunctionType type, List<ExprNode> args) {
      List<Expression> adaptedArgs = new ArrayList<>(args.size());
      for (int i = 0; i < args.size(); i++) {
        ExprNode param = args.get(i);
        SoyType paramType = type.getParameters().get(i).getType();
        Expression adapted = adaptExternArg(visit(param), paramType);
        if (BytecodeUtils.isPrimitive(adapted.resultType())) {
          adapted = BytecodeUtils.boxJavaPrimitive(adapted);
        }
        adaptedArgs.add(adapted);
      }
      return asImmutableList(adaptedArgs);
    }

    private ImmutableList<SoyExpression> visitAll(Collection<ExprNode> expr) {
      return expr.stream().map(this::visit).collect(toImmutableList());
    }

    private SoyExpression callExtern(Extern extern, List<SoyExpression> params) {
      SourceLogicalPath path = extern.getPath();
      JavaImpl javaImpl = extern.getJavaImpl();
      boolean hasJavaImpl = javaImpl != null || extern.hasAutoImpl();
      boolean autoJava = javaImpl == null && extern.hasAutoImpl();
      TypeInfo externOwner;
      boolean linkStatically;
      if (path == null) {
        // The SoyJavaExternFunction adaptor returns a null path.
        linkStatically = true;
        externOwner = null;
      } else {
        PartialFileMetadata owningFile = fileSetMetadata.getPartialFile(path);
        String namespace = owningFile.getNamespace();
        externOwner = TypeInfo.createClass(Names.javaClassNameFromSoyNamespace(namespace));
        linkStatically = hasJavaImpl && owningFile.getSoyFileKind() == SoyFileKind.SRC;
      }
      FunctionType functionType = extern.getSignature();
      SoyRuntimeType soyReturnType = ExternCompiler.getRuntimeType(functionType.getReturnType());
      boolean requiresRenderContext = !linkStatically || requiresRenderContext(extern);

      if (extern.isJavaAsync()) {
        soyReturnType = soyReturnType.box();
      }
      List<Expression> args = new ArrayList<>();

      // Dispatch directly for externs defined in this compilation unit.
      if (linkStatically && !autoJava) {
        TypeInfo externClass = TypeInfo.create(javaImpl.className(), javaImpl.type().isInterface());
        // Collect args for calling Java impl directly.
        Stream<TypeReference> javaParams = javaImpl.paramTypes().stream();
        if (javaImpl.instanceFromContext()) {
          args.add(
              parameters
                  .getRenderContext()
                  .getPluginInstance(javaImpl.className())
                  .checkedCast(externClass.type()));
        } else if (!javaImpl.type().isStatic()) {
          javaParams =
              Stream.concat(Stream.of(TypeReference.create(javaImpl.className())), javaParams);
        }
        int soyArgIndex = 0;
        for (Iterator<TypeReference> i = javaParams.iterator(); i.hasNext(); ) {
          TypeReference paramType = i.next();
          if (paramType.className().equals("com.google.template.soy.data.Dir")) {
            args.add(parameters.getRenderContext().getBidiGlobalDirDir());
          } else if (paramType
              .className()
              .equals("com.google.template.soy.plugin.java.RenderCssHelper")) {
            args.add(parameters.getRenderContext().getRenderCssHelper());
          } else if (paramType.className().equals("com.ibm.icu.util.ULocale")) {
            args.add(parameters.getRenderContext().getULocale());
          } else {
            SoyType soyType = functionType.getParameters().get(soyArgIndex).getType();
            SoyExpression soyArg = params.get(soyArgIndex);
            args.add(ExternCompiler.adaptParameter(soyArg, paramType, soyType, parameters));
            soyArgIndex++;
          }
        }
      } else {
        if (requiresRenderContext) {
          args.add(parameters.getRenderContext());
        }
        for (int i = 0; i < params.size(); i++) {
          args.add(adaptExternArg(params.get(i), functionType.getParameters().get(i).getType()));
        }
      }

      Expression externCall;
      if (linkStatically) {
        if (autoJava) {
          Method asmMethod =
              ExternCompiler.buildMemberMethod(
                  extern.getName(), functionType, requiresRenderContext, extern.isJavaAsync());
          MethodRef ref =
              MethodRef.createStaticMethod(externOwner, asmMethod, MethodPureness.NON_PURE);
          externCall = ref.invoke(args);
        } else {
          MethodRef methodRef = ExternCompiler.getMethodRef(javaImpl);
          externCall = methodRef.invoke(args);
          externCall =
              ExternCompiler.adaptReturnType(
                  getTypeInfoForJavaImpl(javaImpl.returnType().className()).type(),
                  functionType.getReturnType(),
                  externCall);
          // Allow ExternSourceFunction to return a boxed value.
          if (isDefinitelyAssignableFrom(SOY_VALUE_TYPE, externCall.resultType())) {
            soyReturnType = soyReturnType.box();
          }
        }
      } else {
        // For externs defined in other files use invokeDynamic so that we can support the
        // classloader fallback.
        // To support those, we always need to pass the render context even when not otherwise
        // required, so that we can dynamically resolve the target.
        Method asmMethod =
            ExternCompiler.buildMemberMethod(
                extern.getName(), functionType, requiresRenderContext, extern.isJavaAsync());
        externCall =
            new Expression(soyReturnType.runtimeType()) {
              @Override
              protected void doGen(CodeBuilder adapter) {
                for (var arg : args) {
                  arg.gen(adapter);
                }
                adapter.visitInvokeDynamicInsn(
                    "call",
                    asmMethod.getDescriptor(),
                    CALL_EXTERN_HANDLE,
                    externOwner.className(),
                    asmMethod.getName());
              }
            };
      }

      if (extern.isJavaAsync()) {
        externCall =
            detacher.resolveSoyValueProvider(externCall).checkedCast(soyReturnType.runtimeType());
      }
      return SoyExpression.forRuntimeType(soyReturnType, externCall);
    }

    private static Expression adaptExternArg(SoyExpression soyExpression, SoyType type) {
      SoyRuntimeType runtimeType = ExternCompiler.getRuntimeType(type);
      Type javaType = runtimeType.runtimeType();

      if (javaType.equals(Type.BOOLEAN_TYPE)) {
        return soyExpression.coerceToBoolean().unboxAsBoolean();
      } else if (javaType.equals(Type.LONG_TYPE)) {
        return soyExpression.unboxAsLong();
      } else if (javaType.equals(BytecodeUtils.STRING_TYPE)) {
        return soyExpression.unboxAsStringOrJavaNull();
      } else if (javaType.equals(Type.DOUBLE_TYPE)) {
        return soyExpression.coerceToDouble().unboxAsDouble();
      } else if (javaType.getSort() == Type.OBJECT) {
        SoyType nonNullableType = SoyTypes.tryRemoveNullish(type);
        if (nonNullableType.getKind() == Kind.ANY || nonNullableType.getKind() == Kind.UNKNOWN) {
          return soyExpression.boxWithSoyNullAsJavaNull();
        } else if (nonNullableType.getKind() == Kind.PROTO) {
          return soyExpression.unboxAsMessageOrJavaNull(
              ProtoUtils.messageRuntimeType(((SoyProtoType) nonNullableType).getDescriptor())
                  .type());
        } else if (nonNullableType.getKind() == Kind.MESSAGE) {
          return soyExpression.unboxAsMessageOrJavaNull(BytecodeUtils.MESSAGE_TYPE);
        } else if (type.getKind() == Kind.PROTO_ENUM) {
          // TODO(b/217186858): support nullable proto enum parameters.
          return soyExpression.unboxAsLong();
        } else if (nonNullableType.getKind() == Kind.LIST) {
          return soyExpression.unboxAsListOrJavaNull();
        } else if (nonNullableType.getKind() == Kind.ITERABLE) {
          return soyExpression.box().checkedCast(BytecodeUtils.SOY_ITERABLE_TYPE);
        } else if (javaType.equals(BytecodeUtils.SOY_VALUE_TYPE)) {
          return soyExpression.box().checkedCast(javaType);
        } else {
          return soyExpression.boxWithSoyNullishAsJavaNull().checkedCast(javaType);
        }
      } else {
        return soyExpression;
      }
    }

    // Proto initialization calls

    @Override
    protected SoyExpression visitProtoInitFunction(FunctionNode node) {
      return ProtoUtils.createProto(node, this::visit, detacher, varManager);
    }

    @Override
    protected SoyExpression visitVeDefNode(FunctionNode node) {
      Expression id = constant(((NumberNode) node.getParam(1)).longValue());
      Expression name = constant(((StringNode) node.getParam(0)).getValue());
      Expression visualElement;
      if (node.numParams() == 4) {
        Expression metadata = visitProtoInitFunction((FunctionNode) node.getParam(3));
        visualElement =
            MethodRefs.SOY_VISUAL_ELEMENT_CREATE_WITH_METADATA.invoke(id, name, metadata);
      } else {
        visualElement = MethodRefs.SOY_VISUAL_ELEMENT_CREATE.invoke(id, name);
      }
      return SoyExpression.forSoyValue(node.getType(), visualElement);
    }

    private static final Handle GET_TEMPLATE_VALUE_HANDLE =
        MethodRef.createPure(
                ClassLoaderFallbackCallFactory.class,
                "bootstrapTemplateValueLookup",
                MethodHandles.Lookup.class,
                String.class,
                MethodType.class,
                String.class)
            .asHandle();

    private static final String TEMPLATE_VALUE_SIGNATURE =
        Type.getMethodDescriptor(
            BytecodeUtils.TEMPLATE_VALUE_TYPE, BytecodeUtils.RENDER_CONTEXT_TYPE);

    @Override
    protected SoyExpression visitTemplateLiteralNode(TemplateLiteralNode node) {
      if (CompiledTemplateMetadata.isPrivateReference(context, node)) {
        Expression templateValue =
            MethodRef.createStaticMethod(
                    TypeInfo.createClass(
                        Names.javaClassNameFromSoyTemplateName(node.getResolvedName())),
                    CompiledTemplateMetadata.createTemplateMethod(
                        Names.renderMethodNameFromSoyTemplateName(node.getResolvedName())),
                    MethodPureness.PURE)
                .asCheap()
                .asNonJavaNullable()
                .invoke();
        return SoyExpression.forSoyValue(
            node.getType(),
            MethodRefs.CREATE_TEMPLATE_VALUE.invoke(
                constant(node.getResolvedName()), templateValue));
      }
      Expression renderContext = parameters.getRenderContext();
      return SoyExpression.forSoyValue(
          node.getType(),
          new Expression(
              BytecodeUtils.TEMPLATE_VALUE_TYPE, Features.of(Feature.NON_JAVA_NULLABLE)) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              renderContext.gen(adapter);
              adapter.visitInvokeDynamicInsn(
                  "create",
                  TEMPLATE_VALUE_SIGNATURE,
                  GET_TEMPLATE_VALUE_HANDLE,
                  node.getResolvedName());
            }
          });
    }

    // Catch-all for unimplemented nodes
    @Override
    protected SoyExpression visitExprNode(ExprNode node) {
      throw new UnsupportedOperationException(
          "Support for " + node.getKind() + " has not been added yet");
    }
  }

  private static final class CanCompileToConstantVisitor
      extends AbstractReturningExprNodeVisitor<Boolean> {
    private final SoyNode context;

    CanCompileToConstantVisitor(SoyNode context) {
      this.context = context;
    }

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
        case STATE:
          // In jbcsrc all @state variables are compiled to constants so we can reference them
          return true;
        case SYMBOL:
          // For consts we could allow references if they are in the same file and they themselves
          // are constants. However, this would require changing the calling convention so that
          // passing a `RenderContext` is optional.
          // For things like proto extensions and constructors we could allow references. But it
          // isn't clear that that is very useful. For cross file `const`s this isn't possible
          return false;
        case PARAM:
        case LOCAL_VAR:
          return false;
      }
      throw new AssertionError(node.getDefnDecl().kind());
    }

    @Override
    protected Boolean visitPrimitiveNode(PrimitiveNode node) {
      // primitives are fine
      return true;
    }

    @Override
    protected Boolean visitTemplateLiteralNode(TemplateLiteralNode node) {
      // This requires a RenderContext object to look up the template unless it is a same file
      // reference to a private template.
      return CompiledTemplateMetadata.isPrivateReference(context, node);
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
    protected Boolean visitMapLiteralFromListNode(MapLiteralFromListNode node) {
      return areAllChildrenConstant(node);
    }

    @Override
    protected Boolean visitMethodCallNode(MethodCallNode node) {
      var method = node.getSoyMethod();
      if (method == BuiltinMethod.BIND) {
        return areAllChildrenConstant(node);
      }
      return false;
    }

    @Override
    protected Boolean visitDataAccessNode(DataAccessNode node) {
      // Most of these should have already been handled by the optimizer, but odd cases persist
      return areAllChildrenConstant(node);
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
      var function = node.getSoyFunction();
      if (function == BuiltinFunction.VE_DEF) {
        // VE_DEF is a special case because we simply don't generate code for the third parameter
        // when present.
        return visit(node.getParam(0))
            && visit(node.getParam(1))
            && (node.numParams() != 4 || visit(node.getParam(3)));
      }
      if (!areAllParamsConstant(node)) {
        return false;
      }
      if (function == BuiltinFunction.PROTO_INIT
          || function == BuiltinFunction.VE_DATA
          || function == BuiltinFunction.CHECK_NOT_NULL
          || function == BuiltinFunction.INT_TO_NUMBER
          || function == BuiltinFunction.TO_NUMBER
          || function == BuiltinFunction.IS_NAN) {
        // All of these are either constructing a data structure or performing some kind of simple
        // coercion.
        return true;
      }
      if (!node.isPure()) {
        return false;
      }
      // We can evaluate the function if
      // all the parameters are constants and we have an implementation that doesn't depend on the
      // render context.
      // TODO(lukes): if the plugin is annotated as @SoyPureFunction, but it accesses the context,
      // then it isn't pure.  add logic in the validator?
      if (function instanceof SoyJavaSourceFunction) {
        try {
          PluginAnalyzer.PluginMetadata metadata =
              PluginAnalyzer.analyze((SoyJavaSourceFunction) function);
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

    @Override
    protected Boolean visitGroupNode(GroupNode node) {
      // We can get here due to null safe access expressions on template literals for bind calls.
      return areAllChildrenConstant(node);
    }

    private boolean areAllParamsConstant(CallableExpr node) {
      for (ExprNode child : node.getParams()) {
        if (!visit(child)) {
          return false;
        }
      }
      return true;
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
    private final TemplateAnalysis analysis;

    RequiresDetachVisitor(TemplateAnalysis analysis) {
      this.analysis = checkNotNull(analysis);
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
    protected Boolean visitMapLiteralFromListNode(MapLiteralFromListNode node) {
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
        return anyChildMatches(node);
      }
      return true;
    }

    @Override
    protected Boolean visitDataAccessNode(DataAccessNode node) {
      if (!analysis.isResolved(node)) {
        return true;
      }
      return anyChildMatches(node);
    }

    @Override
    protected Boolean visitProtoInitFunction(FunctionNode node) {
      if (anyChildMatches(node)) {
        return true;
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
    protected Boolean visitTemplateLiteralNode(TemplateLiteralNode node) {
      return false;
    }

    @Override
    protected Boolean visitPluginFunction(FunctionNode node) {
      Object fn = node.getSoyFunction();
      if (fn instanceof SoyJavaSourceFunction) {
        PluginAnalyzer.PluginMetadata metadata = PluginAnalyzer.analyze((SoyJavaSourceFunction) fn);
        for (MethodSignature methodSignature :
            Iterables.concat(
                metadata.instanceMethodSignatures(), metadata.staticMethodSignatures())) {
          if (Future.class.isAssignableFrom(methodSignature.returnType())) {
            return true;
          }
        }
      } else if (fn instanceof Extern) {
        if (((Extern) fn).isJavaAsync()) {
          return true;
        }
      }
      return node.getParams().stream().anyMatch(this::visit);
    }

    @Override
    protected Boolean visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        return anyChildMatches((ParentExprNode) node);
      }
      return false;
    }

    private boolean anyChildMatches(ParentExprNode node) {
      for (ExprNode child : node.getChildren()) {
        if (visit(child)) {
          return true;
        }
      }
      return false;
    }
  }

  static boolean requiresRenderContext(Extern extern) {
    JavaImpl javaImpl = extern.getJavaImpl();
    if (javaImpl == null) {
      // Will possibly error elsewhere.
      return extern.hasAutoImpl();
    }
    FunctionType type = extern.getSignature();
    if (type.getParameters().stream().anyMatch(p -> p.getType() instanceof FunctionType)) {
      return true;
    }
    return !javaImpl.type().isStatic()
        || javaImpl.paramTypes().stream()
            .anyMatch(t -> JavaImplNode.IMPLICIT_PARAMS.contains(t.className()));
  }
}
