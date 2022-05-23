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
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;

/** A compiled Soy template. Each instance is suitable for being rendered exactly once. */
@Immutable
public interface CompiledTemplate {

  // TODO(lukes): move to the runtime package?
  /** A factory subtype for representing factories as SoyValues. */
  final class TemplateValue extends SoyAbstractValue {
    public static TemplateValue create(String templateName, CompiledTemplate template) {
      return new TemplateValue(templateName, template);
    }

    private final String templateName;
    private final CompiledTemplate delegate;

    private TemplateValue(String templateName, CompiledTemplate delegate) {
      this.templateName = templateName;
      this.delegate = delegate;
    }

    public CompiledTemplate getTemplate() {
      return delegate;
    }

    public String getTemplateName() {
      return templateName;
    }

    @Override
    public final boolean coerceToBoolean() {
      return true;
    }

    @Override
    public final String coerceToString() {
      return String.format("** FOR DEBUGGING ONLY: %s **", templateName);
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

    @Override
    public final String toString() {
      return coerceToString();
    }
  }

  /**
   * Renders the template.
   *
   * @param params the explicit params of the template
   * @param ij the explicit injected params of the template
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
  RenderResult render(
      SoyRecord params, SoyRecord ij, LoggingAdvisingAppendable appendable, RenderContext context)
      throws IOException;
}
