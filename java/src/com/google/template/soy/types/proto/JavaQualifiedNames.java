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

import com.google.common.base.CharMatcher;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.Descriptors;

/**
 * Helper class for generating fully qualified Java/GWT identfiers for descriptors.
 *
 */
public final class JavaQualifiedNames {
  private JavaQualifiedNames() {}

  /**
   * The suffix to append to the outer class name when it collides with the name of a message
   * declared in the proto file.
   */
  private static final String OUTER_CLASS_SUFFIX = "OuterClass";

  /** Returns the expected java package for protos based on the .proto file. */
  public static String getPackage(Descriptors.FileDescriptor fileDescriptor) {
    FileOptions fileOptions = fileDescriptor.getOptions();

    // Otherwise, derive it.
    String javaPackage = fileDescriptor.getOptions().getJavaPackage();
    if (javaPackage == null || javaPackage.equals("")) {
      String genericPackage = fileDescriptor.getPackage();
      if (genericPackage == null || genericPackage.equals("")) {
        javaPackage = "com.google.protos";
      } else {
        javaPackage = "com.google.protos." + fileDescriptor.getPackage();
      }
    }
    return javaPackage;
  }

  /** Used by the other overload, descends recursively into messages. */
  private static boolean hasConflictingClassName(Descriptors.Descriptor messageDesc, String name) {
    if (name.equals(messageDesc.getName())) {
      return true;
    }
    for (Descriptors.EnumDescriptor enumDesc : messageDesc.getEnumTypes()) {
      if (name.equals(enumDesc.getName())) {
        return true;
      }
    }
    for (Descriptors.Descriptor nestedMessageDesc : messageDesc.getNestedTypes()) {
      if (hasConflictingClassName(nestedMessageDesc, name)) {
        return true;
      }
    }
    return false;
  }

  /** Checks whether any generated classes conflict with the given name. */
  private static boolean hasConflictingClassName(Descriptors.FileDescriptor fileDesc, String name) {
    for (Descriptors.EnumDescriptor enumDesc : fileDesc.getEnumTypes()) {
      if (name.equals(enumDesc.getName())) {
        return true;
      }
    }
    for (Descriptors.ServiceDescriptor serviceDesc : fileDesc.getServices()) {
      if (name.equals(serviceDesc.getName())) {
        return true;
      }
    }
    for (Descriptors.Descriptor messageDesc : fileDesc.getMessageTypes()) {
      if (hasConflictingClassName(messageDesc, name)) {
        return true;
      }
    }
    return false;
  }

  /** Derives the outer class name based on the protobuf (.proto) file name. */
  public static String getOuterClassname(Descriptors.FileDescriptor fileDescriptor) {
    String className = fileDescriptor.getOptions().getJavaOuterClassname();
    if (CharMatcher.whitespace().matchesAllOf(className)) {
      className = fileDescriptor.getName();
      int cut0 = className.lastIndexOf("/");
      int cut1 = className.lastIndexOf(".");
      className = underscoresToCamelCase(className.substring(cut0 + 1, cut1), true);
      if (hasConflictingClassName(fileDescriptor, className)) {
        className = className + OUTER_CLASS_SUFFIX;
      }
    }
    return className;
  }

  private static void appendQualifiedName(StringBuilder builder, Descriptors.FileDescriptor fd,
      String name, Descriptors.Descriptor parent, boolean useCanonicalNames) {
    if (parent == null) {
      boolean topLevelClassSet = false;
      builder.append(getPackage(fd));
      if (fd.getOptions().getJavaMultipleFiles()) {
        // We aren't generating a wrapper class, so the package stays as is
      } else {
        builder.append('.').append(getOuterClassname(fd));
        topLevelClassSet = true;
      }
      builder.append(!topLevelClassSet || useCanonicalNames ? '.' : '$').append(name);
    } else {
      appendQualifiedName(
          builder, fd, parent.getName(), parent.getContainingType(), useCanonicalNames);
      builder.append(useCanonicalNames ? '.' : '$').append(name);
    }
  }

  private static String getQualifiedName(Descriptors.FileDescriptor fd, String name,
      Descriptors.Descriptor parent, boolean useCanonicalNames) {
    StringBuilder builder = new StringBuilder();
    appendQualifiedName(builder, fd, name, parent, useCanonicalNames);
    return builder.toString();
  }

  private static String getQualifiedName(Descriptors.Descriptor msg, boolean useCanonicalNames) {
    return getQualifiedName(
        msg.getFile(), msg.getName(), msg.getContainingType(), useCanonicalNames);
  }

  private static String getQualifiedName(
      Descriptors.EnumDescriptor enumType, boolean useCanonicalNames) {
    return getQualifiedName(
        enumType.getFile(), enumType.getName(), enumType.getContainingType(), useCanonicalNames);
  }

  /**
   * Returns the fully-qualified name for the message descriptor (uses '.' inner class seperator).
   */
  public static String getQualifiedName(Descriptors.Descriptor msg) {
    return getQualifiedName(msg, true);
  }

  /** Returns the fully-qualified name for the enum descriptor (uses '.' inner class seperator). */
  public static String getQualifiedName(Descriptors.EnumDescriptor enumType) {
    return getQualifiedName(enumType, true);
  }

  /** Returns the class name for the message descriptor (uses '$' inner class seperator). */
  public static String getClassName(Descriptors.Descriptor msg) {
    return getQualifiedName(msg, false);
  }

  /** Returns the class name for the enum descriptor (uses '$' inner class seperator). */
  public static String getClassName(Descriptors.EnumDescriptor enumType) {
    return getQualifiedName(enumType, false);
  }

  /** Returns the class name for the enum descriptor (uses '$' inner class seperator). */
  public static String getCaseEnumClassName(Descriptors.OneofDescriptor oneOfDescriptor) {
    return getQualifiedName(oneOfDescriptor.getContainingType(), false)
        + '$'
        + underscoresToCamelCase(oneOfDescriptor.getName(), true)
        + "Case";
  }

  public static String underscoresToCamelCase(String input, boolean capitializeNextLetter) {
    StringBuilder result = new StringBuilder();
    // Note:  I distrust ctype.h due to locales.
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
}
