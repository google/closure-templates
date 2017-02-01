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

package com.google.template.soy.types.proto;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.FileDescriptor.Syntax;
import com.google.protobuf.Message;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;
import java.util.ArrayList;
import java.util.List;

/** A collaborator for {@link SoyProtoType} that handles the interpretation of proto fields. */
abstract class FieldInterpreter {

  /** Creates a {@link FieldInterpreter} for the given field. */
  static FieldInterpreter create(SoyTypeRegistry typeRegistry, FieldDescriptor fieldDescriptor) {
    FieldInterpreter field = getScalarType(typeRegistry, fieldDescriptor);
    if (fieldDescriptor.isRepeated()) {
      if (Protos.hasJsMapKey(fieldDescriptor)) {
        return getMapType(typeRegistry, field, fieldDescriptor);
      } else {
        return getListType(typeRegistry, field);
      }
    }
    return field;
  }

  private static FieldInterpreter getListType(
      SoyTypeRegistry typeRegistry, final FieldInterpreter local) {
    final SoyType listType = typeRegistry.getOrCreateListType(local.type());
    return new FieldInterpreter() {
      @Override
      public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
        @SuppressWarnings("unchecked")
        List<?> entries = (List<?>) field;
        ImmutableList.Builder<SoyValueProvider> builder = ImmutableList.builder();
        for (Object item : entries) {
          builder.add(local.soyFromProto(converter, item));
        }
        return ListImpl.forProviderList(builder.build());
      }

      @Override
      public SoyType type() {
        return listType;
      }

      @Override
      Object protoFromSoy(SoyValue field) {
        SoyList list = (SoyList) field;
        List<Object> uninterpretedValues = new ArrayList<>();
        for (SoyValue item : list.asResolvedJavaList()) {
          uninterpretedValues.add(local.protoFromSoy(item));
        }
        return uninterpretedValues;
      }
    };
  }

  private static FieldInterpreter getMapType(
      SoyTypeRegistry typeRegistry,
      final FieldInterpreter scalarImpl,
      FieldDescriptor fieldDescriptor) {
    String keyFieldName = Protos.getJsMapKeyFieldName(fieldDescriptor);
    final FieldDescriptor keyDescriptor =
        fieldDescriptor.getMessageType().findFieldByName(keyFieldName);
    if (keyDescriptor == null) {
      throw new IllegalArgumentException("Cannot find field with name \"" + keyFieldName + "\".");
    } else if (keyDescriptor.getJavaType() != JavaType.STRING || keyDescriptor.isRepeated()) {
      throw new IllegalArgumentException(
          "\"" + keyFieldName + "\" must be an optional/required string field.");
    }
    final MapType type =
        typeRegistry.getOrCreateMapType(StringType.getInstance(), scalarImpl.type());
    return new FieldInterpreter() {
      @Override
      public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
        @SuppressWarnings("unchecked")
        List<Message> entries = (List<Message>) field;
        ImmutableMap.Builder<String, SoyValueProvider> builder = ImmutableMap.builder();
        for (Message message : entries) {
          String key = (String) message.getField(keyDescriptor);
          if (key.isEmpty()) {
            // Ignore empty keys.
            continue;
          }
          builder.put(key, scalarImpl.soyFromProto(converter, message));
        }
        return DictImpl.forProviderMap(builder.build());
      }

      @Override
      public SoyType type() {
        return type;
      }

      @Override
      Object protoFromSoy(SoyValue field) {
        // TODO(lukes): this is supportable, but mapkey fields are deprecated...  add support
        // when/if someone starts asking for it.
        throw new UnsupportedOperationException(
            "assigning to mapkey fields is not currently supported");
      }
    };
  }

  private static FieldInterpreter getScalarType(
      SoyTypeRegistry typeRegistry, FieldDescriptor fieldDescriptor) {
    // Field definition includes an option that overrides normal type.
    if (Protos.hasJsType(fieldDescriptor)) {
      Protos.JsType jsType = Protos.getJsType(fieldDescriptor);
      switch (jsType) {
        case INT52:
        case NUMBER:
          // in java soy ints are big enough, to work for both cases.
          return LONG_AS_INT;

        case STRING:
          return LONG_AS_STRING;
      }
    }

    switch (fieldDescriptor.getType()) {
      case BOOL:
        return BOOL;

      case DOUBLE:
        return DOUBLE_AS_FLOAT;

      case FLOAT:
        return FLOAT;

      case BYTES:
        return BYTES;

      case GROUP:
        throw new UnsupportedOperationException(
            "soy doesn't support proto groups: " + fieldDescriptor.getFullName());

      case INT64:
        return LONG_AS_INT;
      case INT32:
      case SINT32:
      case UINT32:
      case FIXED32:
      case SFIXED32:
        return INT;

      case FIXED64:
      case SINT64:
      case SFIXED64:
      case UINT64:
        throw new IllegalArgumentException(
            fieldDescriptor.getFullName()
                + ": 64-bit integer types are not supported.  "
                + "Instead, add [(jspb.jstype) = INT52] to the field.");

        // For enums and messages we delegate to the converter for interpretation to resolve a
        // circular dep between SoyProtoType and SoyProtoValue.
        // TODO(user): Remove the circular dependency.
      case ENUM:
        return dynamicTypeField(
            fieldDescriptor, typeRegistry.getType(fieldDescriptor.getEnumType().getFullName()));

      case MESSAGE:
        return dynamicTypeField(
            fieldDescriptor, typeRegistry.getType(fieldDescriptor.getMessageType().getFullName()));

      case STRING:
        return STRING;

      default:
        throw new AssertionError("Unexpected field type in proto");
    }
  }

  /** A {@link FieldInterpreter} for bytes typed fields. */
  private static final FieldInterpreter BYTES =
      new FieldInterpreter() {
        @Override
        public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
          return StringData.forValue(
              BaseEncoding.base64().encode(((ByteString) field).toByteArray()));
        }

        @Override
        public SoyType type() {
          return StringType.getInstance();
        }

        @Override
        Object protoFromSoy(SoyValue field) {
          return ByteString.copyFrom(BaseEncoding.base64().decode(field.stringValue()));
        }
      };

  /** A {@link FieldInterpreter} for bool typed fields. */
  private static final FieldInterpreter BOOL =
      new FieldInterpreter() {
        @Override
        public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
          return BooleanData.forValue((Boolean) field);
        }

        @Override
        public SoyType type() {
          return BoolType.getInstance();
        }

        @Override
        Object protoFromSoy(SoyValue field) {
          return field.booleanValue();
        }
      };

  /** A {@link FieldInterpreter} for int typed fields. */
  private static final FieldInterpreter INT =
      new FieldInterpreter() {
        @Override
        public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
          return IntegerData.forValue(((Number) field).longValue());
        }

        @Override
        public SoyType type() {
          return IntType.getInstance();
        }

        @Override
        Object protoFromSoy(SoyValue field) {
          return field.integerValue();
        }
      };

  /** A {@link FieldInterpreter} for int64 typed fields interpreted as soy ints. */
  private static final FieldInterpreter LONG_AS_INT =
      new FieldInterpreter() {
        @Override
        public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
          return IntegerData.forValue(((Long) field).longValue());
        }

        @Override
        public SoyType type() {
          return IntType.getInstance();
        }

        @Override
        Object protoFromSoy(SoyValue field) {
          return field.longValue();
        }
      };

  /** A {@link FieldInterpreter} for int64 typed fields interpreted as soy strings. */
  private static final FieldInterpreter LONG_AS_STRING =
      new FieldInterpreter() {
        @Override
        public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
          return StringData.forValue(field.toString());
        }

        @Override
        public SoyType type() {
          return StringType.getInstance();
        }

        @Override
        Object protoFromSoy(SoyValue field) {
          return Long.parseLong(field.stringValue());
        }
      };

  /** A {@link FieldInterpreter} for float typed fields. */
  private static final FieldInterpreter FLOAT =
      new FieldInterpreter() {
        @Override
        public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
          return FloatData.forValue(((Float) field).floatValue());
        }

        @Override
        public SoyType type() {
          return FloatType.getInstance();
        }

        @Override
        Object protoFromSoy(SoyValue field) {
          return (float) field.floatValue();
        }
      };

  /** A {@link FieldInterpreter} for double typed fields interpreted as soy floats. */
  private static final FieldInterpreter DOUBLE_AS_FLOAT =
      new FieldInterpreter() {
        @Override
        public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
          return FloatData.forValue(((Double) field).doubleValue());
        }

        @Override
        public SoyType type() {
          return FloatType.getInstance();
        }

        @Override
        Object protoFromSoy(SoyValue field) {
          return field.floatValue();
        }
      };

  /** A {@link FieldInterpreter} for string typed fields. */
  private static final FieldInterpreter STRING =
      new FieldInterpreter() {
        @Override
        public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
          return StringData.forValue(field.toString());
        }

        @Override
        public SoyType type() {
          return StringType.getInstance();
        }

        @Override
        Object protoFromSoy(SoyValue field) {
          return field.stringValue();
        }
      };

  /**
   * Returns a {@link FieldInterpreter} that has the given type and delegates to the
   * SoyValueConverter for interpretation.
   */
  private static final FieldInterpreter dynamicTypeField(
      final FieldDescriptor fieldDescriptor, final SoyType type) {
    checkNotNull(type);
    return new FieldInterpreter() {
      @Override
      public SoyType type() {
        return type;
      }

      @Override
      public SoyValueProvider soyFromProto(SoyValueConverter converter, Object field) {
        return converter.convert(field);
      }

      @Override
      Object protoFromSoy(SoyValue field) {
        if (type.getKind() == Kind.PROTO_ENUM) {
          // The proto reflection api wants the EnumValueDescriptor, not the actual enum instance
          EnumDescriptor enumDescriptor = fieldDescriptor.getEnumType();
          int value = field.integerValue();
          // in proto3 we preserve unknown enum values (for consistency with jbcsrc), but for proto2
          // we don't, and so if the field is unknown we will return null which will trigger an NPE
          // again, for consistency with jbcsrc.
          if (fieldDescriptor.getFile().getSyntax() == Syntax.PROTO3) {
            return enumDescriptor.findValueByNumberCreatingIfUnknown(value);
          }
          return enumDescriptor.findValueByNumber(value);
        } else if (type.getKind() == Kind.PROTO) {
          // We could assert that the message is the correct type, but the proto apis should do that
          // for us
          return ((SoyProtoValue) field).getProto();
        } else if (type.getKind().isKnownSanitizedContent()) {
          return SafeStringTypes.convertToProto(
              (SanitizedContent) field, fieldDescriptor.getMessageType().getFullName());
        }

        throw new AssertionError("unexpected soyType: " + type);
      }
    };
  }

  private FieldInterpreter() {}

  /** Returns the SoyType of the field. */
  abstract SoyType type();

  /** Returns the SoyValueProvider for the ToFu representation of the given field. */
  abstract SoyValueProvider soyFromProto(SoyValueConverter converter, Object field);

  /**
   * Returns an object that can be assigned to a proto field via the proto reflection APIs.
   *
   * <p>Generally this is the inverse operation of {@link #soyFromProto}.
   */
  abstract Object protoFromSoy(SoyValue field);
}
