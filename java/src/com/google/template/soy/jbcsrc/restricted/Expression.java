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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isDefinitelyAssignableFrom;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.FormatMethod;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import java.util.Collections;
import java.util.EnumSet;
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
   * Expression features track additional metadata for expressions.
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
    for (Expression arg : args) {
      if (!arg.isCheap()) {
        return false;
      }
    }
    return true;
  }

  /** Returns true if all referenced expressions are {@linkplain #isCheap() cheap}. */
  public static boolean areAllCheap(Expression first, Expression... rest) {
    return areAllCheap(ImmutableList.<Expression>builder().add(first).add(rest).build());
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

  protected Expression(Type resultType) {
    this(resultType, Features.of());
  }

  protected Expression(Type resultType, Features features) {
    this(resultType, features, SourceLocation.UNKNOWN);
  }

  protected Expression(Type resultType, Features features, SourceLocation location) {
    super(location);
    this.resultType = checkNotNull(resultType);
    this.features = Features.forType(resultType, features);
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
    return new DelegatingExpression(this, features.plus(Feature.CHEAP));
  }

  /** Returns an equivalent expression where {@link #isNonJavaNullable()} returns {@code true}. */
  public Expression asNonJavaNullable() {
    if (isNonJavaNullable()) {
      return this;
    }
    return new DelegatingExpression(this, features.plus(Feature.NON_JAVA_NULLABLE));
  }

  public Expression asJavaNullable() {
    if (!isNonJavaNullable()) {
      return this;
    }
    return new DelegatingExpression(this, features.minus(Feature.NON_JAVA_NULLABLE));
  }

  public Expression asNonSoyNullish() {
    if (isNonSoyNullish()) {
      return this;
    }
    return new DelegatingExpression(this, features.plus(Feature.NON_SOY_NULLISH));
  }

  public Expression asSoyNullish() {
    if (!isNonSoyNullish()) {
      return this;
    }
    return new DelegatingExpression(
        this, BytecodeUtils.SOY_VALUE_TYPE, features.minus(Feature.NON_SOY_NULLISH));
  }

  /**
   * Returns an expression that performs a checked cast from the current type to the target type.
   *
   * @throws IllegalArgumentException if either type is not a reference type.
   */
  public Expression checkedCast(Type target) {
    checkArgument(
        target.getSort() == Type.OBJECT,
        "cast targets must be reference types. (%s)",
        target.getClassName());
    checkArgument(
        resultType().getSort() == Type.OBJECT,
        "you may only cast from reference types. (%s)",
        resultType().getClassName());
    if (BytecodeUtils.isDefinitelyAssignableFrom(target, resultType())) {
      return this;
    }
    return new Expression(target, features()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
        adapter.checkCast(resultType());
      }
    };
  }

  /**
   * Returns an expression that performs a checked cast from the current type to the target type.
   *
   * @throws IllegalArgumentException if either type is not a reference type.
   */
  public Expression checkedCast(Class<?> target) {
    return checkedCast(Type.getType(target));
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
    return new Expression(resultType(), features) {
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
    return new Expression(resultType(), features) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
        adapter.mark(label);
      }
    };
  }

  /**
   * Inserts a runtime type check that this expression matches {@code type}. These checks are
   * typically inserted to validate user-supplied values are the expected type and fail early. The
   * resulting checks typically must call a method rather than a simple bytecode instruction since
   * NULL and UNDEFINED values must pass any type check but are represented as subclasses of {@link
   * SoyValue}.
   */
  public Expression checkedSoyCast(SoyType type) {
    type = SoyTypes.tryRemoveNullish(type);
    if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.SOY_VALUE_TYPE, resultType)) {
      if (isDefinitelyAssignableFrom(BytecodeUtils.NULLISH_DATA_TYPE, resultType)) {
        return this;
      }
      Class<? extends SoyValue> expectedClass = null;
      switch (type.getKind()) {
        case ANY:
        case UNKNOWN:
        case VE:
        case VE_DATA:
          return this;
        case UNION:
          if (type.equals(SoyTypes.NUMBER_TYPE)) {
            if (BytecodeUtils.isDefinitelyAssignableFrom(
                BytecodeUtils.NUMBER_DATA_TYPE, resultType)) {
              return this;
            }
            return MethodRef.CHECK_NUMBER.invoke(this);
          }
          return this;
        case NULL:
          return this.checkedCast(BytecodeUtils.NULL_DATA_TYPE);
        case UNDEFINED:
          return this.checkedCast(BytecodeUtils.UNDEFINED_DATA_TYPE);
        case ATTRIBUTES:
          return MethodRef.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.ATTRIBUTES));
        case CSS:
          return MethodRef.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.CSS));
        case BOOL:
          if (BytecodeUtils.isDefinitelyAssignableFrom(
              BytecodeUtils.BOOLEAN_DATA_TYPE, resultType)) {
            return this;
          }
          return MethodRef.CHECK_BOOLEAN.invoke(this);
        case FLOAT:
          if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.FLOAT_DATA_TYPE, resultType)) {
            return this;
          }
          return MethodRef.CHECK_FLOAT.invoke(this);
        case HTML:
        case ELEMENT:
          return MethodRef.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.HTML));
        case INT:
          if (BytecodeUtils.isDefinitelyAssignableFrom(
              BytecodeUtils.INTEGER_DATA_TYPE, resultType)) {
            return this;
          }
          return MethodRef.CHECK_INT.invoke(this);
        case JS:
          return MethodRef.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.JS));
        case LIST:
          expectedClass = SoyList.class;
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
          return MethodRef.CHECK_PROTO.invoke(this, constant(protoType));
        case PROTO_ENUM:
          expectedClass = IntegerData.class;
          break;
        case RECORD:
          expectedClass = SoyRecord.class;
          break;
        case STRING:
          if (BytecodeUtils.isDefinitelyAssignableFrom(
              BytecodeUtils.STRING_DATA_TYPE, resultType)) {
            return this;
          }
          return MethodRef.CHECK_STRING.invoke(this);
        case TEMPLATE:
          expectedClass = TemplateValue.class;
          break;
        case TRUSTED_RESOURCE_URI:
          return MethodRef.CHECK_CONTENT_KIND.invoke(
              this, constant(ContentKind.TRUSTED_RESOURCE_URI));
        case URI:
          return MethodRef.CHECK_CONTENT_KIND.invoke(this, constant(ContentKind.URI));
        case CSS_TYPE:
        case CSS_MODULE:
        case PROTO_TYPE:
        case PROTO_ENUM_TYPE:
        case PROTO_EXTENSION:
        case PROTO_MODULE:
        case TEMPLATE_TYPE:
        case TEMPLATE_MODULE:
        case FUNCTION:
          throw new UnsupportedOperationException();
      }

      Type expectedType = Type.getType(expectedClass);
      if (isDefinitelyAssignableFrom(expectedType, resultType)) {
        return this;
      }
      return MethodRef.CHECK_TYPE.invoke(this, constant(expectedType));
    }

    SoyRuntimeType unboxedType = SoyRuntimeType.getUnboxedType(type).orElse(null);
    if (unboxedType != null) {
      Type runtimeType = unboxedType.runtimeType();
      if (BytecodeUtils.isPrimitive(runtimeType)) {
        checkArgument(resultType.equals(runtimeType), "%s != %s", resultType, runtimeType);
      } else {
        return this.checkedCast(runtimeType);
      }
    }
    // Expression is not boxed but the soy type can only be a boxed value. Throw.
    return this.checkedCast(BytecodeUtils.SOY_VALUE_TYPE);
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
      super(resultType, features, delegate.location);
      this.delegate = delegate;
    }

    public DelegatingExpression(Expression delegate, Features features) {
      super(delegate.resultType, features, delegate.location);
      this.delegate = delegate;
    }

    public DelegatingExpression(Expression delegate, SourceLocation location) {
      super(delegate.resultType, delegate.features, location);
      this.delegate = delegate;
    }

    @Override
    protected void doGen(CodeBuilder adapter) {
      delegate.gen(adapter);
    }
  }
}
