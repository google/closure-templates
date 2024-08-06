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

package com.google.template.soy.data;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Optional;

/** Reflective utilities related to {@link SoyTemplate}. */
public final class SoyTemplates {
  private static final ClassValue<Optional<SoyTemplate>> defaultInstance =
      new ClassValue<>() {
        @Override
        protected Optional<SoyTemplate> computeValue(Class<?> type) {
          if (SoyTemplate.class.isAssignableFrom(type)) {
            try {
              Method factory = type.getDeclaredMethod("getDefaultInstance");
              Object instance = factory.invoke(null);
              return Optional.of((SoyTemplate) type.cast(instance));
            } catch (NoSuchMethodException e) {
              return Optional.empty();
            } catch (ReflectiveOperationException e) {
              throw new LinkageError(
                  "Unexpected error while calling getDefaultInstance() on " + type.getName(), e);
            }
          }
          throw new IllegalArgumentException("Not a SoyTemplate type: " + type.getName());
        }
      };

  private SoyTemplates() {}

  /**
   * Returns whether a template type has a default instance, that is whether it has no required
   * parameters.
   */
  public static <T extends SoyTemplate> boolean hasDefaultInstance(Class<T> type) {
    return defaultInstance.get(type).isPresent();
  }

  /**
   * Reflectively obtains the default instance of a template type.
   *
   * @throws IllegalArgumentException if the template has one or more required parameters (i.e. the
   *     getDefaultInstance method does not exist on the class).
   */
  public static <T extends SoyTemplate> T getDefaultInstance(Class<T> type) {
    var instance = defaultInstance.get(type);
    if (instance.isEmpty()) {
      throw new IllegalArgumentException("No default instance for template type " + type.getName());
    }
    return type.cast(instance.get());
  }

  @FunctionalInterface
  private interface BuilderFactory {
    SoyTemplate.Builder<?> create();
  }

  private static final ClassValue<BuilderFactory> builderFactory =
      new ClassValue<>() {
        @Override
        protected BuilderFactory computeValue(Class<?> type) {
          if (SoyTemplate.class.isAssignableFrom(type)) {
            try {
              Method factory = type.getDeclaredMethod("builder");
              var lookup = MethodHandles.lookup();
              var handle = lookup.unreflect(factory);
              // Use lambda infrastructure to generate a BuilderFactory at runtime.
              return (BuilderFactory)
                  LambdaMetafactory.metafactory(
                          lookup,
                          "create",
                          MethodType.methodType(BuilderFactory.class),
                          MethodType.methodType(SoyTemplate.Builder.class),
                          handle,
                          MethodType.methodType(SoyTemplate.Builder.class))
                      .getTarget()
                      .invoke();
            } catch (Throwable e) {
              throw new LinkageError(
                  "Unexpected error while calling getDefaultInstance() on " + type.getName(), e);
            }
          }
          throw new IllegalArgumentException("Not a SoyTemplate type: " + type.getName());
        }
      };

  /** Reflectively creates a builder of a template type. */
  @SuppressWarnings("unchecked")
  public static <T extends SoyTemplate> SoyTemplate.Builder<T> getBuilder(Class<T> type) {
    return (SoyTemplate.Builder<T>) builderFactory.get(type).create();
  }
}
