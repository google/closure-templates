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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isDefinitelyAssignableFrom;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.FormatMethod;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyIterable;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoySet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.restricted.GbigintData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import javax.annotation.Nullable;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/**
 * An expression has a {@link #resultType()} and can {@link #gen generate} code to evaluate the
 * expression.
 *
 * <p>Expressions should:
 *
 * <ul>
 *   <li>have no side effects
 *   <li>be idempotent (you can compose them multiple times and get sensible results)
 *   <li>produce <em>exactly 1</em> <i>value</i> onto the runtime stack
 *   <li>not <em>consume</em> stack items
 * </ul>
 *
 * <p>These rules make it easier to compose and reason about the effect of composing expressions. In
 * particular it makes it easier to maintain the stack height and type invariants of the JVM.
 *
 * <p>Due to these constraints there are some natural consequences, a few examples include:
 *
 * <ul>
 *   <li>An expression should never branch to a label outside of the same expression. (Note: {@code
 *       return} and {@code throw} are special cases that are allowed)
 * </ul>
 */
public abstract class Expression extends BytecodeProducer {

  /**
   * Represents a value that can be compiled to a JVM constant.
   *
   * <p>This abstraction is useful for 2 usecases:
   *
   * <ol>
   *   <li>The construction of 'composite' constants via `ConstantDynamic` for these we need all
   *       bootstrap method arguments to also be encoded as JVM constants and this abstraction
   *       allows us to track them.
   *   <li>Compile time constant folding of primitive operations. By tracking 'java values' we can
   *       constant fold certain operations more flexibly than otherwise especially when those
   *       values are related to jvm specific encodings. A standard example is 'numeric
   *       conversions', these are about adapting jvm types and as such the Soy compilers
   *       OptimizationPass is not useful.
   * </ol>
   */
  public static final class ConstantValue {
    private static void checkJavaValueType(Object javaValue, Type type) {
      switch (type.getSort()) {
        case Type.OBJECT:
          // null works for all object type
          if (javaValue == null) {
            break;
          }
          checkType(javaValue, String.class);
          break;
        case Type.BOOLEAN:
          checkType(javaValue, Boolean.class);
          break;
        case Type.INT:
          checkType(javaValue, Integer.class);
          break;
        case Type.CHAR:
          checkType(javaValue, Character.class);
          break;
        case Type.LONG:
          checkType(javaValue, Long.class);
          break;
        case Type.DOUBLE:
          checkType(javaValue, Double.class);
          break;
        case Type.BYTE:
        case Type.SHORT:
        case Type.FLOAT:
        case Type.VOID:
        case Type.ARRAY:
        case Type.METHOD:
          throw new IllegalArgumentException("Invalid constant type: " + javaValue);
        default:
          throw new AssertionError("unexpected constant type " + javaValue);
      }
    }

    public static ConstantValue raw(Object javaValue, Type type) {
      checkJavaValueType(javaValue, type);
      return new ConstantValue(
          Optional.of(javaValue), javaValue, type, /* isTrivialConstant= */ true);
    }

    public static ConstantValue raw(
        @Nullable Object javaValue,
        ConstantDynamic byteCodeValue,
        Type type,
        boolean isTrivialConstant) {
      checkState(type.getDescriptor().equals(byteCodeValue.getDescriptor()));
      checkJavaValueType(javaValue, type);

      return new ConstantValue(
          javaValue == null ? Optional.of(NULL_MARKER) : Optional.of(javaValue),
          byteCodeValue,
          type,
          isTrivialConstant);
    }

    public static ConstantValue dynamic(
        ConstantDynamic byteCodeValue, Type type, boolean isTrivialConstant) {
      checkState(type.getDescriptor().equals(byteCodeValue.getDescriptor()));
      return new ConstantValue(Optional.empty(), byteCodeValue, type, isTrivialConstant);
    }

    private static final Object NULL_MARKER = new Object();
    private final Optional<Object> javaValue;
    private final Object byteCodeValue;
    private final Type type;
    private final boolean isTrivialConstant;

    private ConstantValue(
        Optional<Object> javaValue, Object byteCodeValue, Type type, boolean isTrivialConstant) {
      // The relationship between these 3 values is enforced by our callers
      this.javaValue = javaValue;
      this.byteCodeValue = checkNotNull(byteCodeValue);
      this.type = type;
      this.isTrivialConstant = isTrivialConstant;
      checkArgument(!(byteCodeValue instanceof ConstantValue));
    }

    /**
     * Returns whether or not this is a trivial constant. Trivial constants are useful for passing
     * as arguments to other more complex constant expressions but are not themselves usefully
     * encoded as a 'constant'
     *
     * <p>For example, the constant `true` for a boolean, we just shouldn't bother. Similarly for
     * static final field references. a `getstatic` instruction is just as good as an LDC.
     */
    public boolean isTrivialConstant() {
      return isTrivialConstant;
    }

    /**
     * Returns whether or not this constant has a Java value. Not all constants do, for example we
     * might compile a proto literal to a constant but not be able to represent it in the compiler
     * itself because the proto gencode isn't on our classpath.
     */
    public boolean hasJavaValue() {
      return javaValue.isPresent();
    }

    /**
     * Returns the java value that is the same as what the content would evaluate to.
     *
     * @throws IllegalStateException if {@link #hasJavaValue} would return `false`
     */
    @Nullable
    public Object getJavaValue() {
      Object rawValue = javaValue.get();
      return rawValue == NULL_MARKER ? null : rawValue;
    }

    /**
     * Returns the Java value as the given type or absent if it doesn't match the type (or we don't
     * have a raw value).
     */
    public <T> Optional<T> getJavaValueAsType(Class<T> type) {
      return javaValue.map(value -> type.isInstance(value) ? type.cast(value) : null);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("javaValue", hasJavaValue() ? getJavaValue() : "N/A")
          .add("byteCodeValue", byteCodeValue)
          .toString();
    }
  }

  /**
   * q Expression features track additional metadata for expressions.
   *
   * <p>Features should be defined such that not setting a feature on an expression is a safe
   * default. That way if they get accidentally dropped in a transformation we simply generate less
   * efficient code, not incorrect code.
   */
  public enum Feature {
    /**
     * The expression is guaranteed to not return Java null. This is not necessarily the same as
     * Soy's null and undefined values.
     */
    NON_JAVA_NULLABLE,
    /** The expression is guaranteed to not return NullData or UndefinedData. */
    NON_SOY_NULLISH,
    /**
     * The expression is 'cheap'. As a rule of thumb, if it involves allocation, it is not cheap. If
     * you need to allocate a local variable to calculate the expression, it is not cheap.
     *
     * <p>Cheapness is useful when deciding if it would be reasonable to evaluate an expression more
     * than once if the alternative is generating additional fields and save/restore code.
     */
    CHEAP;

    // TODO(lukes): an idempotent feature would be useful some expressions are not safe to gen more
    // than once.

    public Features asFeatures() {
      return Features.of(this);
    }
  }

  /** An immutable wrapper of an EnumSet of {@link Feature}. */
  public static final class Features {
    private static final Features EMPTY = new Features(EnumSet.noneOf(Feature.class));

    public static Features of() {
      return EMPTY;
    }

    public static Features of(Feature first, Feature... rest) {
      EnumSet<Feature> set = EnumSet.of(first);
      Collections.addAll(set, rest);
      return new Features(set);
    }

    private static Features forType(Type expressionType, Features features) {
      switch (expressionType.getSort()) {
        case Type.OBJECT:
        case Type.ARRAY:
          break;
        case Type.BOOLEAN:
        case Type.BYTE:
        case Type.CHAR:
        case Type.DOUBLE:
        case Type.INT:
        case Type.SHORT:
        case Type.LONG:
        case Type.FLOAT:
          // primitives are never null
          features = features.plus(Feature.NON_JAVA_NULLABLE);
          break;
        case Type.VOID:
        case Type.METHOD:
          throw new IllegalArgumentException("Invalid type: " + expressionType);
        default:
          throw new AssertionError("unexpected type " + expressionType);
      }

      // Save some calculations.
      if (features.has(Feature.NON_SOY_NULLISH) || !features.has(Feature.NON_JAVA_NULLABLE)) {
        return features;
      }

      if (isDefinitelyAssignableFrom(BytecodeUtils.NULLISH_DATA_TYPE, expressionType)
          || expressionType.equals(BytecodeUtils.SOY_VALUE_TYPE)
          || expressionType.equals(BytecodeUtils.SOY_VALUE_PROVIDER_TYPE)) {
        return features;
      }

      boolean isGenerated = Names.isGenerated(expressionType);
      if ((BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.SOY_VALUE_TYPE, expressionType)
          || !isGenerated)) {
        // Boxed types like StringData are rare but are NON_SOY_NULLISH.
        // Unboxed types like String, List, etc NON_SOY_NULLISH, since they are
        // NON_JAVA_NULLABLE.
        features = features.plus(Feature.NON_SOY_NULLISH);
      }

      return features;
    }

    private final EnumSet<Feature> set;

    private Features(EnumSet<Feature> set) {
      this.set = checkNotNull(set);
    }

    public boolean has(Feature feature) {
      return set.contains(feature);
    }

    public Features plus(Feature feature) {
      if (set.contains(feature)) {
        return this;
      }
      EnumSet<Feature> newSet = copyFeatures();
      newSet.add(feature);
      return new Features(newSet);
    }

    public Features minus(Feature feature) {
      if (!set.contains(feature)) {
        return this;
      }
      EnumSet<Feature> newSet = copyFeatures();
      newSet.remove(feature);
      return new Features(newSet);
    }

    public Features intersect(Features other) {
      EnumSet<Feature> newSet = EnumSet.noneOf(Feature.class);
      newSet.addAll(set);
      newSet.retainAll(other.set);
      return new Features(newSet);
    }

    private EnumSet<Feature> copyFeatures() {
      // Can't use EnumSet.copyOf() because it throws on empty collections!
      EnumSet<Feature> newSet = EnumSet.noneOf(Feature.class);
      newSet.addAll(set);
      return newSet;
    }
  }

  /** Returns true if all referenced expressions are {@linkplain #isCheap() cheap}. */
  public static boolean areAllCheap(Iterable<? extends Expression> args) {
    return Iterables.all(args, Expression::isCheap);
  }

  /** Returns true if all referenced expressions are {@linkplain #isCheap() cheap}. */
  public static boolean areAllCheap(Expression first, Expression... rest) {
    return areAllCheap(ImmutableList.<Expression>builder().add(first).add(rest).build());
  }

  public static boolean areAllConstant(Iterable<? extends Expression> args) {
    return Iterables.all(args, Expression::isConstant);
  }

  /** Checks that the given expressions are compatible with the given types. */
  static void checkTypes(ImmutableList<Type> types, Iterable<? extends Expression> exprs) {
    int size = Iterables.size(exprs);
    checkArgument(
        size == types.size(),
        "Supplied the wrong number of parameters. Expected %s, got %s",
        types,
        exprs);
    // checkIsAssignableTo is an no-op if DEBUG is false
    if (Flags.DEBUG) {
      int i = 0;
      for (Expression expr : exprs) {
        expr.checkAssignableTo(types.get(i), "Parameter %s", i);
        i++;
      }
    }
  }

  private final Features features;
  private final Type resultType;
  private final Optional<ConstantValue> constantValue;

  protected Expression(Type resultType) {
    this(resultType, Features.of());
  }

  protected Expression(Type resultType, Features features) {
    this(resultType, features, SourceLocation.UNKNOWN);
  }

  protected Expression(Type resultType, ConstantValue constantValue, Features features) {
    this(resultType, features, SourceLocation.UNKNOWN, Optional.of(constantValue));
  }

  protected Expression(Type resultType, Features features, SourceLocation location) {
    this(resultType, features, location, Optional.empty());
  }

  protected Expression(
      Type resultType,
      Features features,
      SourceLocation location,
      Optional<ConstantValue> constantValue) {
    super(location);
    this.resultType = checkNotNull(resultType);
    this.features = Features.forType(resultType, features);
    this.constantValue = checkNotNull(constantValue);
    if (Flags.DEBUG && constantValue.isPresent()) {
      checkState(
          BytecodeUtils.isPossiblyAssignableFrom(resultType, constantValue.get().type),
          "Type mismatch. Expected constant value of type %s to to be assignable to %s",
          constantValue.get().type,
          resultType);
    }
  }

  private static void checkType(Object value, Class<?> type) {
    if (!type.isInstance(value)) {
      throw new IllegalStateException(
          "expected " + value + " a " + value.getClass() + " to be a " + type);
    }
  }

  /**
   * Generate code to evaluate the expression.
   *
   * <p>The generated code satisfies the invariant that the top of the runtime stack will contain a
   * value with this {@link #resultType()} immediately after evaluation of the code.
   */
  @Override
  protected abstract void doGen(CodeBuilder adapter);

  /** Returns an identical {@link Expression} with the given source location. */
  public Expression withSourceLocation(SourceLocation location) {
    checkNotNull(location);
    if (location.equals(this.location)) {
      return this;
    }
    return new DelegatingExpression(this, location);
  }

  /**
   * If this expression has a constant form, return an expression that evaluates it via an `ldc`
   * instruction unless it is so trivial as to not be worth it.
   */
  public Expression toMaybeConstant() {
    if (isConstant() && !constantValue.get().isTrivialConstant()) {
      return toConstantExpression();
    }
    return this;
  }

  /**
   * Returns this expression encoded as a constantdynamic expression. Throw an error if {@link
   * #isConstant} is false.
   */
  public Expression toConstantExpression() {
    var actualConstantValue = constantValue.get().byteCodeValue;
    return new Expression(resultType, features.plus(Feature.CHEAP), location, constantValue) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.visitLdcInsn(actualConstantValue);
      }
    };
  }

  /** The type of the expression. */
  public final Type resultType() {
    return resultType;
  }

  /** Whether or not this expression is {@link Feature#CHEAP cheap}. */
  public boolean isCheap() {
    return features.has(Feature.CHEAP);
  }

  /** Whether or not this expression is {@link Feature#NON_JAVA_NULLABLE non nullable}. */
  public boolean isNonJavaNullable() {
    return features.has(Feature.NON_JAVA_NULLABLE);
  }

  public boolean isNonSoyNullish() {
    return features.has(Feature.NON_SOY_NULLISH);
  }

  /**
   * Returns all the feature bits. Typically, users will want to invoke one of the convenience
   * accessors {@link #isCheap()} or {@link #isNonJavaNullable()}.
   */
  public Features features() {
    return features;
  }

  /** Check that this expression is assignable to {@code expected}. */
  public final void checkAssignableTo(Type expected) {
    checkAssignableTo(expected, "");
  }

  /** Check that this expression is assignable to {@code expected}. */
  @FormatMethod
  public final void checkAssignableTo(Type expected, String fmt, Object... args) {
    if (Flags.DEBUG && !BytecodeUtils.isPossiblyAssignableFrom(expected, resultType())) {
      String message =
          String.format(
              "Type mismatch. Got %s but expected %s. %b",
              resultType().getClassName(), expected.getClassName(), this instanceof SoyExpression);
      if (!fmt.isEmpty()) {
        message = String.format(fmt, args) + ". " + message;
      }

      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Convert this expression to a statement, by executing it and throwing away the result.
   *
   * <p>This is useful for invoking non-void methods when we don't care about the result.
   */
  public Statement toStatement() {
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
        switch (resultType().getSize()) {
          case 0:
            throw new AssertionError("void expressions are not allowed");
          case 1:
            adapter.pop();
            break;
          case 2:
            adapter.pop2();
            break;
          default:
            throw new AssertionError();
        }
      }
    };
  }

  /** Returns an equivalent expression where {@link #isCheap()} returns {@code true}. */
  public Expression asCheap() {
    if (isCheap()) {
      return this;
    }
    return withNewFeatures(features.plus(Feature.CHEAP));
  }

  /** Returns an equivalent expression where {@link #isNonJavaNullable()} returns {@code true}. */
  public Expression asNonJavaNullable() {
    if (isNonJavaNullable()) {
      return this;
    }
    return withNewFeatures(features.plus(Feature.NON_JAVA_NULLABLE));
  }

  public Expression asJavaNullable() {
    if (!isNonJavaNullable()) {
      return this;
    }
    return withNewFeatures(features.minus(Feature.NON_JAVA_NULLABLE));
  }

  public Expression asNonSoyNullish() {
    if (isNonSoyNullish()) {
      return this;
    }
    return withNewFeatures(features.plus(Feature.NON_SOY_NULLISH));
  }

  public Expression asSoyNullish() {
    if (!isNonSoyNullish()) {
      return this;
    }
    checkAssignableTo(BytecodeUtils.SOY_VALUE_TYPE);
    return new DelegatingExpression(
        // The only type that is a super type of a NullData,UndefinedData and any other SoyValue is
        // SoyValue itself.
        this, BytecodeUtils.SOY_VALUE_TYPE, features.minus(Feature.NON_SOY_NULLISH));
  }

  protected Expression withNewFeatures(Features features) {
    return new DelegatingExpression(this, features);
  }

  /**
   * Returns an expression that performs a checked cast from the current type to the target type.
   *
   * @throws IllegalArgumentException if either type is not a reference type.
   */
  public Expression checkedCast(Type target) {
    return maybeCheckedCast(target).orElse(this);
  }

  /**
   * Returns an expression that performs a checked cast from the current type to the target type.
   *
   * @throws IllegalArgumentException if either type is not a reference type.
   */
  public Expression checkedCast(Class<?> target) {
    return checkedCast(Type.getType(target));
  }

  private Optional<Expression> maybeCheckedCast(Type target) {
    checkArgument(
        target.getSort() == Type.OBJECT,
        "cast targets must be reference types. (%s)",
        target.getClassName());
    checkArgument(
        resultType().getSort() == Type.OBJECT,
        "you may only cast from reference types. (%s)",
        resultType().getClassName());
    if (BytecodeUtils.isDefinitelyAssignableFrom(target, resultType())) {
      return Optional.empty();
    }
    return Optional.of(
        new Expression(target, features()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            Expression.this.gen(adapter);
            adapter.checkCast(resultType());
          }
        });
  }

  /**
   * A simple helper that calls through to {@link MethodRef#invoke(Expression...)}, but allows a
   * more natural fluent call style.
   */
  public Expression invoke(MethodRef method, Expression... args) {
    return method.invoke(ImmutableList.<Expression>builder().add(this).add(args).build());
  }

  /**
   * A simple helper that calls through to {@link MethodRef#invokeVoid(Expression...)}, but allows a
   * more natural fluent call style.
   */
  public Statement invokeVoid(MethodRef method, Expression... args) {
    return method.invokeVoid(ImmutableList.<Expression>builder().add(this).add(args).build());
  }

  /**
   * Returns a new expression identical to this one but with the given label applied at the start of
   * the expression.
   */
  public Expression labelStart(Label label) {
    return new Expression(resultType(), features, SourceLocation.UNKNOWN, constantValue) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(label);
        Expression.this.gen(adapter);
      }
    };
  }

  /**
   * Returns a new expression identical to this one but with the given label applied at the end of
   * the expression.
   */
  public Expression labelEnd(Label label) {
    return new Expression(resultType(), features, SourceLocation.UNKNOWN, constantValue) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
        adapter.mark(label);
      }
    };
  }

  /** Returns true if this expression has a constant value. */
  public boolean isConstant() {
    return constantValue.isPresent();
  }

  public ConstantValue constantValue() {
    return constantValue.get();
  }

  public Object constantBytecodeValue() {
    return constantValue.get().byteCodeValue;
  }

  /** Returns an instanceof of this expression with an attached constant form. */
  public Expression withConstantValue(ConstantValue constantValue) {
    return new DelegatingExpression(this, constantValue);
  }

  /**
   * An expression representing a checked cast operation.
   *
   * <p>Allows it to be folded away in order to avoid redundant checks.
   */
  public static final class SoyCastExpression extends Expression {
    private final Expression original;
    private final Expression checked;

    SoyCastExpression(Expression original, Expression checked) {
      super(checked.resultType(), checked.features, checked.location, checked.constantValue);
      this.original = original;
      this.checked = checked;
    }

    @Override
    protected void doGen(CodeBuilder adapter) {
      checked.gen(adapter);
    }

    public Expression getOriginal() {
      return original;
    }

    @Override
    public Expression asSoyNullish() {
      return new SoyCastExpression(original.asSoyNullish(), checked.asSoyNullish());
    }

    @Override
    public SoyCastExpression withConstantValue(ConstantValue constantValue) {
      return new SoyCastExpression(
          original.withConstantValue(constantValue), checked.withConstantValue(constantValue));
    }

    @Override
    protected SoyCastExpression withNewFeatures(Features features) {
      return new SoyCastExpression(
          original.withNewFeatures(features), checked.withNewFeatures(features));
    }

    @Override
    public SoyCastExpression withSourceLocation(SourceLocation location) {
      return new SoyCastExpression(
          original.withSourceLocation(location), checked.withSourceLocation(location));
    }

    @Override
    public SoyCastExpression labelStart(Label label) {
      return new SoyCastExpression(original.labelStart(label), checked.labelStart(label));
    }

    @Override
    public SoyCastExpression labelEnd(Label label) {
      return new SoyCastExpression(original.labelEnd(label), checked.labelEnd(label));
    }
  }

  /**
   * Inserts a runtime type check that this expression matches {@code type}. These checks are
   * typically inserted to validate user-supplied values are the expected type and fail early. The
   * resulting checks typically must call a method rather than a simple bytecode instruction since
   * NULL and UNDEFINED values must pass any type check but are represented as subclasses of {@link
   * SoyValue}.
   */
  public Expression checkedSoyCast(SoyType type) {
    return doCheckedSoyCast(type)
        .<Expression>map(checked -> new SoyCastExpression(this, checked))
        .orElse(this);
  }

  private Optional<Expression> doCheckedSoyCast(SoyType type) {
    type = SoyTypes.tryRemoveNullish(type);
    if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.SOY_VALUE_TYPE, resultType)) {
      if (isDefinitelyAssignableFrom(BytecodeUtils.NULLISH_DATA_TYPE, resultType)) {
        return Optional.empty();
      }
      Class<? extends SoyValue> expectedClass = null;
      switch (type.getKind()) {
        case ANY:
        case UNKNOWN:
        case VE:
        case VE_DATA:
          return Optional.empty();
        case INTERSECTION:
        case NAMED:
        case INDEXED:
          return doCheckedSoyCast(type.getEffectiveType());
        case UNION:
          if (type.equals(SoyTypes.NUMBER_TYPE)) {
            if (BytecodeUtils.isDefinitelyAssignableFrom(
                BytecodeUtils.NUMBER_DATA_TYPE, resultType)) {
              return Optional.empty();
            }
            return Optional.of(MethodRefs.CHECK_NUMBER.invoke(this));
          }
          return Optional.empty();
        case NULL:
          return this.maybeCheckedCast(BytecodeUtils.NULL_DATA_TYPE);
        case UNDEFINED:
          return this.maybeCheckedCast(BytecodeUtils.UNDEFINED_DATA_TYPE);
        case ATTRIBUTES:
          return Optional.of(
              MethodRefs.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.ATTRIBUTES)));
        case CSS:
          return Optional.of(MethodRefs.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.CSS)));
        case BOOL:
          if (BytecodeUtils.isDefinitelyAssignableFrom(
              BytecodeUtils.BOOLEAN_DATA_TYPE, resultType)) {
            return Optional.empty();
          }
          return Optional.of(MethodRefs.CHECK_BOOLEAN.invoke(this));
        case FLOAT:
          if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.FLOAT_DATA_TYPE, resultType)) {
            return Optional.empty();
          }
          return Optional.of(MethodRefs.CHECK_FLOAT.invoke(this));
        case HTML:
        case ELEMENT:
          return Optional.of(
              MethodRefs.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.HTML)));
        case INT:
          if (BytecodeUtils.isDefinitelyAssignableFrom(
              BytecodeUtils.INTEGER_DATA_TYPE, resultType)) {
            return Optional.empty();
          }
          return Optional.of(MethodRefs.CHECK_INT.invoke(this));

        case GBIGINT:
          expectedClass = GbigintData.class;
          break;
        case JS:
          return Optional.of(MethodRefs.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.JS)));
        case ITERABLE:
          expectedClass = SoyIterable.class;
          break;
        case LIST:
          expectedClass = SoyList.class;
          break;
        case SET:
          expectedClass = SoySet.class;
          break;
        case MAP:
          expectedClass = SoyMap.class;
          break;
        case LEGACY_OBJECT_MAP:
          expectedClass = SoyLegacyObjectMap.class;
          break;
        case MESSAGE:
          expectedClass = SoyProtoValue.class;
          break;
        case PROTO:
          Type protoType =
              TypeInfo.create(
                      JavaQualifiedNames.getClassName(((SoyProtoType) type).getDescriptor()), false)
                  .type();
          return Optional.of(MethodRefs.CHECK_PROTO.invoke(this, constant(protoType)));
        case PROTO_ENUM:
          expectedClass = IntegerData.class;
          break;
        case RECORD:
          expectedClass = SoyRecord.class;
          break;
        case STRING:
          if (BytecodeUtils.isDefinitelyAssignableFrom(
              BytecodeUtils.STRING_DATA_TYPE, resultType)) {
            return Optional.empty();
          }
          return Optional.of(MethodRefs.CHECK_STRING.invoke(this));
        case TEMPLATE:
          expectedClass = TemplateValue.class;
          break;
        case TRUSTED_RESOURCE_URI:
          return Optional.of(
              MethodRefs.CHECK_CONTENT_KIND.invoke(
                  this, constant(ContentKind.TRUSTED_RESOURCE_URI)));
        case URI:
          return Optional.of(MethodRefs.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.URI)));
        case FUNCTION:
          if (BytecodeUtils.isDefinitelyAssignableFrom(
              BytecodeUtils.FUNCTION_VALUE_TYPE, resultType)) {
            return Optional.empty();
          }
          return Optional.of(MethodRefs.CHECK_FUNCTION.invoke(this));
        case NAMESPACE:
        case PROTO_TYPE:
        case PROTO_ENUM_TYPE:
        case PROTO_EXTENSION:
        case TEMPLATE_TYPE:
        case NEVER:
          throw new UnsupportedOperationException(type.getKind().toString());
      }

      Type expectedType = Type.getType(expectedClass);
      if (isDefinitelyAssignableFrom(expectedType, resultType)) {
        return Optional.empty();
      }
      return Optional.of(MethodRefs.CHECK_TYPE.invoke(this, constant(expectedType)));
    }

    SoyRuntimeType unboxedType = SoyRuntimeType.getUnboxedType(type).orElse(null);
    if (unboxedType != null) {
      Type runtimeType = unboxedType.runtimeType();
      if (BytecodeUtils.isPrimitive(runtimeType)) {
        checkArgument(resultType.equals(runtimeType), "%s != %s", resultType, runtimeType);
      } else {
        return this.maybeCheckedCast(runtimeType);
      }
    }
    // Expression is not boxed but the soy type can only be a boxed value. Throw.
    return this.maybeCheckedCast(BytecodeUtils.SOY_VALUE_TYPE);
  }

  /** Subclasses can override this to supply extra properties for the toString method. */
  @ForOverride
  protected void extraToStringProperties(MoreObjects.ToStringHelper helper) {}

  @Override
  public String toString() {
    String name = getClass().getSimpleName();
    if (name.isEmpty()) {
      // provide a default for anonymous subclasses
      name = "Expression";
    }
    MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(name).omitNullValues();
    helper.add("type", resultType());
    extraToStringProperties(helper);
    helper.add("cheap", features.has(Feature.CHEAP) ? "true" : null);
    helper.add(
        "non-null",
        features.has(Feature.NON_JAVA_NULLABLE) && !BytecodeUtils.isPrimitive(resultType)
            ? "true"
            : null);
    helper.add("non-soynullish", isNonSoyNullish());
    return helper + ":\n" + trace();
  }

  private static final class DelegatingExpression extends Expression {
    private final Expression delegate;

    public DelegatingExpression(Expression delegate, Type resultType, Features features) {
      super(resultType, features, delegate.location, delegate.constantValue);
      this.delegate = delegate;
    }

    public DelegatingExpression(Expression delegate, Features features) {
      super(delegate.resultType, features, delegate.location, delegate.constantValue);
      this.delegate = delegate;
    }

    public DelegatingExpression(Expression delegate, SourceLocation location) {
      super(delegate.resultType, delegate.features, location, delegate.constantValue);
      this.delegate = delegate;
    }

    public DelegatingExpression(Expression delegate, ConstantValue value) {
      super(delegate.resultType, delegate.features, delegate.location, Optional.of(value));
      this.delegate = delegate;
    }

    @Override
    protected void doGen(CodeBuilder adapter) {
      delegate.gen(adapter);
    }
  }
}
