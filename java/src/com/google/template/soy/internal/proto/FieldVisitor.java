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

package com.google.template.soy.internal.proto;

import static com.google.common.base.Preconditions.checkState;

import com.google.errorprone.annotations.ForOverride;
import com.google.protobuf.DescriptorProtos.FieldOptions.JSType;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.List;

/**
 * An abstract visitor that handles all the proto field cases that are important to the soy
 * compiler.
 *
 * @param <T> the return type of the visit methods
 */
public abstract class FieldVisitor<T> {
  /** Applies the visitor to the given field. */
  public static <T> T visitField(FieldDescriptor fieldDescriptor, FieldVisitor<T> visitor) {
    // NOTE: map fields are technically repeated, so check isMap first.
    if (fieldDescriptor.isMapField()) {
      List<FieldDescriptor> mapFields = fieldDescriptor.getMessageType().getFields();
      checkState(mapFields.size() == 2, "proto representation of map fields changed");
      FieldDescriptor keyField = mapFields.get(0);
      FieldDescriptor valueField = mapFields.get(1);
      return visitor.visitMap(
          fieldDescriptor, getScalarType(keyField, visitor), getScalarType(valueField, visitor));
    } else if (fieldDescriptor.isRepeated()) {
      return visitor.visitRepeated(getScalarType(fieldDescriptor, visitor));
    } else {
      return getScalarType(fieldDescriptor, visitor);
    }
  }

  /** Visits a proto map field. Which is represented by a soy map. */
  @ForOverride
  protected abstract T visitMap(FieldDescriptor mapField, T keyValue, T valueValue);

  /**
   * Visits a repeated field.
   *
   * @param value the result of applying this visitor the field when interpreted as a scalar value.
   */
  @ForOverride
  protected abstract T visitRepeated(T value);

  /** Visits a long valued field that should be interpreted as a soy int. */
  @ForOverride
  protected abstract T visitLongAsInt();

  /** Visits an unsigned int valued field that should be interpreted as a soy int. */
  @ForOverride
  protected abstract T visitUnsignedInt();

  /** Visits an unsigned long valued field that should be interpreted as a soy string. */
  @ForOverride
  protected abstract T visitUnsignedLongAsString();

  /** Visits a long valued field that should be interpreted as a soy string. */
  @ForOverride
  protected abstract T visitLongAsString();

  /** Visits a boolean valued field that should be interpreted as a soy bool. */
  @ForOverride
  protected abstract T visitBool();

  /** Visits an int valued field that should be interpreted as a soy int. */
  @ForOverride
  protected abstract T visitInt();

  /** Visits a bytes valued field that should be interpreted as a base64 encoded soy string. */
  @ForOverride
  protected abstract T visitBytes();

  /** Visits a string valued field that should be interpreted as a soy string. */
  @ForOverride
  protected abstract T visitString();

  /** Visits a doubble valued field that should be interpreted as a soy float. */
  @ForOverride
  protected abstract T visitDoubleAsFloat();
  /** Visits a float valued field that should be interpreted as a soy float. */
  @ForOverride
  protected abstract T visitFloat();
  /** Visits a SafeHtmlProto field that should be interpreted as a soy html object. */
  @ForOverride
  protected abstract T visitSafeHtml();

  /** Visits a SafeScriptProto field that should be interpreted as a soy js object. */
  @ForOverride
  protected abstract T visitSafeScript();

  /** Visits a SafeStyleProto field that should be interpreted as a soy css object. */
  @ForOverride
  protected abstract T visitSafeStyle();

  /** Visits a SafeStyleSheetProto field that should be interpreted as a soy css object. */
  @ForOverride
  protected abstract T visitSafeStyleSheet();

  /** Visits a SafeUrlProto field that should be interpreted as a soy uri object. */
  @ForOverride
  protected abstract T visitSafeUrl();

  /**
   * Visits a TrustedResourceUrlProto field that should be interpreted as a soy trusted_resource_uri
   * object.
   */
  @ForOverride
  protected abstract T visitTrustedResourceUrl();

  /** Visits a message typed field that should be interpted as a soy proto type. */
  @ForOverride
  protected abstract T visitMessage(Descriptor messageType);

  /** Visits a enum typed field that should be interpted as a soy enum type. */
  @ForOverride
  protected abstract T visitEnum(EnumDescriptor enumType);

  private static <T> T getScalarType(FieldDescriptor fieldDescriptor, FieldVisitor<T> visitor) {
    // Field definition includes an option that overrides normal type.
    if (ProtoUtils.hasJsType(fieldDescriptor)) {
      JSType jsType = ProtoUtils.getJsType(fieldDescriptor);
      switch (jsType) {
        case JS_NORMAL:
        case JS_NUMBER:
          // in java soy ints are big enough, to work for both cases.  except for unsigned, but we
          // can't really support that in javascript anyway.
          return visitor.visitLongAsInt();
        case JS_STRING:
          if (ProtoUtils.isUnsigned(fieldDescriptor)) {
            return visitor.visitUnsignedLongAsString();
          }
          return visitor.visitLongAsString();
      }
    }

    switch (fieldDescriptor.getType()) {
      case BOOL:
        return visitor.visitBool();

      case DOUBLE:
        return visitor.visitDoubleAsFloat();

      case FLOAT:
        return visitor.visitFloat();

      case BYTES:
        return visitor.visitBytes();

      case GROUP:
        throw new UnsupportedOperationException(
            "soy doesn't support proto groups: " + fieldDescriptor.getFullName());

      case INT64:
        return visitor.visitLongAsInt();
      case INT32:
      case SINT32:
      case SFIXED32:
        return visitor.visitInt();
      case UINT32:
      case FIXED32:
        return visitor.visitUnsignedInt();

      case FIXED64:
      case SINT64:
      case SFIXED64:
      case UINT64:
        throw new IllegalArgumentException(
            "Cannot access "
                + fieldDescriptor.getFullName()
                + ": 64-bit integer types are not supported.  "
                + "Consider, adding [(jspb.jstype) = INT52] or [(jspb.jstype) = STRING] to the "
                + "field.");

      case ENUM:
        return visitor.visitEnum(fieldDescriptor.getEnumType());

      case MESSAGE:
        switch (fieldDescriptor.getMessageType().getFullName()) {
          case "webutil.html.types.SafeHtmlProto":
            return visitor.visitSafeHtml();
          case "webutil.html.types.SafeScriptProto":
            return visitor.visitSafeScript();
          case "webutil.html.types.SafeStyleProto":
            return visitor.visitSafeStyle();
          case "webutil.html.types.SafeStyleSheetProto":
            return visitor.visitSafeStyleSheet();
          case "webutil.html.types.SafeUrlProto":
            return visitor.visitSafeUrl();
          case "webutil.html.types.TrustedResourceUrlProto":
            return visitor.visitTrustedResourceUrl();
          default:
            return visitor.visitMessage(fieldDescriptor.getMessageType());
        }

      case STRING:
        return visitor.visitString();

      default:
        throw new AssertionError("Unexpected field type in proto");
    }
  }
}
