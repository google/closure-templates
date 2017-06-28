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
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

/**
 * Helper class for generating fully qualified Java/GWT identfiers for descriptors.
 *
 */
public final class JavaQualifiedNames {
  private JavaQualifiedNames() {}

  private static final ImmutableMap<String, String> SPECIAL_CASES =
      ImmutableMap.<String, String>builder()
          .put("cached_size", "CachedSize_")
          .put("class", "Class_")
          .put("serialized_size", "SerializedSize_")
          .build();

  /** Returns the expected java package for protos based on the .proto file. */
  public static String getPackage(Descriptors.FileDescriptor fileDescriptor) {
    return getPackage(fileDescriptor, ProtoFlavor.PROTO2);
  }

  /** Derives the outer class name based on the protobuf (.proto) file name. */
  public static String getOuterClassname(Descriptors.FileDescriptor fileDescriptor) {
    return getFileClassName(fileDescriptor, ProtoFlavor.PROTO2);
  }

  /**
   * Returns the fully-qualified name for the message descriptor (uses '.' inner class seperator).
   */
  public static String getQualifiedName(Descriptors.Descriptor msg) {
    return getClassName(msg).replace('$', '.');
  }

  /** Returns the fully-qualified name for the enum descriptor (uses '.' inner class seperator). */
  public static String getQualifiedName(Descriptors.EnumDescriptor enumType) {
    return getClassName(enumType).replace('$', '.');
  }

  /** Returns the class name for the message descriptor (uses '$' inner class seperator). */
  public static String getClassName(Descriptors.Descriptor msg) {
    return getClassName(msg, ProtoFlavor.PROTO2);
  }

  /** Returns the class name for the enum descriptor (uses '$' inner class seperator). */
  public static String getClassName(Descriptors.EnumDescriptor enumType) {
    return getClassName(enumType, ProtoFlavor.PROTO2);
  }

  /**
   * Gets the fully qualified name for generated classes in Java convention. Nested classes will be
   * separated using '$' instead of '.'
   */
  public static String getClassName(Descriptor descriptor, ProtoFlavor flavor) {
    return getClassName(classNameWithoutPackage(descriptor), descriptor.getFile(), flavor);
  }

  /**
   * Gets the fully qualified name for generated classes in Java convention. Nested classes will be
   * separated using '$' instead of '.'
   */
  public static String getClassName(EnumDescriptor descriptor, ProtoFlavor flavor) {
    return getClassName(classNameWithoutPackage(descriptor), descriptor.getFile(), flavor);
  }

  /** Returns the Java name for a proto field. */
  public static String getFieldName(
      Descriptors.FieldDescriptor field, boolean capitializeFirstLetter) {
    String fieldName = field.getName();
    if (SPECIAL_CASES.containsKey(fieldName)) {
      String output = SPECIAL_CASES.get(fieldName);
      if (capitializeFirstLetter) {
        return output;
      } else {
        return ((char) (output.charAt(0) + ('a' - 'A'))) + output.substring(1);
      }
    }
    return underscoresToCamelCase(fieldName, capitializeFirstLetter);
  }

  /** Returns the class name for the enum descriptor (uses '$' inner class seperator). */
  public static String getCaseEnumClassName(Descriptors.OneofDescriptor oneOfDescriptor) {
    return getClassName(oneOfDescriptor.getContainingType())
        + '$'
        + underscoresToCamelCase(oneOfDescriptor.getName(), true)
        + "Case";
  }

  /** Converts underscore field names to camel case, while preserving camel case field names. */
  public static String underscoresToCamelCase(String input, boolean capitializeNextLetter) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char ch = input.charAt(i);
      if ('a' <= ch && ch <= 'z') {
        if (capitializeNextLetter) {
          result.append((char) (ch + ('A' - 'a')));
        } else {
          result.append(ch);
        }
        capitializeNextLetter = false;
      } else if ('A' <= ch && ch <= 'Z') {
        if (i == 0 && !capitializeNextLetter) {
          // Force first letter to lower-case unless explicitly told to
          // capitalize it.
          result.append((char) (ch + ('a' - 'A')));
        } else {
          // Capital letters after the first are left as-is.
          result.append(ch);
        }
        capitializeNextLetter = false;
      } else if ('0' <= ch && ch <= '9') {
        result.append(ch);
        capitializeNextLetter = true;
      } else {
        capitializeNextLetter = true;
      }
    }
    return result.toString();
  }

  private static String getClassName(
      String nameWithoutPackage, FileDescriptor file, ProtoFlavor flavor) {
    StringBuilder sb = new StringBuilder();
    if (multipleJavaFiles(file, flavor)) {
      sb.append(getPackage(file, flavor));
      if (sb.length() > 0) {
        sb.append('.');
      }
    } else {
      sb.append(getClassName(file, flavor));
      if (sb.length() > 0) {
        sb.append('$');
      }
    }
    sb.append(nameWithoutPackage.replace('.', '$'));
    return sb.toString();
  }

  private static String getClassName(FileDescriptor file, ProtoFlavor flavor) {
    StringBuilder sb = new StringBuilder();
    sb.append(getPackage(file, flavor));
    if (sb.length() > 0) {
      sb.append('.');
    }
    sb.append(getFileClassName(file, flavor));
    return sb.toString();
  }

  static String getPackage(FileDescriptor file, ProtoFlavor flavor) {
    return getPackage(file.toProto(), flavor);
  }

  static String getPackage(FileDescriptorProto file, ProtoFlavor flavor) {
    FileOptions fileOptions = file.getOptions();
    StringBuilder sb = new StringBuilder();
    if (fileOptions.hasJavaPackage()) {
      sb.append(fileOptions.getJavaPackage());
    } else {
      sb.append("com.google.protos");
      if (!file.getPackage().isEmpty()) {
        sb.append('.').append(file.getPackage());
      }
    }


    return sb.toString();
  }

  private static String classNameWithoutPackage(Descriptor descriptor) {
    return stripPackageName(descriptor.getFullName(), descriptor.getFile());
  }

  private static String classNameWithoutPackage(EnumDescriptor descriptor) {
    // Doesn't append "Mutable" for enum type's name.
    Descriptor messageDescriptor = descriptor.getContainingType();
    if (messageDescriptor == null) {
      return descriptor.getName();
    }
    return classNameWithoutPackage(messageDescriptor) + '.' + descriptor.getName();
  }

  private static String stripPackageName(String fullName, FileDescriptor file) {
    if (file.getPackage().isEmpty()) {
      return fullName;
    }
    return fullName.substring(file.getPackage().length() + 1);
  }

  private static boolean multipleJavaFiles(FileDescriptor fd, ProtoFlavor flavor) {
    return multipleJavaFiles(fd.toProto(), flavor);
  }

  private static boolean multipleJavaFiles(FileDescriptorProto fd, ProtoFlavor flavor) {
    FileOptions options = fd.getOptions();
    switch (flavor) {
      case PROTO2:
        return options.getJavaMultipleFiles();
      default:
        throw new AssertionError();
    }
  }

  /** Derives the outer class name based on the protobuf (.proto) file name. */
  static String getFileClassName(FileDescriptor file, ProtoFlavor flavor) {
    return getFileClassName(file.toProto(), flavor);
  }

  /** Derives the outer class name based on the protobuf (.proto) file name. */
  static String getFileClassName(FileDescriptorProto file, ProtoFlavor flavor) {
    switch (flavor) {
      case PROTO2:
        return getFileImmutableClassName(file);
      default:
        throw new AssertionError();
    }
  }

  private static String getFileImmutableClassName(FileDescriptorProto file) {
    if (file.getOptions().hasJavaOuterClassname()) {
      return file.getOptions().getJavaOuterClassname();
    }
    String className = getFileDefaultImmutableClassName(file);
    if (hasConflictingClassName(file, className)) {
      return className + "OuterClass";
    }
    return className;
  }

  private static String getFileDefaultImmutableClassName(FileDescriptorProto file) {
    String name = file.getName();
    int lastSlash = name.lastIndexOf('/');
    String basename;
    if (lastSlash < 0) {
      basename = name;
    } else {
      basename = name.substring(lastSlash + 1);
    }
    return underscoresToCamelCase(stripProto(basename), true);
  }

  private static String stripProto(String filename) {
    int lastDot = filename.lastIndexOf('.');
    if (lastDot >= 0) {
      switch (filename.substring(lastDot)) {
        case ".protodevel":
        case ".proto":
          return filename.substring(0, lastDot);
      }
    }
    return filename;
  }

  /** Used by the other overload, descends recursively into messages. */
  private static boolean hasConflictingClassName(DescriptorProto messageDesc, String name) {
    if (name.equals(messageDesc.getName())) {
      return true;
    }
    for (EnumDescriptorProto enumDesc : messageDesc.getEnumTypeList()) {
      if (name.equals(enumDesc.getName())) {
        return true;
      }
    }
    for (DescriptorProto nestedMessageDesc : messageDesc.getNestedTypeList()) {
      if (hasConflictingClassName(nestedMessageDesc, name)) {
        return true;
      }
    }
    return false;
  }

  /** Checks whether any generated classes conflict with the given name. */
  private static boolean hasConflictingClassName(FileDescriptorProto file, String name) {
    for (EnumDescriptorProto enumDesc : file.getEnumTypeList()) {
      if (name.equals(enumDesc.getName())) {
        return true;
      }
    }
    for (ServiceDescriptorProto serviceDesc : file.getServiceList()) {
      if (name.equals(serviceDesc.getName())) {
        return true;
      }
    }
    for (DescriptorProto messageDesc : file.getMessageTypeList()) {
      if (hasConflictingClassName(messageDesc, name)) {
        return true;
      }
    }
    return false;
  }
}
