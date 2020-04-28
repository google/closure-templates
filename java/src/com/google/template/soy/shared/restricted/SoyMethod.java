/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.shared.restricted;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.types.SoyType;
import java.util.List;

/**
 * Represents a function that can be executed in Soy via method syntax. The two expected subtypes
 * are {@link com.google.template.soy.shared.internal.BuiltinMethod} and {@link
 * SoySourceFunctionMethod}.
 *
 * <p>Note that this interface represents a single, non-overloaded, non-polymorphic instance of a
 * method.
 */
public interface SoyMethod {

  /** Returns the number of args that this method accepts. */
  int getNumArgs();

  /** Returns whether this method can be passed args of type {@code argTypes}. */
  boolean appliesToArgs(List<SoyType> argTypes);

  /** May return empty string if the method has no one exact name. */
  String getMethodName();

  /** A queryable registry of soy methods. */
  interface Registry {

    ImmutableList<? extends SoyMethod> matchForNameAndBase(String methodName, SoyType baseType);

    ImmutableList<? extends SoyMethod> matchForBaseAndArgs(
        SoyType baseType, List<SoyType> argTypes);
  }
}
