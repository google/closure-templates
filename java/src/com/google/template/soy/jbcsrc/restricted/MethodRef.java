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

  public static final MethodRef ARRAY_LIST_ADD = create(ArrayList.class, "add", Object.class);

  public static final MethodRef BOOLEAN_DATA_FOR_VALUE =
      create(BooleanData.class, "forValue", boolean.class);

  public static final MethodRef BOOLEAN_VALUE = create(Boolean.class, "booleanValue").asCheap();

  public static final MethodRef BOOLEAN_TO_STRING =
      create(Boolean.class, "toString", boolean.class).asCheap().asNonJavaNullable();

  public static final MethodRef COMPILED_TEMPLATE_RENDER =
      create(
          CompiledTemplate.class,
          "render",
          SoyRecord.class,
          SoyRecord.class,
          LoggingAdvisingAppendable.class,
          RenderContext.class);

  public static final MethodRef DICT_IMPL_FOR_PROVIDER_MAP =
      create(DictImpl.class, "forProviderMap", Map.class, RuntimeMapTypeTracker.Type.class);

  public static final MethodRef MAP_IMPL_FOR_PROVIDER_MAP =
      create(SoyMapImpl.class, "forProviderMap", Map.class);

  public static final MethodRef MAP_IMPL_FOR_PROVIDER_MAP_NO_NULL_KEYS =
      create(SoyMapImpl.class, "forProviderMapNoNullKeys", Map.class);

  public static final MethodRef RECORD_IMPL_FOR_PROVIDER_MAP =
      create(SoyRecordImpl.class, "forProviderMap", Map.class);

  public static final MethodRef DOUBLE_TO_STRING =
      create(FloatData.class, "toString", double.class);

  public static final MethodRef EQUALS = create(Object.class, "equals", Object.class);

  public static final MethodRef STRING_COMPARE_TO = create(String.class, "compareTo", String.class);

  public static final MethodRef FLOAT_DATA_FOR_VALUE =
      create(FloatData.class, "forValue", double.class);

  public static final MethodRef RENDER_RESULT_ASSERT_DONE =
      create(RenderResult.class, "assertDone");

  /** a list of all the ImmutableList.of overloads, indexed by arity. */
  public static final ImmutableList<MethodRef> IMMUTABLE_LIST_OF;

  public static final MethodRef IMMUTABLE_LIST_OF_ARRAY;

  static {
    MethodRef[] immutableListOfMethods = new MethodRef[12];
    MethodRef immutableListOfArray = null;
    for (java.lang.reflect.Method m : ImmutableList.class.getMethods()) {
      if (m.getName().equals("of")) {
        Class<?>[] params = m.getParameterTypes();
        MethodRef ref = MethodRef.create(m).asNonJavaNullable();
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
      create(IntegerData.class, "forValue", long.class);

  public static final MethodRef INTS_CHECKED_CAST =
      create(Ints.class, "checkedCast", long.class).asCheap();

  public static final MethodRef MAP_PUT = create(Map.class, "put", Object.class, Object.class);

  public static final MethodRef LIST_GET = create(List.class, "get", int.class).asCheap();

  public static final MethodRef LIST_SIZE = create(List.class, "size").asCheap();

  public static final MethodRef MAP_ENTRY_SET = create(Map.class, "entrySet").asNonJavaNullable();

  public static final MethodRef GET_ITERATOR =
      create(Iterable.class, "iterator").asNonJavaNullable();

  public static final MethodRef ITERATOR_NEXT = create(Iterator.class, "next");

  public static final MethodRef ITERATOR_HAS_NEXT = create(Iterator.class, "hasNext");

  public static final MethodRef MAP_GET_KEY = create(Map.Entry.class, "getKey");

  public static final MethodRef MAP_GET_VALUE = create(Map.Entry.class, "getValue");

  public static final MethodRef LIST_IMPL_FOR_PROVIDER_LIST =
      create(ListImpl.class, "forProviderList", List.class);

  public static final MethodRef LONG_PARSE_LONG =
      create(Long.class, "parseLong", String.class).asCheap().asNonJavaNullable();
  public static final MethodRef UNSIGNED_LONGS_PARSE_UNSIGNED_LONG =
      create(UnsignedLongs.class, "parseUnsignedLong", String.class).asCheap();
  public static final MethodRef UNSIGNED_LONGS_TO_STRING =
      create(UnsignedLongs.class, "toString", long.class).asCheap().asNonJavaNullable();
  public static final MethodRef UNSIGNED_INTS_SATURATED_CAST =
      create(UnsignedInts.class, "saturatedCast", long.class).asCheap();
  public static final MethodRef UNSIGNED_INTS_TO_LONG =
      create(UnsignedInts.class, "toLong", int.class).asCheap();

  public static final MethodRef LONG_TO_STRING =
      create(Long.class, "toString", long.class).asNonJavaNullable();

  public static final MethodRef NUMBER_DOUBLE_VALUE = create(Number.class, "doubleValue").asCheap();
  public static final MethodRef NUMBER_LONG_VALUE = create(Number.class, "longValue").asCheap();
  public static final MethodRef NUMBER_INT_VALUE = create(Number.class, "intValue").asCheap();
  public static final MethodRef NUMBER_FLOAT_VALUE = create(Number.class, "floatValue").asCheap();

  public static final MethodRef OBJECT_TO_STRING =
      create(Object.class, "toString").asNonJavaNullable();

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
      create(
          JbcSrcRuntime.class, "setField", ParamStore.class, String.class, SoyValueProvider.class);

  public static final MethodRef SOY_PROTO_VALUE_CREATE =
      create(SoyProtoValue.class, "create", Message.class);

  public static final MethodRef RENDER_RESULT_DONE = create(RenderResult.class, "done").asCheap();

  public static final MethodRef RENDER_RESULT_IS_DONE =
      create(RenderResult.class, "isDone").asCheap();

  public static final MethodRef RENDER_RESULT_LIMITED =
      create(RenderResult.class, "limited").asCheap();

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
      create(JbcSrcRuntime.class, "bindTemplateParams", TemplateValue.class, SoyRecord.class);

  public static final MethodRef RUNTIME_CALL_LEGACY_FUNCTION =
      create(JbcSrcRuntime.class, "callLegacySoyFunction", LegacyFunctionAdapter.class, List.class);

  public static final MethodRef RUNTIME_COERCE_DOUBLE_TO_BOOLEAN =
      create(JbcSrcRuntime.class, "coerceToBoolean", double.class);

  public static final MethodRef RUNTIME_COERCE_TO_STRING =
      create(JbcSrcRuntime.class, "coerceToString", SoyValue.class);

  public static final MethodRef RUNTIME_COERCE_TO_BOOLEAN =
      create(JbcSrcRuntime.class, "coerceToBoolean", SoyValue.class);

  public static final MethodRef RUNTIME_COERCE_STRING_TO_BOOLEAN =
      create(JbcSrcRuntime.class, "coerceToBoolean", String.class);

  public static final MethodRef RUNTIME_EQUAL =
      create(SharedRuntime.class, "equal", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_TRIPLE_EQUAL =
      create(SharedRuntime.class, "tripleEqual", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_SWITCH_CASE_EQUAL =
      create(SharedRuntime.class, "switchCaseEqual", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_COMPARE_BOXED_STRING =
      create(JbcSrcRuntime.class, "compareBoxedStringToBoxed", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_COMPARE_UNBOXED_STRING =
      create(JbcSrcRuntime.class, "compareUnboxedStringToBoxed", String.class, SoyValue.class);

  public static final MethodRef RUNTIME_GET_FIELD =
      create(JbcSrcRuntime.class, "getField", SoyValue.class, String.class);

  public static final MethodRef RUNTIME_GET_FIELD_PROVIDER =
      create(JbcSrcRuntime.class, "getFieldProvider", SoyValue.class, String.class);

  public static final MethodRef RUNTIME_GET_RECORD_FIELD_PROVIDER =
      create(JbcSrcRuntime.class, "getFieldProvider", SoyRecord.class, String.class);

  public static final MethodRef RUNTIME_GET_RECORD_FIELD_PROVIDER_DEFAULT =
      create(
          JbcSrcRuntime.class, "getFieldProvider", SoyRecord.class, String.class, SoyValue.class);

  public static final MethodRef RUNTIME_PARAM_OR_DEFAULT =
      create(JbcSrcRuntime.class, "paramOrDefault", SoyValueProvider.class, SoyValue.class)
          .asCheap();
  public static final MethodRef RUNTIME_PARAM =
      create(JbcSrcRuntime.class, "param", SoyValueProvider.class).asCheap();

  public static final MethodRef RUNTIME_GET_LIST_ITEM =
      create(JbcSrcRuntime.class, "getSoyListItem", List.class, long.class);

  public static final MethodRef RUNTIME_GET_LIST_ITEM_PROVIDER =
      create(JbcSrcRuntime.class, "getSoyListItemProvider", List.class, long.class);

  public static final MethodRef RUNTIME_GET_LIST_STATUS =
      create(JbcSrcRuntime.class, "getListStatus", List.class);

  public static final MethodRef RUNTIME_GET_MAP_STATUS =
      create(JbcSrcRuntime.class, "getMapStatus", Map.class);

  public static final MethodRef RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM =
      create(JbcSrcRuntime.class, "getSoyLegacyObjectMapItem", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_GET_LEGACY_OBJECT_MAP_ITEM_PROVIDER =
      create(
          JbcSrcRuntime.class, "getSoyLegacyObjectMapItemProvider", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_GET_MAP_ITEM =
      create(JbcSrcRuntime.class, "getSoyMapItem", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_GET_MAP_ITEM_PROVIDER =
      create(JbcSrcRuntime.class, "getSoyMapItemProvider", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_LESS_THAN =
      create(SharedRuntime.class, "lessThan", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_LESS_THAN_OR_EQUAL =
      create(SharedRuntime.class, "lessThanOrEqual", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_LOGGER = create(JbcSrcRuntime.class, "logger").asCheap();

  public static final MethodRef RUNTIME_DEBUGGER =
      create(JbcSrcRuntime.class, "debugger", String.class, int.class);

  public static final MethodRef RUNTIME_MINUS =
      create(SharedRuntime.class, "minus", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_NEGATIVE =
      create(SharedRuntime.class, "negative", SoyValue.class);

  public static final MethodRef RUNTIME_PLUS =
      create(SharedRuntime.class, "plus", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_MOD =
      create(SharedRuntime.class, "mod", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_SHIFT_RIGHT =
      create(SharedRuntime.class, "shiftRight", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_SHIFT_LEFT =
      create(SharedRuntime.class, "shiftLeft", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_BITWISE_OR =
      create(SharedRuntime.class, "bitwiseOr", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_BITWISE_XOR =
      create(SharedRuntime.class, "bitwiseXor", SoyValue.class, SoyValue.class);

  public static final MethodRef RUNTIME_BITWISE_AND =
      create(SharedRuntime.class, "bitwiseAnd", SoyValue.class, SoyValue.class);

  public static final MethodRef CONSTRUCT_MAP_FROM_LIST =
      create(SharedRuntime.class, "constructMapFromList", List.class);

  public static final MethodRef RUNTIME_TIMES =
      create(SharedRuntime.class, "times", SoyValue.class, SoyValue.class);

  public static final MethodRef MSG_RENDERER_SET_PLACEHOLDER =
      create(
          JbcSrcRuntime.MsgRenderer.class, "setPlaceholder", String.class, SoyValueProvider.class);

  public static final MethodRef MSG_RENDERER_SET_PLACEHOLDER_AND_ORDERING =
      create(
          JbcSrcRuntime.MsgRenderer.class,
          "setPlaceholderAndOrdering",
          String.class,
          SoyValueProvider.class,
          String.class);

  public static final MethodRef HANDLE_BASIC_TRANSLATION =
      create(JbcSrcRuntime.class, "handleBasicTranslation", List.class);
  public static final MethodRef HANDLE_BASIC_TRANSLATION_AND_ESCAPE_HTML =
      create(JbcSrcRuntime.class, "handleBasicTranslationAndEscapeHtml", List.class);

  public static final MethodRef RUNTIME_STRING_EQUALS_AS_NUMBER =
      create(JbcSrcRuntime.class, "stringEqualsAsNumber", String.class, double.class);

  public static final MethodRef RUNTIME_UNEXPECTED_STATE_ERROR =
      create(JbcSrcRuntime.class, "unexpectedStateError", StackFrame.class);

  public static final MethodRef SOY_VALUE_AS_JAVA_LIST = create(SoyValue.class, "asJavaList");
  public static final MethodRef SOY_VALUE_AS_JAVA_LIST_OR_NULL =
      create(SoyValue.class, "asJavaListOrNull");

  public static final MethodRef SOY_VALUE_AS_JAVA_MAP = create(SoyValue.class, "asJavaMap");

  public static final MethodRef SOY_VALUE_GET_PROTO = create(SoyValue.class, "getProto").asCheap();
  public static final MethodRef SOY_VALUE_GET_PROTO_OR_NULL =
      create(SoyValue.class, "getProtoOrNull").asCheap();

  public static final MethodRef SOY_VALUE_COERCE_TO_BOOLEAN =
      create(SoyValue.class, "coerceToBoolean").asCheap();

  public static final MethodRef SOY_VALUE_BOOLEAN_VALUE =
      create(SoyValue.class, "booleanValue").asCheap();

  public static final MethodRef SOY_VALUE_FLOAT_VALUE =
      create(SoyValue.class, "floatValue").asCheap();

  public static final MethodRef SOY_VALUE_LONG_VALUE =
      create(SoyValue.class, "longValue").asCheap();

  public static final MethodRef SOY_VALUE_NUMBER_VALUE = create(SoyValue.class, "numberValue");

  public static final MethodRef SOY_VALUE_IS_NULLISH =
      create(SoyValue.class, "isNullish").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_IS_NULL =
      create(SoyValue.class, "isNull").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_IS_UNDEFINED =
      create(SoyValue.class, "isUndefined").asCheap().asNonJavaNullable();

  public static final MethodRef SOY_VALUE_JAVA_NUMBER_VALUE =
      create(NumberData.class, "javaNumberValue");

  public static final MethodRef SOY_VALUE_STRING_VALUE =
      create(SoyValue.class, "stringValue").asCheap();

  public static final MethodRef SOY_VALUE_STRING_VALUE_OR_NULL =
      create(SoyValue.class, "stringValueOrNull").asCheap();

  public static final MethodRef SOY_VALUE_COERCE_TO_STRING =
      create(SoyValue.class, "coerceToString");

  public static final MethodRef CHECK_TYPE =
      MethodRef.create(SoyValue.class, "checkNullishType", Class.class);

  public static final MethodRef CHECK_INT = MethodRef.create(SoyValue.class, "checkNullishInt");

  public static final MethodRef CHECK_FLOAT = MethodRef.create(SoyValue.class, "checkNullishFloat");

  public static final MethodRef CHECK_NUMBER =
      MethodRef.create(SoyValue.class, "checkNullishNumber");

  public static final MethodRef CHECK_STRING =
      MethodRef.create(SoyValue.class, "checkNullishString");

  public static final MethodRef CHECK_BOOLEAN =
      MethodRef.create(SoyValue.class, "checkNullishBoolean");

  public static final MethodRef CHECK_CONTENT_KIND =
      MethodRef.create(SoyValue.class, "checkNullishSanitizedContent", ContentKind.class);

  public static final MethodRef CHECK_PROTO =
      MethodRef.create(SoyValue.class, "checkNullishProto", Class.class);

  public static final MethodRef GET_COMPILED_TEMPLATE_FROM_VALUE =
      create(TemplateValue.class, "getCompiledTemplate").asCheap();

  public static final MethodRef CREATE_TEMPLATE_VALUE =
      create(TemplateValue.class, "create", String.class, Object.class);

  public static final MethodRef SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE =
      create(
          SoyValueProvider.class,
          "renderAndResolve",
          LoggingAdvisingAppendable.class,
          boolean.class);

  public static final MethodRef COALESCE_TO_JAVA_NULL =
      create(JbcSrcRuntime.class, "coalesceToJavaNull", SoyValue.class);

  public static final MethodRef SOY_VALUE_PROVIDER_OR_NULLISH =
      create(JbcSrcRuntime.class, "soyValueProviderOrNullish", SoyValueProvider.class);

  public static final MethodRef SOY_VALUE_PROVIDER_STATUS =
      create(SoyValueProvider.class, "status");
  public static final MethodRef SOY_VALUE_PROVIDER_RESOLVE =
      create(SoyValueProvider.class, "resolve");

  public static final MethodRef STRING_CONCAT =
      create(String.class, "concat", String.class).asNonJavaNullable();

  public static final MethodRef STRING_IS_EMPTY = create(String.class, "isEmpty");

  public static final MethodRef STRING_VALUE_OF =
      create(String.class, "valueOf", Object.class).asNonJavaNullable();

  public static final MethodRef BOX_INTEGER =
      create(Integer.class, "valueOf", int.class).asNonJavaNullable();
  public static final MethodRef BOX_LONG =
      create(Long.class, "valueOf", long.class).asNonJavaNullable();
  public static final MethodRef BOX_DOUBLE =
      create(Double.class, "valueOf", double.class).asNonJavaNullable();
  public static final MethodRef BOX_FLOAT =
      create(Float.class, "valueOf", float.class).asNonJavaNullable();
  public static final MethodRef BOX_BOOLEAN =
      create(Boolean.class, "valueOf", boolean.class).asNonJavaNullable();
  public static final MethodRef CHECK_NOT_NULL =
      create(JbcSrcRuntime.class, "checkExpressionNotNull", Object.class, String.class);
  public static final MethodRef IS_SOY_NON_NULLISH =
      create(JbcSrcRuntime.class, "isNonSoyNullish", SoyValueProvider.class);

  public static final MethodRef STRING_DATA_FOR_VALUE =
      create(StringData.class, "forValue", String.class).asCheap();

  public static final MethodRef LOGGING_ADVISING_APPENDABLE_BUFFERING =
      create(LoggingAdvisingAppendable.class, "buffering");

  public static final MethodRef BUFFERED_SOY_VALUE_PROVIDER_CREATE =
      create(BufferedSoyValueProvider.class, "create", BufferingAppendable.class);

  public static final MethodRef CREATE_LOG_STATEMENT =
      MethodRef.create(JbcSrcRuntime.class, "createLogStatement", boolean.class, SoyValue.class);

  public static final MethodRef PROTOCOL_ENUM_GET_NUMBER =
      MethodRef.create(ProtocolMessageEnum.class, "getNumber").asCheap();

  public static final MethodRef SOY_VISUAL_ELEMENT_CREATE =
      MethodRef.create(SoyVisualElement.class, "create", long.class, String.class);

  public static final MethodRef SOY_VISUAL_ELEMENT_CREATE_WITH_METADATA =
      MethodRef.create(
          SoyVisualElement.class,
          "create",
          long.class,
          String.class,
          LoggableElementMetadata.class);

  public static final MethodRef SOY_VISUAL_ELEMENT_DATA_CREATE =
      MethodRef.create(SoyVisualElementData.class, "create", SoyValue.class, Message.class);

  public static final MethodRef LAZY_PROTO_TO_SOY_VALUE_LIST_FOR_LIST =
      MethodRef.create(
          LazyProtoToSoyValueList.class, "forList", List.class, ProtoFieldInterpreter.class);

  public static final MethodRef LAZY_PROTO_TO_SOY_VALUE_MAP_FOR_MAP =
      MethodRef.create(
          LazyProtoToSoyValueMap.class,
          "forMap",
          Map.class,
          ProtoFieldInterpreter.class,
          ProtoFieldInterpreter.class,
          Class.class);

  public static final MethodRef GET_EXTENSION_LIST =
      MethodRef.create(
          JbcSrcRuntime.class,
          "getExtensionList",
          ExtendableMessage.class,
          ExtensionLite.class,
          ProtoFieldInterpreter.class);

  public static final MethodRef AS_SWITCHABLE_VALUE_LONG =
      MethodRef.create(JbcSrcRuntime.class, "asSwitchableValue", long.class, int.class);
  public static final MethodRef AS_SWITCHABLE_VALUE_DOUBLE =
      MethodRef.create(JbcSrcRuntime.class, "asSwitchableValue", double.class, int.class);

  public static final MethodRef AS_SWITCHABLE_VALUE_SOY_VALUE =
      MethodRef.create(JbcSrcRuntime.class, "asSwitchableValue", SoyValue.class, int.class);

  public static MethodRef create(Class<?> clazz, String methodName, Class<?>... params) {
    java.lang.reflect.Method m;
    try {
      // Ensure that the method exists and is public.
      m = clazz.getMethod(methodName, params);
    } catch (Exception e) {
      throw new VerifyException(
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
        featuresForMethod(method));
  }

  public static MethodRef createInterfaceMethod(TypeInfo owner, Method method) {
    Preconditions.checkArgument(owner.isInterface());
    return new AutoValue_MethodRef(
        Opcodes.INVOKEINTERFACE,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(owner.type()).add(method.getArgumentTypes()).build(),
        Features.of());
  }

  public static MethodRef createInstanceMethod(TypeInfo owner, Method method) {
    Preconditions.checkArgument(!owner.isInterface());
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
