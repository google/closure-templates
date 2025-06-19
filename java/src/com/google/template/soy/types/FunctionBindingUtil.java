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

package com.google.template.soy.types;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.error.ErrorReporter.LocationBound;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;

/**
 * Utilities for generating {@link FunctionType}s that are the result of calling `.bind()` on a
 * symbol representing a function.
 */
public class FunctionBindingUtil {
  private FunctionBindingUtil() {}

  private static final SoyErrorKind TOO_MANY_PARAMS =
      SoyErrorKind.of(
          "Cannot bind {0} parameter(s) to function of type `{1}`.", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NOT_ASSIGNABLE =
      SoyErrorKind.of(
          "Argument of type `{0}` is not assignable to parameter of type `{1}`.",
          StyleAllowance.NO_PUNCTUATION);

  public static SoyType bind(
      SoyType baseType, ImmutableList<SoyType> argTypes, ImmutableList<LocationBound> argErrors) {

    ImmutableSet<SoyType> functionTypes = SoyTypes.expandUnions(baseType);
    for (int i = 0; i < argTypes.size(); i++) {
      SoyType argType = argTypes.get(i);
      for (SoyType soyType : functionTypes) {
        FunctionType ft = (FunctionType) soyType;
        if (ft.getParameters().size() <= i) {
          argErrors.get(i).report(TOO_MANY_PARAMS, i + 1, ft);
          return NeverType.getInstance();
        }
        SoyType paramType = ft.getParameters().get(i).getType();
        if (!paramType.isAssignableFromStrict(argType)) {
          argErrors.get(i).report(NOT_ASSIGNABLE, argType, paramType);
          return NeverType.getInstance();
        }
      }
    }

    return UnionType.of(
        functionTypes.stream()
            .map(
                t -> {
                  FunctionType ft = (FunctionType) t;
                  return FunctionType.of(
                      ft.getParameters().subList(argTypes.size(), ft.getParameters().size()),
                      ft.getReturnType(),
                      ft.isVarArgs());
                })
            .collect(toImmutableList()));
  }
}
