/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.tofu.internal;

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.sharedpasses.render.Environment;
import com.google.template.soy.sharedpasses.render.EvalVisitor;
import javax.annotation.Nullable;

/**
 * Version of {@code EvalVisitor} for the Tofu backend.
 *
 * <p>For deprecated function implementations, uses {@code SoyTofuFunction}s instead of {@code
 * SoyJavaRuntimeFunction}s. (For new functions that implement {@code SoyJavaFunction}, there is no
 * difference.)
 *
 */
// TODO: Attempt to remove this class.
final class TofuEvalVisitor extends EvalVisitor {

  /**
   * @param valueConverter Instance of SoyValueConverter to use.
   * @param ijData The current injected data.
   * @param env The current environment.
   */
  protected TofuEvalVisitor(
      SoyValueConverter valueConverter, @Nullable SoyRecord ijData, Environment env) {
    super(valueConverter, ijData, env);
  }
}
