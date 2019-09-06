/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.invocationbuilders.javatypes;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.internal.IndentedLinesBuilder;

/**
 * Class for simple java types (e.g. boolean, String, Number, SafeHtml) that do not need specialized
 * / complex logic. There are static constants at the top of this class for each of the types we
 * support. Callers must use these constants and cannot instantiate new instances of this class.
 *
 * <p>NOTE: For doubles and longs, see {@link PrimitiveJavaNumberType.DOUBLE} and {@link
 * PrimitiveJavaNumberType.LONG}. These are not simple types because they need special logic for
 * coercing {@code Number} types to{@code Long} or {@code Double} at runtime.
 */
public class SimpleJavaType extends JavaType {
  /** Constants for all of the simple types we support. */
  public static final SimpleJavaType BOOLEAN =
      SimpleJavaType.builder()
          .setIsPrimitive(true)
          .setJavaTypeString("boolean")
          .setGenericsTypeArgumentString("Boolean")
          .build();

  public static final SimpleJavaType NUMBER =
      SimpleJavaType.builder()
          .setIsPrimitive(false)
          .setJavaTypeString("Number")
          .setGenericsTypeArgumentString("? extends Number")
          .build();

  public static final SimpleJavaType HTML =
      SimpleJavaType.builder()
          .setIsPrimitive(false)
          .setJavaTypeString("SafeHtml")
          .setGenericsTypeArgumentString("SafeHtml")
          .build();
  public static final SimpleJavaType JS =
      SimpleJavaType.builder()
          .setIsPrimitive(false)
          .setJavaTypeString("SafeScript")
          .setGenericsTypeArgumentString("SafeScript")
          .build();

  public static final SimpleJavaType URL =
      SimpleJavaType.builder()
          .setIsPrimitive(false)
          .setJavaTypeString("SafeUrl")
          .setGenericsTypeArgumentString("SafeUrl")
          .build();

  public static final SimpleJavaType TRUSTED_RESOURCE_URL =
      SimpleJavaType.builder()
          .setIsPrimitive(false)
          .setJavaTypeString("TrustedResourceUrl")
          .setGenericsTypeArgumentString("TrustedResourceUrl")
          .build();

  public static final SimpleJavaType STRING =
      SimpleJavaType.builder()
          .setIsPrimitive(false)
          .setJavaTypeString("String")
          .setGenericsTypeArgumentString("String")
          .build();

  public static final SimpleJavaType OBJECT =
      SimpleJavaType.builder()
          .setIsPrimitive(false)
          .setJavaTypeString("Object")
          .setGenericsTypeArgumentString("?")
          .build();

  // Don't support as list/map type for now because we don't have a way of running the precondition
  // over all values.
  public static final SimpleJavaType ATTRIBUTES =
      new SimpleJavaType("SanitizedContent", false, null) {
        @Override
        public String appendRunTimeOperations(IndentedLinesBuilder ilb, String variableName) {
          ilb.appendLine(
              "Preconditions.checkArgument("
                  + variableName
                  + ".getContentKind() == SanitizedContent.ContentKind.ATTRIBUTES);");
          return variableName;
        }
      };

  private final String javaTypeString;
  private final boolean isPrimitive;
  private final String genericsTypeArgumentString;

  private SimpleJavaType(
      String javaTypeString, boolean isPrimitive, String genericsTypeArgumentString) {
    this.javaTypeString = javaTypeString;
    this.isPrimitive = isPrimitive;
    this.genericsTypeArgumentString = genericsTypeArgumentString;
  }

  @Override
  public String toJavaTypeString() {
    return javaTypeString;
  }

  @Override
  boolean isPrimitive() {
    return isPrimitive;
  }

  @Override
  String asGenericsTypeArgumentString() {
    return genericsTypeArgumentString;
  }

  private static Builder builder() {
    return new Builder();
  }

  private static class Builder {
    private String javaTypeString;
    private boolean isPrimitive;
    private String genericsTypeArgumentString;

    Builder() {}

    Builder setJavaTypeString(String javaTypeString) {
      this.javaTypeString = javaTypeString;
      return this;
    }

    Builder setIsPrimitive(boolean isPrimitive) {
      this.isPrimitive = isPrimitive;
      return this;
    }

    Builder setGenericsTypeArgumentString(String genericsTypeArgumentString) {
      this.genericsTypeArgumentString = genericsTypeArgumentString;
      return this;
    }

    SimpleJavaType build() {
      Preconditions.checkState(
          javaTypeString != null && !javaTypeString.isEmpty(),
          "Must set javaTypeString to a non-empty string");
      Preconditions.checkState(
          genericsTypeArgumentString != null && !genericsTypeArgumentString.isEmpty(),
          "Must set genericsTypeArgumentString to a non-empty string");
      return new SimpleJavaType(javaTypeString, isPrimitive, genericsTypeArgumentString);
    }
  }
}
