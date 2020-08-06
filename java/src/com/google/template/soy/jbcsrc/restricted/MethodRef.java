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

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.restricted.Expression.areAllCheap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLongs;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage.ExtendableMessage;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.ProtoFieldInterpreter;
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
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.LazyProtoToSoyValueList;
import com.google.template.soy.data.internal.LazyProtoToSoyValueMap;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.LegacyFunctionAdapter;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.logging.LoggableElementMetadata;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.shared.internal.SharedRuntime;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import java.io.Closeable;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** A reference to a method that can be called at runtime. */
@AutoValue
public abstract class MethodRef {

  public static final Type[] NO_METHOD_ARGS = {};

  public static final MethodRef ADVISING_STRING_BUILDER_GET_AND_CLEAR =
      create(LoggingAdvisingAppendable.BufferingAppendable.class, "getAndClearBuffer")
          .asNonNullable();

  public static final MethodRef ARRAY_LIST_ADD = create(ArrayList.class, "add", Object.class);

  public static final MethodRef BOOLEAN_DATA_FOR_VALUE =
      create(BooleanData.class, "forValue", boolean.class).asNonNullable();

  public static final MethodRef BOOLEAN_VALUE = create(Boolean.class, "booleanValue").asCheap();

  public static final MethodRef BOOLEAN_TO_STRING =
      create(Boolean.class, "toString", boolean.class).asCheap().asNonNullable();

  public static final MethodRef COMPILED_TEMPLATE_RENDER =
      create(CompiledTemplate.class, "render", LoggingAdvisingAppendable.class, RenderContext.class)
          .asNonNullable();

  public static final MethodRef DICT_IMPL_FOR_PROVIDER_MAP =
      create(DictImpl.class, "forProviderMap", Map.class, RuntimeMapTypeTracker.Type.class)
          .asNonNullable();

  public static final MethodRef MAP_IMPL_FOR_PROVIDER_MAP =
      create(SoyMapImpl.class, "forProviderMap", Map.class).asNonNullable();

  public static final MethodRef DOUBLE_TO_STRING =
      create(FloatData.class, "toString", double.class).asNonNullable();

  public static final MethodRef EQUALS = create(Object.class, "equals", Object.class);

  public static final MethodRef STRING_COMPARE_TO = create(String.class, "compareTo", String.class);

  public static final MethodRef FLOAT_DATA_FOR_VALUE =
      create(FloatData.class, "forValue", double.class).asNonNullable();

  /** a list of all the ImmutableList.of overloads, indexed by arity. */
  public static final ImmutableList<MethodRef> IMMUTABLE_LIST_OF;

  public static final MethodRef IMMUTABLE_LIST_OF_ARRAY;

  static {
    MethodRef[] immutableListOfMethods = new MethodRef[12];
    MethodRef immutableListOfArray = null;
    for (java.lang.reflect.Method m : ImmutableList.class.getMethods()) {
      if (m.getName().equals("of")) {
        Class<?>[] params = m.getParameterTypes();
        MethodRef ref = MethodRef.create(m).asNonNullable();
        if (params.length > 0 && params[params.length - 1].isArray()) {
          // skip the one that takes an array in the final position
          immutableListOfArray = ref;
          continue;
        }
        int arity = params.length;
        if (arity == 0) {
          // the zero arg one is 'cheap'
          ref = ref.asCheap();
        }
        immutableListOfMethods[arity] = ref;
      }
    }
    IMMUTABLE_LIST_OF_ARRAY = immutableListOfArray;
    IMMUTABLE_LIST_OF = ImmutableList.copyOf(immutableListOfMethods);
  }

  /**
   * A list of all the {@link ImmutableMap#of} overloads, indexed by number of key-value pairs.
   * (Note that this is a different indexing scheme than {@link #IMMUTABLE_LIST_OF}.)
   */
  public static final ImmutableList<MethodRef> IMMUTABLE_MAP_OF;

  static {
    MethodRef[] immutableMapOfMethods = new MethodRef[6];
    for (java.lang.reflect.Method m : ImmutableMap.class.getMethods()) {
      if (m.getName().equals("of")) {
        Class<?>[] params = m.getParameterTypes();
        MethodRef ref = MethodRef.create(m).asNonNullable();
        int arity = params.length;
        checkState(arity % 2 == 0);
        int numEntries = arity >> 1;
        if (numEntries == 0) {
          // the zero arg one is 'cheap'
          ref = ref.asCheap();
        }
        immutableMapOfMethods[numEntries] = ref;
      }
    }
    IMMUTABLE_MAP_OF = ImmutableList.copyOf(immutableMapOfMethods);
  }

  public static final MethodRef INTEGER_DATA_FOR_VALUE =
      create(IntegerData.class, "forValue", long.class).asNonNullable();

  public static final MethodRef INTS_CHECKED_CAST =
      create(Ints.class, "checkedCast", long.class).asCheap();

  public static final MethodRef MAP_PUT = create(Map.class, "put", Object.class, Object.class);

  public static final MethodRef LIST_GET = create(List.class, "get", int.class).asCheap();

  public static final MethodRef LIST_SIZE = create(List.class, "size").asCheap();

  public static final MethodRef MAP_SIZE = create(Map.class, "size").asCheap();

  public static final MethodRef MAP_ENTRY_SET = create(Map.class, "entrySet");

  public static final MethodRef GET_ITERATOR = create(Iterable.class, "iterator");

  public static final MethodRef ITERATOR_NEXT = create(Iterator.class, "next");

  public static final MethodRef ITERATOR_HAS_NEXT = create(Iterator.class, "hasNext");

  public static final MethodRef MAP_GET_KEY = create(Map.Entry.class, "getKey");

  public static final MethodRef MAP_GET_VALUE = create(Map.Entry.class, "getValue");

  public static final MethodRef LIST_IMPL_FOR_PROVIDER_LIST =
      create(ListImpl.class, "forProviderList", List.class);

  public static final MethodRef LONG_PARSE_LONG =
      create(Long.class, "parseLong", String.class).asCheap().asNonNullable();
  public static final MethodRef UNSIGNED_LONGS_PARSE_UNSIGNED_LONG =
      create(UnsignedLongs.class, "parseUnsignedLong", String.class).asCheap();
  public static final MethodRef UNSIGNED_LONGS_TO_STRING =
      create(UnsignedLongs.class, "toString", long.class).asCheap().asNonNullable();
  public static final MethodRef UNSIGNED_INTS_SATURATED_CAST =
      create(UnsignedInts.class, "saturatedCast", long.class).asCheap();
  public static final MethodRef UNSIGNED_INTS_TO_LONG =
      create(UnsignedInts.class, "toLong", int.class).asCheap();

  public static final MethodRef LONG_TO_STRING = create(Long.class, "toString", long.class);

  public static final MethodRef NUMBER_DOUBLE_VALUE = create(Number.class, "doubleValue").asCheap();

  public static final MethodRef NUMBER_LONG_VALUE = create(Number.class, "longValue").asCheap();
  public static final MethodRef NUMBER_INT_VALUE = create(Number.class, "intValue").asCheap();

  public static final MethodRef OBJECT_TO_STRING = create(Object.class, "toString");

  public static final MethodRef OBJECTS_EQUALS =
      create(Objects.class, "equals", Object.class, Object.class);

  public static final MethodRef ORDAIN_AS_SAFE =
      create(UnsafeSanitizedContentOrdainer.class, "ordainAsSafe", String.class, ContentKind.class);

  public static final MethodRef ORDAIN_AS_SAFE_DIR =
      create(
          UnsafeSanitizedContentOrdainer.class,
          "ordainAsSafe",
          String.class,
          ContentKind.class,
          Dir.class);

  public static final MethodRef PARAM_STORE_SET_FIELD =
      create(ParamStore.class, "setField", String.class, SoyValueProvider.class);

  public static final MethodRef PRINT_STREAM_PRINTLN = create(PrintStream.class, "println");

  public static final MethodRef SOY_PROTO_VALUE_CREATE =
      create(SoyProtoValue.class, "create", Message.class).asNonNullable();

  public static final MethodRef RENDER_RESULT_DONE =
      create(RenderResult.class, "done").asCheap().asNonNullable();

  public static final MethodRef RENDER_RESULT_IS_DONE =
      create(RenderResult.class, "isDone").asCheap();

  public static final MethodRef RENDER_RESULT_LIMITED =
      create(RenderResult.class, "limited").asCheap().asNonNullable();

  public static final MethodRef RUNTIME_APPLY_ESCAPERS =
      create(JbcSrcRuntime.class, "applyEscapers", CompiledTemplate.class, ImmutableList.class);

  public static final MethodRef RUNTIME_CHECK_RESOLVED_LIST =
      create(JbcSrcRuntime.class, "checkResolved", List.class);

  public static final MethodRef RUNTIME_CHECK_RESOLVED_MAP =
      create(JbcSrcRuntime.class, "checkResolved", Map.class);

  public static final MethodRef SOY_SERVER_KEY =
      MethodRef.create(SharedRuntime.class, "soyServerKey", SoyValue.class).asCheap();

  public static final MethodRef RUNTIME_RANGE_LOOP_LENGTH =
      create(JbcSrcRuntime.class, "rangeLoopLength", int.class, int.class, int.class).asCheap();

  public static final MethodRef RUNTIME_APPLY_PRINT_DIRECTIVE =
      create(
          JbcSrcRuntime.class,
          "applyPrintDirective",
          SoyJavaPrintDirective.class,
          SoyValue.class,
          List.class);

  public static final MethodRef RUNTIME_BIND_TEMPLATE_PARAMS =
      create(
          JbcSrcRuntime.class,
          "bindTemplateParams",
          CompiledTemplate.Factory.class,
          SoyRecord.class);

  public static final MethodRef RUNTIME_CALL_LEGACY_FUNCTION =
      create(JbcSrcRuntime.class, "callLegacySoyFunction", LegacyFunctionAdapter.class, List.class);

  public static final MethodRef RUNTIME_COERCE_DOUBLE_TO_BOOLEAN =
      create(JbcSrcRuntime.class, "coerceToBoolean", double.class);

  public static final MethodRef RUNTIME_COERCE_TO_STRING =
      create(JbcSrcRuntime.class, "coerceToString", SoyValue.class).asNonNullable();

  public static final MethodRef RUNTIME_EQUAL =
      create(SharedRuntime.class, "equal", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_COMPARE_STRING =
      create(SharedRuntime.class, "compareString", String.class, SoyValue.class);

  public static final MethodRef RUNTIME_COMPARE_NULLABLE_STRING =
      create(JbcSrcRuntime.class, "compareNullableString", String.class, SoyValue.class);

  public static final MethodRef RUNTIME_GET_FIELD_PROVIDER =
      create(JbcSrcRuntime.class, "getFieldProvider", SoyRecord.class, String.class)
          .asNonNullable();

  public static final MethodRef RUNTIME_GET_FIELD_PROVIDER_DEFAULT =
      create(JbcSrcRuntime.class, "getFieldProvider", SoyRecord.class, String.class, SoyValue.class)
          .asNonNullable();

  public static final MethodRef RUNTIME_GET_LIST_ITEM =
      create(JbcSrcRuntime.class, "getSoyListItem", List.class, long.class);

  public static final MethodRef RUNTIME_GET_LIST_STATUS =
      create(JbcSrcRuntime.class, "getListStatus", List.class);

  public static final MethodRef RUNTIME_GET_MAP_STATUS =
      create(JbcSrcRuntime.class, "getMapStatus", Map.class);

  public static final MethodRef RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM =
      create(
          JbcSrcRuntime.class,
          "getSoyLegacyObjectMapItem",
          SoyLegacyObjectMap.class,
          SoyValue.class);

  public static final MethodRef RUNTIME_GET_MAP_ITEM =
      create(JbcSrcRuntime.class, "getSoyMapItem", SoyMap.class, SoyValue.class);

  public static final MethodRef RUNTIME_LESS_THAN =
      create(SharedRuntime.class, "lessThan", SoyValue.class, SoyValue.class).asNonNullable();

  public static final MethodRef RUNTIME_LESS_THAN_OR_EQUAL =
      create(SharedRuntime.class, "lessThanOrEqual", SoyValue.class, SoyValue.class)
          .asNonNullable();

  public static final MethodRef RUNTIME_LOGGER =
      create(JbcSrcRuntime.class, "logger").asCheap().asNonNullable();

  public static final MethodRef RUNTIME_DEBUGGER =
      create(JbcSrcRuntime.class, "debugger", String.class, int.class);

  public static final MethodRef RUNTIME_MINUS =
      create(SharedRuntime.class, "minus", SoyValue.class, SoyValue.class).asNonNullable();

  public static final MethodRef RUNTIME_NEGATIVE =
      create(SharedRuntime.class, "negative", SoyValue.class).asNonNullable();

  public static final MethodRef RUNTIME_PLUS =
      create(SharedRuntime.class, "plus", SoyValue.class, SoyValue.class).asNonNullable();

  public static final MethodRef MSG_RENDERER_SET_PLACEHOLDER =
      create(JbcSrcRuntime.MsgRenderer.class, "setPlaceholder", String.class, Object.class);

  public static final MethodRef MSG_RENDERER_ESCAPE_HTML =
      create(JbcSrcRuntime.MsgRenderer.class, "escapeHtml", String.class);

  public static final MethodRef MSG_RENDERER_SET_PLACEHOLDER_AND_ORDERING =
      create(
          JbcSrcRuntime.MsgRenderer.class,
          "setPlaceholderAndOrdering",
          String.class,
          Object.class,
          String.class);

  public static final MethodRef RUNTIME_STRING_EQUALS_AS_NUMBER =
      create(JbcSrcRuntime.class, "stringEqualsAsNumber", String.class, double.class)
          .asNonNullable();

  public static final MethodRef RUNTIME_TIMES =
      create(SharedRuntime.class, "times", SoyValue.class, SoyValue.class).asNonNullable();

  public static final MethodRef RUNTIME_UNEXPECTED_STATE_ERROR =
      create(JbcSrcRuntime.class, "unexpectedStateError", int.class).asNonNullable();

  public static final MethodRef SOY_LIST_AS_JAVA_LIST =
      create(SoyList.class, "asJavaList").asNonNullable();

  public static final MethodRef SOY_DICT_IMPL_AS_JAVA_MAP =
      create(DictImpl.class, "asJavaMap").asNonNullable();

  public static final MethodRef SOY_MAP_IMPL_AS_JAVA_MAP =
      create(SoyMap.class, "asJavaMap").asNonNullable();

  public static final MethodRef SOY_MSG_RAW_TEXT_PART_GET_RAW_TEXT =
      create(SoyMsgRawTextPart.class, "getRawText").asCheap().asNonNullable();

  public static final MethodRef SOY_PROTO_VALUE_GET_PROTO =
      create(SoyProtoValue.class, "getProto").asCheap().asNonNullable();

  public static final MethodRef SOY_VALUE_COERCE_TO_BOOLEAN =
      create(SoyValue.class, "coerceToBoolean").asCheap();

  public static final MethodRef SOY_VALUE_BOOLEAN_VALUE =
      create(SoyValue.class, "booleanValue").asCheap();

  public static final MethodRef SOY_VALUE_FLOAT_VALUE =
      create(SoyValue.class, "floatValue").asCheap();

  public static final MethodRef SOY_VALUE_LONG_VALUE =
      create(SoyValue.class, "longValue").asCheap();

  public static final MethodRef SOY_VALUE_INTEGER_VALUE =
      create(SoyValue.class, "integerValue").asCheap();

  public static final MethodRef SOY_VALUE_NUMBER_VALUE =
      create(SoyValue.class, "numberValue").asNonNullable();

  public static final MethodRef SOY_VALUE_STRING_VALUE =
      create(SoyValue.class, "stringValue").asCheap().asNonNullable();

  public static final MethodRef COMPILED_TEMPLATE_FACTORY_CREATE =
      create(CompiledTemplate.Factory.class, "create", SoyRecord.class, SoyRecord.class)
          .asNonNullable();

  public static final MethodRef RUNTIME_CHECK_SOY_STRING =
      create(JbcSrcRuntime.class, "checkSoyString", Object.class).asCheap().asNonNullable();

  public static final MethodRef SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE =
      create(
              SoyValueProvider.class,
              "renderAndResolve",
              LoggingAdvisingAppendable.class,
              boolean.class)
          .asNonNullable();

  public static final MethodRef SOY_VALUE_PROVIDER_RESOLVE =
      create(JbcSrcRuntime.class, "resolveSoyValueProvider", SoyValueProvider.class);

  public static final MethodRef SOY_VALUE_PROVIDER_OR_NULL =
      create(JbcSrcRuntime.class, "soyValueProviderOrNull", SoyValueProvider.class);

  public static final MethodRef SOY_VALUE_PROVIDER_STATUS =
      create(SoyValueProvider.class, "status").asNonNullable();

  public static final MethodRef STRING_CONCAT =
      create(String.class, "concat", String.class).asNonNullable();

  public static final MethodRef STRING_IS_EMPTY = create(String.class, "isEmpty");

  public static final MethodRef STRING_VALUE_OF =
      create(String.class, "valueOf", Object.class).asNonNullable();

  public static final MethodRef STRING_DATA_FOR_VALUE =
      create(StringData.class, "forValue", String.class).asCheap().asNonNullable();

  public static final MethodRef LOGGING_ADVISING_APPENDABLE_BUFFERING =
      create(LoggingAdvisingAppendable.class, "buffering").asNonNullable();

  public static final MethodRef CREATE_LOG_STATEMENT =
      MethodRef.create(
          JbcSrcRuntime.class, "createLogStatement", boolean.class, SoyVisualElementData.class);

  public static final MethodRef CLOSEABLE_CLOSE = MethodRef.create(Closeable.class, "close");

  public static final MethodRef LEGACY_ADAPTER_COMPUTE =
      MethodRef.create(LegacyFunctionAdapter.METHOD);

  public static final MethodRef PROTOCOL_ENUM_GET_NUMBER =
      MethodRef.create(ProtocolMessageEnum.class, "getNumber").asCheap();

  public static final MethodRef SOY_VISUAL_ELEMENT_CREATE =
      MethodRef.create(SoyVisualElement.class, "create", long.class, String.class);

  public static final MethodRef SOY_VISUAL_ELEMENT_CREATE_METADATA =
      MethodRef.create(
          SoyVisualElement.class,
          "create",
          long.class,
          String.class,
          LoggableElementMetadata.class);

  public static final MethodRef SOY_VISUAL_ELEMENT_DATA_CREATE =
      MethodRef.create(SoyVisualElementData.class, "create", SoyVisualElement.class, Message.class);

  public static final MethodRef FLUSH_LOGS_AND_RENDER =
      MethodRef.create(
          JbcSrcRuntime.class, "flushLogsAndRender", SoyValueProvider.class, SoyLogger.class);

  public static final MethodRef BOX_JAVA_MAP_AS_SOY_MAP =
      MethodRef.create(JbcSrcRuntime.class, "boxJavaMapAsSoyMap", Map.class);

  public static final MethodRef BOX_JAVA_MAP_AS_SOY_RECORD =
      MethodRef.create(JbcSrcRuntime.class, "boxJavaMapAsSoyRecord", Map.class);

  public static final MethodRef BOX_JAVA_MAP_AS_SOY_LEGACY_OBJECT_MAP =
      MethodRef.create(JbcSrcRuntime.class, "boxJavaMapAsSoyLegacyObjectMap", Map.class);

  public static final MethodRef LAZY_PROTO_TO_SOY_VALUE_LIST_FOR_LIST =
      MethodRef.create(
              LazyProtoToSoyValueList.class, "forList", List.class, ProtoFieldInterpreter.class)
          .asNonNullable();

  public static final MethodRef LAZY_PROTO_TO_SOY_VALUE_MAP_FOR_MAP =
      MethodRef.create(
              LazyProtoToSoyValueMap.class,
              "forMap",
              Map.class,
              ProtoFieldInterpreter.class,
              ProtoFieldInterpreter.class,
              Class.class)
          .asNonNullable();

  public static final MethodRef GET_EXTENSION_LIST =
      MethodRef.create(
              JbcSrcRuntime.class,
              "getExtensionList",
              ExtendableMessage.class,
              ExtensionLite.class,
              ProtoFieldInterpreter.class)
          .asNonNullable();

  public static MethodRef create(Class<?> clazz, String methodName, Class<?>... params) {
    java.lang.reflect.Method m;
    try {
      // Ensure that the method exists and is public.
      m = clazz.getMethod(methodName, params);
    } catch (Exception e) {
      throw new RuntimeException(
          "Couldn't find the expected method among: " + Arrays.toString(clazz.getMethods()), e);
    }
    return create(m);
  }

  public static MethodRef create(java.lang.reflect.Method method) {
    Class<?> clazz = method.getDeclaringClass();
    TypeInfo ownerType = TypeInfo.create(method.getDeclaringClass());
    boolean isStatic = Modifier.isStatic(method.getModifiers());
    ImmutableList<Type> argTypes;
    if (isStatic) {
      argTypes = ImmutableList.copyOf(Type.getArgumentTypes(method));
    } else {
      // for instance methods the first 'argument' is always an instance of the class.
      argTypes =
          ImmutableList.<Type>builder()
              .add(ownerType.type())
              .add(Type.getArgumentTypes(method))
              .build();
    }
    return new AutoValue_MethodRef(
        clazz.isInterface()
            ? Opcodes.INVOKEINTERFACE
            : isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
        ownerType,
        Method.getMethod(method),
        Type.getType(method.getReturnType()),
        argTypes,
        Features.of());
  }

  public static MethodRef createInterfaceMethod(TypeInfo owner, Method method) {
    return new AutoValue_MethodRef(
        Opcodes.INVOKEINTERFACE,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(owner.type()).add(method.getArgumentTypes()).build(),
        Features.of());
  }

  public static MethodRef createInstanceMethod(TypeInfo owner, Method method) {
    return new AutoValue_MethodRef(
        Opcodes.INVOKEVIRTUAL,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(owner.type()).add(method.getArgumentTypes()).build(),
        Features.of());
  }

  public static MethodRef createStaticMethod(TypeInfo owner, Method method) {
    return new AutoValue_MethodRef(
        Opcodes.INVOKESTATIC,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(method.getArgumentTypes()).build(),
        Features.of());
  }

  /**
   * The opcode to use to invoke the method. Will be one of {@link Opcodes#INVOKEINTERFACE}, {@link
   * Opcodes#INVOKESTATIC} or {@link Opcodes#INVOKEVIRTUAL}.
   */
  abstract int opcode();

  /** The 'internal name' of the type that owns the method. */
  public abstract TypeInfo owner();

  public abstract Method method();

  public abstract Type returnType();

  abstract ImmutableList<Type> argTypes();

  public abstract Features features();

  public Handle asHandle() {
    int tag;
    switch (opcode()) {
      case Opcodes.INVOKESTATIC:
        tag = Opcodes.H_INVOKESTATIC;
        break;
      case Opcodes.INVOKEINTERFACE:
        tag = Opcodes.H_INVOKEINTERFACE;
        break;
      case Opcodes.INVOKEVIRTUAL:
        tag = Opcodes.H_INVOKEVIRTUAL;
        break;
      default:
        throw new AssertionError("unsupported opcode: " + opcode());
    }
    return new Handle(
        tag,
        owner().internalName(),
        method().getName(),
        method().getDescriptor(),
        owner().isInterface());
  }

  // TODO(lukes): consider different names.  'invocation'? invoke() makes it sounds like we are
  // actually calling the method rather than generating an expression that will output code that
  // will invoke the method.
  public Statement invokeVoid(final Expression... args) {
    return invokeVoid(Arrays.asList(args));
  }

  public Statement invokeVoid(final Iterable<? extends Expression> args) {
    checkState(Type.VOID_TYPE.equals(returnType()), "Method return type is not void.");
    Expression.checkTypes(argTypes(), args);
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        doInvoke(adapter, args);
      }
    };
  }

  public Expression invoke(final Expression... args) {
    return invoke(Arrays.asList(args));
  }

  Expression invoke(final Iterable<? extends Expression> args) {
    // void methods violate the expression contract of pushing a result onto the runtime stack.
    checkState(
        !Type.VOID_TYPE.equals(returnType()), "Cannot produce an expression from a void method.");
    Expression.checkTypes(argTypes(), args);
    Features features = features();
    if (!areAllCheap(args)) {
      features = features.minus(Feature.CHEAP);
    }
    return new Expression(returnType(), features) {
      @Override
      protected void doGen(CodeBuilder mv) {
        doInvoke(mv, args);
      }
    };
  }

  public MethodRef asCheap() {
    return withFeature(Feature.CHEAP);
  }

  public MethodRef asNonNullable() {
    return withFeature(Feature.NON_NULLABLE);
  }

  private MethodRef withFeature(Feature feature) {
    if (features().has(feature)) {
      return this;
    }
    return new AutoValue_MethodRef(
        opcode(), owner(), method(), returnType(), argTypes(), features().plus(feature));
  }

  /**
   * Writes an invoke instruction for this method to the given adapter. Useful when the expression
   * is not useful for representing operations. For example, explicit dup operations are awkward in
   * the Expression api.
   */
  public void invokeUnchecked(CodeBuilder cb) {
    cb.visitMethodInsn(
        opcode(),
        owner().internalName(),
        method().getName(),
        method().getDescriptor(),
        // This is for whether the methods owner is an interface.  This is mostly to handle java8
        // default methods on interfaces.  We don't care about those currently, but ASM requires
        // this.
        owner().isInterface());
  }

  private void doInvoke(CodeBuilder mv, Iterable<? extends Expression> args) {
    for (Expression arg : args) {
      arg.gen(mv);
    }
    invokeUnchecked(mv);
  }
}
