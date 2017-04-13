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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A {@link SoyType} subclass which describes a protocol buffer type.
 *
 */
public final class SoyProtoType implements SoyType {

  private static final Logger logger = Logger.getLogger(SoyProtoType.class.getName());

  private final Descriptor typeDescriptor;
  private final ImmutableMap<String, Field> fields;

  SoyProtoType(
      SoyTypeRegistry typeRegistry, Descriptor descriptor, Set<FieldDescriptor> extensions) {
    this.typeDescriptor = descriptor;

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

    this.fields = fields.build();
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO;
  }

  @Override
  public boolean isAssignableFrom(SoyType fromType) {
    return fromType == this;
  }

  @Override
  public boolean isInstance(SoyValue value) {
    return value instanceof SoyProtoValue
        && ((SoyProtoValue) value).getProto().getDescriptorForType() == typeDescriptor;
  }

  public Descriptor getDescriptor() {
    return typeDescriptor;
  }

  /**
   * For ParseInfo generation, return a string that represents the Java source expression for the
   * static descriptor constant.
   *
   * @return The Java source expression for this type's descriptor.
   */
  public String getDescriptorExpression() {
    // We only need to import the outermost descriptor.
    Descriptor descriptor = typeDescriptor;
    while (descriptor.getContainingType() != null) {
      descriptor = descriptor.getContainingType();
    }
    return JavaQualifiedNames.getQualifiedName(descriptor) + ".getDescriptor()";
  }

  /** Returns the {@link FieldDescriptor} of the given field. */
  public FieldDescriptor getFieldDescriptor(String fieldName) {
    return fields.get(fieldName).getDescriptor();
  }

  /** Returns the {@link Field field metadata} for the given field. */
  @Nullable
  public Field getField(String name) {
    return fields.get(name);
  }

  /** Returns the {@link SoyType} of the given field, or null if the field does not exist. */
  @Nullable
  public SoyType getFieldType(String fieldName) {
    Field field = fields.get(fieldName);
    return field != null ? field.getType() : null;
  }

  /** Returns all the field names of this proto. */
  public ImmutableSet<String> getFieldNames() {
    return fields.keySet();
  }

  /** Returns this proto's type name for the given backend. */
  public String getNameForBackend(SoyBackendKind backend) {
    switch (backend) {
      case JS_SRC:
        // The 'proto' prefix is JSPB-specific. If we ever support some other
        // JavaScript proto implementation, we'll need some way to determine which
        // proto implementation the user wants to use at this point.
        return ProtoUtils.calculateQualifiedJsName(typeDescriptor);
      case TOFU:
      case JBC_SRC:
        return JavaQualifiedNames.getClassName(typeDescriptor);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public String toString() {
    return typeDescriptor.getFullName();
  }
}
