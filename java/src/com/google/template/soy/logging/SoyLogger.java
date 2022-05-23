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
package com.google.template.soy.logging;

import com.google.common.html.types.SafeHtml;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingFunctionInvocation;
import java.util.Optional;

/**
 * Experimental logging interface for soy.
 *
 * <p>This implements a callback protocol with the {@code velog} syntax.
 */
public interface SoyLogger {
  /**
   * Called when a {@code velog} statement is entered.
   *
   * @return Optional VE logging info to be outputted to the DOM while in debug mode. Method
   *     implementation must check and only return VE logging info if in debug mode. Most
   *     implementations will likely return Optional.empty(). TODO(b/148167210): This is currently
   *     under implementation.
   */
  Optional<SafeHtml> enter(LogStatement statement);

  /**
   * Called when a {@code velog} statement is exited.
   *
   * @return Optional VE logging info to be outputted to the DOM while in debug mode. Method
   *     implementation must check and only return VE logging info if in debug mode. Most
   *     implementations will likely return Optional.empty(). TODO(b/148167210): This is currently
   *     under implementation.
   */
  Optional<SafeHtml> exit();

  // called to format a logging function value.
  String evalLoggingFunction(LoggingFunctionInvocation value);

  SoyLogger NO_OP =
      new SoyLogger() {
        @Override
        public Optional<SafeHtml> enter(LogStatement statement) {
          return Optional.empty();
        }

        @Override
        public Optional<SafeHtml> exit() {
          return Optional.empty();
        }

        @Override
        public String evalLoggingFunction(LoggingFunctionInvocation value) {
          return value.placeholderValue();
        }
      };

  /** The ID of the UndefinedVe. */
  long UNDEFINED_VE_ID = -1;
}
