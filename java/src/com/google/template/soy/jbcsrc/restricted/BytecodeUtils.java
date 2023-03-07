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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.primitives.Ints;
import com.google.protobuf.Message;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.internal.Converters;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.LargeStringConstantFactory;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.logging.LoggableElementMetadata;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import java.io.Closeable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.Printer;

/** A set of utilities for generating simple expressions in bytecode */
public final class BytecodeUtils {
  // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.11
  private static final int MAX_CONSTANT_STRING_LENGTH = 65535;

  private static final class NullPseudoTypeClass {}

  /**
   * Not a real type, but used to mark the type of a null value, so it can be appropriately special
   * cased to be assignable to any (reference) type.
   */
  public static final Type NULL_PSEUDO_TYPE = Type.getType(NullPseudoTypeClass.class);

  public static final TypeInfo OBJECT = TypeInfo.create(Object.class);
  private static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);

  public static final Type LOGGING_ADVISING_APPENDABLE_TYPE =
      Type.getType(LoggingAdvisingAppendable.class);
  public static final Type LOGGING_ADVISING_BUILDER_TYPE =
      Type.getType(LoggingAdvisingAppendable.BufferingAppendable.class);
  public static final Type COMPILED_TEMPLATE_TYPE = Type.getType(CompiledTemplate.class);
  public static final Type COMPILED_TEMPLATE_TEMPLATE_VALUE_TYPE =
      Type.getType(CompiledTemplate.TemplateValue.class);
  public static final Type CONTENT_KIND_TYPE = Type.getType(ContentKind.class);
  public static final Type CLOSEABLE_TYPE = Type.getType(Closeable.class);
  public static final Type DIR_TYPE = Type.getType(Dir.class);
  public static final Type HASH_MAP_TYPE = Type.getType(HashMap.class);
  public static final Type NUMBER_DATA_TYPE = Type.getType(NumberData.class);
  public static final Type INTEGER_DATA_TYPE = Type.getType(IntegerData.class);
  public static final Type FLOAT_DATA_TYPE = Type.getType(FloatData.class);
  public static final Type BOOLEAN_DATA_TYPE = Type.getType(BooleanData.class);
  public static final Type STRING_DATA_TYPE = Type.getType(StringData.class);
  public static final Type LINKED_HASH_MAP_TYPE = Type.getType(LinkedHashMap.class);
  public static final Type LIST_TYPE = Type.getType(List.class);
  public static final Type IMMUTIBLE_LIST_TYPE = Type.getType(ImmutableList.class);
  public static final Type IMMUTIBLE_MAP_TYPE = Type.getType(ImmutableMap.class);
  public static final Type MAP_TYPE = Type.getType(Map.class);
  public static final Type MAP_ENTRY_TYPE = Type.getType(Map.Entry.class);
  public static final Type MESSAGE_TYPE = Type.getType(Message.class);
  public static final Type NULL_POINTER_EXCEPTION_TYPE = Type.getType(NullPointerException.class);
  public static final Type RENDER_CONTEXT_TYPE = Type.getType(RenderContext.class);
  public static final Type RENDER_RESULT_TYPE = Type.getType(RenderResult.class);
  public static final Type SANITIZED_CONTENT_TYPE = Type.getType(SanitizedContent.class);
  public static final Type SOY_LIST_TYPE = Type.getType(SoyList.class);
  public static final Type SOY_LEGACY_OBJECT_MAP_TYPE = Type.getType(SoyLegacyObjectMap.class);
  public static final Type SOY_MAP_TYPE = Type.getType(SoyMap.class);
  public static final Type SOY_PROTO_VALUE_TYPE = Type.getType(SoyProtoValue.class);
  public static final Type SOY_RECORD_TYPE = Type.getType(SoyRecord.class);
  public static final Type SOY_VALUE_TYPE = Type.getType(SoyValue.class);
  public static final Type SOY_VALUE_PROVIDER_TYPE = Type.getType(SoyValueProvider.class);
  public static final Type SOY_STRING_TYPE = Type.getType(StringData.class);
  public static final Type STRING_TYPE = Type.getType(String.class);
  public static final Type THROWABLE_TYPE = Type.getType(Throwable.class);
  public static final Type ILLEGAL_STATE_EXCEPTION_TYPE = Type.getType(IllegalStateException.class);
  public static final Type SOY_VISUAL_ELEMENT_TYPE = Type.getType(SoyVisualElement.class);
  public static final Type SOY_VISUAL_ELEMENT_DATA_TYPE = Type.getType(SoyVisualElementData.class);
  public static final Type CLASS_TYPE = Type.getType(Class.class);
  public static final Type INTEGER_TYPE = Type.getType(Integer.class);
  public static final Type BOXED_LONG_TYPE = Type.getType(Long.class);
  public static final Type BOXED_BOOLEAN_TYPE = Type.getType(Boolean.class);
  public static final Type BOXED_DOUBLE_TYPE = Type.getType(Double.class);
  public static final Type BOXED_FLOAT_TYPE = Type.getType(Float.class);
  public static final Type NUMBER_TYPE = Type.getType(Number.class);
  public static final Type LOGGABLE_ELEMENT_METADATA_TYPE =
      Type.getType(LoggableElementMetadata.class);
  public static final Type STACK_FRAME_TYPE = Type.getType(StackFrame.class);
  public static final Type SAFE_URL_TYPE = Type.getType(SafeUrl.class);
  public static final Type SAFE_URL_PROTO_TYPE = Type.getType(SafeUrlProto.class);
  public static final Type TRUSTED_RESOURCE_PROTO_TYPE =
      Type.getType(TrustedResourceUrlProto.class);
  public static final Type SAFE_HTML_PROTO_TYPE = Type.getType(SafeHtmlProto.class);
  public static final Type SAFE_HTML_TYPE = Type.getType(SafeHtml.class);
  public static final Type TRUSTED_RESOURCE_URL_TYPE = Type.getType(TrustedResourceUrl.class);

  public static final Method CLASS_INIT = Method.getMethod("void <clinit>()");
  public static final Method NULLARY_INIT = Method.getMethod("void <init>()");

  private static final LoadingCache<Type, Optional<Class<?>>> objectTypeToClassCache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<Type, Optional<Class<?>>>() {
                @Override
                public Optional<Class<?>> load(Type key) throws Exception {
                  switch (key.getSort()) {
                    case Type.ARRAY:
                      Optional<Class<?>> elementType =
                          objectTypeToClassCache.getUnchecked(key.getElementType());
                      if (elementType.isPresent()) {
                        // The easiest way to generically get an array class.
                        return Optional.of(Array.newInstance(elementType.get(), 0).getClass());
                      }
                      return Optional.empty();
                    case Type.VOID:
                      return Optional.of(void.class);
                    case Type.BOOLEAN:
                      return Optional.of(boolean.class);
                    case Type.BYTE:
                      return Optional.of(byte.class);
                    case Type.CHAR:
                      return Optional.of(char.class);
                    case Type.DOUBLE:
                      return Optional.of(double.class);
                    case Type.INT:
                      return Optional.of(int.class);
                    case Type.SHORT:
                      return Optional.of(short.class);
                    case Type.LONG:
                      return Optional.of(long.class);
                    case Type.FLOAT:
                      return Optional.of(float.class);
                    case Type.OBJECT:
                      try {
                        String className = key.getClassName();
                        if (className.startsWith(Names.CLASS_PREFIX)) {
                          // if the class is generated, don't try to look it up.
                          // It might actually succeed in a case where we have the class on our
                          // classpath already!
                          return Optional.empty();
                        }
                        return Optional.of(
                            Class.forName(
                                className,
                                /*initialize=*/ false,
                                BytecodeUtils.class.getClassLoader()));
                      } catch (ClassNotFoundException e) {
                        return Optional.empty();
                      }
                    default:
                      throw new IllegalArgumentException("unsupported type: " + key);
                  }
                }
              });

  private BytecodeUtils() {}

  /**
   * Returns {@code true} if {@code left} is possibly assignable from {@code right}.
   *
   * <p>Analogous to {@code right instanceof left}.
   */
  public static boolean isPossiblyAssignableFrom(Type left, Type right) {
    return doIsAssignableFrom(left, right, true);
  }

  /**
   * Returns {@code true} if {@code left} is definitely assignable from {@code right}.
   *
   * <p>Analogous to {@code right instanceof left}.
   */
  public static boolean isDefinitelyAssignableFrom(Type left, Type right) {
    return doIsAssignableFrom(left, right, false);
  }

  /**
   * Checks if {@code left} is assignable from {@code right}, however if we don't have information
   * about one of the types then this returns {@code failOpen}.
   */
  private static boolean doIsAssignableFrom(Type left, Type right, boolean failOpen) {
    if (left.equals(right)) {
      return true;
    }
    if (left.getSort() != right.getSort()) {
      return false;
    }
    if (right.equals(NULL_PSEUDO_TYPE)) {
      // null is assignable to any (reference) type
      return left.getSort() == Type.OBJECT || left.getSort() == Type.ARRAY;
    }
    if (left.getSort() != Type.OBJECT) {
      return false; // all other sorts require exact equality (even arrays)
    }
    // for object types we really need to know type hierarchy information to test for whether
    // right is assignable to left.
    Optional<Class<?>> leftClass = objectTypeToClassCache.getUnchecked(left);
    Optional<Class<?>> rightClass = objectTypeToClassCache.getUnchecked(right);
    if (!leftClass.isPresent() || !rightClass.isPresent()) {
      // This means one of the types being compared is a generated object.  So we can't easily check
      // it.  Just delegate responsibility to the verifier.
      return failOpen;
    }
    return leftClass.get().isAssignableFrom(rightClass.get());
  }

  /**
   * Returns the runtime class represented by the given type.
   *
   * @throws IllegalArgumentException if the class cannot be found. It is expected that this method
   *     will only be called for types that have a runtime on the compilers classpath.
   */
  public static Class<?> classFromAsmType(Type type) {
    Optional<Class<?>> maybeClass = objectTypeToClassCache.getUnchecked(type);
    if (!maybeClass.isPresent()) {
      throw new IllegalArgumentException("Could not load: " + type);
    }
    return maybeClass.get();
  }

  private static final Expression FALSE =
      new Expression(Type.BOOLEAN_TYPE, Feature.CHEAP) {
        @Override
        protected void doGen(CodeBuilder mv) {
          mv.pushBoolean(false);
        }
      };

  private static final Expression TRUE =
      new Expression(Type.BOOLEAN_TYPE, Feature.CHEAP) {
        @Override
        protected void doGen(CodeBuilder mv) {
          mv.pushBoolean(true);
        }
      };

  /** Returns an {@link Expression} that can load the given boolean constant. */
  public static Expression constant(boolean value) {
    return value ? TRUE : FALSE;
  }

  /** Returns an {@link Expression} that can load the given int constant. */
  public static Expression constant(final int value) {
    return new Expression(Type.INT_TYPE, Feature.CHEAP) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushInt(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given char constant. */
  public static Expression constant(final char value) {
    return new Expression(Type.CHAR_TYPE, Feature.CHEAP) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushInt(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given long constant. */
  public static Expression constant(final long value) {
    return new Expression(Type.LONG_TYPE, Feature.CHEAP) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushLong(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given double constant. */
  public static Expression constant(final double value) {
    return new Expression(Type.DOUBLE_TYPE, Feature.CHEAP) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushDouble(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given String constant. */
  public static Expression constant(String value) {
    // string constants use a "modified UTF8" encoding
    // https://en.wikipedia.org/wiki/UTF-8#Modified_UTF-8
    // and are limited by the classfile format to contain no more than 65535 bytes
    // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.7
    // In soy we often have large constants that can exceed these limits, which is annoying since
    // it is difficult to predict whether a given string constant will exceed these limits (since it
    // needs to be encoded first).
    int previousStart = 0;
    List<String> stringConstants = new ArrayList<>();
    int byteCount = 0;
    int index = 0;
    while (index < value.length()) {
      char c = value.charAt(index);
      int charBytes;
      // This algorithm is described here
      // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.7
      if (c >= '\001' && c <= '\177') {
        charBytes = 1;
      } else if (c > '\u07FF') {
        charBytes = 3;
      } else {
        charBytes = 2;
      }
      // does this char push us over the limit?
      if (byteCount + charBytes > MAX_CONSTANT_STRING_LENGTH) {
        stringConstants.add(value.substring(previousStart, index));
        byteCount = 0;
        previousStart = index;
      }
      byteCount += charBytes;
      index++;
    }
    stringConstants.add(value.substring(previousStart));
    return new Expression(STRING_TYPE, Feature.CHEAP, Feature.NON_NULLABLE) {
      @Override
      protected void doGen(CodeBuilder cb) {
        if (stringConstants.size() == 1) {
          cb.pushString(stringConstants.get(0));
        } else {
          cb.visitInvokeDynamicInsn(
              "constantString",
              Type.getMethodDescriptor(STRING_TYPE),
              LARGE_STRING_CONSTANT_HANDLE,
              stringConstants.toArray());
        }
      }
    };
  }

  /** Returns an {@link Expression} that evaluates to the given ContentKind, or null. */
  public static Expression constant(@Nullable ContentKind kind) {
    return (kind == null)
        ? BytecodeUtils.constantNull(CONTENT_KIND_TYPE)
        : FieldRef.enumReference(kind).accessor();
  }

  /** Returns an {@link Expression} that evaluates to the given Dir, or null. */
  public static Expression constant(@Nullable Dir dir) {
    return (dir == null)
        ? BytecodeUtils.constantNull(DIR_TYPE)
        : FieldRef.enumReference(dir).accessor();
  }

  public static Expression constant(Type type) {
    return new Expression(CLASS_TYPE, Feature.CHEAP, Feature.NON_NULLABLE) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushType(type);
      }
    };
  }

  private static final Handle LARGE_STRING_CONSTANT_HANDLE =
      MethodRef.create(
              LargeStringConstantFactory.class,
              "bootstrapLargeStringConstant",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              String[].class)
          .asHandle();

  /**
   * Returns an {@link Expression} that evaluates to the {@link ContentKind} value that is
   * equivalent to the given {@link SanitizedContentKind}, or null.
   */
  public static Expression constantSanitizedContentKindAsContentKind(SanitizedContentKind kind) {
    return FieldRef.enumReference(Converters.toContentKind(kind)).accessor();
  }

  /** Returns an {@link Expression} with the given type that always returns null. */
  public static Expression constantNull(Type type) {
    checkArgument(
        type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY,
        "%s is not a reference type",
        type);
    return new Expression(type, Feature.CHEAP) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.visitInsn(Opcodes.ACONST_NULL);
      }
    };
  }

  /**
   * Returns an expression that does a numeric conversion cast from the given expression to the
   * given type.
   *
   * @throws IllegalArgumentException if either the expression or the target type is not a numeric
   *     primitive
   */
  public static Expression numericConversion(Expression expr, Type to) {
    if (to.equals(expr.resultType())) {
      return expr;
    }
    if (!isNumericPrimitive(to) || !isNumericPrimitive(expr.resultType())) {
      throw new IllegalArgumentException("Cannot convert from " + expr.resultType() + " to " + to);
    }
    return new Expression(to, expr.features()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        expr.gen(adapter);
        adapter.cast(expr.resultType(), to);
      }
    };
  }

  private static boolean isNumericPrimitive(Type type) {
    int sort = type.getSort();
    switch (sort) {
      case Type.OBJECT:
      case Type.ARRAY:
      case Type.VOID:
      case Type.METHOD:
      case Type.BOOLEAN:
        return false;
      case Type.BYTE:
      case Type.CHAR:
      case Type.DOUBLE:
      case Type.INT:
      case Type.SHORT:
      case Type.LONG:
      case Type.FLOAT:
        return true;
      default:
        throw new AssertionError("unexpected type " + type);
    }
  }

  /** Returns {@code true} if {@link Type} is a primitive type. */
  public static boolean isPrimitive(Type type) {
    switch (type.getSort()) {
      case Type.OBJECT:
      case Type.ARRAY:
        return false;
      case Type.BOOLEAN:
      case Type.BYTE:
      case Type.CHAR:
      case Type.DOUBLE:
      case Type.INT:
      case Type.SHORT:
      case Type.LONG:
      case Type.FLOAT:
        return true;
      case Type.VOID:
      case Type.METHOD:
        throw new IllegalArgumentException("Invalid type: " + type);
      default:
        throw new AssertionError("unexpected type " + type);
    }
  }

  /**
   * Generates a default nullary public constructor for the given type on the {@link ClassVisitor}.
   *
   * <p>For java classes this is normally generated by the compiler and looks like:
   *
   * <pre>{@code
   * public Foo() {
   *   super();
   * }
   * }</pre>
   */
  public static void defineDefaultConstructor(ClassVisitor cv, TypeInfo ownerType) {
    CodeBuilder mg = new CodeBuilder(Opcodes.ACC_PUBLIC, NULLARY_INIT, null, cv);
    mg.visitCode();
    Label start = mg.mark();
    Label end = mg.newLabel();
    LocalVariable thisVar = LocalVariable.createThisVar(ownerType, start, end);
    thisVar.gen(mg);
    mg.invokeConstructor(OBJECT.type(), NULLARY_INIT);
    mg.returnValue();
    mg.mark(end);
    thisVar.tableEntry(mg);
    mg.endMethod();
  }

  // TODO(lukes): some of these branch operators are a little too branchy.  For example, the
  // expression a == b || a == c, could be implemented by
  // logicalOr(compare(Opcodes.IFEQ, a, b), compare(Opcodes.IFEQ, a, c)), but that is not optimal
  // instead we could allow compare to take an expression for what to do when the comparison fails
  // that way we could save a branch.  Maybe these operators are a failed abstraction?

  /** Compares the two primitive valued expressions using the provided comparison operation. */
  public static Expression compare(
      final int comparisonOpcode, final Expression left, final Expression right) {
    checkArgument(
        left.resultType().equals(right.resultType()),
        "left and right must have matching types, found %s and %s",
        left.resultType(),
        right.resultType());
    checkIntComparisonOpcode(left.resultType(), comparisonOpcode);
    Features features =
        Expression.areAllCheap(left, right) ? Features.of(Feature.CHEAP) : Features.of();
    return new Expression(Type.BOOLEAN_TYPE, features) {
      @Override
      protected void doGen(CodeBuilder mv) {
        left.gen(mv);
        right.gen(mv);
        Label ifTrue = mv.newLabel();
        Label end = mv.newLabel();
        mv.ifCmp(left.resultType(), comparisonOpcode, ifTrue);
        mv.pushBoolean(false);
        mv.goTo(end);
        mv.mark(ifTrue);
        mv.pushBoolean(true);
        mv.mark(end);
      }
    };
  }

  private static void checkIntComparisonOpcode(Type comparisonType, int opcode) {
    switch (opcode) {
      case Opcodes.IFEQ:
      case Opcodes.IFNE:
        return;
      case Opcodes.IFGT:
      case Opcodes.IFGE:
      case Opcodes.IFLT:
      case Opcodes.IFLE:
        if (comparisonType.getSort() == Type.ARRAY || comparisonType.getSort() == Type.OBJECT) {
          throw new IllegalArgumentException(
              "Type: " + comparisonType + " cannot be compared via " + Printer.OPCODES[opcode]);
        }
        return;
      default:
        throw new IllegalArgumentException(
            "Unsupported opcode for comparison operation: " + opcode);
    }
  }

  /**
   * Returns an expression that evaluates to the logical negation of the given boolean valued
   * expression.
   */
  public static Expression logicalNot(final Expression baseExpr) {
    baseExpr.checkAssignableTo(Type.BOOLEAN_TYPE);
    checkArgument(baseExpr.resultType().equals(Type.BOOLEAN_TYPE), "not a boolean expression");
    return new Expression(Type.BOOLEAN_TYPE, baseExpr.features()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        baseExpr.gen(mv);
        // Surprisingly, java bytecode uses a branch (instead of 'xor 1' or something) to implement
        // this. This is most likely useful for allowing true to be represented by any non-zero
        // number.
        Label ifTrue = mv.newLabel();
        Label end = mv.newLabel();
        mv.ifZCmp(Opcodes.IFNE, ifTrue); // if not 0 goto ifTrue
        mv.pushBoolean(true);
        mv.goTo(end);
        mv.mark(ifTrue);
        mv.pushBoolean(false);
        mv.mark(end);
      }
    };
  }

  /** Compares two {@link SoyExpression}s for equality using soy == semantics. */
  public static Expression compareSoyEquals(final SoyExpression left, final SoyExpression right) {
    // We can special case when we know the types.
    // If either is a string, we run special logic so test for that first
    // otherwise we special case primitives and eventually fall back to our runtime.
    SoyRuntimeType leftRuntimeType = left.soyRuntimeType();
    SoyRuntimeType rightRuntimeType = right.soyRuntimeType();
    if (leftRuntimeType.isKnownString()) {
      return doEqualsString(left.unboxAsString(), right);
    }
    if (rightRuntimeType.isKnownString()) {
      // TODO(lukes): we are changing the order of evaluation here.
      return doEqualsString(right.unboxAsString(), left);
    }
    if (leftRuntimeType.isKnownInt()
        && rightRuntimeType.isKnownInt()
        && left.isNonNullable()
        && right.isNonNullable()) {
      return compare(Opcodes.IFEQ, left.unboxAsLong(), right.unboxAsLong());
    }
    if (leftRuntimeType.isKnownNumber()
        && rightRuntimeType.isKnownNumber()
        && left.isNonNullable()
        && right.isNonNullable()
        && (leftRuntimeType.isKnownFloat() || rightRuntimeType.isKnownFloat())) {
      return compare(Opcodes.IFEQ, left.coerceToDouble(), right.coerceToDouble());
    }
    return MethodRef.RUNTIME_EQUAL.invoke(left.box(), right.box());
  }

  /**
   * Compare a string valued expression to another expression using soy == semantics.
   *
   * @param stringExpr An expression that is known to be an unboxed string
   * @param other An expression to compare it to.
   */
  private static Expression doEqualsString(SoyExpression stringExpr, SoyExpression other) {
    // This is compatible with SharedRuntime.compareString, which interestingly makes == break
    // transitivity.  See b/21461181
    SoyRuntimeType otherRuntimeType = other.soyRuntimeType();
    if (otherRuntimeType.isKnownStringOrSanitizedContent()) {
      if (stringExpr.isNonNullable()) {
        return stringExpr.invoke(MethodRef.EQUALS, other.unboxAsString());
      } else {
        return MethodRef.OBJECTS_EQUALS.invoke(stringExpr, other.unboxAsString());
      }
    }
    if (otherRuntimeType.isKnownNumber() && other.isNonNullable()) {
      // in this case, we actually try to convert stringExpr to a number
      return MethodRef.RUNTIME_STRING_EQUALS_AS_NUMBER.invoke(stringExpr, other.coerceToDouble());
    }
    // We don't know what other is, assume the worst and call out to our boxed implementation for
    // string comparisons.
    return MethodRef.RUNTIME_COMPARE_NULLABLE_STRING.invoke(stringExpr, other.box());
  }

  /**
   * Returns an expression that evaluates to {@code left} if left is non null, and evaluates to
   * {@code right} otherwise.
   */
  public static Expression firstNonNull(final Expression left, final Expression right) {
    checkArgument(left.resultType().getSort() == Type.OBJECT);
    checkArgument(right.resultType().getSort() == Type.OBJECT);
    Features features = Features.of();
    if (Expression.areAllCheap(left, right)) {
      features = features.plus(Feature.CHEAP);
    }
    if (right.isNonNullable()) {
      features = features.plus(Feature.NON_NULLABLE);
    }
    return new Expression(left.resultType(), features) {
      @Override
      protected void doGen(CodeBuilder cb) {
        Label leftIsNonNull = new Label();
        left.gen(cb); // Stack: L
        cb.dup(); // Stack: L, L
        cb.ifNonNull(leftIsNonNull); // Stack: L
        // pop the extra copy of left
        cb.pop(); // Stack:
        right.gen(cb); // Stack: R
        cb.mark(leftIsNonNull); // At this point the stack has an instance of L or R
      }
    };
  }

  /**
   * Returns an expression that evaluates equivalently to a java ternary expression: {@code
   * condition ? left : right}
   */
  public static Expression ternary(
      Expression condition, Expression trueBranch, Expression falseBranch) {
    // Choose the type of the ternary as the least specific of the two options.
    // In theory we shold really choose the least common superclass which would cover more cases,
    // but this should be fine for now.  Mostly this is just turning (ImmutableList,List)->List.  If
    // this isn't possible, an error will be thrown and we can re-evaluate this approach.
    Type ternaryType;
    Type trueType = trueBranch.resultType();
    Type falseType = falseBranch.resultType();
    if (isDefinitelyAssignableFrom(trueType, falseType)) {
      ternaryType = trueType;
    } else if (isDefinitelyAssignableFrom(falseType, trueType)) {
      ternaryType = falseType;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "true (%s) and false (%s) branches must be compatible", trueType, falseType));
    }
    return ternary(condition, trueBranch, falseBranch, ternaryType);
  }

  /**
   * Returns an expression that evaluates equivalently to a java ternary expression: {@code
   * condition ? left : right}.
   *
   * <p>This allows the caller to specify the result type of the ternary expression. By default the
   * ternary expression is typed with the type of the true branch, but the caller can specify the
   * result type if they know more about the types of the branches.
   */
  public static Expression ternary(
      final Expression condition,
      final Expression trueBranch,
      final Expression falseBranch,
      Type resultType) {
    checkArgument(
        condition.resultType().equals(Type.BOOLEAN_TYPE),
        "The condition must be a boolean, got %s",
        condition.resultType());
    checkArgument(
        isPossiblyAssignableFrom(resultType, trueBranch.resultType()),
        "expected %s to be assignable to %s",
        trueBranch.resultType(),
        resultType);
    checkArgument(
        isPossiblyAssignableFrom(resultType, falseBranch.resultType()),
        "expected %s to be assignable to %s",
        falseBranch.resultType(),
        resultType);
    Features features = Features.of();
    if (Expression.areAllCheap(condition, trueBranch, falseBranch)) {
      features = features.plus(Feature.CHEAP);
    }
    if (trueBranch.isNonNullable() && falseBranch.isNonNullable()) {
      features = features.plus(Feature.NON_NULLABLE);
    }
    return new Expression(resultType, features) {
      @Override
      protected void doGen(CodeBuilder mv) {
        condition.gen(mv);
        Label ifFalse = new Label();
        Label end = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, ifFalse); // if 0 goto ifFalse
        trueBranch.gen(mv); // eval true branch
        mv.visitJumpInsn(Opcodes.GOTO, end); // jump to the end
        mv.visitLabel(ifFalse);
        falseBranch.gen(mv); // eval false branch
        mv.visitLabel(end);
      }
    };
  }

  /**
   * Implements the short circuiting logical or ({@code ||}) operator over the list of boolean
   * expressions.
   */
  public static Expression logicalOr(Expression... expressions) {
    return logicalOr(ImmutableList.copyOf(expressions));
  }

  /**
   * Implements the short circuiting logical or ({@code ||}) operator over the list of boolean
   * expressions.
   */
  public static Expression logicalOr(List<? extends Expression> expressions) {
    return doShortCircuitingLogicalOperator(ImmutableList.copyOf(expressions), true);
  }

  /**
   * Implements the short circuiting logical and ({@code &&}) operator over the list of boolean
   * expressions.
   */
  public static Expression logicalAnd(Expression... expressions) {
    return logicalAnd(ImmutableList.copyOf(expressions));
  }

  /**
   * Implements the short circuiting logical and ({@code &&}) operator over the list of boolean
   * expressions.
   */
  public static Expression logicalAnd(List<? extends Expression> expressions) {
    return doShortCircuitingLogicalOperator(ImmutableList.copyOf(expressions), false);
  }

  private static Expression doShortCircuitingLogicalOperator(
      final ImmutableList<? extends Expression> expressions, final boolean isOrOperator) {
    checkArgument(!expressions.isEmpty());
    for (Expression expr : expressions) {
      expr.checkAssignableTo(Type.BOOLEAN_TYPE);
    }
    if (expressions.size() == 1) {
      return expressions.get(0);
    }

    return new Expression(
        Type.BOOLEAN_TYPE,
        Expression.areAllCheap(expressions) ? Features.of(Feature.CHEAP) : Features.of()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Label end = new Label();
        Label shortCircuit = new Label();
        for (int i = 0; i < expressions.size(); i++) {
          Expression expr = expressions.get(i);
          expr.gen(adapter);
          if (i == expressions.size() - 1) {
            // if we are the last one, just goto end. Whatever the result of the last expression is
            // determines the result of the whole expression (when all prior tests fail).
            adapter.goTo(end);
          } else {
            adapter.ifZCmp(isOrOperator ? Opcodes.IFNE : Opcodes.IFEQ, shortCircuit);
          }
        }
        adapter.mark(shortCircuit);
        adapter.pushBoolean(isOrOperator); // default for || is true && is false
        adapter.mark(end);
      }
    };
  }

  /**
   * Returns an expression that returns a new {@code ImmutableList} containing the given items.
   *
   * <p>NOTE: {@code ImmutableList} rejects null elements.
   */
  public static Expression asImmutableList(Iterable<? extends Expression> items) {
    ImmutableList<Expression> copy = ImmutableList.copyOf(items);
    if (copy.size() < MethodRef.IMMUTABLE_LIST_OF.size()) {
      return MethodRef.IMMUTABLE_LIST_OF.get(copy.size()).invoke(copy);
    }
    ImmutableList<Expression> explicit = copy.subList(0, MethodRef.IMMUTABLE_LIST_OF.size());
    Expression remainder =
        asArray(OBJECT_ARRAY_TYPE, copy.subList(MethodRef.IMMUTABLE_LIST_OF.size(), copy.size()));
    return MethodRef.IMMUTABLE_LIST_OF_ARRAY.invoke(
        Iterables.concat(explicit, ImmutableList.of(remainder)));
  }

  private static Expression asArray(
      final Type arrayType, final ImmutableList<? extends Expression> elements) {
    final Type elementType = arrayType.getElementType();
    return new Expression(arrayType, Feature.NON_NULLABLE) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.pushInt(elements.size());
        adapter.newArray(elementType);
        for (int i = 0; i < elements.size(); i++) {
          adapter.dup(); // dup the array
          adapter.pushInt(i); // the index to store into
          elements.get(i).gen(adapter); // the element to store
          adapter.arrayStore(elementType);
        }
      }
    };
  }

  /** Returns an expression that returns a new {@link ArrayList} containing all the given items. */
  public static Expression asList(Iterable<? extends Expression> items) {
    final ImmutableList<Expression> copy = ImmutableList.copyOf(items);
    if (copy.isEmpty()) {
      return MethodRef.IMMUTABLE_LIST_OF.get(0).invoke();
    }
    // Note, we cannot necessarily use ImmutableList for anything besides the empty list because
    // we may need to put a null in it.
    final Expression construct = ConstructorRef.ARRAY_LIST_SIZE.construct(constant(copy.size()));
    return new Expression(LIST_TYPE, Feature.NON_NULLABLE) {
      @Override
      protected void doGen(CodeBuilder mv) {
        construct.gen(mv);
        for (Expression child : copy) {
          mv.dup();
          child.gen(mv);
          MethodRef.ARRAY_LIST_ADD.invokeUnchecked(mv);
          mv.pop(); // pop the bool result of arraylist.add
        }
      }
    };
  }

  /**
   * Outputs bytecode that will test the item at the top of the stack for null, and branch to {@code
   * nullExit} if it is {@code null}. At {@code nullSafeExit} there will be a null value at the top
   * of the stack.
   */
  public static void nullCoalesce(CodeBuilder builder, Label nullExit) {
    builder.dup();
    Label nonNull = new Label();
    builder.ifNonNull(nonNull);
    // See http://mail.ow2.org/wws/arc/asm/2016-02/msg00001.html for a discussion of this pattern
    // but even though the value at the top of the stack here is null, its type isn't.  So we need
    // to pop and push.  This is the idiomatic pattern.
    builder.pop();
    builder.pushNull();
    builder.goTo(nullExit);
    builder.mark(nonNull);
  }

  /**
   * Outputs bytecode that unboxes the current top element of the stack as {@code asType}. Top of
   * stack must not be null.
   *
   * <p>Always prefer using {@link SoyExpression#unboxAs} over this method, whenever possible.
   *
   * <p>Guarantees: * Bytecode output will not change stack height * Output will only change the top
   * element, and nothing below that
   *
   * @return the type of the result of the unbox operation
   */
  public static Type unboxUnchecked(CodeBuilder cb, SoyRuntimeType soyType, Class<?> asType) {
    checkArgument(soyType.isBoxed(), "Expected %s to be a boxed type", soyType);
    Type fromType = soyType.runtimeType();
    checkArgument(
        !SoyValue.class.isAssignableFrom(asType),
        "Can't use unboxUnchecked() to convert from %s to a SoyValue: %s.",
        fromType,
        asType);

    // No-op conversion
    if (isDefinitelyAssignableFrom(Type.getType(asType), fromType)) {
      return fromType;
    }

    if (asType.equals(boolean.class)) {
      MethodRef.SOY_VALUE_BOOLEAN_VALUE.invokeUnchecked(cb);
      return Type.BOOLEAN_TYPE;
    }

    if (asType.equals(long.class)) {
      MethodRef.SOY_VALUE_LONG_VALUE.invokeUnchecked(cb);
      return Type.LONG_TYPE;
    }

    if (asType.equals(double.class)) {
      MethodRef.SOY_VALUE_FLOAT_VALUE.invokeUnchecked(cb);
      return Type.DOUBLE_TYPE;
    }

    if (asType.equals(String.class)) {
      MethodRef.SOY_VALUE_STRING_VALUE.invokeUnchecked(cb);
      return STRING_TYPE;
    }

    if (asType.equals(List.class)) {
      cb.checkCast(SOY_LIST_TYPE);
      MethodRef.SOY_LIST_AS_JAVA_LIST.invokeUnchecked(cb);
      return LIST_TYPE;
    }

    if (asType.equals(Message.class)) {
      if (!isDefinitelyAssignableFrom(SOY_PROTO_VALUE_TYPE, fromType)) {
        cb.checkCast(SOY_PROTO_VALUE_TYPE);
      }
      MethodRef.SOY_PROTO_VALUE_GET_PROTO.invokeUnchecked(cb);
      return MESSAGE_TYPE;
    }

    throw new UnsupportedOperationException(
        "Can't unbox top of stack from " + fromType + " to " + asType);
  }

  /** Returns an expression that returns a new {@link HashMap} containing all the given entries. */
  public static Expression newHashMap(
      Iterable<? extends Expression> keys, Iterable<? extends Expression> values) {
    return newMap(keys, values, ConstructorRef.HASH_MAP_CAPACITY, HASH_MAP_TYPE);
  }

  /**
   * Returns an expression that returns a new {@link LinkedHashMap} containing all the given
   * entries.
   */
  public static Expression newLinkedHashMap(
      Iterable<? extends Expression> keys, Iterable<? extends Expression> values) {
    return newMap(keys, values, ConstructorRef.LINKED_HASH_MAP_CAPACITY, LINKED_HASH_MAP_TYPE);
  }

  private static Expression newMap(
      Iterable<? extends Expression> keys,
      Iterable<? extends Expression> values,
      ConstructorRef constructorRef,
      Type mapType) {
    final ImmutableList<Expression> keysCopy = ImmutableList.copyOf(keys);
    final ImmutableList<Expression> valuesCopy = ImmutableList.copyOf(values);
    checkArgument(keysCopy.size() == valuesCopy.size());
    for (int i = 0; i < keysCopy.size(); i++) {
      checkArgument(keysCopy.get(i).resultType().getSort() == Type.OBJECT);
      checkArgument(valuesCopy.get(i).resultType().getSort() == Type.OBJECT);
    }
    final Expression construct =
        constructorRef.construct(constant(hashMapCapacity(keysCopy.size())));
    return new Expression(mapType, Feature.NON_NULLABLE) {
      @Override
      protected void doGen(CodeBuilder mv) {
        construct.gen(mv);
        for (int i = 0; i < keysCopy.size(); i++) {
          Expression key = keysCopy.get(i);
          Expression value = valuesCopy.get(i);
          mv.dup();
          key.gen(mv);
          value.gen(mv);
          MethodRef.MAP_PUT.invokeUnchecked(mv);
          mv.pop(); // pop the Object result of map.put
        }
      }
    };
  }

  private static int hashMapCapacity(int expectedSize) {
    if (expectedSize < 3) {
      return expectedSize + 1;
    }
    if (expectedSize < Ints.MAX_POWER_OF_TWO) {
      // This is the calculation used in JDK8 to resize when a putAll
      // happens; it seems to be the most conservative calculation we
      // can make.  0.75 is the default load factor.
      return (int) (expectedSize / 0.75F + 1.0F);
    }
    return Integer.MAX_VALUE; // any large value
  }

  /**
   * Returns a {@link SoyExpression} that evaluates to true if the expression evaluated to a
   * non-null value.
   */
  public static SoyExpression isNonNull(final Expression expr) {
    if (BytecodeUtils.isPrimitive(expr.resultType())) {
      // Reference the statement so that the SoyValueProvider detaches for resolve, and
      // TemplateAnalysis will correctly cause subsequent accesses to resolve immediately.
      return SoyExpression.forBool(expr.toStatement().then(BytecodeUtils.constant(true)));
    }
    return SoyExpression.forBool(
        new Expression(Type.BOOLEAN_TYPE, expr.features()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            expr.gen(adapter);
            Label isNull = new Label();
            adapter.ifNull(isNull);
            // non-null
            adapter.pushBoolean(true);
            Label end = new Label();
            adapter.goTo(end);
            adapter.mark(isNull);
            adapter.pushBoolean(false);
            adapter.mark(end);
          }
        });
  }

  /** Returns a {@link SoyExpression} that evaluates to true if the expression evaluated to null. */
  public static SoyExpression isNull(final Expression expr) {
    if (BytecodeUtils.isPrimitive(expr.resultType())) {
      // Reference the statement so that the SoyValueProvider detaches for resolve, and
      // TemplateAnalysis will correctly cause subsequent accesses to resolve immediately.
      return SoyExpression.forBool(expr.toStatement().then(BytecodeUtils.constant(false)));
    }
    // This is what javac generates for 'someObject == null'
    return SoyExpression.forBool(
        new Expression(Type.BOOLEAN_TYPE, expr.features()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            expr.gen(adapter);
            Label isNull = new Label();
            adapter.ifNull(isNull);
            // non-null
            adapter.pushBoolean(false);
            Label end = new Label();
            adapter.goTo(end);
            adapter.mark(isNull);
            adapter.pushBoolean(true);
            adapter.mark(end);
          }
        });
  }

  public static Type getTypeForClassName(String name) {
    return Type.getType('L' + name.replace('.', '/') + ';');
  }

  public static Type getTypeForSoyType(SoyType type) {
    switch (type.getKind()) {
      case INT:
        return BOXED_LONG_TYPE;
      case FLOAT:
        return BOXED_DOUBLE_TYPE;
      case BOOL:
        return BOXED_BOOLEAN_TYPE;
      case STRING:
        return STRING_TYPE;
      case PROTO:
        return getTypeForClassName(
            JavaQualifiedNames.getClassName(((SoyProtoType) type).getDescriptor()));
      case PROTO_ENUM:
        return getTypeForClassName(
            JavaQualifiedNames.getClassName(((SoyProtoEnumType) type).getDescriptor()));
      default:
        throw new IllegalArgumentException("unsupported type: " + type);
    }
  }

  /** Converts int to Integer, long to Long, etc. Java "boxing", not Soy "boxing". */
  public static Expression boxJavaPrimitive(SoyExpression actualParam) {
    return boxJavaPrimitive(actualParam.soyRuntimeType().runtimeType(), actualParam);
  }

  public static Expression boxJavaPrimitive(Type type, Expression expr) {
    switch (type.getSort()) {
      case Type.INT:
        return MethodRef.BOX_INTEGER.invoke(expr);
      case Type.LONG:
        return MethodRef.BOX_LONG.invoke(expr);
      case Type.BOOLEAN:
        return MethodRef.BOX_BOOLEAN.invoke(expr);
      case Type.FLOAT:
        return MethodRef.BOX_FLOAT.invoke(expr);
      case Type.DOUBLE:
        return MethodRef.BOX_DOUBLE.invoke(expr);
      default:
        throw new IllegalArgumentException(type.getClassName());
    }
  }

  public static Expression unboxJavaPrimitive(Type type, Expression expr) {
    switch (type.getSort()) {
      case Type.INT:
        return MethodRef.NUMBER_INT_VALUE.invoke(expr.checkedCast(BytecodeUtils.NUMBER_TYPE));
      case Type.LONG:
        return MethodRef.NUMBER_LONG_VALUE.invoke(expr.checkedCast(BytecodeUtils.NUMBER_TYPE));
      case Type.BOOLEAN:
        return MethodRef.BOOLEAN_VALUE.invoke(expr.checkedCast(BytecodeUtils.BOXED_BOOLEAN_TYPE));
      case Type.FLOAT:
        return MethodRef.NUMBER_FLOAT_VALUE.invoke(expr.checkedCast(BytecodeUtils.NUMBER_TYPE));
      case Type.DOUBLE:
        return MethodRef.NUMBER_DOUBLE_VALUE.invoke(expr.checkedCast(BytecodeUtils.NUMBER_TYPE));
      default:
        throw new IllegalArgumentException(type.getClassName());
    }
  }
}
