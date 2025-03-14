/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.base.internal;

import com.google.common.base.Preconditions;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** Utilities related to {@link FunctionalInterface}. */
public final class FunctionalInterfaceUtil {
  private FunctionalInterfaceUtil() {}

  // cache of @FunctionalInterface method of implementor classes
  private static final ClassValue<Method> FUNCTIONAL_IFACE_METHOD_NAME =
      new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
          return type.isInterface() ? findInInterface(type) : findInClass(type);
        }
      };

  @Nullable
  private static Method findInClass(Class<?> clazz) {
    Preconditions.checkArgument(!clazz.isInterface());

    return Arrays.stream(clazz.getInterfaces())
        .map(FunctionalInterfaceUtil::findInInterface)
        .filter(Objects::nonNull)
        .findFirst()
        .orElseGet(
            // did not find here, try super class
            () -> clazz.getSuperclass() != null ? findInClass(clazz.getSuperclass()) : null);
  }

  @Nullable
  private static Method findInInterface(Class<?> clazz) {
    Preconditions.checkArgument(clazz.isInterface());

    // check for @FunctionalInterface
    if (clazz.isAnnotationPresent(FunctionalInterface.class)) {
      // return the first abstract method
      for (Method m : clazz.getMethods()) {
        if (Modifier.isAbstract(m.getModifiers()) && !isOverridableObjectMethod(m)) {
          return m;
        }
      }
    }

    // did not find here, try super interface
    return clazz.getSuperclass() != null ? findInInterface(clazz.getSuperclass()) : null;
  }

  // is this an overridable java.lang.Object method?
  private static boolean isOverridableObjectMethod(Method m) {
    switch (m.getName()) {
      case "equals":
        if (m.getReturnType() == boolean.class) {
          Class<?>[] params = m.getParameterTypes();
          return params.length == 1 && params[0] == Object.class;
        }
        return false;
      case "hashCode":
        return m.getReturnType() == int.class && m.getParameterCount() == 0;
      case "toString":
        return m.getReturnType() == String.class && m.getParameterCount() == 0;
      default: // fall out
    }
    return false;
  }

  @Nullable
  public static Method getMethod(Class<?> clazz) {
    return FUNCTIONAL_IFACE_METHOD_NAME.get(clazz);
  }
}
