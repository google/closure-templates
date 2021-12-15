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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.protobuf.DescriptorProtos.FieldOptions.JSType;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor.Syntax;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.ExtensionRegistry;
import javax.annotation.Nullable;

/** A collection of protobuf utility methods. */
public final class ProtoUtils {

  public static final ExtensionRegistry REGISTRY = createRegistry();

  private static final ExtensionRegistry createRegistry() {
    ExtensionRegistry instance = ExtensionRegistry.newInstance();
    // Add extensions needed for parsing descriptors here.
    return instance;
  }

  private ProtoUtils() {
    // Static only.
  }

  private static final ImmutableSet<String> SAFE_PROTO_TYPES =
      ImmutableSet.of(
          SafeHtmlProto.getDescriptor().getFullName(),
          SafeScriptProto.getDescriptor().getFullName(),
          SafeStyleProto.getDescriptor().getFullName(),
          SafeStyleSheetProto.getDescriptor().getFullName(),
          SafeUrlProto.getDescriptor().getFullName(),
          TrustedResourceUrlProto.getDescriptor().getFullName());

  /** Returns true if fieldDescriptor holds a sanitized proto type. */
  public static boolean isSanitizedContentField(FieldDescriptor fieldDescriptor) {
    return fieldDescriptor.getType() == FieldDescriptor.Type.MESSAGE
        && SAFE_PROTO_TYPES.contains(fieldDescriptor.getMessageType().getFullName());
  }

  /** Returns true if fieldDescriptor holds a map where the values are a sanitized proto type. */
  public static boolean isSanitizedContentMap(FieldDescriptor fieldDescriptor) {
    if (!fieldDescriptor.isMapField()) {
      return false;
    }
    Descriptor valueDesc = getMapValueMessageType(fieldDescriptor);
    if (valueDesc == null) {
      return false;
    }
    return SAFE_PROTO_TYPES.contains(valueDesc.getFullName());
  }

  /**
   * Returns the descriptor representing the type of the value of the map field. Returns null if the
   * map value isn't a message.
   */
  @Nullable
  public static Descriptor getMapValueMessageType(FieldDescriptor mapField) {
    FieldDescriptor valueDesc = getMapValueFieldDescriptor(mapField);
    if (valueDesc.getType() == FieldDescriptor.Type.MESSAGE) {
      return valueDesc.getMessageType();
    } else {
      return null;
    }
  }
  /**
   * Returns the field descriptor representing the type of the value of the map field. Returns null
   * if the map value isn't a message.
   */
  @Nullable
  public static FieldDescriptor getMapValueFieldDescriptor(FieldDescriptor mapField) {
    checkArgument(mapField.isMapField());
    return mapField.getMessageType().findFieldByName("value");
  }

  /** Returns the proper .getDescriptor() call for parse info generation in Tofu. */
  public static String getQualifiedOuterClassname(GenericDescriptor desc) {
    return String.format(
        "%s.%s.getDescriptor()",
        JavaQualifiedNames.getPackage(desc.getFile()),
        JavaQualifiedNames.getOuterClassname(desc.getFile()));
  }

  /** Returns the JS name of the import for the given extension, suitable for goog.require. */
  public static String getJsExtensionImport(FieldDescriptor desc) {
    Descriptor scope = desc.getExtensionScope();
    if (scope != null) {
      return calculateQualifiedJsName(scope);
    }
    return getJsPackage(desc.getFile()) + "." + computeJsExtensionName(desc);
  }

  /** Returns the JS name of the extension, suitable for passing to getExtension(). */
  public static String getJsExtensionName(FieldDescriptor desc) {
    Descriptor scope = desc.getExtensionScope();
    if (scope != null) {
      return calculateQualifiedJsName(scope) + "." + computeJsExtensionName(desc);
    }
    return getJsPackage(desc.getFile()) + "." + computeJsExtensionName(desc);
  }

  /** Performs camelcase translation. */
  private static String computeJsExtensionName(FieldDescriptor field) {
    String name = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, field.getName());
    return field.isRepeated() ? name + "List" : name;
  }

  /** Returns the expected javascript package for protos based on the .proto file. */
  private static String getJsPackage(FileDescriptor file) {
    String protoPackage = file.getPackage();
    if (!protoPackage.isEmpty()) {
      return "proto." + protoPackage;
    }
    return "proto";
  }

  public static boolean shouldJsIgnoreField(FieldDescriptor fieldDescriptor) {
    return false;
  }

  /** Only int64 fields can have jstype annotations. */
  private static final ImmutableSet<FieldDescriptor.Type> JS_TYPEABLE_FIELDS =
      Sets.immutableEnumSet(
          FieldDescriptor.Type.INT64,
          FieldDescriptor.Type.SFIXED64,
          FieldDescriptor.Type.UINT64,
          FieldDescriptor.Type.FIXED64,
          FieldDescriptor.Type.SINT64);

  /** Returns true if this field has a valid jstype annotation. */
  public static boolean hasJsType(FieldDescriptor fieldDescriptor) {
    if (!JS_TYPEABLE_FIELDS.contains(fieldDescriptor.getType())) {
      return false;
    }
    if (fieldDescriptor.getOptions().hasJstype()) {
      return true;
    }
    return false;
  }

  /** Returns true if this field is an unsigned integer. */
  public static boolean isUnsigned(FieldDescriptor descriptor) {
    switch (descriptor.getType()) {
      case FIXED32:
      case FIXED64:
      case UINT32:
      case UINT64:
        return true;
      default:
        return false;
    }
  }

  public static JSType getJsType(FieldDescriptor fieldDescriptor) {
    boolean hasJstype = fieldDescriptor.getOptions().hasJstype();
    if (hasJstype) {
      return fieldDescriptor.getOptions().getJstype();
    }
    return null;
  }

  public static String calculateJsEnumName(EnumDescriptor descriptor) {
    return calculateQualifiedJsName(descriptor);
  }

  public static String calculateQualifiedJsName(GenericDescriptor descriptor) {
    String protoPackage = descriptor.getFile().getPackage();
    // We need a semi-qualified name: including containing types but not the package.
    String name = descriptor.getFullName();
    if (!name.startsWith(protoPackage)) {
      throw new AssertionError("Expected \"" + name + "\" to start with \"" + protoPackage + "\"");
    }
    String jsPackage = getJsPackage(descriptor.getFile());
    // When there is no protoPackage, the semi-qualified name does not have a package prefix nor the
    // "." separator.
    if (protoPackage.isEmpty()) {
      return jsPackage + "." + name;
    }
    return jsPackage + name.substring(protoPackage.length());
  }

  /**
   * Returns whether or not we should check for presence to emulate jspb nullability semantics in
   * server side soy.
   */
  static boolean shouldCheckFieldPresenceToEmulateJspbNullability(FieldDescriptor desc) {
    if (desc.hasDefaultValue() || desc.isRepeated()) {
      return false;
    } else if (desc.getJavaType() == JavaType.MESSAGE) {
      // messages are always nullable
      return true;
    } else if (desc.getFile().getSyntax() == Syntax.PROTO3
    ) {
      return false;
    } else {
      return true;
    }
  }

  public static OneofDescriptor getContainingOneof(FieldDescriptor fd) {
    return
    fd.getContainingOneof();
  }
}
