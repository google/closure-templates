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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.types.SoyType;
import java.util.Set;

/**
 * It is possible for extensions to have conflicting names (since protos only care about tag
 * numbers). However, for soy we need to identify fields by names not numbers. This allows us to
 * represent such fields in the type but make it an error to access them. In the future we may want
 * to add alternate mechanisms to access extensions that don't rely on field names solely.
 */
final class AmbiguousFieldSet extends Field {

  private final String name;
  private final ImmutableSet<ExtensionField> extensions;
  private final ImmutableSet<String> fullFieldNames;

  AmbiguousFieldSet(String name, Set<ExtensionField> fields) {
    Preconditions.checkArgument(fields.size() > 1);

    this.name = name;
    this.extensions = ImmutableSet.copyOf(fields);

    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (ExtensionField field : fields) {
      builder.add(field.fieldDescriptor.getFullName());
    }
    this.fullFieldNames = builder.build();
  }

  ImmutableSet<String> getFullFieldNames() {
    return fullFieldNames;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public SoyType getType() {
    throw failure();
  }

  @Override
  public boolean shouldCheckFieldPresenceToEmulateJspbNullability() {
    throw failure();
  }

  @Override
  boolean hasField(Message proto) {
    for (ExtensionField extension : extensions) {
      if (extension.hasField(proto)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public FieldDescriptor getDescriptor() {
    throw failure();
  }

  @Override
  public SoyValueProvider interpretField(Message owningMessage) {
    throw failure();
  }

  @Override
  public void assignField(Builder builder, SoyValue value) {
    throw failure();
  }

  private RuntimeException failure() {
    return new IllegalStateException(
        String.format(
            "Cannot access $%s. It may refer to any one of the following extensions, "
                + "and Soy doesn't have enough information to decide which.\n%s\nTo resolve ensure "
                + "that all extension fields accessed from soy have unique names.",
            name, fullFieldNames));
  }
}
