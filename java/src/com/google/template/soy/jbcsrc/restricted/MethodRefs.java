/*
 * Copyright 2023 Google Inc.
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

import static com.google.template.soy.jbcsrc.restricted.MethodRef.createNonPure;
import static com.google.template.soy.jbcsrc.restricted.MethodRef.createNonPureConstructor;
import static com.google.template.soy.jbcsrc.restricted.MethodRef.createPure;
import static com.google.template.soy.jbcsrc.restricted.MethodRef.createPureConstructor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLongs;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage.ExtendableMessage;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.LazyProtoToSoyValueList;
import com.google.template.soy.data.internal.LazyProtoToSoyValueMap;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.internal.SetImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.restricted.MethodRef.MethodPureness;
import com.google.template.soy.jbcsrc.runtime.DetachableContentProvider;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.logging.LoggableElementMetadata;
import com.google.template.soy.shared.internal.SharedRuntime;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Standard constant MethodRef instances shared throughout the compiler. */
public final class MethodRefs {

  public static final MethodRef ARRAY_LIST_ADD =
      createNonPure(ArrayList.class, "add", Object.class);

  public static final MethodRef BOOLEAN_DATA_FOR_VALUE =
      createPure(BooleanData.class, "forValue", boolean.class);

  public static final MethodRef BOOLEAN_VALUE = createPure(Boolean.class, "booleanValue").asCheap();

  public static final MethodRef BOOLEAN_TO_STRING =
      createPure(Boolean.class, "toString", boolean.class).asCheap().asNonJavaNullable();

  public static final MethodRef COMPILED_TEMPLATE_RENDER =
      createNonPure(
          CompiledTemplate.class,
          "render",
          StackFrame.class,
          ParamStore.class,
          LoggingAdvisingAppendable.class,
          RenderContext.class);

  public static final MethodRef DICT_IMPL_FOR_PROVIDER_MAP =
      createPure(DictImpl.class, "forProviderMap", Map.class, RuntimeMapTypeTracker.Type.class);

  public static final MethodRef MAP_IMPL_FOR_PROVIDER_MAP =
      createPure(SoyMapImpl.class, "forProviderMap", Map.class);

  public static final MethodRef MAP_IMPL_FOR_PROVIDER_MAP_NO_NULL_KEYS =
      createPure(SoyMapImpl.class, "forProviderMapNoNullKeys", Map.class);

  public static final MethodRef DOUBLE_TO_STRING =
      createPure(FloatData.class, "toString", double.class);

  public static final MethodRef EQUALS = createPure(Object.class, "equals", Object.class);

  public static final MethodRef STRING_COMPARE_TO =
      createPure(String.class, "compareTo", String.class);

  public static final MethodRef FLOAT_DATA_FOR_VALUE =
      createPure(FloatData.class, "forValue", double.class);

  public static final MethodRef RENDER_RESULT_ASSERT_DONE =
      createPure(RenderResult.class, "assertDone");

  public static final MethodRef IMMUTABLE_LIST_BUILDER =
      createNonPure(ImmutableList.class, "builder");
  public static final MethodRef IMMUTABLE_LIST_BUILDER_ADD =
      createNonPure(ImmutableList.Builder.class, "add", Object.class);
  public static final MethodRef IMMUTABLE_LIST_BUILDER_ADD_ALL =
      createNonPure(ImmutableList.Builder.class, "addAll", Iterable.class);
  public static final MethodRef IMMUTABLE_LIST_BUILDER_ADD_ALL_ITERATOR =
      createNonPure(ImmutableList.Builder.class, "addAll", Iterator.class);
  public static final MethodRef IMMUTABLE_LIST_BUILDER_BUILD =
      createNonPure(ImmutableList.Builder.class, "build");

  public static final MethodRef IMMUTABLE_SET_BUILDER =
      createNonPure(ImmutableSet.class, "builder");
  public static final MethodRef IMMUTABLE_SET_BUILDER_ADD_ALL_ITERATOR =
      createNonPure(ImmutableSet.Builder.class, "addAll", Iterator.class);
  public static final MethodRef IMMUTABLE_SET_BUILDER_BUILD =
      createNonPure(ImmutableSet.Builder.class, "build");
  public static final MethodRef IMMUTABLE_SET_COPY_OF =
      createNonPure(ImmutableSet.class, "copyOf", Iterator.class);

  /** a list of all the ImmutableList.of overloads, indexed by arity. */
  public static final ImmutableList<MethodRef> IMMUTABLE_LIST_OF;

  public static final MethodRef IMMUTABLE_LIST_OF_ARRAY;

  /** a list of all the ImmutableList.of overloads, indexed by number of entries. */
  public static final ImmutableList<MethodRef> IMMUTABLE_MAP_OF;

  public static final MethodRef IMMUTABLE_MAP_BUILDER_WITH_EXPECTED_SIZE =
      MethodRef.createNonPure(ImmutableMap.class, "builderWithExpectedSize", int.class);
  public static final MethodRef IMMUTABLE_MAP_BUILDER_PUT =
      MethodRef.createNonPure(ImmutableMap.Builder.class, "put", Object.class, Object.class);
  public static final MethodRef IMMUTABLE_MAP_BUILDER_BUILD_KEEPING_LAST =
      MethodRef.createNonPure(ImmutableMap.Builder.class, "buildKeepingLast");
  public static final MethodRef IMMUTABLE_MAP_BUILDER_BUILD_OR_THROW =
      MethodRef.createNonPure(ImmutableMap.Builder.class, "buildOrThrow");

  static {
    Map<Integer, MethodRef> immutableListOfMethods = new TreeMap<>();
    MethodRef immutableListOfArray = null;
    for (java.lang.reflect.Method m : ImmutableList.class.getMethods()) {
      if (m.getName().equals("of")) {
        Class<?>[] params = m.getParameterTypes();
        MethodRef ref = MethodRef.create(m, MethodPureness.PURE).asNonJavaNullable();
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
        immutableListOfMethods.put(arity, ref);
      }
    }
    IMMUTABLE_LIST_OF_ARRAY = immutableListOfArray;
    IMMUTABLE_LIST_OF = ImmutableList.copyOf(immutableListOfMethods.values());

    Map<Integer, MethodRef> immutableMapOfMethods = new TreeMap<>();
    for (java.lang.reflect.Method m : ImmutableMap.class.getMethods()) {
      if (m.getName().equals("of")) {
        Class<?>[] params = m.getParameterTypes();
        MethodRef ref = MethodRef.create(m, MethodPureness.PURE).asNonJavaNullable();
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
        immutableMapOfMethods.put(arity / 2, ref);
      }
    }
    IMMUTABLE_MAP_OF = ImmutableList.copyOf(immutableMapOfMethods.values());
  }

  public static final MethodRef INTEGER_DATA_FOR_VALUE =
      createPure(IntegerData.class, "forValue", long.class);

  public static final MethodRef INTS_CHECKED_CAST =
      createPure(Ints.class, "checkedCast", long.class).asCheap();

  public static final MethodRef MAP_PUT =
      createNonPure(Map.class, "put", Object.class, Object.class);

  public static final MethodRef LIST_GET = createPure(List.class, "get", int.class).asCheap();

  public static final MethodRef LIST_SIZE = createPure(List.class, "size").asCheap();

  public static final MethodRef MAP_ENTRY_SET =
      createPure(Map.class, "entrySet").asNonJavaNullable();

  public static final MethodRef GET_ITERATOR =
      createPure(Iterable.class, "iterator").asNonJavaNullable();

  public static final MethodRef ITERATOR_NEXT = createNonPure(Iterator.class, "next");

  public static final MethodRef ITERATOR_HAS_NEXT = createPure(Iterator.class, "hasNext");

  public static final MethodRef MAP_ENTRY_GET_KEY = createPure(Map.Entry.class, "getKey");

  public static final MethodRef MAP_ENTRY_GET_VALUE = createPure(Map.Entry.class, "getValue");

  public static final MethodRef LIST_IMPL_FOR_PROVIDER_LIST =
      createPure(ListImpl.class, "forProviderList", List.class);
  public static final MethodRef SET_IMPL_FOR_PROVIDER_SET =
      createPureConstructor(SetImpl.class, Set.class);

  public static final MethodRef LONG_PARSE_LONG =
      createPure(Long.class, "parseLong", String.class).asCheap().asNonJavaNullable();
  public static final MethodRef UNSIGNED_LONGS_PARSE_UNSIGNED_LONG =
      createPure(UnsignedLongs.class, "parseUnsignedLong", String.class).asCheap();
  public static final MethodRef UNSIGNED_LONGS_TO_STRING =
      createPure(UnsignedLongs.class, "toString", long.class).asCheap().asNonJavaNullable();
  public static final MethodRef UNSIGNED_INTS_SATURATED_CAST =
      createPure(UnsignedInts.class, "saturatedCast", long.class).asCheap();
  public static final MethodRef UNSIGNED_INTS_TO_LONG =
      createPure(UnsignedInts.class, "toLong", int.class).asCheap();

  public static final MethodRef LONG_TO_STRING =
      createPure(Long.class, "toString", long.class).asNonJavaNullable();

  public static final MethodRef NUMBER_DOUBLE_VALUE =
      createPure(Number.class, "doubleValue").asCheap();
  public static final MethodRef NUMBER_LONG_VALUE = createPure(Number.class, "longValue").asCheap();
  public static final MethodRef NUMBER_INT_VALUE = createPure(Number.class, "intValue").asCheap();
  public static final MethodRef NUMBER_FLOAT_VALUE =
      createPure(Number.class, "floatValue").asCheap();

  public static final MethodRef OBJECT_TO_STRING =
      createPure(Object.class, "toString").asNonJavaNullable();

  public static final MethodRef OBJECTS_EQUALS =
      createPure(Objects.class, "equals", Object.class, Object.class);

  public static final MethodRef ORDAIN_AS_SAFE =
      createPure(
          UnsafeSanitizedContentOrdainer.class, "ordainAsSafe", String.class, ContentKind.class);

  public static final MethodRef ORDAIN_AS_SAFE_DIR =
      createPure(
          UnsafeSanitizedContentOrdainer.class,
          "ordainAsSafe",
          String.class,
          ContentKind.class,
          Dir.class);

  public static final MethodRef PARAM_STORE_SET_FIELD =
      createNonPure(ParamStore.class, "setField", RecordProperty.class, SoyValueProvider.class);
  public static final MethodRef PARAM_STORE_SET_ALL =
      createNonPure(ParamStore.class, "setAll", SoyRecord.class);
  public static final MethodRef PARAM_STORE_FROM_RECORD =
      createPure(ParamStore.class, "fromRecord", SoyRecord.class);

  public static final MethodRef SOY_PROTO_VALUE_CREATE =
      createPure(SoyProtoValue.class, "create", Message.class);

  public static final MethodRef RENDER_RESULT_DONE =
      createPure(RenderResult.class, "done").asCheap();

  public static final MethodRef RENDER_RESULT_IS_DONE =
      createPure(RenderResult.class, "isDone").asCheap();

  public static final MethodRef RENDER_RESULT_LIMITED =
      createPure(RenderResult.class, "limited").asCheap();

  public static final MethodRef BUFFER_TEMPLATE =
      createNonPure(
          JbcSrcRuntime.class,
          "bufferTemplate",
          CompiledTemplate.class,
          boolean.class,
          JbcSrcRuntime.BufferedRenderDoneFn.class);

  public static final MethodRef RUNTIME_CHECK_RESOLVED_LIST =
      createNonPure(JbcSrcRuntime.class, "checkResolved", List.class);

  public static final MethodRef RUNTIME_CHECK_RESOLVED_MAP =
      createNonPure(JbcSrcRuntime.class, "checkResolved", Map.class);

  public static final MethodRef SOY_SERVER_KEY =
      createPure(SharedRuntime.class, "soyServerKey", SoyValue.class).asCheap();

  public static final MethodRef RUNTIME_RANGE_LOOP_LENGTH =
      createPure(JbcSrcRuntime.class, "rangeLoopLength", int.class, int.class, int.class).asCheap();

  public static final MethodRef SOY_JAVA_PRINT_DIRECTIVE_APPLY_FOR_JAVA =
      createNonPure(SoyJavaPrintDirective.class, "applyForJava", SoyValue.class, List.class);

  public static final MethodRef RUNTIME_BIND_TEMPLATE_PARAMS =
      createPure(JbcSrcRuntime.class, "bindTemplateParams", TemplateValue.class, ParamStore.class);

  public static final MethodRef SOY_JAVA_FUNCTION_COMPUTE_FOR_JAVA =
      createNonPure(SoyJavaFunction.class, "computeForJava", List.class);

  public static final MethodRef RUNTIME_COERCE_DOUBLE_TO_BOOLEAN =
      createPure(JbcSrcRuntime.class, "coerceToBoolean", double.class);

  public static final MethodRef RUNTIME_COERCE_STRING_TO_BOOLEAN =
      createPure(JbcSrcRuntime.class, "coerceToBoolean", String.class);

  public static final MethodRef RUNTIME_EQUAL =
      createPure(SharedRuntime.class, "equal", SoyValue.class, SoyValue.class);

  public static final MethodRef SOY_VALUE_IS_TRUTHY_NON_EMPTY =
      createPure(SoyValue.class, "isTruthyNonEmpty");

  public static final MethodRef SOY_VALUE_HAS_CONTENT = createPure(SoyValue.class, "hasContent");
  public static final MethodRef SOY_VALUE_RENDER =
      createPure(SoyValue.class, "render", LoggingAdvisingAppendable.class);

  public static final MethodRef RUNTIME_TRIPLE_EQUAL =
      createPure(SharedRuntime.class, "tripleEqual", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_SWITCH_CASE_EQUAL =
      createPure(SharedRuntime.class, "switchCaseEqual", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_COMPARE_BOXED_STRING =
      createPure(JbcSrcRuntime.class, "compareBoxedStringToBoxed", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_COMPARE_BOXED_VALUE_TO_BOXED_STRING =
      createPure(
          JbcSrcRuntime.class, "compareBoxedValueToBoxedString", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_COMPARE_UNBOXED_STRING =
      createPure(JbcSrcRuntime.class, "compareUnboxedStringToBoxed", String.class, SoyValue.class);

  public static final MethodRef RUNTIME_COMPARE_BOXED_VALUE_TO_UNBOXED_STRING =
      createPure(
          JbcSrcRuntime.class, "compareBoxedValueToUnboxedString", SoyValue.class, String.class);

  public static final MethodRef RUNTIME_GET_FIELD =
      createPure(JbcSrcRuntime.class, "getField", SoyValue.class, RecordProperty.class);

  public static final MethodRef RUNTIME_GET_FIELD_PROVIDER =
      createPure(JbcSrcRuntime.class, "getFieldProvider", SoyValue.class, RecordProperty.class);

  public static final MethodRef PARAM_STORE_GET_PARAMETER =
      createPure(ParamStore.class, "getParameter", RecordProperty.class);

  public static final MethodRef PARAM_STORE_GET_PARAMETER_DEFAULT =
      createPure(ParamStore.class, "getParameter", RecordProperty.class, SoyValue.class);

  public static final MethodRef RUNTIME_PARAM_OR_DEFAULT =
      createPure(JbcSrcRuntime.class, "paramOrDefault", SoyValueProvider.class, SoyValue.class)
          .asCheap();

  public static final MethodRef RUNTIME_GET_LIST_ITEM =
      createPure(JbcSrcRuntime.class, "getSoyListItem", List.class, long.class);

  public static final MethodRef RUNTIME_GET_LIST_ITEM_PROVIDER =
      createPure(JbcSrcRuntime.class, "getSoyListItemProvider", List.class, long.class);

  public static final MethodRef RUNTIME_GET_LIST_STATUS =
      createNonPure(JbcSrcRuntime.class, "getListStatus", List.class);

  public static final MethodRef RUNTIME_GET_MAP_STATUS =
      createNonPure(JbcSrcRuntime.class, "getMapStatus", Map.class);

  public static final MethodRef RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM =
      createPure(JbcSrcRuntime.class, "getSoyLegacyObjectMapItem", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM_PROVIDER =
      createPure(
          JbcSrcRuntime.class, "getSoyLegacyObjectMapItemProvider", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_GET_MAP_ITEM =
      createPure(JbcSrcRuntime.class, "getSoyMapItem", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_GET_MAP_ITEM_PROVIDER =
      createPure(JbcSrcRuntime.class, "getSoyMapItemProvider", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_LESS_THAN =
      createPure(SharedRuntime.class, "lessThan", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_LESS_THAN_OR_EQUAL =
      createPure(SharedRuntime.class, "lessThanOrEqual", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_LOGGER =
      createPure(JbcSrcRuntime.class, "logger").asCheap();

  public static final MethodRef RUNTIME_DEBUGGER =
      createNonPure(JbcSrcRuntime.class, "debugger", String.class, int.class);

  public static final MethodRef RUNTIME_MINUS =
      createPure(SharedRuntime.class, "minus", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_NEGATIVE =
      createPure(SharedRuntime.class, "negative", SoyValue.class);

  public static final MethodRef RUNTIME_PLUS =
      createPure(SharedRuntime.class, "plus", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_MOD =
      createPure(SharedRuntime.class, "mod", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_SHIFT_RIGHT =
      createPure(SharedRuntime.class, "shiftRight", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_SHIFT_LEFT =
      createPure(SharedRuntime.class, "shiftLeft", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_BITWISE_OR =
      createPure(SharedRuntime.class, "bitwiseOr", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_BITWISE_XOR =
      createPure(SharedRuntime.class, "bitwiseXor", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_BITWISE_AND =
      createPure(SharedRuntime.class, "bitwiseAnd", SoyValue.class, SoyValue.class);

  public static final MethodRef CONSTRUCT_MAP_FROM_ITERATOR =
      createPure(SharedRuntime.class, "constructMapFromIterator", Iterator.class);

  public static final MethodRef RUNTIME_TIMES =
      createPure(SharedRuntime.class, "times", SoyValue.class, SoyValue.class);

  public static final MethodRef MSG_RENDERER_SET_PLACEHOLDER =
      createNonPure(
          JbcSrcRuntime.MsgRenderer.class, "setPlaceholder", String.class, SoyValueProvider.class);

  public static final MethodRef MSG_RENDERER_SET_PLACEHOLDER_AND_ORDERING =
      createNonPure(
          JbcSrcRuntime.MsgRenderer.class,
          "setPlaceholderAndOrdering",
          String.class,
          SoyValueProvider.class,
          String.class);

  public static final MethodRef HANDLE_BASIC_TRANSLATION_AND_ESCAPE_HTML =
      createPure(JbcSrcRuntime.class, "handleBasicTranslationAndEscapeHtml", String.class);

  public static final MethodRef RUNTIME_STRING_EQUALS_AS_NUMBER =
      createPure(JbcSrcRuntime.class, "stringEqualsAsNumber", String.class, double.class);

  public static final MethodRef RUNTIME_NUMBER_EQUALS_STRING_AS_NUMBER =
      createPure(JbcSrcRuntime.class, "numberEqualsStringAsNumber", double.class, String.class);

  public static final MethodRef RUNTIME_EMPTY_TO_UNDEFINED =
      createPure(JbcSrcRuntime.class, "emptyToUndefined", SoyValue.class);

  public static final MethodRef RUNTIME_UNEXPECTED_STATE_ERROR =
      createNonPure(JbcSrcRuntime.class, "unexpectedStateError", StackFrame.class);

  public static final MethodRef SOY_VALUE_AS_JAVA_LIST = createPure(SoyValue.class, "asJavaList");
  public static final MethodRef SOY_VALUE_AS_JAVA_LIST_OR_NULL =
      createPure(SoyValue.class, "asJavaListOrNull");
  public static final MethodRef SOY_VALUE_AS_JAVA_ITERATOR =
      createPure(SoyValue.class, "javaIterator");

  public static final MethodRef SOY_VALUE_AS_JAVA_MAP = createPure(SoyValue.class, "asJavaMap");

  public static final MethodRef SOY_VALUE_GET_PROTO =
      createPure(SoyValue.class, "getProto").asCheap();
  public static final MethodRef SOY_VALUE_GET_PROTO_OR_NULL =
      createPure(SoyValue.class, "getProtoOrNull").asCheap();

  public static final MethodRef SOY_VALUE_COERCE_TO_BOOLEAN =
      createPure(SoyValue.class, "coerceToBoolean").asCheap();

  public static final MethodRef SOY_VALUE_BOOLEAN_VALUE =
      createPure(SoyValue.class, "booleanValue").asCheap();

  public static final MethodRef SOY_VALUE_FLOAT_VALUE =
      createPure(SoyValue.class, "floatValue").asCheap();

  public static final MethodRef SOY_VALUE_LONG_VALUE =
      createPure(SoyValue.class, "longValue").asCheap();

  public static final MethodRef SOY_VALUE_INTEGER_VALUE =
      createPure(SoyValue.class, "integerValue").asCheap();

  public static final MethodRef SOY_VALUE_NUMBER_VALUE = createPure(SoyValue.class, "numberValue");
  public static final MethodRef SOY_VALUE_COERCE_TO_LONG =
      createPure(SoyValue.class, "coerceToLong");

  public static final MethodRef SOY_VALUE_IS_NULLISH =
      createPure(SoyValue.class, "isNullish").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_IS_NULL =
      createPure(SoyValue.class, "isNull").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_IS_UNDEFINED =
      createPure(SoyValue.class, "isUndefined").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_NULLISH_TO_NULL =
      createPure(SoyValue.class, "nullishToNull").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_NULLISH_TO_UNDEFINED =
      createPure(SoyValue.class, "nullishToUndefined").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_JAVA_NUMBER_VALUE =
      createPure(NumberData.class, "javaNumberValue");

  public static final MethodRef SOY_VALUE_STRING_VALUE =
      createPure(SoyValue.class, "stringValue").asCheap();

  public static final MethodRef SOY_VALUE_STRING_VALUE_OR_NULL =
      createPure(SoyValue.class, "stringValueOrNull").asCheap();

  public static final MethodRef SOY_VALUE_COERCE_TO_STRING =
      createPure(SoyValue.class, "coerceToString");

  public static final MethodRef CHECK_TYPE =
      createNonPure(SoyValue.class, "checkNullishType", Class.class);

  public static final MethodRef CHECK_INT = createNonPure(SoyValue.class, "checkNullishInt");

  public static final MethodRef CHECK_FLOAT = createNonPure(SoyValue.class, "checkNullishFloat");

  public static final MethodRef CHECK_NUMBER = createNonPure(SoyValue.class, "checkNullishNumber");

  public static final MethodRef CHECK_STRING = createNonPure(SoyValue.class, "checkNullishString");

  public static final MethodRef CHECK_BOOLEAN =
      createNonPure(SoyValue.class, "checkNullishBoolean");

  public static final MethodRef CHECK_CONTENT_KIND =
      createNonPure(SoyValue.class, "checkNullishSanitizedContent", ContentKind.class);

  public static final MethodRef CHECK_PROTO =
      createNonPure(SoyValue.class, "checkNullishProto", Class.class);

  public static final MethodRef IS_PROTO_INSTANCE =
      createNonPure(SoyValue.class, "isProtoInstance", Class.class);

  public static final MethodRef IS_SANITIZED_CONTENT_KIND =
      createNonPure(SoyValue.class, "isSanitizedContentKind", SanitizedContent.ContentKind.class);

  public static final MethodRef GET_COMPILED_TEMPLATE_FROM_VALUE =
      createPure(TemplateValue.class, "getCompiledTemplate").asCheap();

  public static final MethodRef CREATE_TEMPLATE_VALUE =
      createPure(TemplateValue.class, "create", String.class, Object.class);

  public static final MethodRef SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE =
      createNonPure(SoyValueProvider.class, "renderAndResolve", LoggingAdvisingAppendable.class);

  public static final MethodRef SOY_NULLISH_TO_JAVA_NULL =
      createNonPure(JbcSrcRuntime.class, "soyNullishToJavaNull", SoyValue.class);

  public static final MethodRef SOY_NULL_TO_JAVA_NULL =
      createNonPure(JbcSrcRuntime.class, "soyNullToJavaNull", SoyValue.class);

  public static final MethodRef SOY_VALUE_PROVIDER_OR_NULLISH =
      createNonPure(JbcSrcRuntime.class, "soyValueProviderOrNullish", SoyValueProvider.class);

  public static final MethodRef SOY_VALUE_PROVIDER_STATUS =
      createNonPure(SoyValueProvider.class, "status");
  public static final MethodRef SOY_VALUE_PROVIDER_RESOLVE =
      createNonPure(SoyValueProvider.class, "resolve");

  public static final MethodRef STRING_CONCAT =
      createPure(String.class, "concat", String.class).asNonJavaNullable();

  public static final MethodRef STRING_IS_EMPTY = createPure(String.class, "isEmpty");

  public static final MethodRef STRING_VALUE_OF =
      createPure(String.class, "valueOf", Object.class).asNonJavaNullable();

  public static final MethodRef BOX_INTEGER =
      createPure(Integer.class, "valueOf", int.class).asNonJavaNullable();
  public static final MethodRef BOX_LONG =
      createPure(Long.class, "valueOf", long.class).asNonJavaNullable();
  public static final MethodRef BOX_DOUBLE =
      createPure(Double.class, "valueOf", double.class).asNonJavaNullable();
  public static final MethodRef BOX_FLOAT =
      createPure(Float.class, "valueOf", float.class).asNonJavaNullable();
  public static final MethodRef BOX_BOOLEAN =
      createPure(Boolean.class, "valueOf", boolean.class).asNonJavaNullable();
  public static final MethodRef CHECK_NOT_NULL =
      createNonPure(JbcSrcRuntime.class, "checkExpressionNotNull", Object.class, String.class);
  public static final MethodRef IS_SOY_NON_NULLISH =
      createPure(JbcSrcRuntime.class, "isNonSoyNullish", SoyValueProvider.class);
  public static final MethodRef IS_SOY_NON_NULL =
      createPure(JbcSrcRuntime.class, "isNonSoyNull", SoyValueProvider.class);

  public static final MethodRef STRING_DATA_FOR_VALUE =
      createPure(StringData.class, "forValue", String.class).asCheap();

  public static final MethodRef LOGGING_ADVISING_APPENDABLE_BUFFERING =
      createNonPure(
              LoggingAdvisingAppendable.class, "buffering", SanitizedContent.ContentKind.class)
          .asCheap();
  public static final MethodRef MULTIPLEXING_APPENDABLE =
      createNonPure(
              DetachableContentProvider.MultiplexingAppendable.class,
              "create",
              SanitizedContent.ContentKind.class)
          .asCheap();

  public static final MethodRef BUFFERING_APPENDABLE_GET_AS_STRING_DATA =
      createPure(BufferingAppendable.class, "getAsStringData");
  public static final MethodRef BUFFERING_APPENDABLE_GET_AS_SANITIZED_CONTENT =
      createPure(BufferingAppendable.class, "getAsSanitizedContent");

  public static final MethodRef CREATE_LOG_STATEMENT =
      createPure(JbcSrcRuntime.class, "createLogStatement", boolean.class, SoyValue.class);

  public static final MethodRef CLOSEABLE_CLOSE = createNonPure(Closeable.class, "close");

  public static final MethodRef PROTOCOL_ENUM_GET_NUMBER =
      createPure(ProtocolMessageEnum.class, "getNumber").asCheap();

  public static final MethodRef SOY_VISUAL_ELEMENT_CREATE =
      createPure(SoyVisualElement.class, "create", long.class, String.class);

  public static final MethodRef SOY_VISUAL_ELEMENT_CREATE_WITH_METADATA =
      createPure(
          SoyVisualElement.class,
          "create",
          long.class,
          String.class,
          LoggableElementMetadata.class);

  public static final MethodRef SOY_VISUAL_ELEMENT_DATA_CREATE =
      createPure(SoyVisualElementData.class, "create", SoyValue.class, Message.class);

  public static final MethodRef LAZY_PROTO_TO_SOY_VALUE_LIST_FOR_LIST =
      createPure(LazyProtoToSoyValueList.class, "forList", List.class, ProtoFieldInterpreter.class);

  public static final MethodRef LAZY_PROTO_TO_SOY_VALUE_MAP_FOR_MAP =
      createPure(
          LazyProtoToSoyValueMap.class,
          "forMap",
          Map.class,
          ProtoFieldInterpreter.class,
          ProtoFieldInterpreter.class);

  public static final MethodRef GET_EXTENSION_LIST =
      createPure(
          JbcSrcRuntime.class,
          "getExtensionList",
          ExtendableMessage.class,
          ExtensionLite.class,
          ProtoFieldInterpreter.class);

  public static final MethodRef AS_SWITCHABLE_VALUE_LONG =
      createPure(JbcSrcRuntime.class, "asSwitchableValue", long.class, int.class);
  public static final MethodRef AS_SWITCHABLE_VALUE_DOUBLE =
      createPure(JbcSrcRuntime.class, "asSwitchableValue", double.class, int.class);

  public static final MethodRef AS_SWITCHABLE_VALUE_SOY_VALUE =
      createPure(JbcSrcRuntime.class, "asSwitchableValue", SoyValue.class, int.class);

  public static final MethodRef NEW_SOY_SET = createPureConstructor(SetImpl.class, Iterator.class);

  // Constructors

  public static final MethodRef ARRAY_LIST = createNonPureConstructor(ArrayList.class);
  public static final MethodRef ARRAY_LIST_SIZE =
      createNonPureConstructor(ArrayList.class, int.class);
  public static final MethodRef HASH_MAP_CAPACITY =
      createNonPureConstructor(HashMap.class, int.class);
  public static final MethodRef LINKED_HASH_MAP_CAPACITY =
      createNonPureConstructor(LinkedHashMap.class, int.class);
  public static final MethodRef PARAM_STORE_AUGMENT =
      createPureConstructor(ParamStore.class, ParamStore.class, int.class);
  public static final MethodRef PARAM_STORE_SIZE =
      createPureConstructor(ParamStore.class, int.class);
  public static final MethodRef SOY_RECORD_IMPL =
      createPureConstructor(SoyRecordImpl.class, ParamStore.class);
  public static final MethodRef MSG_RENDERER =
      createPureConstructor(
          JbcSrcRuntime.MsgRenderer.class,
          long.class,
          ImmutableList.class,
          ULocale.class,
          int.class,
          boolean.class);
  public static final MethodRef PLRSEL_MSG_RENDERER =
      createPureConstructor(
          JbcSrcRuntime.PlrSelMsgRenderer.class,
          long.class,
          ImmutableList.class,
          ULocale.class,
          int.class,
          boolean.class);


  public static final MethodRef ESCAPING_BUFFERED_RENDER_DONE_FN =
      createPureConstructor(JbcSrcRuntime.EscapingBufferedRenderDoneFn.class, ImmutableList.class);

  public static final MethodRef STACK_FRAME_CREATE_LEAF =
      createPure(StackFrame.class, "create", RenderResult.class, int.class);

  public static final MethodRef STACK_FRAME_CREATE_NON_LEAF =
      createPure(StackFrame.class, "create", StackFrame.class, int.class);

  private MethodRefs() {}
}
