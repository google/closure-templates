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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A proto member field.
 *
 * <p>This is used to calculate field names and handle ambiguous extensions. Additional logic should
 * be handled by subclasses.
 */
public abstract class Field {
  private static final Logger logger = Logger.getLogger(Field.class.getName());

  private static final SoyErrorKind AMBIGUOUS_FIELDS_ERROR =
      SoyErrorKind.of(
          "Cannot access {0}. It may refer to any one of the following extensions, "
              + "and Soy does not have enough information to decide which.\n"
              + "{1}\n"
              + "To resolve ensure "
              + "that all extension fields accessed from soy have unique names.");

  /** A factory for field types. */
  public interface Factory<T extends Field> {
    /** Returns a field. */
    T create(FieldDescriptor fieldDescriptor);

    /**
     * Creates a field for when there are several fields with conflicting soy names. This happens in
     * the case of extensions. It is expected that the concrete subclass throw an appropriate
     * exception (like {@link #ambiguousFieldsError}) when accessed.
     */
    T createAmbiguousFieldSet(Set<T> fields);
  }

  /** Returns the set of fields indexed by soy accessor name for the given type. */
  public static <T extends Field> ImmutableMap<String, T> getFieldsForType(
      Descriptor descriptor, Set<FieldDescriptor> extensions, Factory<T> factory) {
    SetMultimap<String, T> fieldsBySoyName =
        MultimapBuilder.hashKeys().linkedHashSetValues().build();
    for (FieldDescriptor fieldDescriptor : descriptor.getFields()) {
      if (ProtoUtils.shouldJsIgnoreField(fieldDescriptor)) {
        continue;
      }
      T field = factory.create(fieldDescriptor);
      fieldsBySoyName.put(field.getName(), field);
    }

    for (FieldDescriptor extension : extensions) {
      T field = factory.create(extension);
      fieldsBySoyName.put(field.getName(), field);
    }

    ImmutableMap.Builder<String, T> fields = ImmutableMap.builder();
    for (Map.Entry<String, Set<T>> group : Multimaps.asMap(fieldsBySoyName).entrySet()) {
      Set<T> ambiguousFields = group.getValue();
      String fieldName = group.getKey();
      if (ambiguousFields.size() == 1) {
        fields.put(fieldName, Iterables.getOnlyElement(ambiguousFields));
      } else {
        T value = factory.createAmbiguousFieldSet(ambiguousFields);
        logger.severe(
            "Proto "
                + descriptor.getFullName()
                + " has multiple extensions with the name \""
                + fieldName
                + "\": "
                + fullFieldNames(ambiguousFields)
                + "\nThis field will not be accessible from soy");
        fields.put(fieldName, value);
      }
    }

    return fields.build();
  }

  private final FieldDescriptor fieldDesc;
  private final boolean shouldCheckFieldPresenceToEmulateJspbNullability;
  private final String name;

  protected Field(FieldDescriptor fieldDesc) {
    this.fieldDesc = checkNotNull(fieldDesc);
    this.name = computeSoyName(fieldDesc);
    this.shouldCheckFieldPresenceToEmulateJspbNullability =
        ProtoUtils.shouldCheckFieldPresenceToEmulateJspbNullability(fieldDesc);
  }

  /** Return the name of this member field. */
  public final String getName() {
    return name;
  }

  /**
   * Returns whether or not we need to check for field presence to handle nullability semantics on
   * the server.
   */
  public final boolean shouldCheckFieldPresenceToEmulateJspbNullability() {
    return shouldCheckFieldPresenceToEmulateJspbNullability;
  }

  public final FieldDescriptor getDescriptor() {
    return fieldDesc;
  }

  private static String computeSoyName(FieldDescriptor field) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, field.getName())
        + fieldSuffix(field);
  }

  private static String fieldSuffix(FieldDescriptor field) {
    if (field.isMapField()) {
      return "Map";
    } else if (field.isRepeated()) {
      return "List";
    } else {
      return "";
    }
  }

  protected static RuntimeException ambiguousFieldsError(String name, Set<? extends Field> fields) {
    return new IllegalStateException(AMBIGUOUS_FIELDS_ERROR.format(name, fullFieldNames(fields)));
  }

  public static void reportAmbiguousFieldsError(
      ErrorReporter reporter, SourceLocation location, String name, Set<? extends Field> fields) {
    reporter.report(location, AMBIGUOUS_FIELDS_ERROR, name, fullFieldNames(fields));
  }

  private static ImmutableSet<String> fullFieldNames(Set<? extends Field> fields) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (Field field : fields) {
      builder.add(field.getDescriptor().getFullName());
    }
    return builder.build();
  }
}
