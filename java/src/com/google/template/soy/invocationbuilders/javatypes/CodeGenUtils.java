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
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.BaseSoyTemplateImpl.AbstractBuilder;
import com.google.template.soy.data.SoyValueConverter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Collection of Java members that can be used in generated code. Validates that the members exist
 * during code generation (before code compilation). Can also be used to abstract out where these
 * members reside.
 */
public final class CodeGenUtils {

  private CodeGenUtils() {}

  public static final Member AS_MAP_OF_NUMBERS =
      MethodImpl.method(AbstractBuilder.class, "asMapOfNumbers");
  public static final Member SET_PARAM = MethodImpl.method(AbstractBuilder.class, "setParam");
  public static final Member ADD_TO_LIST_PARAM =
      MethodImpl.method(AbstractBuilder.class, "addToListParam");
  public static final Member CHECK_NOT_NULL =
      MethodImpl.method(Preconditions.class, "checkNotNull");
  public static final Member AS_RECORD = MethodImpl.method(AbstractBuilder.class, "asRecord");
  public static final Member AS_NUMBER = MethodImpl.method(AbstractBuilder.class, "asNumber");
  public static final Member AS_NULLABLE_NUMBER =
      MethodImpl.method(AbstractBuilder.class, "asNullableNumber");
  public static final Member AS_NUMBER_COLLECTION =
      MethodImpl.method(AbstractBuilder.class, "asNumberCollection");
  public static final Member AS_COLLECTION =
      MethodImpl.method(AbstractBuilder.class, "asCollection");
  public static final Member AS_LIST_OF_DOUBLES =
      MethodImpl.method(AbstractBuilder.class, "asListOfDoubles");
  public static final Member DOUBLE_MAPPER =
      MethodImpl.field(AbstractBuilder.class, "doubleMapper");
  public static final Member LONG_MAPPER = MethodImpl.field(AbstractBuilder.class, "longMapper");
  public static final Member NUMBER_MAPPER =
      MethodImpl.field(AbstractBuilder.class, "numberMapper");
  public static final Member AS_LIST_OF_LONGS =
      MethodImpl.method(AbstractBuilder.class, "asListOfLongs");
  public static final Member MARK_AS_SOY_MAP =
      MethodImpl.method(SoyValueConverter.class, "markAsSoyMap");
  public static final Member AS_NULLABLE_ATTRIBUTES =
      MethodImpl.method(AbstractBuilder.class, "asNullableAttributes");
  public static final Member AS_ATTRIBUTES =
      MethodImpl.method(AbstractBuilder.class, "asAttributes");
  public static final Member AS_NULLABLE_CSS =
      MethodImpl.method(AbstractBuilder.class, "asNullableCss");
  public static final Member AS_CSS = MethodImpl.method(AbstractBuilder.class, "asCss");
  public static final Member TO_IMMUTABLE_MAP = unchecked("ImmutableMap.copyOf");

  /** A field or method that can be printed in code generation. */
  @Immutable
  public interface Member {
    @Override
    String toString();
  }

  private static Member unchecked(String s) {
    return new Member() {
      @Override
      public String toString() {
        return s;
      }
    };
  }

  @Immutable
  private static class MethodImpl implements Member {
    private final String name;

    private MethodImpl(java.lang.reflect.Member method) {
      this.name = method.getName();
    }

    private static MethodImpl method(Class<?> type, String methodName) {
      return new MethodImpl(findAnyMethod(type, methodName));
    }

    private static MethodImpl field(Class<?> type, String fieldName) {
      return new MethodImpl(findAnyField(type, fieldName));
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static Method findAnyMethod(Class<?> type, String methodName) {
    return findAny(type.getDeclaredMethods(), methodName);
  }

  private static Field findAnyField(Class<?> type, String fieldName) {
    return findAny(type.getDeclaredFields(), fieldName);
  }

  private static <T extends java.lang.reflect.Member> T findAny(T[] members, String name) {
    for (T member : members) {
      if (name.equals(member.getName())) {
        return member;
      }
    }
    throw new IllegalArgumentException();
  }
}
