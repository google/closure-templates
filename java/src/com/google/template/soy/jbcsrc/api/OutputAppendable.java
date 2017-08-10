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
package com.google.template.soy.jbcsrc.api;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.logging.SoyLogger;
import java.io.IOException;

/**
 * The outermost logger used in rendering.
 *
 * <p>This object is for soy internal use only. Do not use.
 */
public final class OutputAppendable extends LoggingAdvisingAppendable {
  static final SoyLogger NO_OP_LOGGER =
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

  public static OutputAppendable create(AdvisingAppendable outputAppendable, SoyLogger logger) {
    return new OutputAppendable(outputAppendable, logger);
  }

  public static OutputAppendable create(final StringBuilder sb, SoyLogger logger) {
    return new OutputAppendable(
        new AdvisingAppendable() {
          @Override
          public AdvisingAppendable append(CharSequence csq) throws IOException {
            sb.append(csq);
            return this;
          }

          @Override
          public AdvisingAppendable append(CharSequence csq, int start, int end)
              throws IOException {
            sb.append(csq, start, end);
            return this;
          }

          @Override
          public AdvisingAppendable append(char c) throws IOException {
            sb.append(c);
            return this;
          }

          @Override
          public boolean softLimitReached() {
            // no limits in a stringbuilder
            return false;
          }
        },
        logger);
  }

  private final SoyLogger logger;
  private final AdvisingAppendable outputAppendable;

  private OutputAppendable(AdvisingAppendable outputAppendable, SoyLogger logger) {
    this.outputAppendable = checkNotNull(outputAppendable);
    this.logger = checkNotNull(logger);
  }

  @Override
  public boolean softLimitReached() {
    return outputAppendable.softLimitReached();
  }

  @Override
  public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
    outputAppendable.append(csq);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
    outputAppendable.append(csq, start, end);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable append(char c) throws IOException {
    outputAppendable.append(c);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall) throws IOException {
    outputAppendable.append(logger.evalLoggingFunction(funCall));
    return this;
  }

  @Override
  public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
    logger.enter(statement);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable exitLoggableElement() {
    logger.exit();
    return this;
  }
}
