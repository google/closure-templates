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

/** The result of executing the logging function. */
@AutoValue
public abstract class LoggingFunctionInvocation {
  public static LoggingFunctionInvocation create(
      String functionName, String placeholderValue, List<SoyValue> args) {
    return new AutoValue_LoggingFunctionInvocation(functionName, placeholderValue, args);
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
}
