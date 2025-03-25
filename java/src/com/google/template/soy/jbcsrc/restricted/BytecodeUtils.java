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
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.newLabel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.primitives.Ints;
import com.google.protobuf.Message;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.FunctionValue;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyIterable;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoySet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.internal.Converters;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.GbigintData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NullishData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.runtime.DetachableContentProvider;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.ExtraConstantBootstraps;
import com.google.template.soy.jbcsrc.shared.LargeStringConstantFactory;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.logging.LoggableElementMetadata;
import com.google.template.soy.msgs.restricted.SoyMsgRawParts;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.ibm.icu.util.ULocale;
import java.io.Closeable;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** A set of utilities for generating simple expressions in bytecode */
public final class BytecodeUtils {
  // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.11
  private static final int MAX_CONSTANT_STRING_LENGTH = 65535;

  public static final TypeInfo OBJECT = TypeInfo.create(Object.class);
  private static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);

  public static final Type LOGGING_ADVISING_APPENDABLE_TYPE =
      Type.getType(LoggingAdvisingAppendable.class);
  public static final Type BUFFERING_APPENDABLE_TYPE =
      Type.getType(LoggingAdvisingAppendable.BufferingAppendable.class);
  public static final Type MULTIPLEXING_APPENDABLE_TYPE =
      Type.getType(DetachableContentProvider.MultiplexingAppendable.class);
  public static final Type COMPILED_TEMPLATE_TYPE = Type.getType(CompiledTemplate.class);
  public static final Type TEMPLATE_VALUE_TYPE = Type.getType(TemplateValue.class);
  public static final Type CONTENT_KIND_TYPE = Type.getType(ContentKind.class);
  public static final Type CLOSEABLE_TYPE = Type.getType(Closeable.class);
  public static final Type DIR_TYPE = Type.getType(Dir.class);
  public static final Type HASH_MAP_TYPE = Type.getType(HashMap.class);

  public static final Type NULLISH_DATA_TYPE = Type.getType(NullishData.class);
  public static final Type NULL_DATA_TYPE = Type.getType(NullData.class);
  public static final Type UNDEFINED_DATA_TYPE = Type.getType(UndefinedData.class);
  public static final Type PRIMITIVE_DATA_TYPE = Type.getType(PrimitiveData.class);
  public static final Type NUMBER_DATA_TYPE = Type.getType(NumberData.class);
  public static final Type INTEGER_DATA_TYPE = Type.getType(IntegerData.class);
  public static final Type BIG_INTEGER_TYPE = Type.getType(BigInteger.class);
  public static final Type GBIGINT_DATA_TYPE = Type.getType(GbigintData.class);
  public static final Type FLOAT_DATA_TYPE = Type.getType(FloatData.class);
  public static final Type BOOLEAN_DATA_TYPE = Type.getType(BooleanData.class);
  public static final Type STRING_DATA_TYPE = Type.getType(StringData.class);
  public static final Type FUNCTION_VALUE_TYPE = Type.getType(FunctionValue.class);
  public static final Type SANITIZED_CONTENT_TYPE = Type.getType(SanitizedContent.class);
  public static final Type SOY_LIST_TYPE = Type.getType(SoyList.class);
  public static final Type SOY_SET_TYPE = Type.getType(SoySet.class);
  public static final Type SOY_LEGACY_OBJECT_MAP_TYPE = Type.getType(SoyLegacyObjectMap.class);
  public static final Type SOY_MAP_TYPE = Type.getType(SoyMap.class);
  public static final Type SOY_MAP_IMPL_TYPE = Type.getType(SoyMapImpl.class);
  public static final Type SOY_PROTO_VALUE_TYPE = Type.getType(SoyProtoValue.class);
  public static final Type SOY_RECORD_TYPE = Type.getType(SoyRecord.class);
  public static final Type SOY_RECORD_IMPL_TYPE = Type.getType(SoyRecordImpl.class);
  public static final Type SOY_VALUE_TYPE = Type.getType(SoyValue.class);
  public static final Type SOY_VALUE_PROVIDER_TYPE = Type.getType(SoyValueProvider.class);

  public static final Type LINKED_HASH_MAP_TYPE = Type.getType(LinkedHashMap.class);
  public static final Type IDENTITY_HASH_MAP_TYPE = Type.getType(IdentityHashMap.class);
  public static final Type COLLECTION_TYPE = Type.getType(Collection.class);
  public static final Type ITERABLE_TYPE = Type.getType(Iterable.class);
  public static final Type SOY_ITERABLE_TYPE = Type.getType(SoyIterable.class);
  public static final Type LIST_TYPE = Type.getType(List.class);
  public static final Type SET_TYPE = Type.getType(Set.class);
  public static final Type ITERATOR_TYPE = Type.getType(Iterator.class);
  public static final Type IMMUTABLE_LIST_TYPE = Type.getType(ImmutableList.class);
  public static final Type IMMUTABLE_SET_MULTIMAP_TYPE = Type.getType(ImmutableSetMultimap.class);
  public static final Type IMMUTABLE_MAP_TYPE = Type.getType(ImmutableMap.class);
  public static final Type MAP_TYPE = Type.getType(Map.class);
  public static final Type FUTURE_TYPE = Type.getType(Future.class);
  public static final Type TO_INT_FUNCTION_TYPE = Type.getType(ToIntFunction.class);
  public static final Type MAP_ENTRY_TYPE = Type.getType(Map.Entry.class);
  public static final Type MESSAGE_TYPE = Type.getType(Message.class);
  public static final Type NULL_POINTER_EXCEPTION_TYPE = Type.getType(NullPointerException.class);
  public static final Type RENDER_CONTEXT_TYPE = Type.getType(RenderContext.class);
  public static final Type RENDER_RESULT_TYPE = Type.getType(RenderResult.class);
  public static final Type PARAM_STORE_TYPE = Type.getType(ParamStore.class);
  public static final Type STRING_TYPE = Type.getType(String.class);
  public static final Type THROWABLE_TYPE = Type.getType(Throwable.class);
  public static final Type ILLEGAL_STATE_EXCEPTION_TYPE = Type.getType(IllegalStateException.class);
  public static final Type SOY_VISUAL_ELEMENT_TYPE = Type.getType(SoyVisualElement.class);
  public static final Type SOY_VISUAL_ELEMENT_DATA_TYPE = Type.getType(SoyVisualElementData.class);
  public static final Type CLASS_TYPE = Type.getType(Class.class);
  public static final Type BOXED_INTEGER_TYPE = Type.getType(Integer.class);
  public static final Type BOXED_LONG_TYPE = Type.getType(Long.class);
  public static final Type BOXED_BOOLEAN_TYPE = Type.getType(Boolean.class);
  public static final Type BOXED_DOUBLE_TYPE = Type.getType(Double.class);
  public static final Type BOXED_CHARACTER_TYPE = Type.getType(Character.class);
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
  public static final Type RECORD_SYMBOL_TYPE = Type.getType(RecordProperty.class);
  public static final Type SOY_MSG_RAW_PARTS_TYPE = Type.getType(SoyMsgRawParts.class);
  public static final Type ULOCALE_TYPE = Type.getType(ULocale.class);

  public static final Method CLASS_INIT = Method.getMethod("void <clinit>()");
  public static final Method NULLARY_INIT = Method.getMethod("void <init>()");

  private static final Handle LARGE_STRING_CONSTANT_HANDLE =
      MethodRef.createPure(
              LargeStringConstantFactory.class,
              "bootstrapLargeStringConstant",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              String[].class)
          .asHandle();

  private static final Handle NULL_CONSTANT_HANDLE =
      MethodRef.createPure(
              ConstantBootstraps.class,
              "nullConstant",
              MethodHandles.Lookup.class,
              String.class,
              Class.class)
          .asHandle();

  private static final Handle CHAR_CONSTANT_HANDLE =
      MethodRef.createPure(
              ExtraConstantBootstraps.class,
              "constantBoolean",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              int.class)
          .asHandle();

  private static final Handle BOOLEAN_CONSTANT_HANDLE =
      MethodRef.createPure(
              ExtraConstantBootstraps.class,
              "constantBoolean",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              int.class)
          .asHandle();

  private static final Handle CONSTANT_IMMUTABLE_LIST =
      MethodRef.createPure(
              ExtraConstantBootstraps.class,
              "constantList",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              Object[].class)
          .asHandle();

  // Booleans are modeled as integers in the bytecode but in the constant dynamic api we
  // need to use actual boolean values
  public static final Expression.ConstantValue CONSTANT_FALSE =
      Expression.ConstantValue.raw(
          false,
          new ConstantDynamic(
              "false", Type.BOOLEAN_TYPE.getDescriptor(), BOOLEAN_CONSTANT_HANDLE, 0),
          Type.BOOLEAN_TYPE,
          /* isTrivialConstant= */ true);

  public static final Expression.ConstantValue CONSTANT_TRUE =
      Expression.ConstantValue.raw(
          true,
          new ConstantDynamic(
              "true", Type.BOOLEAN_TYPE.getDescriptor(), BOOLEAN_CONSTANT_HANDLE, 1),
          Type.BOOLEAN_TYPE,
          /* isTrivialConstant= */ true);

  private static final Handle RECORD_SYMBOL_CONSTANT_HANDLE =
      MethodRef.createPure(
              ExtraConstantBootstraps.class,
              "symbol",
              MethodHandles.Lookup.class,
              String.class,
              Class.class)
          .asHandle();

  private static final Handle CONSTANT_PARAM_STORE =
      MethodRef.createPure(
              ExtraConstantBootstraps.class,
              "constantParamStore",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              Object[].class)
          .asHandle();

  private static final Handle CONSTANT_RECORD_HANDLE =
      MethodRef.createPure(
              ExtraConstantBootstraps.class,
              "constantSoyRecord",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              int.class,
              Object[].class)
          .asHandle();

  private static final LoadingCache<Type, Optional<Class<?>>> objectTypeToClassCache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<>() {
                @Override
                public Optional<Class<?>> load(Type key) {
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
                        if (Names.isGenerated(key)) {
                          // if the class is generated, don't try to look it up.
                          // It might actually succeed in a case where we have the class on our
                          // classpath already!
                          return Optional.empty();
                        }
                        return Optional.of(
                            Class.forName(
                                key.getClassName(),
                                /* initialize= */ false,
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

  private static final ImmutableList<Type> SUPERTYPES_TO_CHECK =
      ImmutableList.of(NULLISH_DATA_TYPE, PRIMITIVE_DATA_TYPE, SOY_VALUE_TYPE);

  public static Type getCommonSuperType(Type left, Type right) {
    if (left.equals(right)) {
      return left;
    }
    for (Type type : SUPERTYPES_TO_CHECK) {
      if (isDefinitelyAssignableFrom(type, left) && isDefinitelyAssignableFrom(type, right)) {
        return type;
      }
    }
    if ((isDefinitelyAssignableFrom(SOY_VALUE_PROVIDER_TYPE, left)
            || Names.isGeneratedSoyValueProvider(left))
        && (isDefinitelyAssignableFrom(SOY_VALUE_PROVIDER_TYPE, right)
            || Names.isGeneratedSoyValueProvider(right))) {
      return SOY_VALUE_PROVIDER_TYPE;
    }
    throw new IllegalArgumentException(String.format("%s != %s", left, right));
  }

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
    if (left.getSort() != Type.OBJECT) {
      return false; // all other sorts require exact equality (even arrays)
    }
    // for object types we really need to know type hierarchy information to test for whether
    // right is assignable to left.
    Optional<Class<?>> leftClass = objectTypeToClassCache.getUnchecked(left);
    Optional<Class<?>> rightClass = objectTypeToClassCache.getUnchecked(right);
    if (leftClass.isEmpty() || rightClass.isEmpty()) {
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
    return objectTypeToClassCache
        .getUnchecked(type)
        .orElseThrow(() -> new IllegalArgumentException("Could not load: " + type));
  }

  /** Returns an {@link Expression} that can load the given constant. */
  public static Expression constant(ConstantDynamic value, Features features) {
    return constant(Type.getType(value.getDescriptor()), value, features);
  }

  /** Returns an {@link Expression} that can load the given constant. */
  public static Expression constant(Type type, ConstantDynamic value, Features features) {
    checkArgument(!value.getName().equals("<init>"));

    return new Expression(
        type,
        // There is no benefit with transforming to an ldc command because we are already there.
        Expression.ConstantValue.dynamic(value, type, /* isTrivialConstant= */ true),
        features.plus(Feature.CHEAP)) {
      @Override
      protected void doGen(CodeBuilder cb) {
        cb.visitLdcInsn(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given boolean constant. */
  public static Expression constant(boolean value) {
    return (value ? Branch.always() : Branch.never()).asBoolean();
  }

  /** Returns an {@link Expression} that can load the given int constant. */
  public static Expression constant(int value) {
    return new Expression(
        Type.INT_TYPE,
        Expression.ConstantValue.raw(value, Type.INT_TYPE),
        Feature.CHEAP.asFeatures()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushInt(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given char constant. */
  public static Expression constant(final char value) {
    return new Expression(
        Type.CHAR_TYPE,
        // Characters are modeled as integers in the bytecode but in the constant dynamic api we
        // need to use actual char values, use a custom bootstrap
        Expression.ConstantValue.raw(
            value,
            new ConstantDynamic(
                "char", Type.CHAR_TYPE.getDescriptor(), CHAR_CONSTANT_HANDLE, (int) value),
            Type.CHAR_TYPE,
            /* isTrivialConstant= */ true),
        Feature.CHEAP.asFeatures()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushInt(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given long constant. */
  public static Expression constant(long value) {
    return new Expression(
        Type.LONG_TYPE,
        Expression.ConstantValue.raw(value, Type.LONG_TYPE),
        Feature.CHEAP.asFeatures()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushLong(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given double constant. */
  public static Expression constant(double value) {
    return new Expression(
        Type.DOUBLE_TYPE,
        Expression.ConstantValue.raw(value, Type.DOUBLE_TYPE),
        Feature.CHEAP.asFeatures()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushDouble(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given double constant. */
  public static Expression constant(float value) {
    return new Expression(Type.FLOAT_TYPE, Feature.CHEAP.asFeatures()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushFloat(value);
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
    if (stringConstants.size() == 1) {
      String basicString = stringConstants.get(0);
      return new Expression(
          STRING_TYPE,
          Expression.ConstantValue.raw(basicString, STRING_TYPE),
          Features.of(Feature.CHEAP, Feature.NON_JAVA_NULLABLE)) {
        @Override
        protected void doGen(CodeBuilder mv) {
          mv.visitLdcInsn(basicString);
        }
      };
    }
    return constant(
        STRING_TYPE,
        new ConstantDynamic(
            "largeString",
            STRING_TYPE.getDescriptor(),
            LARGE_STRING_CONSTANT_HANDLE,
            stringConstants.toArray()),
        Feature.NON_JAVA_NULLABLE.asFeatures());
  }

  /** Returns an {@link Expression} that evaluates to the given ContentKind, or null. */
  public static Expression constant(ContentKind kind) {
    return FieldRef.enumReference(kind).accessor();
  }

  /** Returns an {@link Expression} that evaluates to the given Dir, or null. */
  public static Expression constant(@Nullable Dir dir) {
    return (dir == null) ? constantNull(DIR_TYPE) : FieldRef.enumReference(dir).accessor();
  }

  public static Expression constant(Type type) {
    return new Expression(CLASS_TYPE, Features.of(Feature.CHEAP, Feature.NON_JAVA_NULLABLE)) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushType(type);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given `RecordSymbol` constant. */
  public static Expression constantRecordProperty(String value) {
    return constant(
        new ConstantDynamic(
            value, RECORD_SYMBOL_TYPE.getDescriptor(), RECORD_SYMBOL_CONSTANT_HANDLE),
        Features.of(Feature.NON_JAVA_NULLABLE, Feature.CHEAP));
  }

  /**
   * Returns an {@link Expression} that evaluates to the {@link ContentKind} value that is
   * equivalent to the given {@link SanitizedContentKind}.
   */
  public static Expression constantSanitizedContentKindAsContentKind(SanitizedContentKind kind) {
    return FieldRef.enumReference(Converters.toContentKind(kind)).accessor();
  }

  /**
   * If a type has a sole instance associated with it, e.g. private constructor and INSTANCE field,
   * return an expression of the instance.
   */
  public static Optional<Expression> getSoleValue(Type type) {
    if (type.equals(BytecodeUtils.NULL_DATA_TYPE)) {
      return Optional.of(soyNull());
    } else if (type.equals(BytecodeUtils.UNDEFINED_DATA_TYPE)) {
      return Optional.of(soyUndefined());
    }
    return Optional.empty();
  }

  private static final Expression SOY_NULL = FieldRef.NULL_DATA.accessor();

  public static Expression soyNull() {
    return SOY_NULL;
  }

  private static final Expression SOY_UNDEFINED = FieldRef.UNDEFINED_DATA.accessor();

  public static Expression soyUndefined() {
    return SOY_UNDEFINED;
  }

  /** Returns an {@link Expression} with the given type that always returns null. */
  public static Expression constantNull(Type type) {
    checkArgument(
        type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY,
        "%s is not a reference type",
        type);
    return new Expression(
        type,
        Expression.ConstantValue.raw(
            null,
            new ConstantDynamic("null", type.getDescriptor(), NULL_CONSTANT_HANDLE),
            type,
            /* isTrivialConstant= */ true),
        Feature.CHEAP.asFeatures()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        mv.pushNull();
      }
    };
  }

  public static Expression instanceOf(Expression expr, Type type) {
    return new Expression(Type.BOOLEAN_TYPE, expr.features()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        expr.gen(adapter);
        adapter.instanceOf(type);
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
    // support constant expressions since soy primitives are always long/double and may need
    // conversion to work in other contexts
    if (expr.isConstant()
        && expr.constantValue().hasJavaValue()
        && expr.constantValue().getJavaValue() instanceof Number) {
      var value = (Number) expr.constantValue().getJavaValue();
      switch (to.getSort()) {
        case Type.DOUBLE:
          return constant(value.doubleValue());
        case Type.INT:
          return constant(value.intValue());
        case Type.LONG:
          return constant(value.longValue());
        case Type.FLOAT:
          return constant(value.floatValue());
        case Type.SHORT:
        case Type.BYTE:
        case Type.CHAR:
          throw new UnsupportedOperationException("unimplemented");
        default:
          throw new AssertionError("unexpected type " + to);
      }
    }
    return new Expression(to, expr.features()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        expr.gen(adapter);
        adapter.cast(expr.resultType(), to);
      }
    };
  }

  static boolean isNumericPrimitive(Type type) {
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
    Label end = newLabel();
    LocalVariable thisVar = LocalVariable.createThisVar(ownerType, start, end);
    thisVar.loadUnchecked(mg);
    mg.invokeConstructor(OBJECT.type(), NULLARY_INIT);
    mg.returnValue();
    mg.mark(end);
    thisVar.tableEntry(mg);
    mg.endMethod();
  }

  public static Expression compareSoyEquals(SoyExpression left, SoyExpression right) {
    // We can special case when we know the types.
    // If either is a string, we run special logic so test for that first
    // otherwise we special case primitives and eventually fall back to our runtime.
    SoyRuntimeType leftRuntimeType = left.soyRuntimeType();
    SoyRuntimeType rightRuntimeType = right.soyRuntimeType();
    if (leftRuntimeType.isKnownString() || rightRuntimeType.isKnownString()) {
      return doEqualsString(left, right);
    }
    Expression numberComparison = maybeFastCompareNumbers(left, right);
    if (numberComparison != null) {
      return numberComparison;
    }
    return MethodRefs.RUNTIME_EQUAL.invoke(left.box(), right.box());
  }

  /** Compares two {@link SoyExpression}s for equality using soy === semantics. */
  public static Expression compareSoyTripleEquals(SoyExpression left, SoyExpression right) {
    Expression numberComparison = maybeFastCompareNumbers(left, right);
    if (numberComparison != null) {
      return numberComparison;
    }
    return MethodRefs.RUNTIME_TRIPLE_EQUAL.invoke(left.box(), right.box());
  }

  /**
   * Same as {@link #compareSoyTripleEquals} except it has special handling for sanitized types and
   * strings.
   */
  public static Expression compareSoySwitchCaseEquals(SoyExpression left, SoyExpression right) {
    Expression numberComparison = maybeFastCompareNumbers(left, right);
    if (numberComparison != null) {
      return numberComparison;
    }
    return MethodRefs.RUNTIME_SWITCH_CASE_EQUAL.invoke(left.box(), right.box());
  }

  @Nullable
  private static Expression maybeFastCompareNumbers(SoyExpression left, SoyExpression right) {
    SoyRuntimeType leftRuntimeType = left.soyRuntimeType();
    SoyRuntimeType rightRuntimeType = right.soyRuntimeType();
    if (leftRuntimeType.isKnownInt()
        && rightRuntimeType.isKnownInt()
        && left.isNonSoyNullish()
        && right.isNonSoyNullish()) {
      return Branch.ifEqual(left.unboxAsLong(), right.unboxAsLong()).asBoolean();
    }
    if (leftRuntimeType.isKnownNumber()
        && rightRuntimeType.isKnownNumber()
        && left.isNonSoyNullish()
        && right.isNonSoyNullish()
        && (leftRuntimeType.isKnownFloat() || rightRuntimeType.isKnownFloat())) {
      return Branch.ifEqual(left.coerceToDouble(), right.coerceToDouble()).asBoolean();
    }
    return null;
  }

  /**
   * Compare a string valued expression to another expression using soy == semantics.
   *
   * @param left An expression that is known to be an unboxed string
   * @param right An expression to compare it to.
   */
  private static Expression doEqualsString(SoyExpression left, SoyExpression right) {
    if (left.resultType().equals(STRING_TYPE)) {
      if (right.resultType().equals(STRING_TYPE)) {
        return doJavaEquals(left, right);
      } else {
        return doUnboxedStringEquals(left, right);
      }
    } else if (right.resultType().equals(STRING_TYPE)) {
      return doBoxedValueEqualsUnboxedString(left, right);
    }

    // neither is an unboxed string
    if (left.soyRuntimeType().isKnownString()) {
      return doBoxedStringEquals(left, right);
    } else {
      return doValueEqualsBoxedString(left, right);
    }
  }

  private static Expression doUnboxedStringEquals(
      SoyExpression unboxedString, SoyExpression other) {
    if (other.soyRuntimeType().isKnownNumber() && other.isNonSoyNullish()) {
      // in this case, we actually try to convert stringExpr to a number
      return MethodRefs.RUNTIME_STRING_EQUALS_AS_NUMBER.invoke(
          unboxedString, other.coerceToDouble());
    }
    if (!other.isBoxed()) {
      // TODO - b/296964679: This is a similar problem where analysis assumes these expression get
      // evaluated but they don't actually. Can we make this an error instead?
      return constant(false);
    }
    return MethodRefs.RUNTIME_COMPARE_UNBOXED_STRING.invoke(unboxedString, other);
  }

  private static Expression doBoxedValueEqualsUnboxedString(
      SoyExpression other, SoyExpression unboxedString) {
    if (other.soyRuntimeType().isKnownNumber() && other.isNonSoyNullish()) {
      // in this case, we actually try to convert stringExpr to a number
      return MethodRefs.RUNTIME_NUMBER_EQUALS_STRING_AS_NUMBER.invoke(
          other.coerceToDouble(), unboxedString);
    }
    if (!other.isBoxed()) {
      // TODO - b/296964679: This is a similar problem where analysis assumes these expression get
      // evaluated but they don't actually. Can we make this an error instead?
      return constant(false);
    }
    return MethodRefs.RUNTIME_COMPARE_BOXED_VALUE_TO_UNBOXED_STRING.invoke(other, unboxedString);
  }

  private static Expression doBoxedStringEquals(SoyExpression boxedString, SoyExpression other) {
    if (other.soyRuntimeType().isKnownNumber()) {
      other = other.box();
    } else if (!other.isBoxed()) {
      // TODO - b/296964679: This is a similar problem where analysis assumes these expression get
      // evaluated but they don't actually. Can we make this an error instead?
      return constant(false);
    }
    return MethodRefs.RUNTIME_COMPARE_BOXED_STRING.invoke(boxedString, other);
  }

  private static Expression doValueEqualsBoxedString(
      SoyExpression other, SoyExpression boxedString) {
    if (other.soyRuntimeType().isKnownNumber()) {
      other = other.box();
    } else if (!other.isBoxed()) {
      // TODO - b/296964679: This is a similar problem where analysis assumes these expression get
      // evaluated but they don't actually. Can we make this an error instead?
      return constant(false);
    }
    return MethodRefs.RUNTIME_COMPARE_BOXED_VALUE_TO_BOXED_STRING.invoke(other, boxedString);
  }

  private static Expression doJavaEquals(SoyExpression left, SoyExpression right) {
    // NOTE: don't do anything clever here, like switch the order to right.equals(left) if left is
    // nullable and right isn't. The gencode depends on the execution order so we must maintain
    // that.
    return MethodRefs.OBJECTS_EQUALS.invoke(left, right);
  }

  /**
   * Returns an expression that evaluates to {@code left} if left is non null, and evaluates to
   * {@code right} otherwise.
   */
  public static Expression firstSoyNonNullish(Expression left, Expression right) {
    checkArgument(
        left.resultType().getSort() == Type.OBJECT,
        "Expected left to be an object, got: %s",
        left.resultType());
    checkArgument(
        right.resultType().getSort() == Type.OBJECT,
        "Expected right to be an object, got: %s",
        right.resultType());
    Features features = Features.of();
    if (Expression.areAllCheap(left, right)) {
      features = features.plus(Feature.CHEAP);
    }
    if (right.isNonSoyNullish()) {
      features = features.plus(Feature.NON_SOY_NULLISH);
    }
    return new Expression(getCommonSuperType(left.resultType(), right.resultType()), features) {
      @Override
      protected void doGen(CodeBuilder cb) {
        Label leftIsNonNull = newLabel();
        left.gen(cb); // Stack: L
        cb.dup(); // Stack: L, L
        ifNonNullish(cb, left.resultType(), leftIsNonNull); // Stack: L
        // pop the extra copy of left
        cb.pop(); // Stack:
        right.gen(cb); // Stack: R
        cb.mark(leftIsNonNull); // At this point the stack has an instance of L or R
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
    // Use a custom bootstrap for a constant list if all the items are constant. This is more
    // efficient than the typical invoke helper that we might get and it scales to more parameters.
    Expression.ConstantValue constantValue =
        Expression.areAllConstant(copy)
            ? Expression.ConstantValue.dynamic(
                new ConstantDynamic(
                    "constantList",
                    BytecodeUtils.IMMUTABLE_LIST_TYPE.getDescriptor(),
                    CONSTANT_IMMUTABLE_LIST,
                    copy.stream().map(Expression::constantBytecodeValue).toArray(Object[]::new)),
                BytecodeUtils.IMMUTABLE_LIST_TYPE,
                /* isTrivialConstant= */ false)
            : null;
    Expression list;
    if (copy.size() < MethodRefs.IMMUTABLE_LIST_OF.size()) {
      list = MethodRefs.IMMUTABLE_LIST_OF.get(copy.size()).invoke(copy);
    } else {
      ImmutableList<Expression> explicit = copy.subList(0, MethodRefs.IMMUTABLE_LIST_OF.size());
      Expression remainder =
          asArray(
              OBJECT_ARRAY_TYPE, copy.subList(MethodRefs.IMMUTABLE_LIST_OF.size(), copy.size()));
      list =
          MethodRefs.IMMUTABLE_LIST_OF_ARRAY.invoke(
              Iterables.concat(explicit, ImmutableList.of(remainder)));
    }
    if (constantValue != null) {
      // Use our own constant value which is smaller and supports a broader range of sizes. (the
      // IMMUTABLE_LIST_OF_ARRAY methodref is not currently constant foldable due to its array
      // argument.)
      list = list.withConstantValue(constantValue);
    }
    return list;
  }

  public static SoyExpression newRecordImplFromParamStore(
      SoyType soyType, SourceLocation location, Expression paramStore) {
    var recordExp =
        SoyExpression.forSoyValue(soyType, MethodRefs.SOY_RECORD_IMPL.invoke(paramStore));
    if (paramStore.isConstant()) {
      var paramCondy = (ConstantDynamic) paramStore.constantBytecodeValue();
      checkState(
          Objects.equals(paramCondy.getBootstrapMethod(), CONSTANT_PARAM_STORE)); // sanity check
      Object[] args = new Object[1 + paramCondy.getBootstrapMethodArgumentCount()];
      // We store a hash of the source location so that each distinct list authored
      // gets a unique value to preserve identity semantics even in constant settings
      args[0] = location.hashCode();
      for (int i = 0; i < paramCondy.getBootstrapMethodArgumentCount(); i++) {
        args[i + 1] = paramCondy.getBootstrapMethodArgument(i);
      }
      // just attach the constant value, it may or may not be appropriate to use it depending
      // on the context.
      recordExp =
          recordExp.withConstantValue(
              Expression.ConstantValue.dynamic(
                  new ConstantDynamic(
                      "soyRecord",
                      BytecodeUtils.SOY_RECORD_IMPL_TYPE.getDescriptor(),
                      CONSTANT_RECORD_HANDLE,
                      args),
                  BytecodeUtils.SOY_RECORD_IMPL_TYPE,
                  /* isTrivialConstant= */ false));
    }
    return recordExp;
  }

  /**
   * Construct a ParamStore
   *
   * <p>The values in the params map must either be `SoyValueProvider` expressions or be
   * SoyExpression instances that can be trivially coerced to SoyValueProvider
   */
  public static Expression newParamStore(
      Optional<Expression> baseStore, Map<String, Expression> params) {
    baseStore.ifPresent(e -> e.checkAssignableTo(BytecodeUtils.PARAM_STORE_TYPE));
    if (params.isEmpty()) {
      return baseStore.orElse(FieldRef.EMPTY_PARAMS.accessor());
    }
    // NOTE: we can always represent ParamStores as a constant
    if (Expression.areAllConstant(params.values())
        && baseStore.map(Expression::isConstant).orElse(true)) {
      Object[] constantArgs = new Object[params.size() * 2 + (baseStore.isPresent() ? 1 : 0)];
      int i = 0;
      if (baseStore.isPresent()) {
        constantArgs[i++] = baseStore.get().constantBytecodeValue();
      }
      for (var entry : params.entrySet()) {
        // We could pass constantRecordSymbol(entry.getKey()).constantBytecodeValue() but it is more
        // efficient to invoke one bulk bootstrap method than a lot of little ones.  Caching within
        // `RecordSymbol` itself ensures we don't construct duplicate keys.
        constantArgs[i++] = entry.getKey();
        constantArgs[i++] = entry.getValue().constantBytecodeValue();
      }
      return constant(
          BytecodeUtils.PARAM_STORE_TYPE,
          new ConstantDynamic(
              "constantParamStore",
              BytecodeUtils.PARAM_STORE_TYPE.getDescriptor(),
              CONSTANT_PARAM_STORE,
              constantArgs),
          Features.of(Feature.NON_JAVA_NULLABLE, Feature.NON_SOY_NULLISH));
    }
    var paramStore =
        baseStore.isPresent()
            ? MethodRefs.PARAM_STORE_AUGMENT.invoke(baseStore.get(), constant(params.size()))
            : MethodRefs.PARAM_STORE_SIZE.invoke(constant(params.size()));

    for (var entry : params.entrySet()) {
      var value = entry.getValue();
      if (value instanceof SoyExpression) {
        value = ((SoyExpression) value).box();
      }
      var key = constantRecordProperty(entry.getKey());
      paramStore = MethodRefs.PARAM_STORE_SET_FIELD.invoke(paramStore, key, value);
    }

    return paramStore;
  }

  public static Expression newImmutableMap(
      List<Expression> keys, List<Expression> values, boolean allowDuplicates) {
    // We can only call ImmutableList.of if we disallow duplicates or there are 1 or 0 keys
    if (keys.size() < MethodRefs.IMMUTABLE_MAP_OF.size()
        && (keys.size() <= 1 || !allowDuplicates)) {
      List<Expression> kvps = new ArrayList<>();
      for (int i = 0; i < keys.size(); i++) {
        kvps.add(keys.get(i));
        kvps.add(values.get(i));
      }
      return MethodRefs.IMMUTABLE_MAP_OF.get(keys.size()).invoke(kvps);
    }
    var builder = MethodRefs.IMMUTABLE_MAP_BUILDER_WITH_EXPECTED_SIZE.invoke(constant(keys.size()));
    for (int i = 0; i < keys.size(); i++) {
      builder = builder.invoke(MethodRefs.IMMUTABLE_MAP_BUILDER_PUT, keys.get(i), values.get(i));
    }
    return builder.invoke(
        allowDuplicates
            ? MethodRefs.IMMUTABLE_MAP_BUILDER_BUILD_KEEPING_LAST
            : MethodRefs.IMMUTABLE_MAP_BUILDER_BUILD_OR_THROW);
  }

  private static Expression asArray(Type arrayType, ImmutableList<? extends Expression> elements) {
    Type elementType = arrayType.getElementType();
    return new Expression(arrayType, Feature.NON_JAVA_NULLABLE.asFeatures()) {
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
    ImmutableList<Expression> copy = ImmutableList.copyOf(items);
    if (copy.isEmpty()) {
      return MethodRefs.IMMUTABLE_LIST_OF.get(0).invoke();
    }
    // Note, we cannot necessarily use ImmutableList for anything besides the empty list because
    // we may need to put a null in it.
    Expression construct = MethodRefs.ARRAY_LIST_SIZE.invoke(constant(copy.size()));
    return new Expression(LIST_TYPE, Feature.NON_JAVA_NULLABLE.asFeatures()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        construct.gen(mv);
        for (Expression child : copy) {
          mv.dup();
          child.gen(mv);
          MethodRefs.ARRAY_LIST_ADD.invokeUnchecked(mv);
          mv.pop(); // pop the bool result of arraylist.add
        }
      }
    };
  }

  /**
   * Tests the top of the stack for soy nullishness, exiting to nullExit with a NullData value at
   * the top of the stack.
   */
  public static void coalesceSoyNullishToSoyNull(
      CodeBuilder builder, Type argType, Label nullExit) {
    if (argType.equals(SOY_VALUE_TYPE)) {
      MethodRefs.SOY_VALUE_NULLISH_TO_NULL.invokeUnchecked(builder);
      builder.dup();
      MethodRefs.SOY_VALUE_IS_NULLISH.invokeUnchecked(builder);
      builder.ifZCmp(Opcodes.IFNE, nullExit);
    } else {
      nullCoalesce(builder, nullExit, argType, cb -> soyNull().gen(cb), /* ish= */ true);
    }
  }

  public static void coalesceSoyNullishToSoyUndefined(
      CodeBuilder builder, Type argType, Label nullExit) {
    if (argType.equals(SOY_VALUE_TYPE)) {
      MethodRefs.SOY_VALUE_NULLISH_TO_UNDEFINED.invokeUnchecked(builder);
      builder.dup();
      MethodRefs.SOY_VALUE_IS_NULLISH.invokeUnchecked(builder);
      builder.ifZCmp(Opcodes.IFNE, nullExit);
    } else {
      nullCoalesce(builder, nullExit, argType, cb -> soyUndefined().gen(cb), /* ish= */ true);
    }
  }

  /**
   * Tests the top of the stack for soy nullishness, exiting to nullExit with a Java null at the top
   * of the stack.
   */
  public static void coalesceSoyNullishToJavaNull(
      CodeBuilder builder, Type argType, Label nullExit) {
    nullCoalesce(builder, nullExit, argType, CodeBuilder::pushNull, /* ish= */ true);
  }

  public static void coalesceSoyNullToJavaNull(CodeBuilder builder, Type argType, Label nullExit) {
    nullCoalesce(builder, nullExit, argType, CodeBuilder::pushNull, /* ish= */ false);
  }

  private static void nullCoalesce(
      CodeBuilder builder,
      Label nullExit,
      Type argType,
      Consumer<CodeBuilder> pusher,
      boolean ish) {
    Label nonNull = newLabel();
    builder.dup();
    if (ish) {
      ifNonNullish(builder, argType, nonNull);
    } else {
      ifNonSoyNull(builder, argType, nonNull);
    }
    // See http://mail.ow2.org/wws/arc/asm/2016-02/msg00001.html for a discussion of this pattern
    // but even though the value at the top of the stack here is null, its type isn't.  So we need
    // to pop and push.  This is the idiomatic pattern.
    builder.pop();
    pusher.accept(builder);
    builder.goTo(nullExit);
    builder.mark(nonNull);
  }

  /** Like {@link #ifNonNullish} but tests on `SoyValue.isNull` rather than `SoyValue.isNullish`. */
  public static void ifNonSoyNull(CodeBuilder cb, Type argType, Label ifNonNull) {
    if (isDefinitelyAssignableFrom(SOY_VALUE_TYPE, argType)) {
      MethodRefs.SOY_VALUE_IS_NULL.invokeUnchecked(cb);
      cb.ifZCmp(Opcodes.IFEQ, ifNonNull);
    } else if (isDefinitelyAssignableFrom(SOY_VALUE_PROVIDER_TYPE, argType)) {
      MethodRefs.IS_SOY_NON_NULL.invokeUnchecked(cb);
      cb.ifZCmp(Opcodes.IFNE, ifNonNull);
    } else {
      cb.ifNonNull(ifNonNull);
    }
  }

  /**
   * Exits to ifNonNullish if the top of the stack is nullish. This works for SoyValue,
   * SoyValueProvider, and Object. Object assumes this is an unboxed value and compares with Java
   * null.
   */
  public static void ifNonNullish(CodeBuilder cb, Type argType, Label ifNonNullish) {
    if (isDefinitelyAssignableFrom(SOY_VALUE_TYPE, argType)) {
      MethodRefs.SOY_VALUE_IS_NULLISH.invokeUnchecked(cb);
      cb.ifZCmp(Opcodes.IFEQ, ifNonNullish);
    } else if (isDefinitelyAssignableFrom(SOY_VALUE_PROVIDER_TYPE, argType)) {
      MethodRefs.IS_SOY_NON_NULLISH.invokeUnchecked(cb);
      cb.ifZCmp(Opcodes.IFNE, ifNonNullish);
    } else {
      cb.ifNonNull(ifNonNullish);
    }
  }

  /** The inverse of {@link #ifNonNullish}. */
  public static void ifNullish(CodeBuilder cb, Type argType, Label ifNullish) {
    if (isDefinitelyAssignableFrom(SOY_VALUE_TYPE, argType)) {
      MethodRefs.SOY_VALUE_IS_NULLISH.invokeUnchecked(cb);
      cb.ifZCmp(Opcodes.IFNE, ifNullish);
    } else if (isDefinitelyAssignableFrom(SOY_VALUE_PROVIDER_TYPE, argType)) {
      MethodRefs.IS_SOY_NON_NULLISH.invokeUnchecked(cb);
      cb.ifZCmp(Opcodes.IFEQ, ifNullish);
    } else {
      cb.ifNull(ifNullish);
    }
  }

  /**
   * Returns an expression that returns a new {@link LinkedHashMap} containing all the given
   * entries.
   */
  public static Expression newLinkedHashMap(
      Iterable<? extends Expression> keys, Iterable<? extends Expression> values) {
    return newMap(
        keys,
        values,
        expectedSize ->
            MethodRefs.LINKED_HASH_MAP_CAPACITY.invoke(constant(hashMapCapacity(expectedSize))),
        LINKED_HASH_MAP_TYPE);
  }

  private static Expression newMap(
      Iterable<? extends Expression> keys,
      Iterable<? extends Expression> values,
      IntFunction<Expression> constructorWithExpectedSize,
      Type mapType) {
    ImmutableList<Expression> keysCopy = ImmutableList.copyOf(keys);
    ImmutableList<Expression> valuesCopy = ImmutableList.copyOf(values);
    checkArgument(keysCopy.size() == valuesCopy.size());
    for (int i = 0; i < keysCopy.size(); i++) {
      checkArgument(keysCopy.get(i).resultType().getSort() == Type.OBJECT);
      checkArgument(valuesCopy.get(i).resultType().getSort() == Type.OBJECT);
    }
    Expression construct = constructorWithExpectedSize.apply(keysCopy.size());
    return new Expression(mapType, Feature.NON_JAVA_NULLABLE.asFeatures()) {
      @Override
      protected void doGen(CodeBuilder mv) {
        construct.gen(mv);
        for (int i = 0; i < keysCopy.size(); i++) {
          Expression key = keysCopy.get(i);
          Expression value = valuesCopy.get(i);
          mv.dup();
          key.gen(mv);
          value.gen(mv);
          MethodRefs.MAP_PUT.invokeUnchecked(mv);
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
  public static SoyExpression isNonSoyNullish(Expression expr) {
    return SoyExpression.forBool(Branch.ifNonSoyNullish(expr).asBoolean());
  }

  /** Returns a {@link SoyExpression} that evaluates to true if the expression evaluated to null. */
  public static SoyExpression isSoyNullish(Expression expr) {
    return SoyExpression.forBool(Branch.ifNonSoyNullish(expr).negate().asBoolean());
  }

  public static SoyExpression isNonSoyNull(Expression expr) {
    return SoyExpression.forBool(Branch.ifNonSoyNull(expr).asBoolean());
  }

  public static SoyExpression isSoyNull(Expression expr) {
    return SoyExpression.forBool(Branch.ifNonSoyNull(expr).negate().asBoolean());
  }

  public static SoyExpression isNonSoyUndefined(Expression expr) {
    return SoyExpression.forBool(Branch.ifNonSoyUndefined(expr).asBoolean());
  }

  public static SoyExpression isSoyUndefined(Expression expr) {
    return SoyExpression.forBool(Branch.ifNonSoyUndefined(expr).negate().asBoolean());
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
      case GBIGINT:
        return BIG_INTEGER_TYPE;
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
        return MethodRefs.BOX_INTEGER.invoke(expr);
      case Type.LONG:
        return MethodRefs.BOX_LONG.invoke(expr);
      case Type.BOOLEAN:
        return MethodRefs.BOX_BOOLEAN.invoke(expr);
      case Type.FLOAT:
        return MethodRefs.BOX_FLOAT.invoke(expr);
      case Type.DOUBLE:
        return MethodRefs.BOX_DOUBLE.invoke(expr);
      default:
        throw new IllegalArgumentException(type.getClassName());
    }
  }

  public static Expression unboxJavaPrimitive(Type type, Expression expr) {
    switch (type.getSort()) {
      case Type.INT:
        return MethodRefs.NUMBER_INT_VALUE.invoke(expr.checkedCast(NUMBER_TYPE));
      case Type.LONG:
        return MethodRefs.NUMBER_LONG_VALUE.invoke(expr.checkedCast(NUMBER_TYPE));
      case Type.BOOLEAN:
        return MethodRefs.BOOLEAN_VALUE.invoke(expr.checkedCast(BOXED_BOOLEAN_TYPE));
      case Type.FLOAT:
        return MethodRefs.NUMBER_FLOAT_VALUE.invoke(expr.checkedCast(NUMBER_TYPE));
      case Type.DOUBLE:
        return MethodRefs.NUMBER_DOUBLE_VALUE.invoke(expr.checkedCast(NUMBER_TYPE));
      default:
        throw new IllegalArgumentException(type.getClassName());
    }
  }

  /**
   * Shared factory for constructing {@link Label}s.
   *
   * <p>Useful for debugging since we can tag labels with a stack trace which makes it easier to
   * debug 'duplicate label' and 'unvisited' label errors.
   */
  public static Label newLabel() {
    var label = new Label();
    if (Flags.DEBUG) {
      label.info = new Exception("Generated By Soy");
    } else {
      label.info = "Generated By Soy";
    }
    return label;
  }
}
