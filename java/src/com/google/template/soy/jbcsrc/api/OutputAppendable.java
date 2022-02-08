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
import static com.google.template.soy.jbcsrc.api.AppendableAsAdvisingAppendable.asAdvisingAppendable;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.AbstractLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.logging.SoyLogger;
import java.io.IOException;

/**
 * The outermost logger used in rendering.
 *
 * <p>This object is for soy internal use only. Do not use.
 */
public final class OutputAppendable extends AbstractLoggingAdvisingAppendable {

  public static OutputAppendable create(AdvisingAppendable outputAppendable, SoyLogger logger) {
    return new OutputAppendable(outputAppendable, logger);
  }

  public static OutputAppendable create(final StringBuilder sb, SoyLogger logger) {
    return new OutputAppendable(asAdvisingAppendable(sb), logger);
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
  protected void doAppend(CharSequence csq) throws IOException {
    outputAppendable.append(csq);
  }

  @Override
  protected void doAppend(CharSequence csq, int start, int end) throws IOException {
    outputAppendable.append(csq, start, end);
  }

  @Override
  protected void doAppend(char c) throws IOException {
    outputAppendable.append(c);
  }

  @Override
  protected void doAppendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException {
    String value = logger.evalLoggingFunction(funCall);
    for (Function<String, String> directive : escapers) {
      value = directive.apply(value);
    }
    outputAppendable.append(value);
  }

  @Override
  protected void doEnterLoggableElement(LogStatement statement) {
    logger.enter(statement);
  }

  @Override
  protected void doExitLoggableElement() {
    logger.exit();
  }

  @Override
  public void flushBuffers(int depth) {
    throw new AssertionError("shouldn't be called");
  }
}
