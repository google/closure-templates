/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Reflective utilities related to {@link SoyTemplate}. Methods in this class are visibility
 * restricted and in general not for use outside of core Soy library code.
 */
public final class SoyTemplatesRestrictedApi {

  private SoyTemplatesRestrictedApi() {}

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
      super(getTemplateName(type), paramsAsMap(getParams(type)));
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

  /**
   * Returns a list of the defined template types in the wrapper class {@code wrapperClass}. One
   * wrapper class is generated per Soy template file by tooling and its name ends with "Templates".
   *
   * @throws IllegalArgumentException if {@code wrapperClass} is not an actual wrapper class.
   */
  public static ImmutableList<Class<? extends SoyTemplate>> getTemplatesInFileWrapper(
      Class<?> wrapperClass) {
    @SuppressWarnings("unchecked")
    ImmutableList<Class<? extends SoyTemplate>> rv =
        stream(wrapperClass.getDeclaredClasses())
            .filter(
                c -> Modifier.isStatic(c.getModifiers()) && SoyTemplate.class.isAssignableFrom(c))
            .map(c -> (Class<? extends SoyTemplate>) c)
            .collect(toImmutableList());

    if (rv.isEmpty() && !wrapperClass.getName().endsWith("Templates")) {
      throw new IllegalArgumentException("Not a file wrapper class: " + wrapperClass);
    }

    return rv;
  }
}
