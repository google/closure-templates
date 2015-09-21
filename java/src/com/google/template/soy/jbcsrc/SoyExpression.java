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
import static com.google.template.soy.jbcsrc.BytecodeUtils.OBJECT;
import static com.google.template.soy.jbcsrc.BytecodeUtils.SOY_LIST_TYPE;
import static com.google.template.soy.jbcsrc.BytecodeUtils.classFromAsmType;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.BytecodeUtils.logicalNot;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * An Expression involving a soy value.
 *
 * <p>SoyExpressions can be {@link #box() boxed} into SoyValue subtypes and they also support some
 * implicit conversions.
 *
 * <p>All soy expressions are convertable to {@code boolean} or {@link String} valued expressions,
 * but depending on the type they may also support additional unboxing conversions.
 */
class SoyExpression extends Expression {
  private static final ImmutableSet<Kind> STRING_KINDS =
      Sets.immutableEnumSet(Kind.STRING, Kind.HTML, Kind.ATTRIBUTES, Kind.JS, Kind.CSS, Kind.URI);

  static SoyExpression forSoyValue(SoyType type, Expression delegate) {
    return new SoyExpression(type, type.javaType(), delegate, Optional.<Expression>absent());
  }

  static SoyExpression forBool(Expression delegate) {
    return new SoyExpression(BoolType.getInstance(), boolean.class, delegate);
  }

  static SoyExpression forFloat(Expression delegate) {
    return new SoyExpression(FloatType.getInstance(), double.class, delegate);
  }

  static SoyExpression forInt(Expression delegate) {
    return new SoyExpression(IntType.getInstance(), long.class, delegate);
  }

  static SoyExpression forString(Expression delegate) {
    return new SoyExpression(StringType.getInstance(), String.class, delegate);
  }

  static SoyExpression forSanitizedString(Expression delegate, ContentKind kind) {
    return new SoyExpression(SanitizedType.getTypeForContentKind(kind), String.class, delegate);
  }

  static SoyExpression forList(ListType listType, Expression delegate) {
    return new SoyExpression(listType, List.class, delegate);
  }

  /**
   * Returns an Expression that evaluates to a list containing all the items as boxed soy values.
   */
  static Expression asBoxedList(List<SoyExpression> items) {
    List<Expression> childExprs = new ArrayList<>(items.size());
    for (SoyExpression child : items) {
      childExprs.add(child.box());
    }
    return BytecodeUtils.asList(childExprs);
  }

  static final SoyExpression NULL =
      new SoyExpression(
          NullType.getInstance(),
          Object.class,
          new Expression(OBJECT.type(), Feature.CHEAP) {
            @Override
            void doGen(CodeBuilder adapter) {
              adapter.visitInsn(Opcodes.ACONST_NULL);
            }
          });

  static final SoyExpression TRUE =
      new SoyExpression(BoolType.getInstance(), boolean.class, BytecodeUtils.constant(true)) {
        @Override
        SoyExpression box() {
          return new DefaultBoxed(BoolType.getInstance(), this,
              FieldRef.BOOLEAN_DATA_TRUE.accessor(), Optional.<Expression>absent());
        }
      };

  static final SoyExpression FALSE =
      new SoyExpression(BoolType.getInstance(), boolean.class, BytecodeUtils.constant(false)) {
        @Override
        SoyExpression box() {
          return new DefaultBoxed(BoolType.getInstance(), this,
              FieldRef.BOOLEAN_DATA_FALSE.accessor(), Optional.<Expression>absent());
        }
      };

  private final Class<?> clazz;
  private final SoyType soyType;
  private final Expression delegate;
  private final Optional<Expression> renderContext;

  private SoyExpression(SoyType soyType, Class<?> clazz, Expression delegate) {
    this(soyType, clazz, delegate, Optional.<Expression>absent());
  }

  private SoyExpression(SoyType soyType, Class<?> clazz, Expression delegate,
      Optional<Expression> renderContext) {
    super(delegate.resultType(), delegate.features());
    checkArgument(
        clazz.isAssignableFrom(classFromAsmType(delegate.resultType())),
        "delegate with type %s isn't compatible with asserted SoyExpression type %s",
        delegate.resultType(),
        clazz);
    // If this is a boxed type, make sure the declared clazz is compatible
    // TODO(lukes): support this check for unboxed types as well.
    if (SoyValue.class.isAssignableFrom(clazz) && !soyType.javaType().isAssignableFrom(clazz)) {
      throw new IllegalArgumentException(clazz + " is not compatible with soy type: " + soyType);
    }
    this.soyType = soyType;
    this.clazz = clazz;
    this.delegate = delegate;
    this.renderContext = renderContext;
  }

  /** Returns the {@link SoyType} of the expression. */
  final SoyType soyType() {
    return soyType;
  }

  @Override final void doGen(CodeBuilder adapter) {
    delegate.gen(adapter);
  }

  /**
   * Returns {@code true} if the expression is known to be a string at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a string, just that it is not <em>known</em> to be a string at compile time. For
   * example, {@code $b ? 'hello' : 2} is a valid soy expression that will be typed as 'any' at
   * compile time. So {@link #isKnownString()} on that soy expression will return false even though
   * it may in fact be a string.
   */
  boolean isKnownString() {
    return soyType.getKind() == Kind.STRING;
  }

  boolean isKnownStringOrSanitizedContent() {
    // It 'is' a string if it is unboxed or is one of our string types
    return STRING_KINDS.contains(soyType.getKind());
  }

  boolean isKnownSanitizedContent() {
    return soyType.getKind() != Kind.STRING && STRING_KINDS.contains(soyType.getKind());
  }

  /**
   * Returns {@code true} if the expression is known to be an int at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a int, just that it is not <em>known</em> to be a int at compile time.
   */
  boolean isKnownInt() {
    return soyType.getKind() == Kind.INT;
  }

  boolean assignableToNullableInt() {
    return assignableToNullableType(IntType.getInstance());
  }

  boolean assignableToNullableFloat() {
    return assignableToNullableType(FloatType.getInstance());
  }

  boolean assignableToNullableNumber() {
    return assignableToNullableType(SoyTypes.NUMBER_TYPE);
  }

  private boolean assignableToNullableType(SoyType type) {
    return type.isAssignableFrom(soyType)
        || (soyType.getKind() == Kind.UNION && type.isAssignableFrom(SoyTypes.removeNull(soyType)));
  }
  /**
   * Returns {@code true} if the expression is known to be a float at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a float, just that it is not <em>known</em> to be a float at compile time.
   */
  boolean isKnownFloat() {
    return soyType.getKind() == Kind.FLOAT;
  }

  boolean isKnownList() {
    return soyType.getKind() == Kind.LIST;
  }

  boolean isKnownMap() {
    return soyType.getKind() == Kind.MAP;
  }

  boolean isKnownRecord() {
    return soyType.getKind() == Kind.RECORD;
  }

  boolean isKnownBool() {
    return soyType.getKind() == Kind.BOOL;
  }

  boolean isBoxed() {
    return SoyValue.class.isAssignableFrom(clazz);
  }

  /**
   * Returns {@code true} if the expression is known to be an {@linkplain #isKnownInt() int} or a
   * {@linkplain #isKnownFloat() float} at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a number, just that it is not <em>known</em> to be a number at compile time.
   */
  final boolean isKnownNumber() {
    return SoyTypes.NUMBER_TYPE.isAssignableFrom(soyType);
  }

  /** Returns a SoyExpression that evaluates to a subtype of {@link SoyValue}. */
  SoyExpression box() {
    if (isBoxed()) {
      return this;
    }
    if (soyType.equals(NullType.getInstance())) {
      return this;
    }
    // If null is expected and it is a reference type we want to propagate null through the boxing
    // operation
    if (!delegate.isNonNullable()) {
      // now prefix with a null check and then box so null is preserved via 'boxing'
      final Label end = new Label();
      return withSource(
              new Expression(resultType(), features()) {
                @Override
                void doGen(CodeBuilder adapter) {
                  delegate.gen(adapter);
                  adapter.dup();
                  adapter.ifNull(end);
                }
              })
          .asNonNullable()
          .box()
          .asNullable()
          .labelEnd(end);
    }
    if (isKnownBool()) {
      return asBoxed(MethodRef.BOOLEAN_DATA_FOR_VALUE.invoke(delegate));
    }
    if (isKnownInt()) {
      return asBoxed(MethodRef.INTEGER_DATA_FOR_VALUE.invoke(delegate));
    }
    if (isKnownFloat()) {
      return asBoxed(MethodRef.FLOAT_DATA_FOR_VALUE.invoke(delegate));
    }
    if (isKnownSanitizedContent()) {
      return asBoxed(MethodRef.ORDAIN_AS_SAFE.invoke(delegate,
          FieldRef.enumReference(((SanitizedType) soyType).getContentKind()).accessor()));
    }
    if (isKnownString()) {
      return asBoxed(MethodRef.STRING_DATA_FOR_VALUE.invoke(delegate));
    }
    if (isKnownList()) {
      return asBoxed(MethodRef.LIST_IMPL_FOR_PROVIDER_LIST.invoke(delegate));
    }
    throw new IllegalStateException(
        "cannot box soy expression of type " + soyType + " with runtime type " + clazz);
  }

  private DefaultBoxed asBoxed(Expression expr) {
    return new DefaultBoxed(soyType, this, expr, renderContext);
  }

  /** Coerce this expression to a boolean value. */
  SoyExpression coerceToBoolean() {
    // First deal with primitives which don't have to care about null.
    if (BytecodeUtils.isPrimitive(resultType())) {
      return coercePrimitiveToBoolean();
    }
    if (soyType.equals(NullType.getInstance())) {
      return FALSE;
    }
    if (delegate.isNonNullable()) {
      return coerceNonNullableReferenceTypeToBoolean();
    } else {
      // If we are potentially nullable, then map null to false and run the normal logic recursively
      // for the non-nullable branch.
      final Label end = new Label();
      return withSource(
              new Expression(delegate.resultType(), delegate.features()) {
                @Override
                void doGen(CodeBuilder adapter) {
                  delegate.gen(adapter);
                  adapter.dup();
                  Label nonNull = new Label();
                  adapter.ifNonNull(nonNull);
                  adapter.pop();
                  adapter.pushBoolean(false);
                  adapter.goTo(end);
                  adapter.mark(nonNull);
                }
              })
          .asNonNullable()
          .coerceToBoolean()
          .labelEnd(end);
    }
  }

  private SoyExpression coercePrimitiveToBoolean() {
    if (resultType().equals(Type.BOOLEAN_TYPE)) {
      return this;
    } else if (resultType().equals(Type.DOUBLE_TYPE)) {
      return forBool(MethodRef.RUNTIME_COERCE_DOUBLE_TO_BOOLEAN.invoke(delegate));
    } else if (resultType().equals(Type.LONG_TYPE)) {
      return forBool(BytecodeUtils.compare(Opcodes.IFNE, delegate, BytecodeUtils.constant(0L)));
    } else {
      throw new AssertionError(
          "resultType(): " + resultType() + " is not a valid type for a SoyExpression");
    }
  }

  private SoyExpression coerceNonNullableReferenceTypeToBoolean() {
    if (isBoxed()) {
      // If we are boxed, just call the SoyValue method
      return forBool(delegate.invoke(MethodRef.SOY_VALUE_COERCE_TO_BOOLEAN));
    }
    // unboxed non-primitive types.  This would be strings, protos or lists
    if (clazz.equals(String.class)) {
      return forBool(logicalNot(delegate.invoke(MethodRef.STRING_IS_EMPTY)));
    }
    // All other types are always truthy, but we still need to eval the delegate in case it has
    // side effects or contains a null exit branch.
    return forBool(
            new Expression(Type.BOOLEAN_TYPE, delegate.features()) {
              @Override void doGen(CodeBuilder adapter) {
                delegate.gen(adapter);
                adapter.pop();
                adapter.pushBoolean(true);
              }
            });
  }

  /** Coerce this expression to a string value. */
  SoyExpression coerceToString() {
    if (clazz.equals(String.class)) {
      return this;
    }
    if (BytecodeUtils.isPrimitive(resultType())) {
      if (resultType().equals(Type.BOOLEAN_TYPE)) {
        return forString(MethodRef.BOOLEAN_TO_STRING.invoke(delegate));
      } else if (resultType().equals(Type.DOUBLE_TYPE)) {
        return forString(MethodRef.DOUBLE_TO_STRING.invoke(delegate));
      } else if (resultType().equals(Type.LONG_TYPE)) {
        return forString(MethodRef.LONG_TO_STRING.invoke(delegate));
      } else {
        throw new AssertionError(
            "resultType(): " + resultType() + " is not a valid type for a SoyExpression");
      }
    }
    if (!isBoxed()) {
      // this is for unboxed reference types (strings, lists, protos) String.valueOf handles null
      // implicitly
      return forString(MethodRef.STRING_VALUE_OF.invoke(delegate));
    }
    return forString(MethodRef.RUNTIME_COERCE_TO_STRING.invoke(delegate));
  }

  /**
   * Coerce to a double, useful for float-int comparisons
   */
  SoyExpression coerceToDouble() {
    if (clazz.equals(double.class)) {
      return this;
    }
    if (clazz.equals(long.class)) {
      return forFloat(BytecodeUtils.numericConversion(delegate, Type.DOUBLE_TYPE));
    }
    if (!isBoxed()) {
      throw new UnsupportedOperationException("Can't convert " + resultType() + " to a double");
    }
    if (isKnownFloat()) {
      return forFloat(delegate.invoke(MethodRef.SOY_VALUE_FLOAT_VALUE));
    }
    return forFloat(delegate.invoke(MethodRef.SOY_VALUE_NUMBER_VALUE));
  }

  /**
   * Unboxes this to a {@link SoyExpression} with a runtime type of {@code asType}.
   *
   * <p>This method is appropriate when you know (likely via inspection of the {@link #soyType()},
   * or other means) that the value does have the appropriate type but you prefer to interact with
   * it as its unboxed representation.  If you simply want to 'coerce' the given value to a new type
   * consider {@link #coerceToBoolean()} {@link #coerceToDouble()} or {@link #coerceToString()}
   * which are designed for that use case.
   */
  SoyExpression unboxAs(Class<?> asType) {
    checkArgument(
        !SoyValue.class.isAssignableFrom(asType),
        "Cannot use convert() to convert to a  SoyValue: %s, use .box() instead",
        asType);
    // no op conversion, always allow.
    if (asType.equals(clazz)) {
      return this;
    }
    if (!isBoxed()) {
      throw new IllegalStateException(
          "Trying to unbox an unboxed value (" + clazz + ") doesn't make sense, "
              + "should you be using a type coercion? e.g. .coerceToBoolean()");
    }
    if (asType.equals(boolean.class)) {
      return forBool(delegate.invoke(MethodRef.SOY_VALUE_BOOLEAN_VALUE));
    }
    if (asType.equals(long.class)) {
      return forInt(delegate.invoke(MethodRef.SOY_VALUE_LONG_VALUE));
    }
    if (asType.equals(double.class)) {
      return forFloat(delegate.invoke(MethodRef.SOY_VALUE_FLOAT_VALUE));
    }
    if (delegate.isNonNullable()) {
      if (asType.equals(String.class)) {
        Expression unboxedString = delegate.invoke(MethodRef.SOY_VALUE_STRING_VALUE);
        // We need to ensure that santized types don't lose their content kinds
        return isKnownSanitizedContent()
            ? forSanitizedString(unboxedString, ((SanitizedType) soyType).getContentKind())
            : forString(unboxedString);
      }
      if (asType.equals(List.class)) {
        return unboxAsList();
      }
    } else {
      // else it must be a List/Proto/String all of which must preserve null through the unboxing
      // operation
      final Label ifNull = new Label();
      Expression nonNullDelegate =
          new Expression(resultType(), features()) {
            @Override void doGen(CodeBuilder adapter) {
              delegate.gen(adapter);
              adapter.dup();
              adapter.ifNull(ifNull);
            }
          };
      final SoyExpression unboxAs = withSource(nonNullDelegate).asNonNullable().unboxAs(asType);
      return unboxAs.withSource(
          new Expression(unboxAs.resultType(), features()) {
            @Override
            void doGen(CodeBuilder adapter) {
              unboxAs.gen(adapter);
              adapter.mark(ifNull);
              adapter.checkCast(unboxAs.resultType()); // insert a cast to force type agreement
            }
          });
    }
    throw new UnsupportedOperationException("Can't unbox " + clazz + " as " + asType);
  }

  private SoyExpression unboxAsList() {
    ListType asListType;
    if (isKnownList()) {
      asListType = (ListType) soyType;
    } else {
      Kind kind = soyType.getKind();
      if (kind == Kind.UNKNOWN) {
        asListType = ListType.of(UnknownType.getInstance());
      } else {
        // The type checker should have already rejected all of these
        throw new UnsupportedOperationException("Cannot convert " + soyType + " to List");
      }
    }
    return forList(
        asListType, delegate.cast(SOY_LIST_TYPE).invoke(MethodRef.SOY_LIST_AS_JAVA_LIST));
  }

  /**
   * A generic unbox operator.  Doesn't always work since not every type has a canonical unboxed
   * representation and we don't always have enough type information.
   *
   * <p>For example, unboxed 'int' is always a java {@code long}, but unboxed '?' is undefined.
   */
  Optional<SoyExpression> tryUnbox() {
    if (!isBoxed()) {
      return Optional.of(this);
    }
    switch (soyType.getKind()) {
      case OBJECT:
      case RECORD:
      case UNKNOWN:
      case ANY:
      case MAP:
        return Optional.absent();
      case CSS:
      case ATTRIBUTES:
      case HTML:
      case JS:
      case URI:
      case STRING:
        return Optional.of(unboxAs(String.class));
      case BOOL:
        return Optional.of(unboxAs(boolean.class));
      case ENUM:
      case INT:
        return Optional.of(unboxAs(long.class));
      case UNION:
        // TODO(lukes): special case nullable reference types
        // fall-through
        return Optional.absent();
      case FLOAT:
        return Optional.of(unboxAs(double.class));
      case LIST:
        return Optional.of(unboxAs(List.class));
      case NULL:
        return Optional.of(NULL);
      case ERROR:
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns a new {@link SoyExpression} with the same type but a new delegate expression.
   */
  SoyExpression withSource(Expression expr) {
    return new SoyExpression(soyType, clazz, expr, renderContext);
  }

  /**
  * Applies a print directive to the soyValue, only useful for parameterless print directives such
  * as those applied to {@link MsgNode msg nodes} and {@link CallNode call nodes} for autoescaping.
  * For {@link PrintNode print nodes}, the directives may be parameterized by arbitrary soy
  * expressions.
  */
  SoyExpression applyPrintDirective(Expression renderContext, String directive) {
    return applyPrintDirective(renderContext, directive, MethodRef.IMMUTABLE_LIST_OF.invoke());
  }

  /**
  * Applies a print directive to the soyValue.
  */
  SoyExpression applyPrintDirective(
      Expression renderContext, String directive, Expression argsList) {
    // Technically the type is either StringData or SanitizedContent depending on this type, but
    // boxed.  Consider propagating the type more accurately, currently there isn't (afaict) much
    // benefit (and strangely there is no common super type for SanitizedContent and String), this
    // is probably because after escaping, the only thing you would ever do is convert to a string.
    return SoyExpression.forSoyValue(UnknownType.getInstance(),
        MethodRef.RUNTIME_APPLY_PRINT_DIRECTIVE.invoke(
            renderContext
                .invoke(MethodRef.RENDER_CONTEXT_GET_PRINT_DIRECTIVE, constant(directive)),
                this.box(),
            argsList));
  }

  @Override SoyExpression asCheap() {
    return withSource(delegate.asCheap());
  }

  @Override SoyExpression asNonNullable() {
    return new SoyExpression(
        SoyTypes.removeNull(soyType), clazz, delegate.asNonNullable(), renderContext);
  }

  @Override
  public SoyExpression asNullable() {
    return new SoyExpression(
        SoyTypes.makeNullable(soyType), clazz, delegate.asNullable(), renderContext);
  }

  @Override SoyExpression labelStart(Label label) {
    return withSource(delegate.labelStart(label));
  }

  @Override SoyExpression labelEnd(Label label) {
    return withSource(delegate.labelEnd(label));
  }

  /**
   * Default subtype of {@link SoyExpression} used by our core expression implementations.
   */
  private static final class DefaultBoxed extends SoyExpression {
    private final SoyExpression unboxed;

    DefaultBoxed(SoyType soyType, SoyExpression unboxed, Expression delegate,
        Optional<Expression> expr) {
      super(soyType, soyType.javaType(), delegate, expr);
      this.unboxed = unboxed;
    }

    @Override final SoyExpression unboxAs(Class<?> asType) {
      return unboxed.unboxAs(asType);
    }

    @Override
    Optional<SoyExpression> tryUnbox() {
      return Optional.of(unboxed);
    }

    @Override SoyExpression coerceToBoolean() {
      return unboxed.coerceToBoolean();
    }

    @Override SoyExpression coerceToString() {
      return unboxed.coerceToString();
    }

    @Override SoyExpression coerceToDouble() {
      return unboxed.coerceToDouble();
    }

    @Override final SoyExpression box() {
      return this;
    }
  }
}
