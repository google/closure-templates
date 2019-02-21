/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.sharedpasses.render;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.Flags;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.SoyString;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnionType;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Implements runtime type checks for tofu. */
public final class TofuTypeChecks {
  private static final Logger logger = Logger.getLogger(TofuTypeChecks.class.getName());
  /**
   * Returns true if the given {@linkplain SoyValue value} is an instance of the {@linkplain SoyType
   * type}. For generic types, this only checks the overall shape of the type (list, map, etc) since
   * Java type erasure does not allow the type parameters to be checked. Also, in some cases the
   * "instanceof" test may be defined somewhat loosely - for example, sanitized types may be
   * considered instances of type string, since they are usable in any context where a string is
   * usable, even though internally they are not implemented as subclasses of string. This test does
   * not take into account automatic coercions, such as converting to string or boolean.
   *
   * @param type The type to test.
   * @param value The value to check against the type.
   * @param location The source location of the instance
   * @return True if the value is an instance of the type.
   */
  public static final boolean isInstance(SoyType type, SoyValue value, SourceLocation location) {
    switch (type.getKind()) {
      case ANY:
      case UNKNOWN:
        return true;
      case ATTRIBUTES:
        return isSanitizedofKind(value, ContentKind.ATTRIBUTES);
      case CSS:
        return isSanitizedofKind(value, ContentKind.CSS);
      case BOOL:
        return value instanceof BooleanData;
      case FLOAT:
        return value instanceof FloatData;
      case HTML:
        return isSanitizedofKind(value, ContentKind.HTML);
      case INT:
        return value instanceof IntegerData;
      case JS:
        return isSanitizedofKind(value, ContentKind.JS);
      case LIST:
        return value instanceof SoyList;
      case MAP:
        return value instanceof SoyMap;
      case LEGACY_OBJECT_MAP:
        return value instanceof SoyLegacyObjectMap;
      case NULL:
        return value == NullData.INSTANCE || value == UndefinedData.INSTANCE;
      case PROTO:
        // proto descriptors use instance equality.
        return value instanceof SoyProtoValue
            && ((SoyProtoValue) value).getProto().getDescriptorForType()
                == ((SoyProtoType) type).getDescriptor();
      case PROTO_ENUM:
        // TODO(lukes): this should also assert that the value is in range
        return value instanceof IntegerData;
      case RECORD:
        return value instanceof SoyRecord;
      case STRING:
        if (Flags.stringIsNotSanitizedContent()) {
          return value instanceof SoyString;
        } else {
          if (value instanceof SoyString
              && value instanceof SanitizedContent
              && ((SanitizedContent) value).getContentKind() != ContentKind.TEXT
              && logger.isLoggable(Level.WARNING)) {
            logger.log(
                Level.WARNING,
                String.format(
                    "Passing in sanitized content into a template that accepts only string is "
                        + "forbidden. Please modify the template at %s to take in "
                        + "%s instead of just %s.",
                    location != null ? location.toString() : "unknown",
                    ((SanitizedContent) value).getContentKind(), type.toString()));
          }
          return value instanceof SoyString || value instanceof SanitizedContent;
        }
      case TRUSTED_RESOURCE_URI:
        return isSanitizedofKind(value, ContentKind.TRUSTED_RESOURCE_URI);
      case UNION:
        for (SoyType memberType : ((UnionType) type).getMembers()) {
          if (isInstance(memberType, value, location)) {
            return true;
          }
        }
        return false;
      case URI:
        return isSanitizedofKind(value, ContentKind.URI);
      case VE:
      case VE_DATA:
        // Dynamic VE support is minimally implemented in Tofu: ve and ve_data objects are always
        // null.
        return value == NullData.INSTANCE;
      case ERROR:
        // continue
    }
    throw new AssertionError("invalid type: " + type);
  }

  private static boolean isSanitizedofKind(SoyValue value, ContentKind kind) {
    return value instanceof SanitizedContent && ((SanitizedContent) value).getContentKind() == kind;
  }
}
