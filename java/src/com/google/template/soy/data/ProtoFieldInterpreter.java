/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLongs;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor.Syntax;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.proto.FieldVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A collaborator for {@link SoyProtoValue} that handles the interpretation of proto fields.
 *
 * <p>Do not use outside of Soy code.
 */
public abstract class ProtoFieldInterpreter {
  private static final FieldVisitor<ProtoFieldInterpreter> VISITOR =
      new FieldVisitor<ProtoFieldInterpreter>() {
        @Override
        protected ProtoFieldInterpreter visitLongAsInt() {
          return LONG_AS_INT;
        }

        @Override
        protected ProtoFieldInterpreter visitUnsignedInt() {
          return UNSIGNED_INT;
        }

        @Override
        protected ProtoFieldInterpreter visitUnsignedLongAsString() {
          return UNSIGNEDLONG_AS_STRING;
        }

        @Override
        protected ProtoFieldInterpreter visitLongAsString() {
          return LONG_AS_STRING;
        }

        @Override
        protected ProtoFieldInterpreter visitBool() {
          return BOOL;
        }

        @Override
        protected ProtoFieldInterpreter visitBytes() {
          return BYTES;
        }

        @Override
        protected ProtoFieldInterpreter visitString() {
          return STRING;
        }

        @Override
        protected ProtoFieldInterpreter visitDoubleAsFloat() {
          return DOUBLE_AS_FLOAT;
        }

        @Override
        protected ProtoFieldInterpreter visitFloat() {
          return FLOAT;
        }

        @Override
        protected ProtoFieldInterpreter visitInt() {
          return INT;
        }

        @Override
        protected ProtoFieldInterpreter visitSafeHtml() {
          return SAFE_HTML_PROTO;
        }

        @Override
        protected ProtoFieldInterpreter visitSafeScript() {
          return SAFE_SCRIPT_PROTO;
        }

        @Override
        protected ProtoFieldInterpreter visitSafeStyle() {
          return SAFE_STYLE_PROTO;
        }

        @Override
        protected ProtoFieldInterpreter visitSafeStyleSheet() {
          return SAFE_STYLE_SHEET_PROTO;
        }

        @Override
        protected ProtoFieldInterpreter visitSafeUrl() {
          return SAFE_URL_PROTO;
        }

        @Override
        protected ProtoFieldInterpreter visitTrustedResourceUrl() {
          return TRUSTED_RESOURCE_URI_PROTO;
        }

        @Override
        protected ProtoFieldInterpreter visitMessage(Descriptor messageType) {
          return PROTO_MESSAGE;
        }

        @Override
        protected ProtoFieldInterpreter visitEnum(
            EnumDescriptor enumType, FieldDescriptor fieldType) {
          return enumTypeField(enumType);
        }

        @Override
        protected ProtoFieldInterpreter visitMap(
            FieldDescriptor mapField,
            ProtoFieldInterpreter keyValue,
            ProtoFieldInterpreter valueValue) {
          return getMapType(mapField, keyValue, valueValue);
        }

        @Override
        protected ProtoFieldInterpreter visitRepeated(ProtoFieldInterpreter value) {
          return getListType(value);
        }
      };

  /** Creates a {@link ProtoFieldInterpreter} for the given field. */
  static ProtoFieldInterpreter create(FieldDescriptor fieldDescriptor) {
    return FieldVisitor.visitField(fieldDescriptor, VISITOR);
  }

  private static ProtoFieldInterpreter getListType(final ProtoFieldInterpreter local) {
    return new ProtoFieldInterpreter() {
      @Override
      public SoyValue soyFromProto(Object field) {
        List<?> entries = (List<?>) field;
        ImmutableList.Builder<SoyValueProvider> builder = ImmutableList.builder();
        for (Object item : entries) {
          builder.add(local.soyFromProto(item));
        }
        return ListImpl.forProviderList(builder.build());
      }

      @Override
      public Object protoFromSoy(SoyValue field) {
        SoyList list = (SoyList) field;
        List<Object> uninterpretedValues = new ArrayList<>();
        for (SoyValue item : list.asResolvedJavaList()) {
          uninterpretedValues.add(local.protoFromSoy(item));
        }
        return uninterpretedValues;
      }
    };
  }

  private static ProtoFieldInterpreter getMapType(
      final FieldDescriptor mapField,
      final ProtoFieldInterpreter keyField,
      final ProtoFieldInterpreter valueField) {
    final Descriptor messageDescriptor = mapField.getMessageType();
    final FieldDescriptor keyDescriptor = messageDescriptor.getFields().get(0);
    final FieldDescriptor valueDescriptor = messageDescriptor.getFields().get(1);
    return new ProtoFieldInterpreter() {

      @Override
      public SoyValue soyFromProto(Object field) {
        @SuppressWarnings("unchecked")
        List<Message> entries = (List<Message>) field;
        Map<SoyValue, SoyValueProvider> map = Maps.newHashMapWithExpectedSize(entries.size());
        for (Message message : entries) {
          SoyValue key = keyField.soyFromProto(message.getField(keyDescriptor)).resolve();
          map.put(key, valueField.soyFromProto(message.getField(valueDescriptor)));
        }
        return SoyMapImpl.forProviderMap(map);
      }

      @Override
      public Object protoFromSoy(SoyValue field) {
        SoyMap map = (SoyMap) field;
        // Proto map fields use a non-standard API. A protobuf map is actually a repeated list of
        // MapEntry quasi-messages, which one cannot mutate in-place inside a map.
        ImmutableList.Builder<Message> mapEntries = ImmutableList.builder();
        Message.Builder defaultInstance =
            DynamicMessage.newBuilder(messageDescriptor.getContainingType());
        for (Map.Entry<? extends SoyValue, ? extends SoyValueProvider> entry :
            map.asJavaMap().entrySet()) {
          Message.Builder entryBuilder = defaultInstance.newBuilderForField(mapField);
          entryBuilder.setField(keyDescriptor, keyField.protoFromSoy(entry.getKey()));
          entryBuilder.setField(
              valueDescriptor, valueField.protoFromSoy(entry.getValue().resolve()));
          mapEntries.add(entryBuilder.build());
        }
        return mapEntries.build();
      }
    };
  }

  /** A {@link ProtoFieldInterpreter} for bytes typed fields. */
  public static final ProtoFieldInterpreter BYTES =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return StringData.forValue(
              BaseEncoding.base64().encode(((ByteString) field).toByteArray()));
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return ByteString.copyFrom(BaseEncoding.base64().decode(field.stringValue()));
        }
      };

  /** A {@link ProtoFieldInterpreter} for bool typed fields. */
  public static final ProtoFieldInterpreter BOOL =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return BooleanData.forValue((Boolean) field);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return field.booleanValue();
        }
      };

  /** A {@link ProtoFieldInterpreter} for int typed fields. */
  public static final ProtoFieldInterpreter INT =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return IntegerData.forValue(((Number) field).longValue());
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return Ints.saturatedCast(field.longValue());
        }
      };

  /** A {@link ProtoFieldInterpreter} for int typed fields. */
  public static final ProtoFieldInterpreter UNSIGNED_INT =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return IntegerData.forValue(UnsignedInts.toLong(((Number) field).intValue()));
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return UnsignedInts.saturatedCast(field.longValue());
        }
      };

  /** A {@link ProtoFieldInterpreter} for int64 typed fields interpreted as soy ints. */
  public static final ProtoFieldInterpreter LONG_AS_INT =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return IntegerData.forValue(((Long) field).longValue());
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return field.longValue();
        }
      };

  /** A {@link ProtoFieldInterpreter} for int64 typed fields interpreted as soy strings. */
  public static final ProtoFieldInterpreter LONG_AS_STRING =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return StringData.forValue(field.toString());
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return Long.parseLong(field.stringValue());
        }
      };

  /**
   * A {@link ProtoFieldInterpreter} for uint64 typed fields interpreted as soy strings.
   *
   * <p>TODO(lukes): when soy fully switches to java8 use the methods on java.lang.Long
   */
  public static final ProtoFieldInterpreter UNSIGNEDLONG_AS_STRING =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return StringData.forValue(UnsignedLongs.toString((Long) field));
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return UnsignedLongs.parseUnsignedLong(field.stringValue());
        }
      };

  /** A {@link ProtoFieldInterpreter} for float typed fields. */
  public static final ProtoFieldInterpreter FLOAT =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return FloatData.forValue(((Float) field).floatValue());
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return (float) field.floatValue();
        }
      };

  /** A {@link ProtoFieldInterpreter} for double typed fields interpreted as soy floats. */
  public static final ProtoFieldInterpreter DOUBLE_AS_FLOAT =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return FloatData.forValue(((Double) field).doubleValue());
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return field.floatValue();
        }
      };

  /** A {@link ProtoFieldInterpreter} for string typed fields. */
  public static final ProtoFieldInterpreter STRING =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return StringData.forValue(field.toString());
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return field.stringValue();
        }
      };

  public static final ProtoFieldInterpreter SAFE_HTML_PROTO =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return SanitizedContents.fromSafeHtmlProto((SafeHtmlProto) field);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return ((SanitizedContent) field).toSafeHtmlProto();
        }
      };

  public static final ProtoFieldInterpreter SAFE_SCRIPT_PROTO =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return SanitizedContents.fromSafeScriptProto((SafeScriptProto) field);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return ((SanitizedContent) field).toSafeScriptProto();
        }
      };

  public static final ProtoFieldInterpreter SAFE_STYLE_PROTO =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return SanitizedContents.fromSafeStyleProto((SafeStyleProto) field);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return ((SanitizedContent) field).toSafeStyleProto();
        }
      };

  public static final ProtoFieldInterpreter SAFE_STYLE_SHEET_PROTO =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return SanitizedContents.fromSafeStyleSheetProto((SafeStyleSheetProto) field);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return ((SanitizedContent) field).toSafeStyleSheetProto();
        }
      };

  public static final ProtoFieldInterpreter SAFE_URL_PROTO =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return SanitizedContents.fromSafeUrlProto((SafeUrlProto) field);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return ((SanitizedContent) field).toSafeUrlProto();
        }
      };
  public static final ProtoFieldInterpreter TRUSTED_RESOURCE_URI_PROTO =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return SanitizedContents.fromTrustedResourceUrlProto((TrustedResourceUrlProto) field);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return ((SanitizedContent) field).toTrustedResourceUrlProto();
        }
      };

  public static final ProtoFieldInterpreter ENUM_FROM_PROTO =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          int value;
          if (field instanceof ProtocolMessageEnum) {
            value = ((ProtocolMessageEnum) field).getNumber();
          } else {
            // The value will be an EnumValueDescriptor when fetched via reflection or a
            // ProtocolMessageEnum otherwise.  Who knows why.
            value = ((EnumValueDescriptor) field).getNumber();
          }
          return IntegerData.forValue(value);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          throw new UnsupportedOperationException("can't convert enum to proto");
        }
      };

  /**
   * Returns a {@link ProtoFieldInterpreter} that has the given type and delegates to the
   * SoyValueConverter for interpretation.
   */
  private static final ProtoFieldInterpreter enumTypeField(final EnumDescriptor enumDescriptor) {
    return new ProtoFieldInterpreter() {

      @Override
      public SoyValue soyFromProto(Object field) {
        return ENUM_FROM_PROTO.soyFromProto(field);
      }

      @Override
      public Object protoFromSoy(SoyValue field) {
        // The proto reflection api wants the EnumValueDescriptor, not the actual enum instance
        int value = field.integerValue();
        // in proto3 we preserve unknown enum values (for consistency with jbcsrc), but for proto2
        // we don't, and so if the field is unknown we will return null which will trigger an NPE
        // again, for consistency with jbcsrc.
        if (enumDescriptor.getFile().getSyntax() == Syntax.PROTO3) {
          return enumDescriptor.findValueByNumberCreatingIfUnknown(value);
        }
        return enumDescriptor.findValueByNumber(value);
      }
    };
  }

  public static final ProtoFieldInterpreter PROTO_MESSAGE =
      new ProtoFieldInterpreter() {
        @Override
        public SoyValue soyFromProto(Object field) {
          return SoyProtoValue.create((Message) field);
        }

        @Override
        public Object protoFromSoy(SoyValue field) {
          return ((SoyProtoValue) field).getProto();
        }
      };

  private ProtoFieldInterpreter() {}

  /** Returns the SoyValue for the Tofu representation of the given field. */
  public abstract SoyValue soyFromProto(Object field);

  /**
   * Returns an object that can be assigned to a proto field via the proto reflection APIs.
   *
   * <p>Generally this is the inverse operation of {@link #soyFromProto}.
   */
  public abstract Object protoFromSoy(SoyValue field);
}
