/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.tofu.restricted;

import com.google.template.soy.data.SoyData;
import com.google.template.soy.shared.restricted.SoyFunction;

import java.util.List;


/**
 * Interface for a Soy function implemented for the Tofu backend (a.k.a. Java Object backend).
 *
 * <p> Important: This may only be used in implementing function plugins.
 *
 * <p> Consider also implementing
 * {@link com.google.template.soy.shared.restricted.SoyJavaRuntimeFunction}. The {@code compute()}
 * method in {@code SoyJavaRuntimeFunction} should be exactly the same as the
 * {@code computeForTofu()} method of this interface, but can be used outside of the Tofu backend by
 * optimization passes. The easiest way to implement both interfaces at once is to subclass
 * {@link SoyAbstractTofuFunction}.
 *
 * @see SoyAbstractTofuFunction
 * @see com.google.template.soy.shared.restricted.SoyJavaRuntimeFunction
 * @author Kai Huang
 */
public interface SoyTofuFunction extends SoyFunction {


  /**
   * Computes this function on the given arguments for the Tofu backend.
   *
   * @param args The function arguments.
   * @return The computed result of this function.
   */
  public SoyData computeForTofu(List<SoyData> args);

}
