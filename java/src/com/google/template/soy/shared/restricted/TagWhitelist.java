/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.shared.restricted;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contains lower-case names of innocuous HTML elements.
 */
public final class TagWhitelist {

  /** Contains lower-case names of innocuous HTML elements. */
  private final ImmutableSet<String> safeTagNames;

  TagWhitelist(Collection<? extends String> tagNames) {
    this.safeTagNames = ImmutableSet.copyOf(tagNames);
    assert requireLowerCaseTagNames(this.safeTagNames);
  }

  TagWhitelist(String... tagNames) {
    this(Arrays.asList(tagNames));
  }

  public boolean isSafeTag(String tagName) {
    return safeTagNames.contains(tagName);
  }

  private static final Pattern VALID_TAG_NAME = Pattern.compile(
      "^[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z][A-Za-z0-9]*)*\\z");

  // Any changes to this must be reviewed by ise-team@.
  /** A white-list of common formatting tags used by jslayout. */
  public static final TagWhitelist FORMATTING = new TagWhitelist(
    "b",
    "br",
    "em",
    "i",
    "s",
    "sub",
    "sup",
    "u"
    // Any changes to this must be reviewed by ise-team@.
    );

  public Set<String> asSet() { return safeTagNames; }

  private static boolean requireLowerCaseTagNames(Iterable<? extends String> strs) {
    for (String str : strs) {
      assert str.equals(str.toLowerCase(Locale.ENGLISH))
          && VALID_TAG_NAME.matcher(str).matches()
          : str;
    }
    // We assert above instead of returning false so that the assertion error contains the
    // offending tag name.
    return true;
  }

}
