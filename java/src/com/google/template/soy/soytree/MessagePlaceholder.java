/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import javax.annotation.Nullable;

// TODO(b/161453282): Add placeholder example, user-supplied name, and user-supplied name source
// location field to `MessagePlaceholder`.
/**
 * Meta-data about a placeholder, including original user-supplied values.
 *
 * <p>This also includes static utility functions for validating user-supplied placeholder values.
 */
@AutoValue
public abstract class MessagePlaceholder {
  public static final String PHNAME_ATTR = "phname";
  public static final String PHEX_ATTR = "phex";
  private static final SoyErrorKind INVALID_PHNAME_ATTRIBUTE =
      SoyErrorKind.of("''phname'' is not a valid identifier.");
  private static final SoyErrorKind INVALID_PHNAME_EXAMPLE =
      SoyErrorKind.of("Placeholder examples must be non-empty.");

  /** Returns {@code name} if it's valid; otherwise, returns {@code null} and reports the error. */
  @Nullable
  public static String validatePlaceholderName(
      String name, SourceLocation location, ErrorReporter reporter) {
    if (BaseUtils.isIdentifier(name)) {
      return name;
    }
    reporter.report(location, INVALID_PHNAME_ATTRIBUTE);
    return null;
  }

  /**
   * Returns {@code example} if it's valid; otherwise, returns {@code null} and reports the error.
   */
  static String validatePlaceholderExample(
      String example, SourceLocation location, ErrorReporter reporter) {
    if (!example.isEmpty()) {
      return example;
    }
    reporter.report(location, INVALID_PHNAME_EXAMPLE);
    return null;
  }

  /** Message placeholder name and example. */
  @AutoValue
  public abstract static class Summary {
    public static Summary create(String name) {
      return create(name, /* example */ null);
    }

    public static Summary create(String name, @Nullable String example) {
      return new AutoValue_MessagePlaceholder_Summary(name, example);
    }

    public abstract String name();

    @Nullable
    public abstract String example();
  }

  public static MessagePlaceholder create(String name) {
    return create(name, /* isUserSupplied */ false);
  }

  public static MessagePlaceholder create(String name, boolean isUserSupplied) {
    return new AutoValue_MessagePlaceholder(Summary.create(name), isUserSupplied);
  }

  public abstract Summary summary();

  public abstract boolean isUserSupplied();

  public String name() {
    return summary().name();
  }

  @Nullable
  public String example() {
    return summary().example();
  }
}
