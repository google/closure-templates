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
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Meta-data about a placeholder, including original user-supplied values.
 *
 * <p>This also includes static utility functions for validating user-supplied placeholder values.
 */
@Immutable
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
  @Nullable
  static String validatePlaceholderExample(
      String example, SourceLocation location, ErrorReporter reporter) {
    if (!example.isEmpty()) {
      return example;
    }
    reporter.report(location, INVALID_PHNAME_EXAMPLE);
    return null;
  }

  /** Message placeholder name and example. */
  @Immutable
  @AutoValue
  public abstract static class Summary {
    public static Summary create(String name) {
      return create(name, /* example */ Optional.empty());
    }

    public static Summary create(String name, Optional<String> example) {
      return new AutoValue_MessagePlaceholder_Summary(name, example);
    }

    public abstract String name();

    public abstract Optional<String> example();
  }

  public static MessagePlaceholder create(String name) {
    return create(name, /* example */ Optional.empty());
  }

  public static MessagePlaceholder create(String name, Optional<String> example) {
    return new AutoValue_MessagePlaceholder(
        Summary.create(name, example),
        /* userSuppliedName */ Optional.empty(),
        /* userSuppliedNameLocation */ Optional.empty());
  }

  /**
   * Create {@code MessagePlaceholder} with a user-supplied name that's the same as {@code name}.
   */
  public static MessagePlaceholder createWithUserSuppliedName(
      String userSuppliedName, SourceLocation userSuppliedNameLocation) {
    return createWithUserSuppliedName(
        /* name */ userSuppliedName, userSuppliedName, userSuppliedNameLocation);
  }

  /**
   * Create {@code MessagePlaceholder} with a user-supplied name that's different from {@code name}.
   */
  public static MessagePlaceholder createWithUserSuppliedName(
      String name, String userSuppliedName, SourceLocation userSuppliedNameLocation) {
    return createWithUserSuppliedName(
        name, userSuppliedName, userSuppliedNameLocation, /* example */ Optional.empty());
  }

  /**
   * Create {@code MessagePlaceholder} with a user-supplied name that's different from {@code name},
   * and a user-supplied example.
   */
  public static MessagePlaceholder createWithUserSuppliedName(
      String name,
      String userSuppliedName,
      SourceLocation userSuppliedNameLocation,
      Optional<String> example) {
    return new AutoValue_MessagePlaceholder(
        Summary.create(name, example),
        Optional.of(userSuppliedName),
        Optional.of(userSuppliedNameLocation));
  }

  public abstract Summary summary();

  /**
   * User-supplied placeholder name, or {@code null} if not user-supplied. If present, this is
   * generally the same as {@code name}, except that {@code name} is often concerted to UPPER_SNAKE
   * case.
   */
  public abstract Optional<String> userSuppliedName();

  /** Location of the user-supplied placeholder name, or {@code null} if not user-supplied. */
  public abstract Optional<SourceLocation> userSuppliedNameLocation();

  public String name() {
    return summary().name();
  }

  public Optional<String> example() {
    return summary().example();
  }
}
