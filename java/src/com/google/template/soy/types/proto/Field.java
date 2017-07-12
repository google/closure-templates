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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** A proto member field. */
abstract class Field {
  private static final Logger logger = Logger.getLogger(Field.class.getName());

  static ImmutableMap<String, Field> getFieldsForType(
      @Nullable SoyTypeRegistry typeRegistry,
      Descriptor descriptor,
      Set<FieldDescriptor> extensions) {
    ImmutableMap.Builder<String, Field> fields = ImmutableMap.builder();
    for (FieldDescriptor fieldDescriptor : descriptor.getFields()) {
      if (ProtoUtils.shouldJsIgnoreField(fieldDescriptor)) {
        continue;
      }
      NormalField field = new NormalField(typeRegistry, fieldDescriptor);
      fields.put(field.getName(), field);
    }

    SetMultimap<String, ExtensionField> extensionsBySoyName =
        MultimapBuilder.hashKeys().hashSetValues().build();
    for (FieldDescriptor extension : extensions) {
      ExtensionField field = new ExtensionField(typeRegistry, extension);
      extensionsBySoyName.put(field.getName(), field);
    }

    for (Map.Entry<String, Set<ExtensionField>> group :
        Multimaps.asMap(extensionsBySoyName).entrySet()) {
      String fieldName = group.getKey();
      Set<ExtensionField> ambiguousFields = group.getValue();
      if (ambiguousFields.size() == 1) {
        fields.put(fieldName, Iterables.getOnlyElement(ambiguousFields));
      } else {
        AmbiguousFieldSet value = new AmbiguousFieldSet(fieldName, ambiguousFields);

        logger.severe(
            "Proto "
                + descriptor.getFullName()
                + " has multiple extensions with the name \""
                + fieldName
                + "\": "
                + value.getFullFieldNames()
                + "\nThis field will not be accessible from soy");
        fields.put(fieldName, value);
      }
    }

    return fields.build();
  }

  /** Return the name of this member field. */
  abstract String getName();

  /** Return the type of this member field. */
  abstract SoyType getType();
  
  /** Returns true if the given proto has a value for this field. */
  abstract boolean hasField(Message proto);

  /**
   * Returns whether or not we need to check for field presence to handle nullability semantics on
   * the server.
   */
  abstract boolean shouldCheckFieldPresenceToEmulateJspbNullability();

  /**
   * Returns an appropriately typed {@link SoyValueProvider} for this field in the given message.
   */
  abstract SoyValueProvider interpretField(Message owningMessage);

  /** Assigns the value to the field in the builder, coercing to a proto compatible type. */
  abstract void assignField(Message.Builder builder, SoyValue value);

  abstract FieldDescriptor getDescriptor();
}
