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
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;

/** A compiled Soy template. Each instance is suitable for being rendered exactly once. */
public interface CompiledTemplate {
  /** A factory interface for constructing a {@link CompiledTemplate}. */
  @Immutable
  abstract class Factory extends SoyAbstractValue {
    /** Returns a new {@link CompiledTemplate} with the given {@link SoyRecord params}. */
    public abstract CompiledTemplate create(SoyRecord params, SoyRecord ij);

    @Override
    public final boolean coerceToBoolean() {
      return true;
    }

    @Override
    public final String coerceToString() {
      String className = getClass().getName();
      String soyTemplateName =
          Names.soyTemplateNameFromJavaClassName(
              className.substring(0, className.length() - "$Factory".length()));
      return String.format("** FOR DEBUGGING ONLY: template(%s) **", soyTemplateName);
    }

    @Override
    public final void render(LoggingAdvisingAppendable appendable) {
      throw new IllegalStateException("Printing template types is not allowed.");
    }

    @Override
    public final boolean equals(Object other) {
      return this == other;
    }

    @Override
    public final int hashCode() {
      return System.identityHashCode(this);
    }
  }

  /**
   * Renders the template.
   *
   * @param appendable The output target
   * @param context The rendering context
   * @return {@link RenderResult#done()} if rendering has completed successfully, {@link
   *     RenderResult#limited()} if rendering was paused because the appendable reported {@link
   *     LoggingAdvisingAppendable#softLimitReached()} or {@link
   *     RenderResult#continueAfter(java.util.concurrent.Future)} if rendering encountered a future
   *     that was not {@link java.util.concurrent.Future#isDone done}.
   * @throws IOException If the output stream throws an exception. This is a fatal error and
   *     rendering cannot be continued.
   */
  RenderResult render(LoggingAdvisingAppendable appendable, RenderContext context)
      throws IOException;

  /**
   * The content kind of the compiled template, used for making runtime content escaping decisions.
   */
  ContentKind kind();
}
