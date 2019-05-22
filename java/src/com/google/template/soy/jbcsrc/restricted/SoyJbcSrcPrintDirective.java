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

import com.google.auto.value.AutoValue;
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
@SuppressWarnings("deprecation")
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
     * A simple object that represents an {@link Expression} for a {@link LoggingAdvisingAppendable}
     * which will apply a given print directive, as well as additional options for the behavior of
     * the print directive.
     */
    @AutoValue
    abstract class AppendableAndOptions {
      /**
       * Creates an appendable that doesn't need to be closed.
       *
       * <p>This means the appendable cannot buffer any content, each {@link
       * LoggingAdvisingAppendable#append} call must be handled immediately.
       */
      public static AppendableAndOptions create(Expression expression) {

        return create(expression, /* closeable= */ false);
      }

      /**
       * Creates an appendable that needs to be closed.
       *
       * <p>This means the appendable must implement {@link java.io.Closeable} and that the compiler
       * will ensure that the {@link java.io.Closeable#close} method will be called when no more
       * interactions will occur. Implementations can take advantage of this to implement print
       * directives that use a temporary buffer.
       */
      public static AppendableAndOptions createCloseable(Expression expression) {
        expression.checkAssignableTo(BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE);
        return create(expression, /* closeable= */ true);
      }

      private static AppendableAndOptions create(Expression expression, boolean closeable) {
        expression.checkAssignableTo(BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE);
        return new AutoValue_SoyJbcSrcPrintDirective_Streamable_AppendableAndOptions(
            expression, /* closeable= */ closeable);
      }

      /**
       * The {@link LoggingAdvisingAppendable} expression. Generates code to produce another {@link
       * LoggingAdvisingAppendable} that when written to applies the directive logic.
       */
      public abstract Expression appendable();

      /**
       * Specifies whether the {@link #appendable()} implements {@link java.io.Closeable} and needs
       * to have it's {@link java.io.Closeable#close()} method called to function correctly.
       */
      public abstract boolean closeable();
    }

    /**
     * Applies the directive to a {@link LoggingAdvisingAppendable} object.
     *
     * @param context The rendering context object.
     * @param delegateAppendable The delegate appendable
     * @param args The print directive arguments.
     * @return An {@link AppendableAndOptions} for the directive.
     */
    AppendableAndOptions applyForJbcSrcStreaming(
        JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args);
  }
}
