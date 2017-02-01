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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Contains lower-case names of innocuous HTML elements. */
public final class TagWhitelist {

  /** Additional tags which can be white-listed as safe. */
  public static enum OptionalSafeTag {
    LI("li"),
    OL("ol"),
    SPAN("span"),
    UL("ul");

    private final String tagName;

    OptionalSafeTag(String tagName) {
      this.tagName = tagName;
    }

    public String getTagName() {
      return tagName;
    }

    public static OptionalSafeTag fromTagName(String tagName) {
      OptionalSafeTag tag = OPTIONAL_SAFE_TAGS_BY_TAG_NAME.get(tagName);
      if (tag == null) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid optional safe tag.", tagName));
      }
      return tag;
    }

    public static final Function<String, OptionalSafeTag> FROM_TAG_NAME =
        new Function<String, OptionalSafeTag>() {
          @Override
          public OptionalSafeTag apply(String tagName) {
            return fromTagName(tagName);
          }
        };

    public static final Function<OptionalSafeTag, String> TO_TAG_NAME =
        new Function<OptionalSafeTag, String>() {
          @Override
          public String apply(OptionalSafeTag tag) {
            return tag.getTagName();
          }
        };

    private static final ImmutableMap<String, OptionalSafeTag> OPTIONAL_SAFE_TAGS_BY_TAG_NAME =
        Maps.uniqueIndex(EnumSet.allOf(OptionalSafeTag.class), TO_TAG_NAME);
  }

  /** Contains lower-case names of innocuous HTML elements. */
  private final ImmutableSet<String> safeTagNames;

  TagWhitelist(Collection<String> tagNames) {
    this.safeTagNames = ImmutableSet.copyOf(tagNames);
    assert requireLowerCaseTagNames(this.safeTagNames);
  }

  TagWhitelist(String... tagNames) {
    this(Arrays.asList(tagNames));
  }

  public TagWhitelist withOptionalSafeTags(Collection<? extends OptionalSafeTag> optionalSafeTags) {
    if (optionalSafeTags.isEmpty()) {
      return this;
    }
    ImmutableSet<String> optionalSafeTagNames =
        FluentIterable.from(optionalSafeTags).transform(OptionalSafeTag.TO_TAG_NAME).toSet();
    return new TagWhitelist(Sets.union(safeTagNames, optionalSafeTagNames));
  }

  public boolean isSafeTag(String tagName) {
    return safeTagNames.contains(tagName);
  }

  private static final Pattern VALID_TAG_NAME =
      Pattern.compile("^[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z][A-Za-z0-9]*)*\\z");

  // Any changes to this must be reviewed by ise-team@.
  /** A white-list of common formatting tags used by jslayout. */
  public static final TagWhitelist FORMATTING =
      new TagWhitelist(
          "b", "br", "em", "i", "s", "sub", "sup", "u"
          // Any changes to this must be reviewed by ise-team@.
          );

  public Set<String> asSet() {
    return safeTagNames;
  }

  private static boolean requireLowerCaseTagNames(Iterable<String> strs) {
    for (String str : strs) {
      assert str.equals(str.toLowerCase(Locale.ENGLISH)) && VALID_TAG_NAME.matcher(str).matches()
          : str;
    }
    // We assert above instead of returning false so that the assertion error contains the
    // offending tag name.
    return true;
  }
}
