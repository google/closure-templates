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
import com.google.common.base.Preconditions;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
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
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.runtime.BufferedSoyValueProvider;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.LegacyFunctionAdapter;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.logging.LoggableElementMetadata;
import com.google.template.soy.shared.internal.SharedRuntime;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import java.io.Closeable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** A reference to a method that can be called at runtime. */
@AutoValue
public abstract class MethodRef {

  public static final Type[] NO_METHOD_ARGS = {};

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
          SoyRecord.class,
          SoyRecord.class,
          LoggingAdvisingAppendable.class,
          RenderContext.class);

  public static final MethodRef DICT_IMPL_FOR_PROVIDER_MAP =
      createPure(DictImpl.class, "forProviderMap", Map.class, RuntimeMapTypeTracker.Type.class);

  public static final MethodRef MAP_IMPL_FOR_PROVIDER_MAP =
      createPure(SoyMapImpl.class, "forProviderMap", Map.class);

  public static final MethodRef MAP_IMPL_FOR_PROVIDER_MAP_NO_NULL_KEYS =
      createPure(SoyMapImpl.class, "forProviderMapNoNullKeys", Map.class);

  public static final MethodRef RECORD_IMPL_FOR_PROVIDER_MAP =
      createPure(SoyRecordImpl.class, "forProviderMap", Map.class);

  public static final MethodRef DOUBLE_TO_STRING =
      createPure(FloatData.class, "toString", double.class);

  public static final MethodRef EQUALS = createPure(Object.class, "equals", Object.class);

  public static final MethodRef STRING_COMPARE_TO =
      createPure(String.class, "compareTo", String.class);

  public static final MethodRef FLOAT_DATA_FOR_VALUE =
      createPure(FloatData.class, "forValue", double.class);

  public static final MethodRef RENDER_RESULT_ASSERT_DONE =
      createPure(RenderResult.class, "assertDone");

  /** a list of all the ImmutableList.of overloads, indexed by arity. */
  public static final ImmutableList<MethodRef> IMMUTABLE_LIST_OF;

  public static final MethodRef IMMUTABLE_LIST_OF_ARRAY;

  static {
    MethodRef[] immutableListOfMethods = new MethodRef[12];
    MethodRef immutableListOfArray = null;
    for (java.lang.reflect.Method m : ImmutableList.class.getMethods()) {
      if (m.getName().equals("of")) {
        Class<?>[] params = m.getParameterTypes();
        MethodRef ref = create(m, MethodPureness.PURE).asNonJavaNullable();
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

  public static final MethodRef MAP_GET_KEY = createPure(Map.Entry.class, "getKey");

  public static final MethodRef MAP_GET_VALUE = createPure(Map.Entry.class, "getValue");

  public static final MethodRef LIST_IMPL_FOR_PROVIDER_LIST =
      createPure(ListImpl.class, "forProviderList", List.class);

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
      createNonPure(
          JbcSrcRuntime.class, "setField", ParamStore.class, String.class, SoyValueProvider.class);

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

  public static final MethodRef RUNTIME_APPLY_PRINT_DIRECTIVE =
      createNonPure(
          JbcSrcRuntime.class,
          "applyPrintDirective",
          SoyJavaPrintDirective.class,
          SoyValue.class,
          List.class);

  public static final MethodRef RUNTIME_BIND_TEMPLATE_PARAMS =
      createPure(JbcSrcRuntime.class, "bindTemplateParams", TemplateValue.class, SoyRecord.class);

  public static final MethodRef RUNTIME_CALL_LEGACY_FUNCTION =
      createNonPure(
          JbcSrcRuntime.class, "callLegacySoyFunction", LegacyFunctionAdapter.class, List.class);

  public static final MethodRef RUNTIME_COERCE_DOUBLE_TO_BOOLEAN =
      createPure(JbcSrcRuntime.class, "coerceToBoolean", double.class);

  public static final MethodRef RUNTIME_COERCE_TO_STRING =
      createPure(JbcSrcRuntime.class, "coerceToString", SoyValue.class);

  public static final MethodRef RUNTIME_COERCE_TO_BOOLEAN =
      createPure(JbcSrcRuntime.class, "coerceToBoolean", SoyValue.class);

  public static final MethodRef RUNTIME_COERCE_STRING_TO_BOOLEAN =
      createPure(JbcSrcRuntime.class, "coerceToBoolean", String.class);

  public static final MethodRef RUNTIME_EQUAL =
      createPure(SharedRuntime.class, "equal", SoyValue.class, SoyValue.class);

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
      createPure(JbcSrcRuntime.class, "getField", SoyValue.class, String.class);

  public static final MethodRef RUNTIME_GET_FIELD_PROVIDER =
      createPure(JbcSrcRuntime.class, "getFieldProvider", SoyValue.class, String.class);

  public static final MethodRef RUNTIME_GET_RECORD_FIELD_PROVIDER =
      createPure(JbcSrcRuntime.class, "getFieldProvider", SoyRecord.class, String.class);

  public static final MethodRef RUNTIME_GET_RECORD_FIELD_PROVIDER_DEFAULT =
      createPure(
          JbcSrcRuntime.class, "getFieldProvider", SoyRecord.class, String.class, SoyValue.class);

  public static final MethodRef RUNTIME_PARAM_OR_DEFAULT =
      createPure(JbcSrcRuntime.class, "paramOrDefault", SoyValueProvider.class, SoyValue.class)
          .asCheap();
  public static final MethodRef RUNTIME_PARAM =
      createPure(JbcSrcRuntime.class, "param", SoyValueProvider.class).asCheap();

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

  public static final MethodRef CONSTRUCT_MAP_FROM_LIST =
      createPure(SharedRuntime.class, "constructMapFromList", List.class);

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

  public static final MethodRef HANDLE_BASIC_TRANSLATION =
      createPure(JbcSrcRuntime.class, "handleBasicTranslation", List.class);
  public static final MethodRef HANDLE_BASIC_TRANSLATION_AND_ESCAPE_HTML =
      createPure(JbcSrcRuntime.class, "handleBasicTranslationAndEscapeHtml", List.class);

  public static final MethodRef RUNTIME_STRING_EQUALS_AS_NUMBER =
      createPure(JbcSrcRuntime.class, "stringEqualsAsNumber", String.class, double.class);

  public static final MethodRef RUNTIME_NUMBER_EQUALS_STRING_AS_NUMBER =
      createPure(JbcSrcRuntime.class, "numberEqualsStringAsNumber", double.class, String.class);

  public static final MethodRef RUNTIME_EMPTY_TO_NULL =
      createPure(JbcSrcRuntime.class, "emptyToNull", SoyValue.class);

  public static final MethodRef RUNTIME_UNEXPECTED_STATE_ERROR =
      createNonPure(JbcSrcRuntime.class, "unexpectedStateError", StackFrame.class);

  public static final MethodRef SOY_VALUE_AS_JAVA_LIST = createPure(SoyValue.class, "asJavaList");
  public static final MethodRef SOY_VALUE_AS_JAVA_LIST_OR_NULL =
      createPure(SoyValue.class, "asJavaListOrNull");

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

  public static final MethodRef SOY_VALUE_IS_NULLISH =
      createPure(SoyValue.class, "isNullish").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_IS_NULL =
      createPure(SoyValue.class, "isNull").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_IS_UNDEFINED =
      createPure(SoyValue.class, "isUndefined").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_NULLISH_TO_NULL =
      createPure(SoyValue.class, "nullishToNull").asCheap().asNonJavaNullable();

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
      createNonPure(LoggingAdvisingAppendable.class, "buffering");

  public static final MethodRef BUFFERED_SOY_VALUE_PROVIDER_CREATE =
      createPure(BufferedSoyValueProvider.class, "create", BufferingAppendable.class);

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
          ProtoFieldInterpreter.class,
          Class.class);

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

  /**
   * Tracks whether a given method is pure.
   *
   * <p>Pureness implies that, given constant arguments, we can promote the method call to a
   * constant as well. Whether or not doing so is appropriate may depend on contextual information.
   */
  public enum MethodPureness {
    PURE,
    NON_PURE;
  }

  public static MethodRef createNonPure(Class<?> clazz, String methodName, Class<?>... params) {
    return create(getMethodUnchecked(clazz, methodName, params), MethodPureness.NON_PURE);
  }

  public static MethodRef createPure(Class<?> clazz, String methodName, Class<?>... params) {
    return create(getMethodUnchecked(clazz, methodName, params), MethodPureness.PURE);
  }

  private static java.lang.reflect.Method getMethodUnchecked(
      Class<?> clazz, String methodName, Class<?>... params) {
    try {
      // Ensure that the method exists and is public.
      return clazz.getMethod(methodName, params);
    } catch (Exception e) {
      throw new VerifyException(
          "Couldn't find the expected method among: " + Arrays.toString(clazz.getMethods()), e);
    }
    
  }

  public static MethodRef create(java.lang.reflect.Method method, MethodPureness pureness) {
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
        featuresForMethod(method),
        pureness);
  }

  public static MethodRef createInterfaceMethod(
      TypeInfo owner, Method method, MethodPureness pureness) {
    Preconditions.checkArgument(owner.isInterface());
    return new AutoValue_MethodRef(
        Opcodes.INVOKEINTERFACE,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(owner.type()).add(method.getArgumentTypes()).build(),
        Features.of(),
        pureness);
  }

  public static MethodRef createInstanceMethod(
      TypeInfo owner, Method method, MethodPureness pureness) {
    Preconditions.checkArgument(!owner.isInterface());
    return new AutoValue_MethodRef(
        Opcodes.INVOKEVIRTUAL,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(owner.type()).add(method.getArgumentTypes()).build(),
        Features.of(),
        pureness);
  }

  public static MethodRef createStaticMethod(
      TypeInfo owner, Method method, MethodPureness pureness) {
    return new AutoValue_MethodRef(
        Opcodes.INVOKESTATIC,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(method.getArgumentTypes()).build(),
        Features.of(),
        pureness);
  }

  private static Features featuresForMethod(java.lang.reflect.Method method) {
    boolean nonnull = method.isAnnotationPresent(Nonnull.class);
    return nonnull ? Features.of(Feature.NON_JAVA_NULLABLE) : Features.of();
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

  public abstract ImmutableList<Type> argTypes();

  public abstract Features features();

  public abstract MethodPureness pureness();

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
  public Statement invokeVoid(Expression... args) {
    return invokeVoid(Arrays.asList(args));
  }

  public Statement invokeVoid(Iterable<? extends Expression> args) {
    checkState(Type.VOID_TYPE.equals(returnType()), "Method return type is not void.");
    Expression.checkTypes(argTypes(), args);
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        doInvoke(adapter, args);
      }
    };
  }

  public Expression invoke(Expression... args) {
    return invoke(Arrays.asList(args));
  }

  public Expression invoke(Iterable<? extends Expression> args) {
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

  public MethodRef asNonJavaNullable() {
    return withFeature(Feature.NON_JAVA_NULLABLE);
  }

  private MethodRef withFeature(Feature feature) {
    if (features().has(feature)) {
      return this;
    }
    return new AutoValue_MethodRef(
        opcode(),
        owner(),
        method(),
        returnType(),
        argTypes(),
        features().plus(feature),
        pureness());
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
