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
 * Represents any syntactic or semantic error made by a Soy template author, which can be collected
 * during compilation and displayed back to the author. (In particular, this class is not intended
 * to convey errors in the Soy implementation itself.) The error can be customized with {@link
 * #format string arguments}.
 *
 * <p>Classes that report SoyErrorKinds should declare them as static final fields, making it easy
 * for readers to inspect the errors that the class could report.
 *
 * <p>Error messages should be capitalized, and concluded with a punctuation mark.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class SoyErrorKind {

  /** Enum to enable exceptions to Soy error message formatting rules. */
  public enum StyleAllowance {
    NO_CAPS,
    NO_PUNCTUATION,
  }

  private final MessageFormat messageFormat;
  private final int requiredArgs;

  private SoyErrorKind(MessageFormat messageFormat) {
    this.messageFormat = messageFormat;
    this.requiredArgs = messageFormat.getFormatsByArgumentIndex().length;
  }

  public String format(Object... args) {
    Preconditions.checkState(
        args.length == requiredArgs,
        "Error format required %s parameters, %s were supplied.",
        requiredArgs,
        args.length);
    return messageFormat.format(args);
  }

  public static SoyErrorKind of(String format, StyleAllowance... exceptions) {
    checkFormat(format, exceptions);
    return new SoyErrorKind(new MessageFormat(format));
  }

  private static void checkFormat(String format, StyleAllowance... exceptions) {
    // Check for unmatched single quotes.  MessageFormat has some stupid legacy behavior to support
    // unmatched single quotes which is interpreted as 'escape the rest of the format string', this
    // is error prone.  If someone really wants to do that they can just add a "'" at the end of the
    // string.
    int index = 0;
    char singleQuote = '\'';
    while ((index = format.indexOf(singleQuote, index)) != -1) {
      int nextIndex = format.indexOf(singleQuote, index + 1);
      if (nextIndex == -1) {
        throw new IllegalArgumentException(
            "Found an unmatched single quote at char: " + index + " in '" + format + "'");
      }
      index = nextIndex + 1;
    }

    boolean checkCaps = true;
    boolean checkPunctuation = true;
    for (StyleAllowance ex : exceptions) {
      if (ex == StyleAllowance.NO_CAPS) {
        checkCaps = false;
      }
      if (ex == StyleAllowance.NO_PUNCTUATION) {
        checkPunctuation = false;
      }
    }

    // Error messages always start with a capital letter.
    if (checkCaps && Character.isLowerCase(format.charAt(0))) {
      throw new IllegalArgumentException("Message must start with a capital letter: " + format);
    }

    // Error messages should end with punctuation, unless it is a colon: statement
    if (checkPunctuation) {
      char lastChar = format.charAt(format.length() - 1);
      if (Character.getType(lastChar) != Character.OTHER_PUNCTUATION) {
        throw new IllegalArgumentException("Message must end with punctuation: " + format);
      }
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + messageFormat.toPattern() + "}";
  }
}
