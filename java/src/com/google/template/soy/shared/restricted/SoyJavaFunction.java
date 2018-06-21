/*
 * Copyright 2013 Google Inc.
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

import com.google.template.soy.data.SoyValue;
import java.util.List;

/**
 * Interface for a Soy function implemented for Java runtime rendering. Functions implementing this
 * interface will be used during Tofu rendering, and may also be used during optimization passes if
 * the function is also marked with annotation {@code @SoyPureFunction}.
 *
 * <p>Important: This may only be used in implementing function plugins.
 *
 */
public interface SoyJavaFunction extends SoyFunction {

  /**
   * Computes this function on the given arguments.
   *
   * @param args The function arguments.
   * @return The computed result of this function.
   */
  public SoyValue computeForJava(List<SoyValue> args);
}
