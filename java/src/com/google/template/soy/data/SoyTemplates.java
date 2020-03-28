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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reflective utilities related to {@link SoyTemplate}. */
public final class SoyTemplates {

  private SoyTemplates() {}

  /**
   * Reflectively obtains the default instance of a template type.
   *
   * @throws IllegalArgumentException if the template has one or more required parameters (i.e. the
   *     getDefaultInstance method does not exist on the class).
   */
  public static <T extends SoyTemplate> T getDefaultInstance(Class<T> type) {
    try {
      Method factory = type.getDeclaredMethod("getDefaultInstance");
      Object instance = factory.invoke(null);
      return type.cast(instance);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "No default instance for template type " + type.getName(), e);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Unexpected error while calling getDefaultInstance() on " + type.getName(), e);
    }
  }

  /** Reflectively creates a builder of a template type. */
  public static <T extends SoyTemplate> SoyTemplate.Builder<T> getBuilder(Class<T> type) {
    try {
      Method factory = type.getDeclaredMethod("builder");
      @SuppressWarnings("unchecked")
      SoyTemplate.Builder<T> instance = (SoyTemplate.Builder<T>) factory.invoke(null);
      return instance;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Unexpected error while calling builder() on " + type.getName(), e);
    }
  }

  private static final ClassValue<String> templateNameValue =
      new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
          try {
            Field field = type.getDeclaredField("__NAME__");
            field.setAccessible(true); // the field is private
            return (String) field.get(null);
          } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Unexpected error while accessing the template name of " + type.getName(), e);
          }
        }
      };

  /** Returns the name of the Soy template that {@code type} renders. */
  public static String getTemplateName(Class<? extends SoyTemplate> type) {
    return templateNameValue.get(type);
  }

  private static final ClassValue<ImmutableSet<SoyTemplateParam<?>>> templateParamsValue =
      new ClassValue<ImmutableSet<SoyTemplateParam<?>>>() {
        @Override
        @SuppressWarnings("unchecked")
        protected ImmutableSet<SoyTemplateParam<?>> computeValue(Class<?> type) {
          try {
            Field field = type.getDeclaredField("__PARAMS__");
            field.setAccessible(true); // the field is private
            return (ImmutableSet<SoyTemplateParam<?>>) field.get(null);
          } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Unexpected error while accessing the template params of " + type.getName(), e);
          }
        }
      };

  /**
   * Returns the set of params of the Soy template that {@code type} renders. This list will not
   * include params unsupported by the type-safe API, like indirect proto params.
   */
  static ImmutableSet<SoyTemplateParam<?>> getParams(Class<? extends SoyTemplate> type) {
    return templateParamsValue.get(type);
  }

  /** Returns a {@link SoyTemplateInfo} representation of a template class. */
  public static SoyTemplateInfo asSoyTemplateInfo(Class<? extends SoyTemplate> type) {
    return new SoyTemplateInfoShim(type);
  }

  private static final class SoyTemplateInfoShim extends SoyTemplateInfo {
    SoyTemplateInfoShim(Class<? extends SoyTemplate> type) {
      super(getTemplateName(type), paramsAsMap(SoyTemplates.getParams(type)));
    }

    private static ImmutableMap<String, ParamRequisiteness> paramsAsMap(
        ImmutableSet<SoyTemplateParam<?>> params) {
      return params.stream()
          .collect(
              toImmutableMap(
                  SoyTemplateParam::getName,
                  p -> p.isRequired() ? ParamRequisiteness.REQUIRED : ParamRequisiteness.OPTIONAL));
    }
  }
}
