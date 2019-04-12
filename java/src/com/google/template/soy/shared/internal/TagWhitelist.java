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

package com.google.template.soy.shared.internal;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Contains lower-case names of innocuous HTML elements. */
public final class TagWhitelist {

  /** Additional tags which can be white-listed as safe. */
  public static enum OptionalSafeTag {
    HR("hr"),
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

    private static final ImmutableMap<String, OptionalSafeTag> OPTIONAL_SAFE_TAGS_BY_TAG_NAME =
        Maps.uniqueIndex(EnumSet.allOf(OptionalSafeTag.class), OptionalSafeTag::getTagName);
  }

  /** Contains lower-case names of innocuous HTML elements. */
  private final ImmutableSet<String> safeTagNames;

  TagWhitelist(Collection<String> tagNames) {
    this.safeTagNames = ImmutableSet.copyOf(tagNames);
    requireLowerCaseTagNames(this.safeTagNames);
  }

  TagWhitelist(String... tagNames) {
    this(Arrays.asList(tagNames));
  }

  public TagWhitelist withOptionalSafeTags(Collection<? extends OptionalSafeTag> optionalSafeTags) {
    if (optionalSafeTags.isEmpty()) {
      return this;
    }
    ImmutableSet<String> optionalSafeTagNames =
        optionalSafeTags.stream().map(OptionalSafeTag::getTagName).collect(toImmutableSet());
    return new TagWhitelist(Sets.union(safeTagNames, optionalSafeTagNames));
  }

  public boolean isSafeTag(String tagName) {
    return safeTagNames.contains(tagName);
  }

  // No need to handle uper case characters, the assertion below already requires that they are
  // all lower case ascii
  private static final Pattern VALID_TAG_NAME =
      Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z][a-z0-9]*)*\\z");

  // Any changes to this must be reviewed by go/ise-team-yaqs.
  /** A white-list of common formatting tags used by jslayout. */
  public static final TagWhitelist FORMATTING =
      new TagWhitelist(
          "b", "br", "em", "i", "s", "strong", "sub", "sup", "u"
          // Any changes to this must be reviewed by go/ise-team-yaqs.
          );

  public Set<String> asSet() {
    return safeTagNames;
  }

  private static void requireLowerCaseTagNames(Iterable<String> strs) {
    for (String str : strs) {
      Preconditions.checkArgument(
          str.equals(Ascii.toLowerCase(str)) && VALID_TAG_NAME.matcher(str).matches(), str);
    }
  }
}
