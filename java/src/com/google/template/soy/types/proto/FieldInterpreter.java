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
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Message;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;

import java.util.List;

/**
 * A collaborator for {@link SoyProtoType} that handles the interpretation of proto fields.
 */
abstract class FieldInterpreter {

  /**
   * Creates a {@link FieldInterpreter} for the given field.
   */
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
      @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
        @SuppressWarnings("unchecked")
        List<?> entries = (List<?>) field;
        ImmutableList.Builder<SoyValueProvider> builder = ImmutableList.builder();
        for (Object item : entries) {
          builder.add(local.intepretField(converter, item));
        }
        return ListImpl.forProviderList(builder.build());
      }

      @Override public SoyType type() {
        return listType;
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
      throw new IllegalArgumentException(
          "Cannot find field with name \"" + keyFieldName + "\".");
    } else if (keyDescriptor.getJavaType() != JavaType.STRING || keyDescriptor.isRepeated()) {
      throw new IllegalArgumentException(
          "\"" + keyFieldName + "\" must be an optional/required string field.");
    }
    final MapType type = typeRegistry.getOrCreateMapType(
        StringType.getInstance(), scalarImpl.type());
    return new FieldInterpreter() {
      @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
        @SuppressWarnings("unchecked")
        List<Message> entries = (List<Message>) field;
        ImmutableMap.Builder<String, SoyValueProvider> builder = ImmutableMap.builder();
        for (Message message : entries) {
          String key = (String) message.getField(keyDescriptor);
          if (key.isEmpty()) {
            // Ignore empty keys.
            continue;
          }
          builder.put(key, scalarImpl.intepretField(converter, message));
        }
        return DictImpl.forProviderMap(builder.build());
      }

      @Override public SoyType type() {
        return type;
      }
    };
  }

  private static FieldInterpreter getScalarType(SoyTypeRegistry typeRegistry,
      FieldDescriptor fieldDescriptor) {
    // Field definition includes an option that overrides normal type.
    if (Protos.hasJsType(fieldDescriptor)) {
      Protos.JsType jsType = Protos.getJsType(fieldDescriptor);
      switch (jsType) {
        case INT52:
          return INT;

        case NUMBER:
          return getNumberFieldInterpreter(typeRegistry);

        case STRING:
          return STRING;
      }
    }

    switch (fieldDescriptor.getType()) {
      case BOOL:
        return BOOL;

      case DOUBLE:
      case FLOAT:
        return FLOAT;

      case BYTES:
      case GROUP:
        break;

      case INT32:
      case INT64:
      case SINT32:
      case UINT32:
      case FIXED32:
      case SFIXED32:
        return INT;

      case FIXED64:
      case SINT64:
      case SFIXED64:
      case UINT64:
        throw new IllegalArgumentException(fieldDescriptor.getFullName()
            + ": 64-bit integer types are not supported.  "
            + "Instead, add [(jspb.jstype) = INT52] to the field.");

      // For enums and messages we delegate to the converter for interpretation to resolve a
      // circular dep between SoyProtoTypeImpl and SoyProtoValue.
      case ENUM:
        return dynamicTypeField(typeRegistry.getType(fieldDescriptor.getEnumType().getFullName()));

      case MESSAGE:
        return dynamicTypeField(
            typeRegistry.getType(fieldDescriptor.getMessageType().getFullName()));

      case STRING:
        return STRING;

      default:
        throw new AssertionError("Unexpected field type in proto");
    }
    return ANY;
  }


  /** A {@link FieldInterpreter} for any typed fields. */
  private static final FieldInterpreter ANY = new FieldInterpreter() {
    @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
      return converter.convert(field);
    }

    @Override public SoyType type() {
      return AnyType.getInstance();
    }
  };


  /** A {@link FieldInterpreter} for bool typed fields. */
  private static final FieldInterpreter BOOL = new FieldInterpreter() {
    @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
      return BooleanData.forValue((Boolean) field);
    }

    @Override public SoyType type() {
      return BoolType.getInstance();
    }
  };


  /** A {@link FieldInterpreter} for int typed fields. */
  private static final FieldInterpreter INT = new FieldInterpreter() {
    @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
      return IntegerData.forValue(((Number) field).longValue());
    }

    @Override public SoyType type() {
      return IntType.getInstance();
    }
  };

  /** A {@link FieldInterpreter} for float typed fields. */
  private static final FieldInterpreter FLOAT = new FieldInterpreter() {
    @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
      return FloatData.forValue(((Number) field).doubleValue());
    }

    @Override public SoyType type() {
      return FloatType.getInstance();
    }
  };

  /** A {@link FieldInterpreter} for string typed fields. */
  private static final FieldInterpreter STRING = new FieldInterpreter() {
    @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
      return StringData.forValue(field.toString());
    }

    @Override public SoyType type() {
      return StringType.getInstance();
    }
  };

  /**
   * Returns a {@link FieldInterpreter} that has the given type and delegates to the
   * SoyValueConverter for interpretation.
   */
  private static final FieldInterpreter dynamicTypeField(final SoyType type) {
    checkNotNull(type);
    return new FieldInterpreter() {
      @Override public SoyType type() {
        return type;
      }

      @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
        return converter.convert(field);
      }
    };
  }

  // This is lazily/racily initialized by getNumberFieldInterpreter.
  private static volatile FieldInterpreter numberFactory;

  /**
   * Returns a {@link FieldInterpreter} for fields of type 'number' which is the super type of
   * 'float' and 'int'
   */
  private static FieldInterpreter getNumberFieldInterpreter(SoyTypeRegistry registry) {
    FieldInterpreter factory = numberFactory;
    if (factory == null) {
      final SoyType type = SoyTypes.NUMBER_TYPE;
      factory = new FieldInterpreter() {
        @Override public SoyType type() {
          return type;
        }

        @Override public SoyValueProvider intepretField(SoyValueConverter converter, Object field) {
          if (field instanceof Long | field instanceof Integer) {
            return IntegerData.forValue(((Number) field).longValue());
          } else if (field instanceof Double | field instanceof Float) {
            return FloatData.forValue(((Number) field).doubleValue());
          }
          throw new AssertionError("unknown number value: " + field);
        }
      };
      numberFactory = factory;
    }
    return factory;
  }

  private FieldInterpreter() {}
  
  /** Returns the SoyType of the field. */
  abstract SoyType type();

  /** Returns the SoyValueProvider for the ToFu representation of the given field. */
  abstract SoyValueProvider intepretField(SoyValueConverter converter, Object field);
}
