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
import static com.google.template.soy.jbcsrc.SyntheticVarName.foreachLoopIndex;
import static com.google.template.soy.jbcsrc.SyntheticVarName.foreachLoopLength;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.NULL_POINTER_EXCEPTION_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.compare;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.firstNonNull;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.logicalNot;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.ternary;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
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
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.jbcsrc.ExpressionDetacher.BasicDetacher;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.shared.LegacyFunctionAdapter;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.defn.InjectedParam;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
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
        TemplateParameterLookup parameters,
        TemplateVariableManager varManager,
        ErrorReporter reporter,
        SoyTypeRegistry registry) {
      this.compilerVisitor =
          new CompilerVisitor(
              parameters,
              varManager,
              Suppliers.ofInstance(BasicDetacher.INSTANCE),
              reporter,
              registry);
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
      ExpressionDetacher.Factory detacherFactory,
      TemplateParameterLookup parameters,
      TemplateVariableManager varManager,
      ErrorReporter reporter,
      SoyTypeRegistry registry) {
    return new ExpressionCompiler(detacherFactory, parameters, varManager, reporter, registry);
  }

  static BasicExpressionCompiler createConstantCompiler(
      TemplateVariableManager varManager, ErrorReporter reporter, SoyTypeRegistry registry) {
    return new BasicExpressionCompiler(
        new CompilerVisitor(
            /* parameters= */ null,
            varManager,
            Suppliers.ofInstance(ExpressionDetacher.NullDetatcher.INSTANCE),
            reporter,
            registry));
  }

  /**
   * Create a basic compiler with trivial detaching logic.
   *
   * <p>All generated detach points are implemented as {@code return} statements and the returned
   * value is boxed, so it is only valid for use by the {@link LazyClosureCompiler}.
   */
  static BasicExpressionCompiler createBasicCompiler(
      TemplateParameterLookup parameters,
      TemplateVariableManager varManager,
      ErrorReporter reporter,
      SoyTypeRegistry registry) {
    return new BasicExpressionCompiler(parameters, varManager, reporter, registry);
  }

  @Nullable private final TemplateParameterLookup parameters;
  private final TemplateVariableManager varManager;
  private final ExpressionDetacher.Factory detacherFactory;
  private final ErrorReporter reporter;
  private final SoyTypeRegistry registry;

  private ExpressionCompiler(
      ExpressionDetacher.Factory detacherFactory,
      TemplateParameterLookup parameters,
      TemplateVariableManager varManager,
      ErrorReporter reporter,
      SoyTypeRegistry registry) {
    this.detacherFactory = detacherFactory;
    this.parameters = parameters;
    this.varManager = varManager;
    this.reporter = reporter;
    this.registry = registry;
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode.
   *
   * <p>The reattachPoint should be {@link CodeBuilder#mark(Label) marked} by the caller at a
   * location where the stack depth is 0 and will be used to 'reattach' execution if the compiled
   * expression needs to perform a detach operation.
   */
  SoyExpression compile(ExprNode node, Label reattachPoint) {
    return asBasicCompiler(reattachPoint).compile(node);
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode in the current method visitor.
   *
   * <p>The generated bytecode expects that the evaluation stack is empty when this method is called
   * and it will generate code such that the stack contains a single SoyValue when it returns. The
   * SoyValue object will have a runtime type equal to {@code node.getType().javaType()}.
   */
  SoyExpression compile(ExprNode node) {
    Label reattachPoint = new Label();
    final SoyExpression exec = compile(node, reattachPoint);
    return exec.withSource(exec.labelStart(reattachPoint));
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode if it can be done without
   * generating any detach operations.
   */
  Optional<SoyExpression> compileWithNoDetaches(ExprNode node) {
    checkNotNull(node);
    if (RequiresDetachVisitor.INSTANCE.exec(node)) {
      return Optional.absent();
    }
    Supplier<ExpressionDetacher> throwingSupplier =
        () -> {
          throw new AssertionError();
        };
    return Optional.of(
        new CompilerVisitor(parameters, varManager, throwingSupplier, reporter, registry)
            .exec(node));
  }

  /**
   * Returns a {@link BasicExpressionCompiler} that can be used to compile multiple expressions all
   * with the same detach logic.
   */
  BasicExpressionCompiler asBasicCompiler(final Label reattachPoint) {
    return new BasicExpressionCompiler(
        new CompilerVisitor(
            parameters,
            varManager,
            // Use a lazy supplier to allocate the expression detacher on demand.  Allocating the
            // detacher eagerly creates detach points so we want to delay until definitely
            // necessary.
            Suppliers.memoize(() -> detacherFactory.createExpressionDetacher(reattachPoint)),
            reporter,
            registry));
  }

  private static final class CompilerVisitor
      extends EnhancedAbstractExprNodeVisitor<SoyExpression> {
    final Supplier<? extends ExpressionDetacher> detacher;
    final TemplateParameterLookup parameters;
    final TemplateVariableManager varManager;
    final ErrorReporter reporter;
    final SoyTypeRegistry registry;

    CompilerVisitor(
        TemplateParameterLookup parameters,
        TemplateVariableManager varManager,
        Supplier<? extends ExpressionDetacher> detacher,
        ErrorReporter reporter,
        SoyTypeRegistry registry) {
      this.detacher = detacher;
      this.parameters = parameters;
      this.varManager = varManager;
      this.reporter = reporter;
      this.registry = registry;
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
      return SoyExpression.forString(constant(node.getValue(), varManager));
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

    @Override
    final SoyExpression visitStateNode(VarRefNode node, TemplateStateVar state) {
      return parameters.getState(state);
    }

    // Collection literals

    @Override
    protected final SoyExpression visitListLiteralNode(ListLiteralNode node) {
      // TODO(lukes): this should really box the children as SoyValueProviders, we are boxing them
      // anyway and could additionally delay detach generation. Ditto for RecordLiteralNode.
      return SoyExpression.forList(
          (ListType) node.getType(), SoyExpression.asBoxedList(visitChildren(node)));
    }

    @Override
    protected final SoyExpression visitRecordLiteralNode(RecordLiteralNode node) {
      final int numItems = node.numChildren();
      if (numItems == 0) {
        return SoyExpression.forSoyValue(node.getType(), FieldRef.EMPTY_DICT.accessor());
      }
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
      // If the underlying expression is not an int, then this will throw a SoyDataExpression at
      // runtime.  This is how the current tofu works.
      // If the expression is known not to be an int, then this will throw an exception at compile
      // time.  This should generally be handled by the type checker. See b/19833234
      return applyBinaryIntOperator(Opcodes.LREM, visit(node.getChild(0)), visit(node.getChild(1)));
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
      } else {
        // otherwise it must be a SoyValueProvider, resolve and cast
        expression = detacher.get().resolveSoyValueProvider(expression);
        return SoyExpression.forSoyValue(
            varRef.getType(),
            expression.checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
      }
    }

    // Params

    @Override
    SoyExpression visitParam(VarRefNode varRef, TemplateParam param) {
      // TODO(lukes): It would be nice not to generate a detach for every param access, since
      // after the first successful 'resolve()' we know that all later ones will also resolve
      // successfully. This means that we will generate a potentially large amount of dead
      // branches/states/calls to SoyValueProvider.status(). We could eliminate these by doing
      // some kind of definite assignment analysis to know whether or not a particular varref is
      // _not_ the first one. This would be super awesome and would save bytecode/branches/states
      // and technically be useful for all varrefs. For the time being we do the naive thing and
      // just assume that the jit can handle all the dead branches effectively.
      Expression paramExpr = detacher.get().resolveSoyValueProvider(parameters.getParam(param));
      // This inserts a CHECKCAST instruction (aka runtime type checking).  However, it is limited
      // since we do not have good checking for unions (or nullability)
      // TODO(lukes): Where/how should we implement type checking.  For the time being type errors
      // will show up here, and in the unboxing conversions performed during expression
      // manipulation. And, presumably, in NullPointerExceptions.
      return SoyExpression.forSoyValue(
          varRef.getType(),
          paramExpr.checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
    }

    @Override
    SoyExpression visitIjParam(VarRefNode varRef, InjectedParam param) {
      Expression ij =
          MethodRef.RUNTIME_GET_FIELD_PROVIDER.invoke(
              parameters.getIjRecord(), constant(param.name()));
      return SoyExpression.forSoyValue(
          varRef.getType(),
          detacher
              .get()
              .resolveSoyValueProvider(ij)
              .checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
    }

    // Let vars

    @Override
    SoyExpression visitLetNodeVar(VarRefNode varRef, LocalVar local) {
      Expression expression = parameters.getLocal(local);
      expression = detacher.get().resolveSoyValueProvider(expression);
      return SoyExpression.forSoyValue(
          varRef.getType(),
          expression.checkedCast(SoyRuntimeType.getBoxedType(varRef.getType()).runtimeType()));
    }

    // Data access

    @Override
    protected SoyExpression visitDataAccessNode(DataAccessNode node) {
      return new NullSafeAccessVisitor().visit(node);
    }

    // Field access

    @Override
    protected SoyExpression visitFieldAccessNode(FieldAccessNode node) {
      return new NullSafeAccessVisitor().visit(node);
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
    SoyExpression visitCheckNotNullFunction(FunctionNode node) {
      // there is only ever a single child
      final ExprNode childNode = Iterables.getOnlyElement(node.getChildren());
      final SoyExpression childExpr = visit(childNode);
      return childExpr
          .withSource(
              new Expression(childExpr.resultType(), childExpr.features()) {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  childExpr.gen(adapter);
                  adapter.dup();
                  Label end = new Label();
                  adapter.ifNonNull(end);
                  adapter.throwException(
                      NULL_POINTER_EXCEPTION_TYPE,
                      "'" + childNode.toSourceString() + "' evaluates to null");
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
    SoyExpression visitIsPrimaryMsgInUse(FunctionNode node) {
      return SoyExpression.forBool(
          parameters
              .getRenderContext()
              .usePrimaryMsg(
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
        return new JbcSrcValueFactory(
                node,
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
                      public Expression getULocale() {
                        return error();
                      }
                    }
                    : parameters.getPluginContext(),
                new JbcSrcValueFactory.PluginInstanceLookup() {
                  @Override
                  public Expression getPluginInstance(String pluginName) {
                    if (parameters == null) {
                      throw new UnsupportedOperationException(
                          "Pure functions cannot have instances");
                    }
                    return parameters.getRenderContext().getPluginInstance(pluginName);
                  }
                },
                reporter,
                registry)
            .computeForJavaSource(args);
      }

      // Functions that are not a SoyJbcSrcFunction nor a SoyJavaSourceFunction
      // are registered with a LegacyFunctionAdapter by SoySauceImpl.
      Expression legacyFunctionRuntimeExpr =
          parameters
              .getRenderContext()
              .getPluginInstance(node.getFunctionName())
              .checkedCast(LegacyFunctionAdapter.class);
      Expression list = SoyExpression.asBoxedList(args);
      // Most soy functions don't have return types, but if they do we should enforce it
      return SoyExpression.forSoyValue(
          node.getType(),
          MethodRef.RUNTIME_CALL_LEGACY_FUNCTION
              .invoke(legacyFunctionRuntimeExpr, list)
              .checkedCast(SoyRuntimeType.getBoxedType(node.getType()).runtimeType()));
    }

    // Proto initialization calls

    @Override
    protected final SoyExpression visitProtoInitNode(ProtoInitNode node) {
      return ProtoUtils.createProto(node, this::visit, detacher, varManager);
    }

    @Override
    protected SoyExpression visitVeLiteralNode(VeLiteralNode node) {
      return SoyExpression.forSoyValue(
          node.getType(),
          MethodRef.SOY_VISUAL_ELEMENT_CREATE.invoke(
              constant(node.getId()), constant(node.getName().identifier())));
    }

    // Catch-all for unimplemented nodes

    @Override
    protected final SoyExpression visitExprNode(ExprNode node) {
      throw new UnsupportedOperationException(
          "Support for " + node.getKind() + " has not been added yet");
    }

    /**
     * A helper for generating code for null safe access expressions.
     *
     * <p>A null safe access {@code $foo?.bar?.baz} is syntactic sugar for {@code $foo == null ?
     * null : ($foo.bar == null ? null : $foo.bar.baz)}. So to generate code for it we need to have
     * a way to 'exit' the full access chain as soon as we observe a failed null safety check.
     */
    private final class NullSafeAccessVisitor {
      Label nullSafeExit;

      Label getNullSafeExit() {
        Label local = nullSafeExit;
        return local == null ? nullSafeExit = new Label() : local;
      }

      SoyExpression visit(DataAccessNode node) {
        SoyExpression dataAccess = visitNullSafeNodeRecurse(node);
        if (nullSafeExit == null) {
          return dataAccess;
        }
        if (BytecodeUtils.isPrimitive(dataAccess.resultType())) {
          // proto accessors will return primitives, so in order to allow it to be compatible with
          // a nullable expression we need to box.
          dataAccess = dataAccess.box();
        }
        return dataAccess.asNullable().labelEnd(nullSafeExit);
      }

      SoyExpression addNullSafetyCheck(final SoyExpression baseExpr) {
        // need to check if baseExpr == null
        final Label nullSafeExit = getNullSafeExit();
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

      SoyExpression visitNullSafeNodeRecurse(ExprNode node) {
        switch (node.getKind()) {
          case FIELD_ACCESS_NODE:
          case ITEM_ACCESS_NODE:
            SoyExpression baseExpr =
                visitNullSafeNodeRecurse(((DataAccessNode) node).getBaseExprChild());
            if (((DataAccessNode) node).isNullSafe()) {
              baseExpr = addNullSafetyCheck(baseExpr);
            } else {
              // Mark non nullable.
              // Dereferencing for access below may require unboxing and there is no point in
              // adding null safety checks to the unboxing code.  So we just mark non nullable. In
              // otherwords, if we are going to hit an NPE while dereferencing this expression, it
              // makes no difference if it is due to the unboxing or the actual dereference.
              baseExpr = baseExpr.asNonNullable();
            }
            if (node.getKind() == ExprNode.Kind.FIELD_ACCESS_NODE) {
              return visitNullSafeFieldAccess(baseExpr, (FieldAccessNode) node)
                  .withSourceLocation(node.getSourceLocation());
            } else {
              return visitNullSafeItemAccess(baseExpr, (ItemAccessNode) node)
                  .withSourceLocation(node.getSourceLocation());
            }
          default:
            return CompilerVisitor.this.visit(node);
        }
      }

      SoyExpression visitNullSafeFieldAccess(SoyExpression baseExpr, FieldAccessNode node) {
        if (baseExpr.soyRuntimeType().isKnownProtoOrUnionOfProtos()) {
          if (baseExpr.soyType().getKind() == Kind.PROTO) {
            // It is a single known proto field.  Generate code to call the getter directly
            SoyProtoType protoType = (SoyProtoType) baseExpr.soyType();
            return ProtoUtils.accessField(protoType, baseExpr, node);
          } else {
            // this must be some kind of union of protos which has support via the boxing apis
            return SoyExpression.forSoyValue(
                node.getType(),
                MethodRef.RUNTIME_GET_PROTO_FIELD
                    .invoke(
                        baseExpr.box().checkedCast(SoyProtoValue.class),
                        BytecodeUtils.constant(node.getFieldName()))
                    .checkedCast(SoyRuntimeType.getBoxedType(node.getType()).runtimeType()));
          }
        }
        // Otherwise this must be a vanilla SoyRecord.  Box, call getFieldProvider and resolve the
        // provider
        Expression fieldProvider =
            MethodRef.RUNTIME_GET_FIELD_PROVIDER.invoke(
                baseExpr.box().checkedCast(SoyRecord.class), constant(node.getFieldName()));
        return SoyExpression.forSoyValue(
            node.getType(),
            detacher
                .get()
                .resolveSoyValueProvider(fieldProvider)
                .checkedCast(SoyRuntimeType.getBoxedType(node.getType()).runtimeType()));
      }

      SoyExpression visitNullSafeItemAccess(SoyExpression baseExpr, ItemAccessNode node) {
        // KeyExprs never participate in the current null access chain.
        SoyExpression keyExpr = CompilerVisitor.this.visit(node.getKeyExprChild());

        Expression soyValueProvider;
        // Special case index lookups on lists to avoid boxing the int key.  Maps cannot be
        // optimized the same way because there is no real way to 'unbox' a SoyMap.
        if (baseExpr.soyRuntimeType().isKnownListOrUnionOfLists()) {
          soyValueProvider =
              MethodRef.RUNTIME_GET_LIST_ITEM.invoke(baseExpr.unboxAsList(), keyExpr.unboxAsLong());
        } else if (baseExpr.soyRuntimeType().isKnownMapOrUnionOfMaps()) {
          // Box and do a map style lookup.
          soyValueProvider =
              MethodRef.RUNTIME_GET_MAP_ITEM.invoke(
                  baseExpr.box().checkedCast(SoyMap.class), keyExpr.box());
        } else {
          // Box and do a map style lookup.
          soyValueProvider =
              MethodRef.RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM.invoke(
                  baseExpr.box().checkedCast(SoyLegacyObjectMap.class), keyExpr.box());
        }
        Expression soyValue =
            detacher
                .get()
                .resolveSoyValueProvider(soyValueProvider)
                // Just like javac, we insert cast operations when removing from a collection.
                .checkedCast(SoyRuntimeType.getBoxedType(node.getType()).runtimeType());
        return SoyExpression.forSoyValue(node.getType(), soyValue);
      }
    }
  }

  /**
   * A visitor that scans an expression to see if it has any subexpression that may require detach
   * operations. Should be kept in sync with {@link CompilerVisitor}.
   */
  private static final class RequiresDetachVisitor
      extends EnhancedAbstractExprNodeVisitor<Boolean> {
    static final RequiresDetachVisitor INSTANCE = new RequiresDetachVisitor();

    @Override
    Boolean visitForLoopVar(VarRefNode varRef, LocalVar local) {
      return true;
    }

    @Override
    Boolean visitParam(VarRefNode varRef, TemplateParam param) {
      return true;
    }

    @Override
    Boolean visitLetNodeVar(VarRefNode node, LocalVar local) {
      return true;
    }

    @Override
    protected Boolean visitDataAccessNode(DataAccessNode node) {
      return true;
    }

    @Override
    Boolean visitIjParam(VarRefNode node, InjectedParam param) {
      return true;
    }

    @Override
    Boolean visitStateNode(VarRefNode node, TemplateStateVar state) {
      return false;
    }

    @Override
    protected Boolean visitProtoInitNode(ProtoInitNode node) {
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
