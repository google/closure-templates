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
import static com.google.template.soy.jbcsrc.BytecodeUtils.NULL_POINTER_EXCEPTION_TYPE;
import static com.google.template.soy.jbcsrc.BytecodeUtils.compare;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.BytecodeUtils.firstNonNull;
import static com.google.template.soy.jbcsrc.BytecodeUtils.logicalNot;
import static com.google.template.soy.jbcsrc.BytecodeUtils.ternary;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
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
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.Expression.Feature;
import com.google.template.soy.jbcsrc.Expression.Features;
import com.google.template.soy.jbcsrc.ExpressionDetacher.BasicDetacher;
import com.google.template.soy.soytree.defn.InjectedParam;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.primitive.UnknownType;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles a {@link ExprNode} to a {@link SoyExpression}.
 *
 * <p>A note on how we use soy types.  We generally try to limit the places where we read type
 * information from the AST.  This is because it tends to be not very accurate.  Specifically, the
 * places where we rely on type information from the ast are:
 * <ul>
 *     <li>{@link VarRefNode}
 *     <li>{@link PrimitiveNode}
 *     <li>{@link DataAccessNode}
 * </ul>
 *
 * <p>This is because these are the points that are most likely to be in direct control of the user.
 * All other type information is derived directly from operations on SoyExpression objects.
 */
final class ExpressionCompiler {

  static final class BasicExpressionCompiler {
    private final CompilerVisitor compilerVisitor;

    private BasicExpressionCompiler(VariableLookup variables) {
      this.compilerVisitor =
          new CompilerVisitor(variables, new PluginFunctionCompiler(variables),
              Suppliers.ofInstance(BasicDetacher.INSTANCE));
    }

    private BasicExpressionCompiler(CompilerVisitor visitor) {
      this.compilerVisitor = visitor;
    }

    /**
     * Compile an expression.
     */
    SoyExpression compile(ExprNode expr) {
      return compilerVisitor.exec(expr);
    }

    /**
     * Returns an expression that evaluates to a {@code List<SoyValue>} containing all the children.
     */
    Expression compileToList(List<? extends ExprNode> children) {
      List<SoyExpression> soyExprs = new ArrayList<>(children.size());
      for (ExprNode expr : children) {
        soyExprs.add(compile(expr));
      }
      return SoyExpression.asBoxedList(soyExprs);
    }
  }

  /**
   * Create an expression compiler that can implement complex detaching logic with the given
   * {@link ExpressionDetacher.Factory}
   */
  static ExpressionCompiler create(
      ExpressionDetacher.Factory detacherFactory,
      VariableLookup variables) {
    return new ExpressionCompiler(detacherFactory, variables);
  }

  /**
   * Create a basic compiler with trivial detaching logic.
   *
   * <p>All generated detach points are implemented as {@code return} statements and the returned
   * value is boxed, so it is only valid for use by the {@link LazyClosureCompiler}.
   */
  static BasicExpressionCompiler createBasicCompiler(VariableLookup variables) {
    return new BasicExpressionCompiler(variables);
  }

  private final VariableLookup variables;
  private final ExpressionDetacher.Factory detacherFactory;

  private ExpressionCompiler(
      ExpressionDetacher.Factory detacherFactory,
      VariableLookup variables) {
    this.detacherFactory = detacherFactory;
    this.variables = variables;
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
   * Compiles the given expression tree to a sequence of bytecode if it can be done without
   * generating any detach operations.
   */
  Optional<SoyExpression> compileWithNoDetaches(ExprNode node) {
    checkNotNull(node);
    if (RequiresDetachVisitor.INSTANCE.exec(node)) {
      return Optional.absent();
    }
    Supplier<ExpressionDetacher> throwingSupplier = new Supplier<ExpressionDetacher>() {
      @Override public ExpressionDetacher get() {
        throw new AssertionError();
      }
    };
    return Optional.of(
        new CompilerVisitor(
            variables, new PluginFunctionCompiler(variables), throwingSupplier)
                .exec(node));
  }

  /**
   * Returns a {@link BasicExpressionCompiler} that can be used to compile multiple expressions all
   * with the same detach logic.
   */
  BasicExpressionCompiler asBasicCompiler(final Label reattachPoint) {
    return new BasicExpressionCompiler(
        new CompilerVisitor(variables, new PluginFunctionCompiler(variables),
            // Use a lazy supplier to allocate the expression detacher on demand.  Allocating the
            // detacher eagerly creates detach points so we want to delay until definitely
            // neccesary.
            Suppliers.memoize(new Supplier<ExpressionDetacher>() {
              @Override public ExpressionDetacher get() {
                return detacherFactory.createExpressionDetacher(reattachPoint);
              }
            })));
  }

  /**
   * Compiles the given expression tree to a sequence of bytecode in the current method visitor.
   *
   * <p>The generated bytecode expects that the evaluation stack is empty when this method is
   * called and it will generate code such that the stack contains a single SoyValue when it
   * returns.  The SoyValue object will have a runtime type equal to
   * {@code node.getType().javaType()}.
   */
  SoyExpression compile(ExprNode node) {
    Label reattachPoint = new Label();
    final SoyExpression exec = compile(node, reattachPoint);
    return exec.withSource(exec.labelStart(reattachPoint));
  }

  private static final class CompilerVisitor
      extends EnhancedAbstractExprNodeVisitor<SoyExpression> {
    final Supplier<? extends ExpressionDetacher> detacher;
    final VariableLookup variables;
    final PluginFunctionCompiler functions;

    CompilerVisitor(VariableLookup variables,
        PluginFunctionCompiler functions,
        Supplier<? extends ExpressionDetacher> detacher) {
      this.detacher = detacher;
      this.variables = variables;
      this.functions = functions;
    }

    @Override protected final SoyExpression visitExprRootNode(ExprRootNode node) {
      return visit(node.getRoot());
    }

  // Primitive value constants

    @Override protected final SoyExpression visitNullNode(NullNode node) {
      return SoyExpression.NULL;
    }

    @Override protected final SoyExpression visitFloatNode(FloatNode node) {
      return SoyExpression.forFloat(constant(node.getValue()));
    }

    @Override protected final SoyExpression visitStringNode(StringNode node) {
      return SoyExpression.forString(constant(node.getValue()));
    }

    @Override protected final SoyExpression visitBooleanNode(BooleanNode node) {
      return node.getValue() ? SoyExpression.TRUE : SoyExpression.FALSE;
    }

    @Override protected final SoyExpression visitIntegerNode(IntegerNode node) {
      return SoyExpression.forInt(BytecodeUtils.constant((long) node.getValue()));
    }

  // Collection literals

    @Override protected final SoyExpression visitListLiteralNode(ListLiteralNode node) {
      // TODO(lukes): this should really box the children as SoyValueProviders, we are boxing them
      // anyway and could additionally delay detach generation.  Ditto for MapLiteralNode.
      return SoyExpression.forList((ListType) node.getType(),
          SoyExpression.asBoxedList(visitChildren(node)));
    }

    @Override protected final SoyExpression visitMapLiteralNode(MapLiteralNode node) {
      // map literals are either records (if all the strings are literals) or maps if they aren't
      // constants.
      final int numItems = node.numChildren() / 2;
      if (numItems == 0) {
        return SoyExpression.forSoyValue(node.getType(), FieldRef.EMPTY_DICT.accessor());
      }
      boolean isRecord = node.getType().getKind() == Kind.RECORD;
      List<Expression> keys = new ArrayList<>(numItems);
      List<Expression> values = new ArrayList<>(numItems);
      for (int i = 0; i < numItems; i++) {
        // Keys are strings and values are boxed SoyValues
        // Note: The soy grammar and type system both allow for maps to have arbitrary keys for
        // types but none of the implementations support this.  So we don't support it either.
        // b/20468013
        keys.add(visit(node.getChild(2 * i)).unboxAs(String.class));
        values.add(visit(node.getChild(2 * i + 1)).box());
      }
      Expression soyDict =
          MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invoke(BytecodeUtils.newLinkedHashMap(keys, values));
      if (isRecord) {
        return SoyExpression.forSoyValue(node.getType(), soyDict);
      }
      return SoyExpression.forSoyValue(node.getType(), soyDict);
    }

  // Comparison operators.

    @Override protected final SoyExpression visitEqualOpNode(EqualOpNode node) {
      return SoyExpression.forBool(
          BytecodeUtils.compareSoyEquals(visit(node.getChild(0)), visit(node.getChild(1))));
    }

    @Override protected final SoyExpression visitNotEqualOpNode(NotEqualOpNode node) {
      return SoyExpression.forBool(
          logicalNot(
              BytecodeUtils.compareSoyEquals(visit(node.getChild(0)), visit(node.getChild(1)))));
    }

    // binary comparison operators.  N.B. it is ok to coerce 'number' values to floats because that
    // coercion preserves ordering

    @Override protected final SoyExpression visitLessThanOpNode(LessThanOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFLT, left.unboxAs(long.class), right.unboxAs(long.class)));
      }
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFLT, left.coerceToDouble(), right.coerceToDouble()));
      }
      return SoyExpression.forBool(MethodRef.RUNTIME_LESS_THAN.invoke(left.box(), right.box()));
    }

    @Override protected final SoyExpression visitGreaterThanOpNode(GreaterThanOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFGT, left.unboxAs(long.class), right.unboxAs(long.class)));
      }
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFGT, left.coerceToDouble(), right.coerceToDouble()));
      }
      // Note the argument reversal
      return SoyExpression.forBool(
          MethodRef.RUNTIME_LESS_THAN.invoke(right.box(), left.box()));
    }

    @Override protected final SoyExpression visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFLE, left.unboxAs(long.class), right.unboxAs(long.class)));
      }
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFLE, left.coerceToDouble(), right.coerceToDouble()));
      }
      return SoyExpression.forBool(
          MethodRef.RUNTIME_LESS_THAN_OR_EQUAL.invoke(left.box(), right.box()));
    }

    @Override protected final SoyExpression visitGreaterThanOrEqualOpNode(
        GreaterThanOrEqualOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFGE, left.unboxAs(long.class), right.unboxAs(long.class)));
      }
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        return SoyExpression.forBool(
            compare(Opcodes.IFGE, left.coerceToDouble(), right.coerceToDouble()));
      }
      // Note the reversal of the arguments.
      return SoyExpression.forBool(
          MethodRef.RUNTIME_LESS_THAN_OR_EQUAL.invoke(right.box(), left.box()));
    }

    // Binary operators
    // For the binary math operators we try to do unboxed arithmetic as much as possible.
    // If both args are definitely ints -> do int math
    // If both args are definitely numbers and at least one is definitely a float -> do float math
    // otherwise use our boxed runtime methods.

    @Override protected final SoyExpression visitPlusOpNode(PlusOpNode node) {
      SoyExpression left = visit(node.getChild(0));
      SoyExpression right = visit(node.getChild(1));
      // They are both definitely numbers
      if (left.assignableToNullableNumber() && right.assignableToNullableNumber()) {
        if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
          return applyBinaryIntOperator(Opcodes.LADD, left, right);
        }
        // if either is definitely a float, then we are definitely coercing so just do it now
        if (left.assignableToNullableFloat() || right.assignableToNullableFloat()) {
          return applyBinaryFloatOperator(Opcodes.DADD, left, right);
        }
      }
      // '+' is overloaded for string arguments to mean concatenation.
      if (left.isKnownString() || right.isKnownString()) {
        SoyExpression leftString = left.coerceToString();
        SoyExpression rightString = right.coerceToString();
        return SoyExpression.forString(leftString.invoke(MethodRef.STRING_CONCAT, rightString));
      }
      return SoyExpression.forSoyValue(SoyTypes.NUMBER_TYPE,
          MethodRef.RUNTIME_PLUS.invoke(left.box(), right.box()));
    }

    @Override protected final SoyExpression visitMinusOpNode(MinusOpNode node) {
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
      return SoyExpression.forSoyValue(SoyTypes.NUMBER_TYPE,
          MethodRef.RUNTIME_MINUS.invoke(left.box(), right.box()));
    }

    @Override protected final SoyExpression visitTimesOpNode(TimesOpNode node) {
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
      return SoyExpression.forSoyValue(SoyTypes.NUMBER_TYPE,
          MethodRef.RUNTIME_TIMES.invoke(left.box(), right.box()));
    }

    @Override protected final SoyExpression visitDivideByOpNode(DivideByOpNode node) {
      // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
      // Note that this *will* lose precision for longs.
      return applyBinaryFloatOperator(
          Opcodes.DDIV, visit(node.getChild(0)), visit(node.getChild(1)));
    }

    @Override protected final SoyExpression visitModOpNode(ModOpNode node) {
      // If the underlying expression is not an int, then this will throw a SoyDataExpression at
      // runtime.  This is how the current tofu works.
      // If the expression is known not to be an int, then this will throw an exception at compile
      // time.  This should generally be handled by the type checker. See b/19833234
      return applyBinaryIntOperator(Opcodes.LREM, visit(node.getChild(0)), visit(node.getChild(1)));
    }

    private SoyExpression applyBinaryIntOperator(final int operator, SoyExpression left,
        SoyExpression right) {
      final SoyExpression leftInt = left.unboxAs(long.class);
      final SoyExpression rightInt = right.unboxAs(long.class);
      return SoyExpression.forInt(
          new Expression(Type.LONG_TYPE) {
            @Override void doGen(CodeBuilder mv) {
              leftInt.gen(mv);
              rightInt.gen(mv);
              mv.visitInsn(operator);
            }
          });
    }

    private SoyExpression applyBinaryFloatOperator(final int operator, SoyExpression left,
        SoyExpression right) {
      final SoyExpression leftFloat = left.coerceToDouble();
      final SoyExpression rightFloat = right.coerceToDouble();
      return SoyExpression.forFloat(
          new Expression(Type.DOUBLE_TYPE) {
            @Override void doGen(CodeBuilder mv) {
              leftFloat.gen(mv);
              rightFloat.gen(mv);
              mv.visitInsn(operator);
            }
          });
    }

  // Unary negation

    @Override protected final SoyExpression visitNegativeOpNode(NegativeOpNode node) {
      final SoyExpression child = visit(node.getChild(0));
      if (child.assignableToNullableInt()) {
        final SoyExpression intExpr = child.unboxAs(long.class);
        return SoyExpression.forInt(new Expression(Type.LONG_TYPE, child.features()) {
          @Override void doGen(CodeBuilder mv) {
            intExpr.gen(mv);
            mv.visitInsn(Opcodes.LNEG);
          }
        });
      }
      if (child.assignableToNullableFloat()) {
        final SoyExpression floatExpr = child.unboxAs(double.class);
        return SoyExpression.forFloat(new Expression(Type.DOUBLE_TYPE, child.features()) {
          @Override void doGen(CodeBuilder mv) {
            floatExpr.gen(mv);
            mv.visitInsn(Opcodes.DNEG);
          }
        });
      }
      return SoyExpression.forSoyValue(SoyTypes.NUMBER_TYPE,
          MethodRef.RUNTIME_NEGATIVE.invoke(child.box()));
    }

  // Boolean operators

    @Override protected final SoyExpression visitNotOpNode(NotOpNode node) {
      // All values are convertible to boolean
      return SoyExpression.forBool(logicalNot(visit(node.getChild(0)).coerceToBoolean()));
    }

    @Override protected final SoyExpression visitAndOpNode(AndOpNode node) {
      SoyExpression left = visit(node.getChild(0)).coerceToBoolean();
      SoyExpression right = visit(node.getChild(1)).coerceToBoolean();
      return SoyExpression.forBool(BytecodeUtils.logicalAnd(left, right));
    }

    @Override protected final SoyExpression visitOrOpNode(OrOpNode node) {
      SoyExpression left = visit(node.getChild(0)).coerceToBoolean();
      SoyExpression right = visit(node.getChild(1)).coerceToBoolean();
      return SoyExpression.forBool(BytecodeUtils.logicalOr(left, right));
    }

    @Override protected SoyExpression visitNullCoalescingOpNode(NullCoalescingOpNode node) {
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
      SoyType nonNullLeftType = SoyTypes.removeNull(left.soyType());
      Features features = Features.of();
      if (Expression.areAllCheap(left, right)) {
        features = features.plus(Feature.CHEAP);
      }
      if (right.isNonNullable()) {
        features = features.plus(Feature.NON_NULLABLE);
      }
      if (nonNullLeftType.equals(right.soyType())) {
        if (left.isBoxed() == right.isBoxed()) {
          // no conversions!
          return right.withSource(firstNonNull(left, right));
        }
        if (right.isBoxed()) {
          // right is boxed and left is unboxed so just box the left hand side.
          // TODO(lukes): I cannot trigger this case in a test currently, it may in fact be
          // impossible
          SoyExpression boxedLeft = left.box();
          return boxedLeft.withSource(firstNonNull(boxedLeft, right));
        } else {
          // left is boxed and right is unboxed, try to avoid unboxing the right hand side by
          // attempting an unboxing conversion on the left hand side.  However, we cannot do the
          // type conversion until after the null check... so it is a bit tricky.
          final Label leftIsNull = new Label();
          final Optional<SoyExpression> nullCheckedUnboxedLeft =
              left
                  .withSource(
                      new Expression(left.resultType(), features) {
                        @Override
                        void doGen(CodeBuilder cb) {
                          left.gen(cb);
                          cb.dup();
                          cb.ifNull(leftIsNull);
                        }
                      })
                  .asNonNullable()
                  // TODO(lukes): consider inlining the tryUnbox logic here, this is the only use
                  .tryUnbox();
          if (nullCheckedUnboxedLeft.isPresent()) {
            return right.withSource(
                new Expression(right.resultType(), features) {
                  @Override
                  void doGen(CodeBuilder adapter) {
                    nullCheckedUnboxedLeft.get().gen(adapter);
                    Label end = new Label();
                    adapter.goTo(end);
                    adapter.mark(leftIsNull);
                    adapter.pop(); // pop the extra copy of left off the stack
                    right.gen(adapter);
                    adapter.mark(end);
                  }
                });
          }
        }
      }
      // Now we need to do some boxing conversions.  soy expression boxes null -> null so this is
      // safe (and I assume that the jit can eliminate the resulting redundant branches)
      return SoyExpression.forSoyValue(UnknownType.getInstance(),
          firstNonNull(left.box().cast(SoyValue.class), right.box().cast(SoyValue.class)));
    }

    @Override protected final SoyExpression visitConditionalOpNode(ConditionalOpNode node) {
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
      // 1. expose the 'non-null' prover from ResolveExpressionTypesVisitor, this can in fact be
      //    relied on.  However it is currently mixed in with other parts of the type system which
      //    cannot be trusted
      // 2. compute a least common upper bound for these types. At least that way we would preserve
      //    more type information
      boolean typesEqual = trueBranch.soyType().equals(falseBranch.soyType());
      if (typesEqual) {
        if (trueBranch.isBoxed() == falseBranch.isBoxed()) {
          return trueBranch.withSource(ternary(condition, trueBranch, falseBranch));
        }
        SoyExpression boxedTrue = trueBranch.box();
        return boxedTrue.withSource(ternary(condition, boxedTrue, falseBranch.box()));
      }
      return SoyExpression.forSoyValue(
          UnknownType.getInstance(),
          ternary(
              condition,
              trueBranch.box().cast(SoyValue.class),
              falseBranch.box().cast(SoyValue.class)));
    }

    @Override SoyExpression visitForLoopIndex(VarRefNode varRef, LocalVar local) {
      // an index variable in a {for $index in range(...)} statement
      // These are special because they do not need any attaching/detaching logic and are
      // always unboxed ints
      return SoyExpression.forInt(
          BytecodeUtils.numericConversion(variables.getLocal(local), Type.LONG_TYPE));
    }

    @Override SoyExpression visitForeachLoopVar(VarRefNode varRef, LocalVar local) {
      Expression expression = variables.getLocal(local);
      expression = detacher.get().resolveSoyValueProvider(expression);
      return SoyExpression.forSoyValue(varRef.getType(),
          expression.cast(varRef.getType().javaType()));
    }

    @Override SoyExpression visitParam(VarRefNode varRef, TemplateParam param) {
      // TODO(lukes): It would be nice not to generate a detach for every param access, since
      // after the first successful 'resolve()' we know that all later ones will also resolve
      // successfully. This means that we will generate a potentially large amount of dead
      // branches/states/calls to SoyValueProvider.status(). We could eliminate these by doing
      // some kind of definite assignment analysis to know whether or not a particular varref is
      // _not_ the first one. This would be super awesome and would save bytecode/branches/states
      // and technically be useful for all varrefs. For the time being we do the naive thing and
      // just assume that the jit can handle all the dead branches effectively.
      Expression paramExpr = detacher.get().resolveSoyValueProvider(variables.getParam(param));
      // This inserts a CHECKCAST instruction (aka runtime type checking).  However, it is limited
      // since we do not have good checking for unions (or nullability)
      // TODO(lukes): Where/how should we implement type checking.  For the time being type errors
      // will show up here, and in the unboxing conversions performed during expression
      // manipulation. And, presumably, in NullPointerExceptions.
      return SoyExpression.forSoyValue(varRef.getType(),
          paramExpr.cast(varRef.getType().javaType()));
    }

    @Override SoyExpression visitIjParam(VarRefNode varRef, InjectedParam param) {
      Expression ij = MethodRef.RUNTIME_GET_FIELD_PROVIDER
          .invoke(variables.getIjRecord(), constant(param.name()));
      return SoyExpression.forSoyValue(varRef.getType(),
          detacher.get().resolveSoyValueProvider(ij).cast(varRef.getType().javaType()));
    }

    @Override SoyExpression visitLetNodeVar(VarRefNode varRef, LocalVar local) {
      Expression expression = variables.getLocal(local);
      expression = detacher.get().resolveSoyValueProvider(expression);
      return SoyExpression.forSoyValue(varRef.getType(),
          expression.cast(varRef.getType().javaType()));
    }

    @Override protected SoyExpression visitDataAccessNode(DataAccessNode node) {
      return new NullSafeAccessVisitor().visit(node);
    }

    @Override protected SoyExpression visitFieldAccessNode(FieldAccessNode node) {
      return new NullSafeAccessVisitor().visit(node);
    }

    @Override SoyExpression visitIsFirstFunction(FunctionNode node, SyntheticVarName indexVar) {
      final Expression expr = variables.getLocal(indexVar);

      return SoyExpression.forBool(new Expression(Type.BOOLEAN_TYPE) {
        @Override void doGen(CodeBuilder adapter) {
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

    @Override SoyExpression visitIsLastFunction(
        FunctionNode node, SyntheticVarName indexVar, SyntheticVarName lengthVar) {
      final Expression index = variables.getLocal(indexVar);
      final Expression length = variables.getLocal(lengthVar);
      // basically 'index + 1 == length'
      return SoyExpression.forBool(new Expression(Type.BOOLEAN_TYPE) {
        @Override void doGen(CodeBuilder adapter) {
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

    @Override SoyExpression visitIndexFunction(FunctionNode node, SyntheticVarName indexVar) {
      // '(long) index'
      return SoyExpression.forInt(
          BytecodeUtils.numericConversion(variables.getLocal(indexVar), Type.LONG_TYPE));
    }

    @Override SoyExpression visitCheckNotNullFunction(FunctionNode node) {
      // there is only ever a single child
      final ExprNode childNode = Iterables.getOnlyElement(node.getChildren());
      final SoyExpression childExpr = visit(childNode);
      return childExpr.withSource(
              new Expression(childExpr.resultType(), childExpr.features()) {
                @Override
                void doGen(CodeBuilder adapter) {
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

    // TODO(lukes): For plugins we simply add the Map<String, SoyJavaFunction> map to RenderContext
    // and pull it out of there.  However, it seems like we should be able to turn some of those
    // calls into static method calls (maybe be stashing instances in static fields in our
    // template). We would probably need to introduce a new mechanism for registering functions.
    // Or we should just 'intrinsify' a number of extra function (isNonnull for example)
    @Override SoyExpression visitPluginFunction(FunctionNode node) {
      return functions.callPluginFunction(node, visitChildren(node));
    }

    @Override protected final SoyExpression visitExprNode(ExprNode node) {
      throw new UnsupportedOperationException(
          "Support for " + node.getKind() + " has node been added yet");
    }

    /**
     * A helper for generating code for null safe access expressions.
     *
     * <p>A null safe access {@code $foo?.bar?.baz} is syntactic sugar for
     * {@code $foo == null ? null : ($foo.bar == null ? null : $foo.bar.baz)}.  So to generate code
     * for it we need to have a way to 'exit' the full access chain as soon as we observe a failed
     * null safety check.
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
        final SoyExpression orig = dataAccess;
        return dataAccess
            .withSource(
                new Expression(dataAccess.resultType(), dataAccess.features()) {
                  @Override
                  void doGen(CodeBuilder adapter) {
                    orig.gen(adapter);
                    // At this point either 'orig' will be on the top of stack, or it will be a null
                    // value (if a null safety check failed).
                    adapter.mark(nullSafeExit);
                    // insert a cast operator to enforce type agreement between both branches.
                    adapter.checkCast(this.resultType());
              }

              // TODO(b/20537225):  The type system lies to us in this case.  It says that the
              // result of foo?.bar is non-nullable when in fact it has to be!  Fixing the
              // underlying issue is extremely complicated (and likely involves changing the type
              // system).  For now we workaround by fixing it up after the fact.
                })
            .asNullable();
      }

      SoyExpression addNullSafetyCheck(final SoyExpression baseExpr) {
        // need to check if baseExpr == null
        final Label nullSafeExit = getNullSafeExit();
        return baseExpr
            .withSource(
                new Expression(baseExpr.resultType(), baseExpr.features()) {
                  @Override
                  void doGen(CodeBuilder adapter) {
                    baseExpr.gen(adapter); // S
                    adapter.dup(); // S, S
                    adapter.ifNull(nullSafeExit); // S
                    // Note. When we jump to nullSafeExit there is still an instance of 'orig' on
                    // the stack but we know it is == null.
                  }
                })
            .asNonNullable();
      }

      SoyExpression visitNullSafeNodeRecurse(ExprNode node) {
        switch (node.getKind()) {
          // Note: unlike the other backends we don't support nullsafe injected data (i.e. $ij?.foo)
          // because we generally don't support $ij!
          case FIELD_ACCESS_NODE:
          case ITEM_ACCESS_NODE:
            SoyExpression baseExpr =
                visitNullSafeNodeRecurse(((DataAccessNode) node).getBaseExprChild());
            if (((DataAccessNode) node).isNullSafe()) {
              baseExpr = addNullSafetyCheck(baseExpr);
            }
            if (node.getKind() == ExprNode.Kind.FIELD_ACCESS_NODE) {
              return visitNullSafeFieldAccess(baseExpr, (FieldAccessNode) node);
            } else {
              return visitNullSafeItemAccess(baseExpr, (ItemAccessNode) node);
            }
          default:
            return CompilerVisitor.this.visit(node);
        }
      }

      SoyExpression visitNullSafeFieldAccess(SoyExpression baseExpr, FieldAccessNode node) {
        switch (baseExpr.soyType().getKind()) {
          case OBJECT:
          case UNKNOWN:
          case UNION:
          case RECORD:
            // Always fall back to SoyRecord.  All known object and record types implement this
            // interface.
            Expression fieldProvider = MethodRef.RUNTIME_GET_FIELD_PROVIDER.invoke(
                baseExpr.box().cast(SoyRecord.class),
                constant(node.getFieldName()));
            return SoyExpression.forSoyValue(node.getType(),
                detacher.get()
                    .resolveSoyValueProvider(fieldProvider)
                    .cast(node.getType().javaType()));
          default:
            throw new AssertionError("unexpected field access operation");
        }
      }

      SoyExpression visitNullSafeItemAccess(SoyExpression baseExpr, ItemAccessNode node) {
        // KeyExprs never participate in the current null access chain.
        SoyExpression keyExpr = CompilerVisitor.this.visit(node.getKeyExprChild());

        Expression soyValueProvider;
        // Special case index lookups on lists to avoid boxing the int key.  Maps cannot be
        // optimized the same way because there is no real way to 'unbox' a SoyMap.
        if (baseExpr.isKnownList()) {
          soyValueProvider =
              MethodRef.RUNTIME_GET_LIST_ITEM.invoke(
                  baseExpr.unboxAs(List.class), keyExpr.unboxAs(long.class));
        } else {
          // Box and do a map style lookup.
          soyValueProvider = MethodRef.RUNTIME_GET_MAP_ITEM.invoke(
              baseExpr.box().cast(SoyMap.class),
              keyExpr.box());
        }
        Expression soyValue = detacher.get().resolveSoyValueProvider(soyValueProvider)
            // Just like javac, we insert cast operations when removing from a collection.
            .cast(node.getType().javaType());
        return SoyExpression.forSoyValue(node.getType(), soyValue);
      }
    }
  }

  /**
   * A visitor that scans an expression to see if it has any subexpression that may require detach
   * operations.  Should be kept in sync with {@link CompilerVisitor}.
   */
  private static final class RequiresDetachVisitor extends
      EnhancedAbstractExprNodeVisitor<Boolean> {
    static final RequiresDetachVisitor INSTANCE = new RequiresDetachVisitor();

    @Override Boolean visitForeachLoopVar(VarRefNode varRef, LocalVar local) {
      return true;
    }

    @Override Boolean visitParam(VarRefNode varRef, TemplateParam param) {
      return true;
    }

    @Override Boolean visitLetNodeVar(VarRefNode node, LocalVar local) {
      return true;
    }

    @Override protected Boolean visitDataAccessNode(DataAccessNode node) {
      return true;
    }

    @Override Boolean visitIjParam(VarRefNode node, InjectedParam param) {
      return true;
    }

    @Override protected Boolean visitExprNode(ExprNode node) {
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
