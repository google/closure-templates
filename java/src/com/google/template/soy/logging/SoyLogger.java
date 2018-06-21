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

import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingFunctionInvocation;

/**
 * Experimental logging interface for soy.
 *
 * <p>This implements a callback protocol with the {@code velog} syntax.
 */
public interface SoyLogger {
  /** Called when a {@code velog} statement is entered. */
  void enter(LogStatement statement);

  /** Called when a {@code velog} statement is exited. */
  void exit();

  // called to format a logging function value.
  String evalLoggingFunction(LoggingFunctionInvocation value);

  static final SoyLogger NO_OP =
      new SoyLogger() {
        @Override
        public void enter(LogStatement statement) {}

        @Override
        public void exit() {}

        @Override
        public String evalLoggingFunction(LoggingFunctionInvocation value) {
          return value.placeholderValue();
        }
      };
}
