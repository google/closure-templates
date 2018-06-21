/*
 * Copyright 2013 Google Inc.
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

import java.util.List;
import javax.annotation.Nullable;

/**
 * The identifiers associated with the support for a particular escaping directive.
 *
 */
public final class DirectiveDigest {

  /** The name of the directive to output. */
  private final String directiveName;

  /** Index for the associated escape map name. */
  private final int escapeMapVar;

  /** Escapes name of the object that maps characters to escaped text. */
  private String escapesName;

  /** Index of the associated matcher regex name. */
  private final int matcherVar;

  /** Matcher regex name. */
  private String matcherName;

  /** Index of the associated filter regex name. */
  private final int filterVar;

  /** Filter regex name. */
  private String filterName;

  /** The prefix to use for non-ASCII characters not in the escape map. */
  @Nullable final String nonAsciiPrefix;

  /** Innocuous output for this context. */
  private final String innocuousOutput;

  /**
   * @param directiveName The name of the directive being generated.
   * @param escapeMapVar The index of the associated escape map name.
   * @param matcherVar The index of the associated matcher regex name.
   * @param filterVar The index of the associated filter regex name.
   * @param nonAsciiPrefix The prefix used for non-ASCII characters not in the escape map.
   * @param innocuousOutput Innocuous output for failed filters in this context.
   */
  DirectiveDigest(
      String directiveName,
      int escapeMapVar,
      int matcherVar,
      int filterVar,
      @Nullable String nonAsciiPrefix,
      String innocuousOutput) {
    this.directiveName = directiveName;
    this.escapeMapVar = escapeMapVar;
    this.matcherVar = matcherVar;
    this.filterVar = filterVar;
    this.nonAsciiPrefix = nonAsciiPrefix;
    this.innocuousOutput = innocuousOutput;
  }

  /**
   * Update the escaper, matcher, and filter names based on the supplied lists and indices.
   *
   * @param escapeMapNames The list of escape map names.
   * @param matcherNames The list of matcher regex names.
   * @param filterNames The list of filter regex names.
   */
  public void updateNames(
      List<String> escapeMapNames, List<String> matcherNames, List<String> filterNames) {
    // Store the names for this directive for use in building the helper function.
    escapesName = escapeMapVar >= 0 ? escapeMapNames.get(escapeMapVar) : null;
    matcherName = matcherVar >= 0 ? matcherNames.get(matcherVar) : null;
    filterName = filterVar >= 0 ? filterNames.get(filterVar) : null;
  }

  /** @return the directiveName */
  public String getDirectiveName() {
    return directiveName;
  }

  /** @return the escapesName */
  public String getEscapesName() {
    return escapesName;
  }

  /** @return the matcherName */
  public String getMatcherName() {
    return matcherName;
  }

  /** @return the filterName */
  public String getFilterName() {
    return filterName;
  }

  /** @return the nonAsciiPrefix */
  public String getNonAsciiPrefix() {
    return nonAsciiPrefix;
  }

  /** @return the innocuousOutput */
  public String getInnocuousOutput() {
    return innocuousOutput;
  }
}
