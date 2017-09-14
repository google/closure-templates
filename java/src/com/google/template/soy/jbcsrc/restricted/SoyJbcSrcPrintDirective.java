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

import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.List;

/**
 * A specialization of SoyPrintDirective for generating code for directives for the {@code jbcsrc}
 * backend.
 *
 * <p>Soy super package private. This interface is subject to change and should only be implemented
 * within the Soy codebase.
 */
public interface SoyJbcSrcPrintDirective extends SoyPrintDirective {

  /**
   * Applies this directive on the given value.
   *
   * <p>Important note when implementing this method: The value may not yet have been coerced to a
   * string. You may need to explicitly coerce it to a string using the {@link
   * SoyExpression#coerceToString()}.
   *
   * @param value The value to apply the directive on. This value may not yet have been coerced to a
   *     string.
   * @param args The directive's arguments, if any (usually none).
   * @return The resulting value.
   */
  SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args);

  /**
   * A print directive that supports streaming.
   *
   * <p>Streaming print directives work by wrapping the {@link LoggingAdvisingAppendable} object.
   * This means that the {@code value} is not passed directly to the directive, instead the {@code
   * value} is represented by a sequence of {@link LoggingAdvisingAppendable#append(CharSequence)
   * append} operations on the wrapped appendable object. It is further more expected that all
   * non-append operations (e.g. {@link
   * LoggingAdvisingAppendable#enterLoggableElement(com.google.template.soy.data.LogStatement)}) are
   * simply proxied through to the underlying object.
   *
   * <p>NOTE: any streamable print directive must also support a non-streaming option. The compiler
   * will prefer to use the streaming option but it will not always be possible or necessary (for
   * example, if the content is a compile time constant then we may avoid streaming it, or if this
   * print directive is combined with non-streamable print directives then we will not be able to
   * stream.
   */
  interface Streamable extends SoyJbcSrcPrintDirective {
    /**
     * Applies the directive to a {@link LoggingAdvisingAppendable} object.
     *
     * @param context The rendering context object.
     * @param delegateAppendable The delegate appendable
     * @param args The print directive arguments.
     * @return An expression of type {@link LoggingAdvisingAppendable} that applies the print
     *     directive logic.
     */
    Expression applyForJbcSrcStreaming(
        JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args);
  }
}
