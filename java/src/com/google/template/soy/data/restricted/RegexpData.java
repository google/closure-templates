/*
 * Copyright 2026 Google Inc.
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

package com.google.template.soy.data.restricted;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.SoyValue;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/** Regexp data. */
@Immutable
public final class RegexpData extends PrimitiveData {

  private final String pattern;
  private final String flags;

  private RegexpData(String pattern, String flags) {
    this.pattern = checkNotNull(pattern);
    this.flags = checkNotNull(flags);
  }

  @Nonnull
  public static RegexpData of(String pattern, String flags) {
    return new RegexpData(pattern, flags);
  }

  public String getPattern() {
    return pattern;
  }

  public String getFlags() {
    return flags;
  }

  /** Converts this JS regex into a Java Pattern instance for evaluation. */
  public Pattern toJavaPattern() {
    int flagsMask = 0;
    if (flags.contains("i")) {
      flagsMask |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    }
    if (flags.contains("m")) {
      flagsMask |= Pattern.MULTILINE;
    }
    if (flags.contains("s")) {
      flagsMask |= Pattern.DOTALL;
    }
    if (flags.contains("u") || flags.contains("v")) {
      flagsMask |= Pattern.UNICODE_CHARACTER_CLASS;
    }
    return Pattern.compile(pattern, flagsMask);
  }

  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public String coerceToString() {
    return "/" + pattern + "/" + flags;
  }

  @Override
  public final SoyValue checkNullishRegexp() {
    return this;
  }

  @Override
  @Nonnull
  public String toString() {
    return coerceToString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(pattern, flags);
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof RegexpData o) {
      return pattern.equals(o.pattern) && flags.equals(o.flags);
    }
    return false;
  }

  @Override
  public String getSoyTypeName() {
    return "regexp";
  }
}
