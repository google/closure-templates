/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc.shared;

import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.internal.ParamStore;
import java.io.IOException;
import javax.annotation.Nullable;

/** A compiled Soy template. Each instance is suitable for being rendered exactly once. */
@Immutable
public interface CompiledTemplate {

  /**
   * Renders the template.
   *
   * @param frame The StackFrame to use to resume executing, or {@code null} for the initial render.
   * @param params the explicit params of the template
   * @param appendable The output target
   * @param context The rendering context
   * @return {@code null} if rendering has completed successfully, a {@link StackFrame} defining how
   *     to continue otherwise.
   * @throws IOException If the output stream throws an exception. This is a fatal error and
   *     rendering cannot be continued.
   */
  @Nullable
  StackFrame render(
      @Nullable StackFrame frame,
      ParamStore params,
      LoggingAdvisingAppendable appendable,
      RenderContext context)
      throws IOException;
}
