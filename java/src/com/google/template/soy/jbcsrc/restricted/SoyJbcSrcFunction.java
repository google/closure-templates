/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.jbcsrc.restricted;

import com.google.template.soy.shared.restricted.SoyFunction;
import java.util.List;

/**
 * A specialization of SoyFunction for generating code for plugin functions for the {@code jbcsrc}
 * backend.
 *
 * <p>Soy super package private. This interface is subject to change and should only be implemented
 * within the Soy codebase.
 */
public interface SoyJbcSrcFunction extends SoyFunction {
  /**
   * Computes this function on the given arguments for the Jbcsrc Source backend.
   *
   * @param context Contextual data for the current render operation.
   * @param args The function arguments.
   * @return The computed result of this function.
   */
  SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args);
}
