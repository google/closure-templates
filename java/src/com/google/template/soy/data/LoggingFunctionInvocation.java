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
package com.google.template.soy.data;

import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/** The result of executing the logging function. */
@AutoValue
public abstract class LoggingFunctionInvocation {

  @Nonnull
  public static LoggingFunctionInvocation create(
      String functionName, String placeholderValue, List<SoyValue> args) {
    return new AutoValue_LoggingFunctionInvocation(
        functionName, placeholderValue, args, Optional.<Consumer<String>>empty());
  }

  /**
   * Returns the name of the function being executed. As defined by {@code SoyFunction#getName()}.
   */
  public abstract String functionName();

  /** Returns the placeholder value. As defined by {@code LoggingFunction#getPlaceholder()}. */
  public abstract String placeholderValue();

  /**
   * Returns the arguments passed to the function. The number of items in the list will be one of
   * {@code SoyFunction#getValidArgsSizes()}.
   */
  public abstract List<SoyValue> args();

  /**
   * When set, informs the ultimate logger that the content should be sent to the consumer instead
   * of the output.
   */
  public abstract Optional<Consumer<String>> resultConsumer();

  /** Returns a new invocation that will send the result to the given consumer. */
  LoggingFunctionInvocation withResultConsumer(Consumer<String> resultConsumer) {
    if (resultConsumer().isPresent()) {
      // There is no known usecase where multiple consumers are needed.  If they are it is trivial
      // to compose `Consumer` objects via `Consumer.andThen
      throw new IllegalStateException("resultConsumer already set");
    }
    return new AutoValue_LoggingFunctionInvocation(
        functionName(), placeholderValue(), args(), Optional.of(resultConsumer));
  }
}
