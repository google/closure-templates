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

package com.google.template.soy.error;

import com.google.common.base.Preconditions;

import java.text.MessageFormat;

/**
 * Represents any syntactic or semantic error made by a Soy template author, which can be
 * collected during compilation and displayed back to the author.
 * (In particular, this class is not intended to convey errors in the Soy implementation itself.)
 * The error can be customized with {@link #format string arguments}.
 *
 * <p>Classes that report SoyErrors should declare them as static final fields, making it easy
 * for readers to inspect the errors that the class could report.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class SoyError {

  private final MessageFormat messageFormat;

  private SoyError(MessageFormat messageFormat) {
    this.messageFormat = messageFormat;
  }

  String format(String... args) {
    Preconditions.checkNotNull(args);
    Preconditions.checkState(args.length == messageFormat.getFormats().length);
    return messageFormat.format(args);
  }

  public static SoyError of(String format) {
    return new SoyError(new MessageFormat(format));
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "{" + messageFormat.toPattern() + "}";
  }
}
